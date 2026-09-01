package com.rafael.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rafael.GreatSageMod;
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
import java.util.concurrent.TimeUnit;

/**
 * Reads an operator-local authorization marker/source manifest and acquires voice
 * references automatically. Actor/source URLs are intentionally not shipped in
 * the public repository; the local deploy bootstrap writes them only for an
 * authorized installation.
 */
public final class AuthorizedVoiceReferenceManager {
    private static final String YTDLP_VERSION = "2026.08.19";
    private static final String YTDLP_BASE = "https://github.com/yt-dlp/yt-dlp/releases/download/" + YTDLP_VERSION + "/";
    private static final long MIN_REFERENCE_BYTES = 80_000L;
    private static final long MIN_YTDLP_BYTES = 2_000_000L;
    private static final Object LOCK = new Object();

    private AuthorizedVoiceReferenceManager() {}

    public static boolean isAuthorizedLocally() {
        Path root = root();
        return Files.isRegularFile(root.resolve("authorization.accepted"))
                && Files.isRegularFile(root.resolve("sources.json"));
    }

    public static long manifestStamp() {
        try {
            Path manifest = root().resolve("sources.json");
            return Files.isRegularFile(manifest) ? Files.getLastModifiedTime(manifest).toMillis() : 0L;
        } catch (Exception ignored) {
            return 0L;
        }
    }

    public static List<ReferenceSample> prepare(String language) throws Exception {
        String lang = RafaelLanguageManager.normalize(language);
        synchronized (LOCK) {
            if (!isAuthorizedLocally()) {
                throw new IOException("Authorized voice source manifest is not installed locally.");
            }

            JsonObject manifest = JsonParser.parseString(Files.readString(root().resolve("sources.json"), StandardCharsets.UTF_8)).getAsJsonObject();
            String key = RafaelLanguageManager.isSpanish(lang) ? "spanish" : "english";
            if (!manifest.has(key) || !manifest.get(key).isJsonArray()) {
                throw new IOException("No authorized references configured for " + key + ".");
            }

            Path referenceDir = root().resolve("references").resolve(lang);
            Files.createDirectories(referenceDir);
            JsonArray array = manifest.getAsJsonArray(key);
            List<ReferenceSample> samples = new ArrayList<>();
            List<String> errors = new ArrayList<>();

            for (int i = 0; i < array.size(); i++) {
                JsonElement element = array.get(i);
                if (!element.isJsonObject()) continue;
                JsonObject source = element.getAsJsonObject();
                try {
                    String type = string(source, "type", "direct").trim().toLowerCase(Locale.ROOT);
                    String url = string(source, "url", "").trim();
                    if (url.isBlank()) continue;
                    validateHttps(url);
                    double skip = number(source, "skipSeconds", 0.5);
                    double duration = number(source, "durationSeconds", 20.0);

                    Path file;
                    String format;
                    if ("soundcloud_mp3".equals(type) || "media_mp3".equals(type)) {
                        file = referenceDir.resolve("reference-" + i + ".mp3");
                        format = "mp3";
                        if (!validFile(file, MIN_REFERENCE_BYTES)) downloadMp3WithYtDlp(url, file);
                    } else {
                        String extension = extensionFromUrl(url);
                        if (!extension.equals("wav") && !extension.equals("mp3")) extension = string(source, "format", "wav").toLowerCase(Locale.ROOT);
                        if (!extension.equals("wav") && !extension.equals("mp3")) throw new IOException("Unsupported direct reference format: " + extension);
                        file = referenceDir.resolve("reference-" + i + "." + extension);
                        format = extension;
                        if (!validFile(file, MIN_REFERENCE_BYTES)) downloadDirect(url, file, MIN_REFERENCE_BYTES);
                    }
                    if (validFile(file, MIN_REFERENCE_BYTES)) {
                        samples.add(new ReferenceSample(file, format, Math.max(0.0, skip), Math.max(4.0, Math.min(30.0, duration))));
                    }
                } catch (Exception e) {
                    errors.add("#" + i + " " + e.getMessage());
                    GreatSageMod.LOGGER.warn("Authorized voice reference {} could not be prepared: {}", i, e.toString());
                }
            }

            if (samples.isEmpty()) {
                throw new IOException("No authorized reference could be acquired" + (errors.isEmpty() ? "." : ": " + String.join(" | ", errors)));
            }
            return samples;
        }
    }

    public static Path root() {
        return FMLPaths.GAMEDIR.get().resolve("great_sage_voice").resolve("authorized_voice");
    }

    private static void downloadMp3WithYtDlp(String mediaUrl, Path target) throws Exception {
        Path tool = ensureYtDlp();
        Files.createDirectories(target.getParent());
        Files.deleteIfExists(target);
        Path tempTemplate = target.resolveSibling(target.getFileName().toString() + ".part.%(ext)s");

        List<String> command = new ArrayList<>();
        command.add(tool.toAbsolutePath().toString());
        command.add("--no-playlist");
        command.add("--no-warnings");
        command.add("--no-progress");
        command.add("--force-overwrites");
        command.add("--socket-timeout");
        command.add("30");
        command.add("-f");
        command.add("http_mp3_1_0/bestaudio[ext=mp3]");
        command.add("-o");
        command.add(tempTemplate.toAbsolutePath().toString());
        command.add(mediaUrl);

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(target.getParent().toFile());
        builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        builder.redirectError(ProcessBuilder.Redirect.DISCARD);
        Process process = builder.start();
        boolean finished = process.waitFor(120, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            cleanupDownloadParts(target);
            throw new IOException("Reference media download timed out.");
        }
        if (process.exitValue() != 0) {
            cleanupDownloadParts(target);
            throw new IOException("yt-dlp could not acquire the authorized reference (exit " + process.exitValue() + ").");
        }

        Path downloaded = findDownloadedPart(target);
        if (downloaded == null || !validFile(downloaded, MIN_REFERENCE_BYTES)) {
            cleanupDownloadParts(target);
            throw new IOException("yt-dlp did not produce a usable MP3 reference.");
        }
        moveReplace(downloaded, target);
        cleanupDownloadParts(target);
    }

    private static Path ensureYtDlp() throws Exception {
        Path tools = root().resolve("tools");
        Files.createDirectories(tools);
        YtDlpAsset asset = ytDlpAsset();
        Path tool = tools.resolve(asset.fileName());
        if (validFile(tool, MIN_YTDLP_BYTES) && asset.sha256().equalsIgnoreCase(sha256(tool))) {
            makeExecutable(tool);
            return tool;
        }
        Files.deleteIfExists(tool);
        downloadDirect(YTDLP_BASE + asset.fileName(), tool, MIN_YTDLP_BYTES);
        String digest = sha256(tool);
        if (!asset.sha256().equalsIgnoreCase(digest)) {
            Files.deleteIfExists(tool);
            throw new IOException("yt-dlp checksum mismatch: " + digest);
        }
        makeExecutable(tool);
        return tool;
    }

    private static YtDlpAsset ytDlpAsset() throws IOException {
        String os = osName();
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        if ("windows".equals(os) && isX64(arch)) {
            return new YtDlpAsset("yt-dlp.exe", "66674953fe251b89f4d08c5f0e35e0728679bd67ab3d7d05c0562af101dd3e7a");
        }
        if ("linux".equals(os) && isX64(arch)) {
            return new YtDlpAsset("yt-dlp_linux", "58162f9bfdc27458ea47bfcb311cf47028f17d8154a8bf7d689861d46399230a");
        }
        throw new IOException("Automatic MP3 reference acquisition is currently supported on Windows x64 and Linux x64; platform=" + os + "/" + arch);
    }

    private static Path findDownloadedPart(Path target) throws IOException {
        String prefix = target.getFileName().toString() + ".part.";
        try (var stream = Files.list(target.getParent())) {
            return stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().startsWith(prefix))
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".mp3"))
                    .findFirst().orElse(null);
        }
    }

    private static void cleanupDownloadParts(Path target) {
        try (var stream = Files.list(target.getParent())) {
            String prefix = target.getFileName().toString() + ".part.";
            stream.filter(path -> path.getFileName().toString().startsWith(prefix)).forEach(path -> {
                try { Files.deleteIfExists(path); } catch (Exception ignored) {}
            });
        } catch (Exception ignored) {}
    }

    private static void downloadDirect(String url, Path target, long minBytes) throws IOException {
        Files.createDirectories(target.getParent());
        Path temp = target.resolveSibling(target.getFileName().toString() + ".download");
        Files.deleteIfExists(temp);
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setInstanceFollowRedirects(true);
        connection.setConnectTimeout(15_000);
        connection.setReadTimeout(60_000);
        connection.setRequestProperty("User-Agent", "GreatSageVoice/1.4.0");
        int code = connection.getResponseCode();
        if (code < 200 || code >= 300) {
            connection.disconnect();
            throw new IOException("HTTP " + code + " while downloading authorized voice asset.");
        }
        long total = 0L;
        try (InputStream input = new BufferedInputStream(connection.getInputStream()); OutputStream output = new BufferedOutputStream(Files.newOutputStream(temp))) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read == 0) continue;
                output.write(buffer, 0, read);
                total += read;
                if (total > 300_000_000L) throw new IOException("Authorized voice asset exceeded 300 MB safety limit.");
            }
        } finally {
            connection.disconnect();
        }
        if (total < minBytes) {
            Files.deleteIfExists(temp);
            throw new IOException("Downloaded authorized voice asset is incomplete (" + total + " bytes).");
        }
        moveReplace(temp, target);
    }

    private static void validateHttps(String raw) throws Exception {
        URL url = new URL(raw);
        if (!"https".equalsIgnoreCase(url.getProtocol())) throw new IOException("Only HTTPS authorized reference URLs are accepted.");
    }

    private static boolean validFile(Path path, long minBytes) {
        try { return Files.isRegularFile(path) && Files.size(path) >= minBytes; }
        catch (Exception ignored) { return false; }
    }

    private static String extensionFromUrl(String raw) {
        try {
            String path = new URL(raw).getPath().toLowerCase(Locale.ROOT);
            int dot = path.lastIndexOf('.');
            if (dot >= 0 && dot + 1 < path.length()) return path.substring(dot + 1);
        } catch (Exception ignored) {}
        return "";
    }

    private static String string(JsonObject object, String name, String fallback) {
        try { return object.has(name) ? object.get(name).getAsString() : fallback; }
        catch (Exception ignored) { return fallback; }
    }

    private static double number(JsonObject object, String name, double fallback) {
        try { return object.has(name) ? object.get(name).getAsDouble() : fallback; }
        catch (Exception ignored) { return fallback; }
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

    private static void makeExecutable(Path path) {
        try { path.toFile().setExecutable(true, false); } catch (Exception ignored) {}
    }

    private static String osName() {
        String value = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (value.contains("win")) return "windows";
        if (value.contains("linux")) return "linux";
        if (value.contains("mac") || value.contains("darwin")) return "mac";
        return value.replaceAll("\\s+", "_");
    }

    private static boolean isX64(String arch) {
        return arch.contains("amd64") || arch.contains("x86_64") || arch.contains("x64");
    }

    public record ReferenceSample(Path path, String format, double skipSeconds, double durationSeconds) {}
    private record YtDlpAsset(String fileName, String sha256) {}
}
