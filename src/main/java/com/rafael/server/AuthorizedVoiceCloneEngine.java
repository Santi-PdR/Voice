package com.rafael.server;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import com.rafael.GreatSageMod;
import com.rafael.config.GreatSageConfig;
import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;
import net.minecraftforge.fml.loading.FMLPaths;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.FloatBuffer;
import java.nio.LongBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Local zero-shot timbre transfer for installations that have an operator-local
 * authorization marker and source manifest. Piper remains the prosody/base TTS;
 * OpenVoice V2 ONNX transfers the authorized target tone color. No persistent
 * Python/backend process is used.
 */
public final class AuthorizedVoiceCloneEngine {
    private static final String MODEL_BASE = "https://huggingface.co/TigreGotico/voiceclonnx-openvoice-v2/resolve/main/";
    private static final String ENCODER_FILE = "tone_ref_encoder_q8.onnx";
    private static final String CONVERTER_FILE = "tone_converter_q8.onnx";
    private static final String ENCODER_SHA256 = "8e46097e46a68a2137acf105b58bc67cf686ec0d811c1c45ada28557a608c0e3";
    private static final String CONVERTER_SHA256 = "54ea73764c46cdbb74af2124e30cce42007045f1f5a60bd7520472f155eb6f4c";
    private static final long ENCODER_MIN_BYTES = 2_000_000L;
    private static final long CONVERTER_MIN_BYTES = 38_000_000L;
    private static final int SAMPLE_RATE = 22_050;
    private static final int N_FFT = 1024;
    private static final int HOP = 256;
    private static final int PAD = (N_FFT - HOP) / 2;
    private static final int BINS = N_FFT / 2 + 1;
    private static final int EMBEDDING_SIZE = 256;
    private static final int EMBEDDING_CACHE_VERSION = 2;
    private static final int MAX_SOURCE_SECONDS = 22;
    private static final Object INSTALL_LOCK = new Object();
    private static final Object INFERENCE_LOCK = new Object();

    private static final OrtEnvironment ENV = OrtEnvironment.getEnvironment();
    private static volatile OrtSession encoderSession;
    private static volatile OrtSession converterSession;
    private static final Map<String, float[]> TARGET_EMBEDDINGS = new ConcurrentHashMap<>();
    private static final Map<String, float[]> SOURCE_EMBEDDINGS = new ConcurrentHashMap<>();
    private static final Map<String, String> STATES = new ConcurrentHashMap<>();
    private static final Map<String, String> DETAILS = new ConcurrentHashMap<>();

    private AuthorizedVoiceCloneEngine() {}

    public static boolean shouldUse(String language) {
        return GreatSageConfig.SERVER.enableAuthorizedVoiceClone.get()
                && AuthorizedVoiceReferenceManager.isAuthorizedLocally();
    }

    public static void prepare(String language) throws Exception {
        String lang = RafaelLanguageManager.normalize(language);
        if (!GreatSageConfig.SERVER.enableAuthorizedVoiceClone.get()) {
            STATES.put(lang, "disabled");
            DETAILS.put(lang, "authorized clone disabled");
            return;
        }
        if (!AuthorizedVoiceReferenceManager.isAuthorizedLocally()) {
            STATES.put(lang, "inactive");
            DETAILS.put(lang, "no local authorization manifest");
            return;
        }
        if (TARGET_EMBEDDINGS.containsKey(lang) && sessionsReady()) return;

        synchronized (INSTALL_LOCK) {
            if (TARGET_EMBEDDINGS.containsKey(lang) && sessionsReady()) return;
            STATES.put(lang, "preparing");
            DETAILS.put(lang, "OpenVoice ONNX + authorized references");
            try {
                ensureModelsAndSessions();
                float[] target = loadCachedTargetEmbedding(lang);
                if (target == null) {
                    List<AuthorizedVoiceReferenceManager.ReferenceSample> references = AuthorizedVoiceReferenceManager.prepare(lang);
                    target = buildTargetEmbedding(references);
                    saveTargetEmbedding(lang, target);
                }
                TARGET_EMBEDDINGS.put(lang, target);
                STATES.put(lang, "ready");
                DETAILS.put(lang, RafaelLanguageManager.isSpanish(lang)
                        ? "authorized Circe Luna tone profile"
                        : "authorized Mallorie Rodak tone profile");
                GreatSageMod.LOGGER.info("Authorized Raphael tone profile ready for {}.", lang);
            } catch (Exception e) {
                STATES.put(lang, "error");
                DETAILS.put(lang, abbreviate(e.getMessage() == null ? e.toString() : e.getMessage(), 150));
                throw e;
            }
        }
    }

    public static byte[] apply(byte[] baseWav, String language) {
        if (baseWav == null || baseWav.length < 44 || !shouldUse(language)) return baseWav;
        String lang = RafaelLanguageManager.normalize(language);
        try {
            prepare(lang);
            synchronized (INFERENCE_LOCK) {
                PcmAudio source = decodeWav(baseWav);
                source = resampleTo22050(source);
                if (source.samples().length < SAMPLE_RATE / 2) return baseWav;
                if (source.samples().length > SAMPLE_RATE * MAX_SOURCE_SECONDS) {
                    GreatSageMod.LOGGER.debug("Authorized clone skipped: source speech exceeds {} seconds.", MAX_SOURCE_SECONDS);
                    return baseWav;
                }

                float[] srcEmbedding = SOURCE_EMBEDDINGS.get(lang);
                if (srcEmbedding == null) {
                    PcmAudio enrollment = crop(source, 0.0, Math.min(12.0, source.samples().length / (double) SAMPLE_RATE));
                    srcEmbedding = extractEmbedding(enrollment.samples());
                    SOURCE_EMBEDDINGS.put(lang, srcEmbedding);
                }
                float[] targetEmbedding = TARGET_EMBEDDINGS.get(lang);
                if (targetEmbedding == null) return baseWav;

                double strength = GreatSageConfig.SERVER.authorizedVoiceCloneStrength.get();
                float[] blendedTarget = blendEmbedding(srcEmbedding, targetEmbedding, (float) strength);
                float[][] spec = spectrogram(source.samples());
                float[] converted = runConverter(spec, srcEmbedding, blendedTarget);
                if (converted.length < SAMPLE_RATE / 4) return baseWav;
                converted = postProcess(converted, source.samples());
                return encodeWav(converted, SAMPLE_RATE);
            }
        } catch (Throwable e) {
            STATES.put(lang, "degraded");
            DETAILS.put(lang, abbreviate(e.getMessage() == null ? e.toString() : e.getMessage(), 150));
            GreatSageMod.LOGGER.warn("Authorized Raphael tone conversion failed for {}; using original offline voice: {}", lang, e.toString());
            return baseWav;
        }
    }

    public static String statusSummary(String language) {
        String lang = RafaelLanguageManager.normalize(language);
        if (!GreatSageConfig.SERVER.enableAuthorizedVoiceClone.get()) return "disabled";
        if (!AuthorizedVoiceReferenceManager.isAuthorizedLocally()) return "inactive / local authorization not installed";
        return STATES.getOrDefault(lang, "pending") + " / " + DETAILS.getOrDefault(lang, "authorized tone profile");
    }

    public static String identity(String language) {
        if (!shouldUse(language)) return "clone-off";
        String lang = RafaelLanguageManager.normalize(language);
        return "openvoice-q8-v2|" + lang + "|" + AuthorizedVoiceReferenceManager.manifestStamp()
                + "|" + GreatSageConfig.SERVER.authorizedVoiceCloneStrength.get();
    }

    private static void ensureModelsAndSessions() throws Exception {
        Path modelRoot = root().resolve("models");
        Files.createDirectories(modelRoot);
        Path encoder = modelRoot.resolve(ENCODER_FILE);
        Path converter = modelRoot.resolve(CONVERTER_FILE);
        ensureModel(encoder, MODEL_BASE + ENCODER_FILE + "?download=true", ENCODER_SHA256, ENCODER_MIN_BYTES);
        ensureModel(converter, MODEL_BASE + CONVERTER_FILE + "?download=true", CONVERTER_SHA256, CONVERTER_MIN_BYTES);

        if (encoderSession == null) {
            encoderSession = ENV.createSession(encoder.toAbsolutePath().toString(), new OrtSession.SessionOptions());
        }
        if (converterSession == null) {
            converterSession = ENV.createSession(converter.toAbsolutePath().toString(), new OrtSession.SessionOptions());
        }
    }

    private static boolean sessionsReady() {
        return encoderSession != null && converterSession != null;
    }

    private static void ensureModel(Path target, String url, String expectedSha, long minBytes) throws Exception {
        if (Files.isRegularFile(target) && Files.size(target) >= minBytes && expectedSha.equalsIgnoreCase(sha256(target))) return;
        Files.deleteIfExists(target);
        download(url, target, minBytes);
        String digest = sha256(target);
        if (!expectedSha.equalsIgnoreCase(digest)) {
            Files.deleteIfExists(target);
            throw new IOException("OpenVoice model checksum mismatch: " + target.getFileName() + " -> " + digest);
        }
    }

    private static void download(String rawUrl, Path target, long minBytes) throws Exception {
        URL url = new URL(rawUrl);
        if (!"https".equalsIgnoreCase(url.getProtocol())) throw new IOException("OpenVoice model URL must be HTTPS.");
        Files.createDirectories(target.getParent());
        Path temp = target.resolveSibling(target.getFileName().toString() + ".part");
        Files.deleteIfExists(temp);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setInstanceFollowRedirects(true);
        connection.setConnectTimeout(15_000);
        connection.setReadTimeout(90_000);
        connection.setRequestProperty("User-Agent", "GreatSageVoice/1.4.0");
        int code = connection.getResponseCode();
        if (code < 200 || code >= 300) {
            connection.disconnect();
            throw new IOException("OpenVoice asset download failed: HTTP " + code);
        }
        long total = 0L;
        try (InputStream in = new BufferedInputStream(connection.getInputStream()); var out = Files.newOutputStream(temp)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) >= 0) {
                if (read == 0) continue;
                out.write(buffer, 0, read);
                total += read;
                if (total > 180_000_000L) throw new IOException("OpenVoice asset exceeded expected safety limit.");
            }
        } finally {
            connection.disconnect();
        }
        if (total < minBytes) {
            Files.deleteIfExists(temp);
            throw new IOException("OpenVoice asset download incomplete: " + total + " bytes");
        }
        moveReplace(temp, target);
    }

    private static float[] buildTargetEmbedding(List<AuthorizedVoiceReferenceManager.ReferenceSample> references) throws Exception {
        List<float[]> embeddings = new ArrayList<>();
        for (AuthorizedVoiceReferenceManager.ReferenceSample reference : references) {
            PcmAudio audio = "mp3".equalsIgnoreCase(reference.format()) ? decodeMp3(reference.path()) : decodeWav(Files.readAllBytes(reference.path()));
            audio = resampleTo22050(audio);
            double totalSeconds = audio.samples().length / (double) SAMPLE_RATE;
            if (totalSeconds < 3.0) continue;
            double duration = Math.min(reference.durationSeconds(), Math.max(3.0, totalSeconds - reference.skipSeconds()));
            PcmAudio first = crop(audio, Math.min(reference.skipSeconds(), Math.max(0.0, totalSeconds - 3.0)), duration);
            if (first.samples().length >= SAMPLE_RATE * 3) embeddings.add(extractEmbedding(first.samples()));

            double secondStart = reference.skipSeconds() + duration + 5.0;
            if (totalSeconds - secondStart >= 6.0) {
                PcmAudio second = crop(audio, secondStart, Math.min(duration, totalSeconds - secondStart));
                if (second.samples().length >= SAMPLE_RATE * 3) embeddings.add(extractEmbedding(second.samples()));
            }
        }
        if (embeddings.isEmpty()) throw new IOException("Authorized references did not contain enough decodable speech.");
        float[] average = new float[EMBEDDING_SIZE];
        for (float[] embedding : embeddings) {
            if (embedding.length < EMBEDDING_SIZE) continue;
            for (int i = 0; i < EMBEDDING_SIZE; i++) average[i] += embedding[i];
        }
        for (int i = 0; i < EMBEDDING_SIZE; i++) average[i] /= embeddings.size();
        return average;
    }

    private static float[] extractEmbedding(float[] samples) throws Exception {
        float[][] spec = spectrogram(samples);
        int frames = spec[0].length;
        float[] flat = new float[frames * BINS];
        int index = 0;
        for (int t = 0; t < frames; t++) {
            for (int bin = 0; bin < BINS; bin++) flat[index++] = spec[bin][t];
        }
        try (OnnxTensor input = OnnxTensor.createTensor(ENV, FloatBuffer.wrap(flat), new long[]{1, frames, BINS})) {
            Map<String, OnnxTensor> inputs = new HashMap<>();
            inputs.put("spec", input);
            try (OrtSession.Result result = encoderSession.run(inputs)) {
                Object value = result.get(0).getValue();
                if (value instanceof float[][] array && array.length > 0) return array[0].clone();
                if (value instanceof float[][][] array && array.length > 0 && array[0].length > 0) return array[0][0].clone();
                throw new IOException("Unexpected OpenVoice encoder output shape.");
            }
        }
    }

    private static float[] runConverter(float[][] spec, float[] sourceEmbedding, float[] targetEmbedding) throws Exception {
        int frames = spec[0].length;
        float[] flatSpec = new float[BINS * frames];
        int pos = 0;
        for (int bin = 0; bin < BINS; bin++) {
            for (int frame = 0; frame < frames; frame++) flatSpec[pos++] = spec[bin][frame];
        }
        float[] src = embedding3d(sourceEmbedding);
        float[] tgt = embedding3d(targetEmbedding);

        try (OnnxTensor specTensor = OnnxTensor.createTensor(ENV, FloatBuffer.wrap(flatSpec), new long[]{1, BINS, frames});
             OnnxTensor lengthTensor = OnnxTensor.createTensor(ENV, LongBuffer.wrap(new long[]{frames}), new long[]{1});
             OnnxTensor srcTensor = OnnxTensor.createTensor(ENV, FloatBuffer.wrap(src), new long[]{1, EMBEDDING_SIZE, 1});
             OnnxTensor tgtTensor = OnnxTensor.createTensor(ENV, FloatBuffer.wrap(tgt), new long[]{1, EMBEDDING_SIZE, 1})) {
            Map<String, OnnxTensor> inputs = new HashMap<>();
            inputs.put("spec", specTensor);
            inputs.put("spec_lengths", lengthTensor);
            inputs.put("src_g", srcTensor);
            inputs.put("tgt_g", tgtTensor);
            try (OrtSession.Result result = converterSession.run(inputs)) {
                Object value = result.get(0).getValue();
                if (value instanceof float[][][] array && array.length > 0 && array[0].length > 0) return array[0][0].clone();
                if (value instanceof float[][] array && array.length > 0) return array[0].clone();
                if (value instanceof float[] array) return array.clone();
                throw new IOException("Unexpected OpenVoice converter output shape.");
            }
        }
    }

    private static float[] embedding3d(float[] embedding) {
        float[] out = new float[EMBEDDING_SIZE];
        System.arraycopy(embedding, 0, out, 0, Math.min(embedding.length, EMBEDDING_SIZE));
        return out;
    }

    private static float[] blendEmbedding(float[] source, float[] target, float strength) {
        float s = Math.max(0.0f, Math.min(1.20f, strength));
        float[] out = new float[EMBEDDING_SIZE];
        for (int i = 0; i < EMBEDDING_SIZE; i++) {
            float src = i < source.length ? source[i] : 0f;
            float tgt = i < target.length ? target[i] : src;
            out[i] = src + (tgt - src) * s;
        }
        return out;
    }

    /** Returns magnitude spectrogram [513][frames] matching OpenVoice preprocessing. */
    private static float[][] spectrogram(float[] input) {
        float[] samples = input;
        if (samples.length < N_FFT) {
            samples = new float[N_FFT];
            System.arraycopy(input, 0, samples, 0, input.length);
        }
        float[] padded = reflectPad(samples, PAD);
        int frames = Math.max(1, 1 + (padded.length - N_FFT) / HOP);
        float[][] output = new float[BINS][frames];
        double[] real = new double[N_FFT];
        double[] imag = new double[N_FFT];

        for (int frame = 0; frame < frames; frame++) {
            int offset = frame * HOP;
            for (int i = 0; i < N_FFT; i++) {
                double window = 0.5 - 0.5 * Math.cos((2.0 * Math.PI * i) / N_FFT);
                real[i] = padded[Math.min(padded.length - 1, offset + i)] * window;
                imag[i] = 0.0;
            }
            fft(real, imag);
            for (int bin = 0; bin < BINS; bin++) {
                output[bin][frame] = (float) Math.sqrt(real[bin] * real[bin] + imag[bin] * imag[bin] + 1.0e-6);
            }
        }
        return output;
    }

    private static float[] reflectPad(float[] source, int pad) {
        float[] out = new float[source.length + pad * 2];
        for (int i = 0; i < out.length; i++) {
            int sourceIndex = i - pad;
            while (sourceIndex < 0 || sourceIndex >= source.length) {
                if (sourceIndex < 0) sourceIndex = -sourceIndex;
                if (sourceIndex >= source.length) sourceIndex = 2 * source.length - 2 - sourceIndex;
                if (source.length == 1) sourceIndex = 0;
            }
            out[i] = source[sourceIndex];
        }
        return out;
    }

    private static void fft(double[] real, double[] imag) {
        int n = real.length;
        int j = 0;
        for (int i = 1; i < n; i++) {
            int bit = n >> 1;
            while ((j & bit) != 0) { j ^= bit; bit >>= 1; }
            j ^= bit;
            if (i < j) {
                double tr = real[i]; real[i] = real[j]; real[j] = tr;
                double ti = imag[i]; imag[i] = imag[j]; imag[j] = ti;
            }
        }
        for (int len = 2; len <= n; len <<= 1) {
            double angle = -2.0 * Math.PI / len;
            double wLenR = Math.cos(angle);
            double wLenI = Math.sin(angle);
            for (int i = 0; i < n; i += len) {
                double wr = 1.0, wi = 0.0;
                for (int k = 0; k < len / 2; k++) {
                    int even = i + k;
                    int odd = even + len / 2;
                    double vr = real[odd] * wr - imag[odd] * wi;
                    double vi = real[odd] * wi + imag[odd] * wr;
                    double ur = real[even];
                    double ui = imag[even];
                    real[even] = ur + vr;
                    imag[even] = ui + vi;
                    real[odd] = ur - vr;
                    imag[odd] = ui - vi;
                    double nextWr = wr * wLenR - wi * wLenI;
                    wi = wr * wLenI + wi * wLenR;
                    wr = nextWr;
                }
            }
        }
    }

    private static PcmAudio decodeWav(byte[] wav) throws Exception {
        try (ByteArrayInputStream bytes = new ByteArrayInputStream(wav);
             BufferedInputStream buffered = new BufferedInputStream(bytes);
             AudioInputStream original = AudioSystem.getAudioInputStream(buffered)) {
            AudioFormat src = original.getFormat();
            int channels = Math.max(1, src.getChannels());
            AudioFormat pcm = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, src.getSampleRate(), 16, channels, channels * 2, src.getSampleRate(), false);
            try (AudioInputStream decoded = AudioSystem.isConversionSupported(pcm, src) ? AudioSystem.getAudioInputStream(pcm, original) : original) {
                byte[] raw = decoded.readAllBytes();
                AudioFormat actual = decoded.getFormat();
                if (actual.getSampleSizeInBits() != 16 || actual.isBigEndian()) throw new IOException("Reference WAV is not convertible to PCM16 little-endian.");
                int actualChannels = Math.max(1, actual.getChannels());
                int frameBytes = actualChannels * 2;
                int frames = raw.length / frameBytes;
                float[] mono = new float[frames];
                for (int frame = 0; frame < frames; frame++) {
                    int base = frame * frameBytes;
                    int sum = 0;
                    for (int c = 0; c < actualChannels; c++) {
                        int p = base + c * 2;
                        int value = (short) ((raw[p] & 0xFF) | (raw[p + 1] << 8));
                        sum += value;
                    }
                    mono[frame] = (sum / (float) actualChannels) / 32768.0f;
                }
                return new PcmAudio(mono, Math.round(actual.getSampleRate()));
            }
        }
    }

    private static PcmAudio decodeMp3(Path path) throws Exception {
        List<short[]> chunks = new ArrayList<>();
        List<Integer> lengths = new ArrayList<>();
        int sampleRate = 0;
        int channels = 0;
        int totalSamples = 0;
        try (InputStream file = new BufferedInputStream(Files.newInputStream(path))) {
            Bitstream bitstream = new Bitstream(file);
            Decoder decoder = new Decoder();
            Header header;
            while ((header = bitstream.readFrame()) != null) {
                SampleBuffer output = (SampleBuffer) decoder.decodeFrame(header, bitstream);
                if (sampleRate == 0) {
                    sampleRate = output.getSampleFrequency();
                    channels = Math.max(1, output.getChannelCount());
                }
                int length = output.getBufferLength();
                short[] copy = new short[length];
                System.arraycopy(output.getBuffer(), 0, copy, 0, length);
                chunks.add(copy);
                lengths.add(length);
                totalSamples += length;
                bitstream.closeFrame();
                if (totalSamples > sampleRate * Math.max(1, channels) * 180) break;
            }
            bitstream.close();
        }
        if (sampleRate <= 0 || chunks.isEmpty()) throw new IOException("Could not decode MP3 reference.");
        int frameCount = totalSamples / Math.max(1, channels);
        float[] mono = new float[frameCount];
        int frame = 0;
        for (int chunkIndex = 0; chunkIndex < chunks.size(); chunkIndex++) {
            short[] chunk = chunks.get(chunkIndex);
            int length = lengths.get(chunkIndex);
            for (int i = 0; i + channels - 1 < length; i += channels) {
                int sum = 0;
                for (int c = 0; c < channels; c++) sum += chunk[i + c];
                if (frame < mono.length) mono[frame++] = (sum / (float) channels) / 32768.0f;
            }
        }
        if (frame != mono.length) {
            float[] exact = new float[frame];
            System.arraycopy(mono, 0, exact, 0, frame);
            mono = exact;
        }
        return new PcmAudio(mono, sampleRate);
    }

    private static PcmAudio resampleTo22050(PcmAudio source) {
        if (source.sampleRate() == SAMPLE_RATE) return source;
        if (source.sampleRate() <= 0 || source.samples().length == 0) return new PcmAudio(new float[0], SAMPLE_RATE);
        int targetLength = Math.max(1, (int) Math.round(source.samples().length * (SAMPLE_RATE / (double) source.sampleRate())));
        float[] out = new float[targetLength];
        double ratio = source.sampleRate() / (double) SAMPLE_RATE;
        for (int i = 0; i < targetLength; i++) {
            double sourcePos = i * ratio;
            int left = Math.min(source.samples().length - 1, (int) Math.floor(sourcePos));
            int right = Math.min(source.samples().length - 1, left + 1);
            double fraction = sourcePos - left;
            out[i] = (float) (source.samples()[left] * (1.0 - fraction) + source.samples()[right] * fraction);
        }
        return new PcmAudio(out, SAMPLE_RATE);
    }

    private static PcmAudio crop(PcmAudio source, double startSeconds, double durationSeconds) {
        int start = Math.max(0, Math.min(source.samples().length, (int) Math.round(startSeconds * source.sampleRate())));
        int requested = Math.max(1, (int) Math.round(durationSeconds * source.sampleRate()));
        int end = Math.max(start, Math.min(source.samples().length, start + requested));
        float[] out = new float[Math.max(0, end - start)];
        System.arraycopy(source.samples(), start, out, 0, out.length);
        return new PcmAudio(out, source.sampleRate());
    }

    private static float[] postProcess(float[] converted, float[] source) {
        float[] out = converted.clone();
        double srcRms = rms(source);
        double outRms = rms(out);
        double gain = outRms > 1.0e-6 ? Math.min(1.55, Math.max(0.55, (srcRms / outRms) * 0.96)) : 1.0;
        double mean = 0.0;
        for (float sample : out) mean += sample;
        mean /= Math.max(1, out.length);
        int fade = Math.min(out.length / 2, SAMPLE_RATE / 100);
        for (int i = 0; i < out.length; i++) {
            double value = (out[i] - mean) * gain;
            if (i < fade) value *= i / (double) Math.max(1, fade);
            if (i >= out.length - fade) value *= (out.length - 1 - i) / (double) Math.max(1, fade);
            out[i] = (float) Math.max(-0.98, Math.min(0.98, value));
        }
        return out;
    }

    private static double rms(float[] samples) {
        if (samples.length == 0) return 0.0;
        double sum = 0.0;
        for (float sample : samples) sum += sample * sample;
        return Math.sqrt(sum / samples.length);
    }

    private static byte[] encodeWav(float[] samples, int sampleRate) {
        int dataSize = samples.length * 2;
        ByteArrayOutputStream out = new ByteArrayOutputStream(dataSize + 44);
        writeAscii(out, "RIFF"); writeLe32(out, 36 + dataSize); writeAscii(out, "WAVE"); writeAscii(out, "fmt ");
        writeLe32(out, 16); writeLe16(out, 1); writeLe16(out, 1); writeLe32(out, sampleRate); writeLe32(out, sampleRate * 2); writeLe16(out, 2); writeLe16(out, 16);
        writeAscii(out, "data"); writeLe32(out, dataSize);
        for (float sample : samples) {
            int value = Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, Math.round(sample * 32767.0f)));
            writeLe16(out, value);
        }
        return out.toByteArray();
    }

    private static void writeAscii(ByteArrayOutputStream out, String value) {
        out.writeBytes(value.getBytes(StandardCharsets.US_ASCII));
    }

    private static void writeLe16(ByteArrayOutputStream out, int value) {
        out.write(value & 0xFF);
        out.write((value >>> 8) & 0xFF);
    }

    private static void writeLe32(ByteArrayOutputStream out, int value) {
        out.write(value & 0xFF);
        out.write((value >>> 8) & 0xFF);
        out.write((value >>> 16) & 0xFF);
        out.write((value >>> 24) & 0xFF);
    }

    private static float[] loadCachedTargetEmbedding(String language) {
        Path path = embeddingPath(language);
        try {
            if (!Files.isRegularFile(path)) return null;
            try (DataInputStream input = new DataInputStream(Files.newInputStream(path))) {
                if (input.readInt() != 0x52414645) return null;
                if (input.readInt() != EMBEDDING_CACHE_VERSION) return null;
                long stamp = input.readLong();
                if (stamp != AuthorizedVoiceReferenceManager.manifestStamp()) return null;
                int length = input.readInt();
                if (length != EMBEDDING_SIZE) return null;
                float[] data = new float[length];
                for (int i = 0; i < length; i++) data[i] = input.readFloat();
                return data;
            }
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void saveTargetEmbedding(String language, float[] embedding) {
        try {
            Path path = embeddingPath(language);
            Files.createDirectories(path.getParent());
            try (DataOutputStream output = new DataOutputStream(Files.newOutputStream(path))) {
                output.writeInt(0x52414645);
                output.writeInt(EMBEDDING_CACHE_VERSION);
                output.writeLong(AuthorizedVoiceReferenceManager.manifestStamp());
                output.writeInt(EMBEDDING_SIZE);
                for (int i = 0; i < EMBEDDING_SIZE; i++) output.writeFloat(i < embedding.length ? embedding[i] : 0f);
            }
        } catch (Exception e) {
            GreatSageMod.LOGGER.debug("Could not persist authorized voice embedding: {}", e.toString());
        }
    }

    private static Path embeddingPath(String language) {
        return root().resolve("embeddings").resolve(RafaelLanguageManager.normalize(language) + ".f32");
    }

    private static Path root() {
        return FMLPaths.GAMEDIR.get().resolve("great_sage_voice").resolve("authorized_voice");
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

    private static String abbreviate(String value, int max) {
        if (value == null) return "unknown";
        return value.length() <= max ? value : value.substring(0, max) + "…";
    }

    private record PcmAudio(float[] samples, int sampleRate) {}
}
