package com.rafael.server;

import com.rafael.GreatSageMod;
import com.rafael.config.GreatSageConfig;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Self-managed bilingual Piper runtime used by integrated and dedicated Forge servers. */
public final class OfflineVoiceEngine {
    private static final Object INSTALL_LOCK = new Object();
    private static final String PIPER_RELEASE = "2023.11.14-2";
    private static final String PIPER_RELEASE_BASE = "https://github.com/rhasspy/piper/releases/download/" + PIPER_RELEASE + "/";
    private static final long MIN_ENGINE_ARCHIVE_BYTES = 15_000_000L;

    private static final VoiceProfile SPANISH = new VoiceProfile(
            "es", "es_AR-daniela-high", "Daniela High / es-419",
            "https://huggingface.co/rhasspy/piper-voices/resolve/main/es/es_AR/daniela/high/",
            "es_AR-daniela-high.onnx",
            "7ceb1fc0dab349418c5b54a639ae9ee595212d7c9ea422220d8419163d5cc985",
            110_000_000L);

    private static final VoiceProfile ENGLISH = new VoiceProfile(
            "en", "en_US-lessac-high", "Lessac High / en-US",
            "https://huggingface.co/rhasspy/piper-voices/resolve/main/en/en_US/lessac/high/",
            "en_US-lessac-high.onnx",
            "4cabf7c3a638017137f34a1516522032d4fe3f38228a843cc9b764ddcbcd9e09",
            110_000_000L);

    private static volatile Path executable;
    private static final Map<String, VoiceFiles> VOICES = new ConcurrentHashMap<>();
    private static final Map<String, String> STATES = new ConcurrentHashMap<>();
    private static final Map<String, String> DETAILS = new ConcurrentHashMap<>();

    private OfflineVoiceEngine() {}

    public static void prepare(String language) throws Exception {
        ensureReady(language);
    }

    public static boolean isReady(String language) {
        VoiceFiles files = VOICES.get(profileFor(language).key());
        Path exe = executable;
        return exe != null && files != null && Files.isRegularFile(exe)
                && Files.isRegularFile(files.model()) && Files.isRegularFile(files.config());
    }

    public static String statusSummary(String language) {
        VoiceProfile profile = profileFor(language);
        String state = STATES.getOrDefault(profile.key(), "pending");
        String detail = DETAILS.getOrDefault(profile.key(), profile.displayName());
        return state + " / " + detail;
    }

    public static String voiceIdentity(String language) {
        VoiceProfile profile = profileFor(language);
        VoiceFiles files = VOICES.get(profile.key());
        if (files != null && files.custom()) {
            try {
                return "custom-" + profile.key() + "-" + Files.getLastModifiedTime(files.model()).toMillis();
            } catch (Exception ignored) {
                return "custom-" + profile.key();
            }
        }
        return profile.id();
    }

    public static byte[] synthesize(String text, String language) throws Exception {
        if (text == null || text.isBlank()) return new byte[0];
        VoiceProfile profile = profileFor(language);
        ensureReady(profile.key());
        VoiceFiles voice = VOICES.get(profile.key());

        Path outputDir = runtimeRoot().resolve("output");
        Files.createDirectories(outputDir);
        Path output = Files.createTempFile(outputDir, "raphael-" + profile.key() + "-", ".wav");

        List<String> command = new ArrayList<>();
        command.add(executable.toAbsolutePath().toString());
        command.add("--model");
        command.add(voice.model().toAbsolutePath().toString());
        command.add("--config");
        command.add(voice.config().toAbsolutePath().toString());
        command.add("--output_file");
        command.add(output.toAbsolutePath().toString());
        command.add("--length_scale");
        command.add(formatDouble(RafaelLanguageManager.isSpanish(profile.key())
                ? GreatSageConfig.SERVER.offlineLengthScale.get()
                : GreatSageConfig.SERVER.offlineEnglishLengthScale.get()));
        command.add("--noise_scale");
        command.add(formatDouble(GreatSageConfig.SERVER.offlineNoiseScale.get()));
        command.add("--noise_w");
        command.add(formatDouble(GreatSageConfig.SERVER.offlineNoiseWidth.get()));
        command.add("--sentence_silence");
        command.add("0.065");
        command.add("--quiet");

        ProcessBuilder builder = new ProcessBuilder(command);
        Path engineDir = executable.getParent();
        builder.directory(engineDir.toFile());
        builder.redirectErrorStream(true);
        String os = osName();
        if ("linux".equals(os)) prependEnv(builder, "LD_LIBRARY_PATH", engineDir.toAbsolutePath().toString());
        if ("mac".equals(os)) prependEnv(builder, "DYLD_LIBRARY_PATH", engineDir.toAbsolutePath().toString());

        Process process = builder.start();
        String cleanText = normalizeSpeechText(text, profile.key());
        try (OutputStream stdin = process.getOutputStream()) {
            stdin.write(cleanText.getBytes(StandardCharsets.UTF_8));
            stdin.write('\n');
        }

        boolean finished = process.waitFor(GreatSageConfig.SERVER.offlineSynthesisTimeoutSeconds.get(), TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            Files.deleteIfExists(output);
            throw new IOException("Piper synthesis timeout.");
        }
        byte[] processLog = process.getInputStream().readAllBytes();
        if (process.exitValue() != 0) {
            Files.deleteIfExists(output);
            throw new IOException("Piper exit code " + process.exitValue() + ": " + abbreviate(new String(processLog, StandardCharsets.UTF_8), 500));
        }
        if (!Files.isRegularFile(output) || Files.size(output) < 44) {
            Files.deleteIfExists(output);
            throw new IOException("Piper did not produce a valid WAV.");
        }

        byte[] wav = Files.readAllBytes(output);
        Files.deleteIfExists(output);
        return wav;
    }

    private static void ensureReady(String language) throws Exception {
        VoiceProfile profile = profileFor(language);
        if (isReady(profile.key())) return;
        synchronized (INSTALL_LOCK) {
            if (isReady(profile.key())) return;
            try {
                STATES.put(profile.key(), "preparing");
                DETAILS.put(profile.key(), "runtime / " + profile.displayName());
                Path root = runtimeRoot();
                Path engineRoot = root.resolve("engine");
                Files.createDirectories(engineRoot);

                Path foundExe = findPiperExecutable(engineRoot);
                if (foundExe == null) {
                    if (!GreatSageConfig.SERVER.autoInstallOfflineVoice.get()) throw new IOException("Automatic Piper installation is disabled.");
                    PlatformAsset asset = platformAsset();
                    Path archive = root.resolve(asset.fileName());
                    download(asset.url(), archive, MIN_ENGINE_ARCHIVE_BYTES, profile.key(), "Piper runtime");
                    DETAILS.put(profile.key(), "extracting Piper runtime");
                    if (asset.zip()) extractZip(archive, engineRoot); else extractTarGz(archive, engineRoot);
                    Files.deleteIfExists(archive);
                    foundExe = findPiperExecutable(engineRoot);
                    if (foundExe == null) throw new IOException("Piper executable not found after extraction.");
                }
                makeExecutable(foundExe);
                makeNearbyExecutables(foundExe.getParent());
                executable = foundExe;

                VoiceFiles custom = customVoice(profile);
                if (custom != null) {
                    VOICES.put(profile.key(), custom);
                    STATES.put(profile.key(), "ready");
                    DETAILS.put(profile.key(), "custom authorized model / " + profile.key().toUpperCase(Locale.ROOT));
                    GreatSageMod.LOGGER.info("Raphael using custom local {} voice model: {}", profile.key(), custom.model());
                    return;
                }

                if (!GreatSageConfig.SERVER.autoInstallOfflineVoice.get()) throw new IOException("Automatic voice model installation is disabled.");
                Path voiceRoot = root.resolve("voice").resolve(profile.key());
                Files.createDirectories(voiceRoot);
                migrateLegacySpanish(root, voiceRoot, profile);

                Path model = voiceRoot.resolve(profile.modelName());
                Path config = voiceRoot.resolve(profile.configName());
                if (!Files.isRegularFile(model) || Files.size(model) < profile.minModelBytes() || !profile.sha256().equalsIgnoreCase(sha256(model))) {
                    Files.deleteIfExists(model);
                    download(profile.baseUrl() + profile.modelName() + "?download=true", model, profile.minModelBytes(), profile.key(), profile.displayName());
                    String digest = sha256(model);
                    if (!profile.sha256().equalsIgnoreCase(digest)) {
                        Files.deleteIfExists(model);
                        throw new IOException("Voice model checksum mismatch: " + digest);
                    }
                }
                if (!Files.isRegularFile(config) || Files.size(config) < 1000) {
                    Files.deleteIfExists(config);
                    download(profile.baseUrl() + profile.configName() + "?download=true", config, 1000, profile.key(), profile.displayName() + " config");
                }

                VOICES.put(profile.key(), new VoiceFiles(model, config, false));
                STATES.put(profile.key(), "ready");
                DETAILS.put(profile.key(), profile.displayName());
                GreatSageMod.LOGGER.info("Raphael offline {} voice ready: {}", profile.key(), model);
            } catch (Exception e) {
                STATES.put(profile.key(), "error");
                DETAILS.put(profile.key(), abbreviate(e.getMessage() == null ? e.toString() : e.getMessage(), 160));
                throw e;
            }
        }
    }

    private static VoiceFiles customVoice(VoiceProfile profile) {
        if (!GreatSageConfig.SERVER.preferCustomVoiceModels.get()) return null;
        Path root = FMLPaths.GAMEDIR.get().resolve("great_sage_voice").resolve("custom_voice");
        Path model = root.resolve(profile.key() + ".onnx");
        Path config = root.resolve(profile.key() + ".onnx.json");
        try {
            if (Files.isRegularFile(model) && Files.size(model) > 1_000_000L && Files.isRegularFile(config) && Files.size(config) > 500L) {
                return new VoiceFiles(model, config, true);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static void migrateLegacySpanish(Path root, Path voiceRoot, VoiceProfile profile) {
        if (!"es".equals(profile.key())) return;
        Path legacyRoot = root.resolve("voice");
        Path oldModel = legacyRoot.resolve("es_AR-daniela-high.onnx");
        Path oldConfig = legacyRoot.resolve("es_AR-daniela-high.onnx.json");
        Path newModel = voiceRoot.resolve(profile.modelName());
        Path newConfig = voiceRoot.resolve(profile.configName());
        try {
            if (!Files.exists(newModel) && Files.isRegularFile(oldModel)) moveReplace(oldModel, newModel);
            if (!Files.exists(newConfig) && Files.isRegularFile(oldConfig)) moveReplace(oldConfig, newConfig);
        } catch (Exception e) {
            GreatSageMod.LOGGER.debug("Could not migrate v1.2 Spanish voice cache: {}", e.toString());
        }
    }

    private static String normalizeSpeechText(String text, String language) {
        String clean = text.replace('…', '.').replace('\r', ' ').replace('\n', ' ').replaceAll("\\s+", " ").trim();
        if (RafaelLanguageManager.isSpanish(language)) {
            clean = clean.replace("HP", "puntos de vida").replace("XP", "experiencia");
        } else {
            clean = clean.replace("HP", "health points").replace("XP", "experience");
        }
        return clean;
    }

    private static VoiceProfile profileFor(String language) {
        return RafaelLanguageManager.isSpanish(language) ? SPANISH : ENGLISH;
    }

    private static Path runtimeRoot() {
        return FMLPaths.GAMEDIR.get().resolve("great_sage_voice").resolve("offline_voice");
    }

    private static PlatformAsset platformAsset() throws IOException {
        String os = osName();
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        if ("windows".equals(os) && isX64(arch)) return new PlatformAsset("piper_windows_amd64.zip", true);
        if ("linux".equals(os) && isX64(arch)) return new PlatformAsset("piper_linux_x86_64.tar.gz", false);
        if ("linux".equals(os) && isArm64(arch)) return new PlatformAsset("piper_linux_aarch64.tar.gz", false);
        if ("mac".equals(os) && isX64(arch)) return new PlatformAsset("piper_macos_x64.tar.gz", false);
        if ("mac".equals(os) && isArm64(arch)) return new PlatformAsset("piper_macos_aarch64.tar.gz", false);
        throw new IOException("Unsupported Piper platform: " + os + "/" + arch);
    }

    private static String osName() {
        String value = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (value.contains("win")) return "windows";
        if (value.contains("mac") || value.contains("darwin")) return "mac";
        if (value.contains("linux")) return "linux";
        return value.replaceAll("\\s+", "_");
    }

    private static boolean isX64(String arch) { return arch.contains("amd64") || arch.contains("x86_64") || arch.contains("x64"); }
    private static boolean isArm64(String arch) { return arch.contains("aarch64") || arch.contains("arm64"); }

    private static void download(String url, Path target, long minBytes, String language, String label) throws IOException {
        Files.createDirectories(target.getParent());
        Path temp = target.resolveSibling(target.getFileName() + ".part");
        Files.deleteIfExists(temp);
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setInstanceFollowRedirects(true);
        connection.setConnectTimeout(15_000);
        connection.setReadTimeout(60_000);
        connection.setRequestProperty("User-Agent", "GreatSageVoice/1.3.0");
        int code = connection.getResponseCode();
        if (code < 200 || code >= 300) {
            connection.disconnect();
            throw new IOException("Download failed for " + label + ": HTTP " + code);
        }
        long expected = connection.getContentLengthLong();
        STATES.put(language, "downloading");
        DETAILS.put(language, label);
        long total = 0L;
        int lastBucket = -1;
        try (InputStream input = new BufferedInputStream(connection.getInputStream()); OutputStream output = new BufferedOutputStream(Files.newOutputStream(temp))) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read == 0) continue;
                output.write(buffer, 0, read);
                total += read;
                if (expected > 0) {
                    int bucket = (int) ((total * 10L) / expected);
                    if (bucket != lastBucket && bucket >= 1) {
                        lastBucket = bucket;
                        DETAILS.put(language, label + " " + Math.min(100, bucket * 10) + "%");
                    }
                }
            }
        } finally {
            connection.disconnect();
        }
        if (total < minBytes) {
            Files.deleteIfExists(temp);
            throw new IOException("Incomplete download for " + label + ": " + total + " bytes");
        }
        moveReplace(temp, target);
    }

    private static void extractZip(Path archive, Path destination) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(Files.newInputStream(archive)))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                Path target = safeResolve(destination, entry.getName());
                if (entry.isDirectory()) Files.createDirectories(target);
                else {
                    Files.createDirectories(target.getParent());
                    try (OutputStream output = new BufferedOutputStream(Files.newOutputStream(target))) { zip.transferTo(output); }
                }
                zip.closeEntry();
            }
        }
    }

    private static void extractTarGz(Path archive, Path destination) throws IOException {
        List<SymlinkEntry> links = new ArrayList<>();
        try (InputStream input = new GZIPInputStream(new BufferedInputStream(Files.newInputStream(archive)))) {
            byte[] header = new byte[512];
            while (true) {
                int headerRead = readFully(input, header, 0, 512);
                if (headerRead == 0 || isZeroBlock(header)) break;
                if (headerRead != 512) throw new IOException("Incomplete TAR header.");
                String name = tarString(header, 0, 100);
                String prefix = tarString(header, 345, 155);
                if (!prefix.isBlank()) name = prefix + "/" + name;
                long size = tarOctal(header, 124, 12);
                char type = (char) header[156];
                String linkName = tarString(header, 157, 100);
                Path target = safeResolve(destination, name);
                if (type == '5') {
                    Files.createDirectories(target);
                    skipFully(input, size);
                } else if (type == '2') {
                    Files.createDirectories(target.getParent());
                    links.add(new SymlinkEntry(target, linkName));
                    skipFully(input, size);
                } else if (type == 0 || type == '0') {
                    Files.createDirectories(target.getParent());
                    try (OutputStream output = new BufferedOutputStream(Files.newOutputStream(target))) { copyExactly(input, output, size); }
                } else {
                    skipFully(input, size);
                }
                long padding = (512 - (size % 512)) % 512;
                skipFully(input, padding);
            }
        }
        for (SymlinkEntry link : links) createSymlinkOrCopy(link.path(), link.target());
    }

    private static void createSymlinkOrCopy(Path link, String targetText) throws IOException {
        Files.deleteIfExists(link);
        try {
            Files.createSymbolicLink(link, Path.of(targetText));
        } catch (Exception ignored) {
            Path source = link.getParent().resolve(targetText).normalize();
            if (Files.isRegularFile(source)) Files.copy(source, link, StandardCopyOption.REPLACE_EXISTING);
            else GreatSageMod.LOGGER.warn("Could not recreate Piper symlink '{}' -> '{}'", link, targetText);
        }
    }

    private static Path findPiperExecutable(Path root) throws IOException {
        if (!Files.exists(root)) return null;
        String wanted = "windows".equals(osName()) ? "piper.exe" : "piper";
        try (var stream = Files.walk(root, 4)) {
            return stream.filter(Files::isRegularFile).filter(path -> path.getFileName().toString().equalsIgnoreCase(wanted)).findFirst().orElse(null);
        }
    }

    private static void makeNearbyExecutables(Path directory) {
        if (directory == null || !Files.isDirectory(directory)) return;
        try (var stream = Files.list(directory)) {
            stream.filter(Files::isRegularFile).filter(path -> {
                String n = path.getFileName().toString().toLowerCase(Locale.ROOT);
                return n.equals("piper") || n.equals("piper_phonemize") || n.equals("espeak-ng");
            }).forEach(OfflineVoiceEngine::makeExecutable);
        } catch (Exception ignored) {}
    }

    private static void makeExecutable(Path path) { if (path != null) path.toFile().setExecutable(true, false); }

    private static void prependEnv(ProcessBuilder builder, String key, String value) {
        String old = builder.environment().get(key);
        builder.environment().put(key, value + (old == null || old.isBlank() ? "" : java.io.File.pathSeparator + old));
    }

    private static Path safeResolve(Path root, String child) throws IOException {
        Path resolved = root.resolve(child).normalize();
        if (!resolved.startsWith(root.normalize())) throw new IOException("Unsafe archive entry: " + child);
        return resolved;
    }

    private static String sha256(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) if (read > 0) digest.update(buffer, 0, read);
        }
        StringBuilder out = new StringBuilder(64);
        for (byte b : digest.digest()) out.append(String.format(Locale.ROOT, "%02x", b));
        return out.toString();
    }

    private static void moveReplace(Path source, Path target) throws IOException {
        try { Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (Exception ignored) { Files.move(source, target, StandardCopyOption.REPLACE_EXISTING); }
    }

    private static boolean isZeroBlock(byte[] block) { for (byte b : block) if (b != 0) return false; return true; }

    private static String tarString(byte[] block, int offset, int length) {
        int end = offset;
        int max = Math.min(block.length, offset + length);
        while (end < max && block[end] != 0) end++;
        return new String(block, offset, Math.max(0, end - offset), StandardCharsets.UTF_8).trim();
    }

    private static long tarOctal(byte[] block, int offset, int length) {
        String value = tarString(block, offset, length).replace("\u0000", "").trim();
        if (value.isEmpty()) return 0L;
        try { return Long.parseLong(value, 8); } catch (NumberFormatException e) { return 0L; }
    }

    private static int readFully(InputStream input, byte[] buffer, int offset, int length) throws IOException {
        int total = 0;
        while (total < length) {
            int read = input.read(buffer, offset + total, length - total);
            if (read < 0) break;
            total += read;
        }
        return total;
    }

    private static void copyExactly(InputStream input, OutputStream output, long size) throws IOException {
        byte[] buffer = new byte[64 * 1024];
        long remaining = size;
        while (remaining > 0) {
            int read = input.read(buffer, 0, (int) Math.min(buffer.length, remaining));
            if (read < 0) throw new IOException("TAR ended before file completion.");
            output.write(buffer, 0, read);
            remaining -= read;
        }
    }

    private static void skipFully(InputStream input, long bytes) throws IOException {
        long remaining = bytes;
        while (remaining > 0) {
            long skipped = input.skip(remaining);
            if (skipped > 0) { remaining -= skipped; continue; }
            if (input.read() < 0) throw new IOException("TAR ended during skip.");
            remaining--;
        }
    }

    private static String formatDouble(double value) { return String.format(Locale.ROOT, "%.3f", value); }
    private static String abbreviate(String value, int max) { return value == null ? "" : (value.length() <= max ? value : value.substring(0, max) + "…"); }

    private record VoiceProfile(String key, String id, String displayName, String baseUrl, String modelName, String sha256, long minModelBytes) {
        String configName() { return modelName + ".json"; }
    }
    private record VoiceFiles(Path model, Path config, boolean custom) {}
    private record PlatformAsset(String fileName, boolean zip) { String url() { return PIPER_RELEASE_BASE + fileName; } }
    private record SymlinkEntry(Path path, String target) {}
}
