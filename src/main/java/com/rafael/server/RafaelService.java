package com.rafael.server;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rafael.GreatSageMod;
import com.rafael.config.GreatSageConfig;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/** Bilingual server-side Raphael brain + speech pipeline. */
public final class RafaelService {
    private static final String OPENAI_RESPONSES_URL = "https://api.openai.com/v1/responses";
    private static final String OPENAI_SPEECH_URL = "https://api.openai.com/v1/audio/speech";
    private static final String ELEVENLABS_TTS_BASE = "https://api.elevenlabs.io/v1/text-to-speech/";
    private static final int MAX_HTTP_BODY_BYTES = 2_000_000;
    private static final int MAX_AUDIO_BYTES = 900_000;
    private static final int AUDIO_CACHE_ENTRIES = 64;
    private static final AtomicBoolean WARNED_OPENAI_KEY = new AtomicBoolean(false);
    private static final Set<String> PREWARM_LANGUAGES = ConcurrentHashMap.newKeySet();

    private static final ThreadFactory THREAD_FACTORY = new ThreadFactory() {
        private final AtomicInteger counter = new AtomicInteger(1);
        @Override public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "Raphael-Service-" + counter.getAndIncrement());
            thread.setDaemon(true);
            thread.setUncaughtExceptionHandler((t, e) -> GreatSageMod.LOGGER.error("Unhandled error in {}", t.getName(), e));
            return thread;
        }
    };

    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2, THREAD_FACTORY);
    private static final Map<String, byte[]> AUDIO_CACHE = Collections.synchronizedMap(
            new LinkedHashMap<String, byte[]>(AUDIO_CACHE_ENTRIES + 1, 0.75f, true) {
                @Override protected boolean removeEldestEntry(Map.Entry<String, byte[]> eldest) {
                    return size() > AUDIO_CACHE_ENTRIES;
                }
            });

    private RafaelService() {}

    public static void prewarmVoice() {
        prewarmVoice(GreatSageConfig.SERVER.fallbackLanguage.get());
    }

    public static void prewarmVoice(String language) {
        if (!GreatSageConfig.SERVER.enableVoice.get() || !GreatSageConfig.SERVER.prewarmOfflineVoice.get()) return;
        String normalized = RafaelLanguageManager.normalize(language);
        if (!PREWARM_LANGUAGES.add(normalized)) return;
        EXECUTOR.execute(() -> {
            try {
                OfflineVoiceEngine.prepare(normalized);
                // This is a no-op unless the local operator installed the authorization marker + manifest.
                AuthorizedVoiceCloneEngine.prepare(normalized);
            } catch (Exception e) {
                PREWARM_LANGUAGES.remove(normalized);
                GreatSageMod.LOGGER.warn("Raphael {} voice prewarm incomplete; retrying on demand: {}", normalized, e.toString());
            }
        });
    }

    public static void requestSpeech(EventSnapshot snapshot, Consumer<SpeechResult> callback) {
        EXECUTOR.execute(() -> {
            SpeechResult result;
            try {
                String text = buildResponseText(snapshot);
                String emotion = emotionFor(snapshot.eventType());
                byte[] audio = synthesizeVoice(text, snapshot.language());
                result = new SpeechResult(text, audio, emotion, audio.length > 0, snapshot.language());
            } catch (Exception e) {
                GreatSageMod.LOGGER.warn("Raphael could not process '{}': {}", snapshot.eventType(), e.toString(), e);
                String local = sanitizeText(buildLocalResponse(snapshot));
                result = new SpeechResult(local, new byte[0], emotionFor(snapshot.eventType()), false, snapshot.language());
            }
            try { callback.accept(result); }
            catch (Exception e) { GreatSageMod.LOGGER.error("Could not deliver Raphael result", e); }
        });
    }

    private static String buildResponseText(EventSnapshot snapshot) {
        String local = sanitizeText(buildLocalResponse(snapshot));
        if (!GreatSageConfig.SERVER.enableGeneratedResponses.get()) return local;
        String apiKey = openAiKey();
        if (apiKey.isBlank()) {
            if (WARNED_OPENAI_KEY.compareAndSet(false, true)) {
                GreatSageMod.LOGGER.info("Raphael uses the bilingual local analytical core. Cloud AI is optional.");
            }
            return local;
        }
        try {
            JsonObject payload = new JsonObject();
            payload.addProperty("model", GreatSageConfig.SERVER.openAiResponseModel.get());
            payload.addProperty("instructions", systemPrompt(snapshot.language()));
            payload.addProperty("input", buildEventPrompt(snapshot));
            payload.addProperty("max_output_tokens", 105);
            JsonObject response = postJson(OPENAI_RESPONSES_URL, apiKey, payload, true);
            String generated = extractResponseText(response);
            if (!generated.isBlank()) return sanitizeText(generated);
        } catch (Exception e) {
            GreatSageMod.LOGGER.warn("Optional cloud analysis failed; local core continues: {}", e.toString());
        }
        return local;
    }

    private static String systemPrompt(String language) {
        if (RafaelLanguageManager.isSpanish(language)) {
            return "Eres Rafael, el Gran Sabio: una inteligencia superior de análisis integrada en Minecraft. Responde en español neutro latinoamericano. "
                    + "Habla con serenidad absoluta, precisión clínica, emoción muy contenida y autoridad protectora. Frases cortas, declarativas y útiles; jamás suenes como chatbot. "
                    + "No uses markdown, emojis, listas, bromas ni nombres de APIs. No inventes telemetría. Máximo dos frases compactas.";
        }
        return "You are Raphael, the Great Sage: a superior analytical intelligence integrated into Minecraft. Reply in natural English. "
                + "Use absolute composure, clinical precision, restrained emotion and protective authority. Keep sentences short, declarative and useful; never sound like a chatbot. "
                + "No markdown, emojis, lists, jokes or API names. Never invent telemetry. Maximum two compact sentences.";
    }

    private static String buildLocalResponse(EventSnapshot s) {
        boolean es = RafaelLanguageManager.isSpanish(s.language());
        String event = normalize(s.eventType());
        String detail = sanitizeLoose(s.detail());
        String fallback = sanitizeLoose(s.fallbackText());

        if (event.contains("prueba manual") || event.contains("manual test")) return localManual(s, detail);
        if (event.contains("prueba de voz") || event.contains("voice test")) {
            return es ? "Subsistema vocal sincronizado. Conversión de timbre local lista; enlace acústico estable."
                    : "Voice subsystem synchronized. Local tone conversion ready; acoustic link stable.";
        }
        if (containsAny(event, "muerte", "death")) {
            if (!fallback.isBlank()) return fallback;
            return es ? "Alerta crítica. Firma vital perdida; causa de baja registrada para análisis."
                    : "Critical alert. Vital signature lost; cause of termination recorded for analysis.";
        }
        if (containsAny(event, "salud", "health")) {
            return es ? "Advertencia. Integridad vital crítica: " + oneDecimal(s.health()) + " de " + oneDecimal(s.maxHealth()) + ". Curación o retirada inmediata recomendada."
                    : "Warning. Vital integrity critical: " + oneDecimal(s.health()) + " of " + oneDecimal(s.maxHealth()) + ". Immediate healing or withdrawal recommended.";
        }
        if (containsAny(event, "hambre", "food", "hunger")) {
            return es ? "Advertencia metabólica. Reserva nutricional: " + s.food() + " de 20. Repón alimento antes de continuar operaciones de riesgo."
                    : "Metabolic warning. Nutritional reserve: " + s.food() + " of 20. Restore food before continuing high-risk operations.";
        }
        if (containsAny(event, "aire", "air")) {
            return es ? "Advertencia respiratoria. Reserva de aire crítica. Asciende o localiza una cámara de aire inmediatamente."
                    : "Respiratory warning. Air reserve critical. Surface or locate an air pocket immediately.";
        }
        if (containsAny(event, "conex", "login")) {
            return es ? "Sincronización completada. Usuario " + s.playerName() + " reconocido; entorno " + friendlyDimension(s.dimension()) + " estable."
                    : "Synchronization complete. User " + s.playerName() + " recognized; " + friendlyDimension(s.dimension()) + " environment stable.";
        }
        if (containsAny(event, "reapar", "respawn")) {
            return es ? "Regeneración completada. Conciencia restaurada; análisis del entorno reanudado."
                    : "Regeneration complete. Consciousness restored; environmental analysis resumed.";
        }
        if (containsAny(event, "dimensi", "dimension")) {
            return es ? "Transición dimensional confirmada. Destino: " + friendlyDimension(s.dimension()) + ". Recalibrando navegación y ambiente."
                    : "Dimensional transition confirmed. Destination: " + friendlyDimension(s.dimension()) + ". Recalibrating navigation and environment.";
        }
        if (containsAny(event, "modo de juego", "gamemode")) {
            return !fallback.isBlank() ? fallback : (es ? "Parámetros operativos reconfigurados. Nuevo modo registrado." : "Operational parameters reconfigured. New game mode registered.");
        }
        if (containsAny(event, "logro", "hito", "advancement")) {
            return !fallback.isBlank() ? fallback : (es ? "Nuevo hito registrado. Progreso actualizado." : "New milestone registered. Progress updated.");
        }
        if (containsAny(event, "objeto", "item")) {
            return !fallback.isBlank() ? fallback : (es ? "Cambio de inventario detectado. Objeto descartado." : "Inventory state change detected. Item discarded.");
        }
        return !fallback.isBlank() ? fallback : (es ? "Análisis completado. No se detectan anomalías críticas." : "Analysis complete. No critical anomalies detected.");
    }

    private static String localManual(EventSnapshot s, String detail) {
        boolean es = RafaelLanguageManager.isSpanish(s.language());
        String lower = normalize(detail);
        if (containsAny(lower, "diagnost", "estado", "sistema", "diagnostic", "status", "system")) {
            return es ? "Diagnóstico completado. Vida " + oneDecimal(s.health()) + " de " + oneDecimal(s.maxHealth()) + ", hambre " + s.food() + " de 20, armadura " + s.armor() + ", nivel " + s.experienceLevel() + ". Entorno: " + friendlyDimension(s.dimension()) + "."
                    : "Diagnostic complete. Health " + oneDecimal(s.health()) + " of " + oneDecimal(s.maxHealth()) + ", food " + s.food() + " of 20, armor " + s.armor() + ", level " + s.experienceLevel() + ". Environment: " + friendlyDimension(s.dimension()) + ".";
        }
        if (containsAny(lower, "salud", "vida", "corazon", "health", "heart")) {
            return es ? "Lectura vital: " + oneDecimal(s.health()) + " de " + oneDecimal(s.maxHealth()) + ". " + (s.health() <= 4.0f ? "Nivel crítico; prioriza curación." : "Margen operativo aceptable.")
                    : "Vital reading: " + oneDecimal(s.health()) + " of " + oneDecimal(s.maxHealth()) + ". " + (s.health() <= 4.0f ? "Critical level; prioritize healing." : "Operating margin acceptable.");
        }
        if (containsAny(lower, "hambre", "comida", "food", "hunger")) {
            return es ? "Reserva nutricional: " + s.food() + " de 20. " + (s.food() <= 6 ? "Nivel bajo; aliméntate antes de combatir." : "Nivel suficiente.")
                    : "Nutritional reserve: " + s.food() + " of 20. " + (s.food() <= 6 ? "Low level; eat before combat." : "Level sufficient.");
        }
        if (containsAny(lower, "donde", "dimension", "entorno", "where", "location")) {
            return es ? "Localización: " + friendlyDimension(s.dimension()) + ", coordenadas aproximadas " + s.x() + ", " + s.y() + ", " + s.z() + "."
                    : "Location: " + friendlyDimension(s.dimension()) + ", approximate coordinates " + s.x() + ", " + s.y() + ", " + s.z() + ".";
        }
        if (containsAny(lower, "peligro", "riesgo", "danger", "risk")) {
            boolean risk = s.health() <= 6.0f || s.food() <= 6;
            return es ? (risk ? "Riesgo elevado. Recupera reservas antes de avanzar." : "No detecto una condición fisiológica crítica. Mantén vigilancia.")
                    : (risk ? "Elevated risk. Restore reserves before advancing." : "No critical physiological condition detected. Maintain awareness.");
        }
        return es ? "Consulta registrada. Puedo evaluar vida, hambre, armadura, nivel, coordenadas, dimensión y eventos en tiempo real."
                : "Query registered. I can evaluate health, food, armor, level, coordinates, dimension and events in real time.";
    }

    private static String buildEventPrompt(EventSnapshot s) {
        return "Target language: " + (RafaelLanguageManager.isSpanish(s.language()) ? "Spanish" : "English")
                + "\nPlayer: " + s.playerName() + "\nEvent: " + s.eventType() + "\nDetail: " + s.detail()
                + "\nHealth: " + oneDecimal(s.health()) + "/" + oneDecimal(s.maxHealth())
                + "\nFood: " + s.food() + "/20\nArmor: " + s.armor() + "\nXP level: " + s.experienceLevel()
                + "\nDimension: " + s.dimension() + "\nPosition: " + s.x() + "," + s.y() + "," + s.z();
    }

    private static String extractResponseText(JsonObject root) {
        if (!root.has("output") || !root.get("output").isJsonArray()) return "";
        for (JsonElement outputElement : root.getAsJsonArray("output")) {
            if (!outputElement.isJsonObject()) continue;
            JsonObject output = outputElement.getAsJsonObject();
            if (!output.has("content") || !output.get("content").isJsonArray()) continue;
            for (JsonElement contentElement : output.getAsJsonArray("content")) {
                if (!contentElement.isJsonObject()) continue;
                JsonObject content = contentElement.getAsJsonObject();
                if (content.has("type") && "output_text".equals(content.get("type").getAsString()) && content.has("text")) {
                    return content.get("text").getAsString();
                }
            }
        }
        return "";
    }

    private static byte[] synthesizeVoice(String text, String language) {
        if (!GreatSageConfig.SERVER.enableVoice.get() || text.isBlank()) return new byte[0];
        String lang = RafaelLanguageManager.normalize(language);
        String provider = GreatSageConfig.SERVER.ttsProvider.get().trim().toLowerCase(Locale.ROOT);
        String cacheKey = sha256(provider + "|" + lang + "|" + voiceIdentity(provider, lang)
                + "|" + AuthorizedVoiceCloneEngine.identity(lang) + "|" + text);
        byte[] cached = AUDIO_CACHE.get(cacheKey);
        if (cached != null) return cached.clone();

        byte[] audio = new byte[0];
        try {
            audio = switch (provider) {
                case "offline", "piper", "local" -> OfflineVoiceEngine.synthesize(text, lang);
                case "openai" -> {
                    if (openAiKey().isBlank()) yield OfflineVoiceEngine.synthesize(text, lang);
                    try { yield synthesizeOpenAi(text, lang); }
                    catch (Exception cloudError) {
                        GreatSageMod.LOGGER.warn("OpenAI TTS failed; offline {} fallback: {}", lang, cloudError.toString());
                        yield OfflineVoiceEngine.synthesize(text, lang);
                    }
                }
                case "elevenlabs" -> {
                    if (elevenLabsKey().isBlank() || GreatSageConfig.SERVER.elevenLabsVoiceId.get().isBlank()) yield OfflineVoiceEngine.synthesize(text, lang);
                    try { yield synthesizeElevenLabs(text); }
                    catch (Exception cloudError) {
                        GreatSageMod.LOGGER.warn("ElevenLabs TTS failed; offline {} fallback: {}", lang, cloudError.toString());
                        yield OfflineVoiceEngine.synthesize(text, lang);
                    }
                }
                default -> OfflineVoiceEngine.synthesize(text, lang);
            };

            if (audio.length > 0) {
                audio = AuthorizedVoiceCloneEngine.apply(audio, lang);
            }
        } catch (Exception e) {
            GreatSageMod.LOGGER.warn("Raphael voice unavailable for this response; HUD/text continue: {}", e.toString());
        }

        if (audio.length > 0 && audio.length <= MAX_AUDIO_BYTES) {
            AUDIO_CACHE.put(cacheKey, audio.clone());
            return audio;
        }
        if (audio.length > MAX_AUDIO_BYTES) GreatSageMod.LOGGER.warn("Raphael WAV discarded: {} bytes > {}", audio.length, MAX_AUDIO_BYTES);
        return new byte[0];
    }

    private static byte[] synthesizeOpenAi(String text, String language) throws Exception {
        String apiKey = openAiKey();
        if (apiKey.isBlank()) return new byte[0];
        JsonObject payload = new JsonObject();
        payload.addProperty("model", GreatSageConfig.SERVER.openAiTtsModel.get());
        payload.addProperty("voice", GreatSageConfig.SERVER.openAiVoice.get());
        payload.addProperty("input", text);
        payload.addProperty("instructions", GreatSageConfig.SERVER.voiceInstructions.get()
                + (RafaelLanguageManager.isSpanish(language) ? " Speak neutral Latin American Spanish." : " Speak natural neutral English."));
        payload.addProperty("response_format", "wav");
        return postBinary(OPENAI_SPEECH_URL, "Authorization", "Bearer " + apiKey, payload, "audio/wav");
    }

    private static byte[] synthesizeElevenLabs(String text) throws Exception {
        String apiKey = elevenLabsKey();
        String voiceId = GreatSageConfig.SERVER.elevenLabsVoiceId.get().trim();
        if (apiKey.isBlank() || voiceId.isBlank()) return new byte[0];
        String encodedVoiceId = URLEncoder.encode(voiceId, StandardCharsets.UTF_8).replace("+", "%20");
        String url = ELEVENLABS_TTS_BASE + encodedVoiceId + "?output_format=pcm_24000";
        JsonObject payload = new JsonObject();
        payload.addProperty("text", text);
        payload.addProperty("model_id", GreatSageConfig.SERVER.elevenLabsModel.get());
        JsonObject settings = new JsonObject();
        settings.addProperty("stability", 0.82);
        settings.addProperty("similarity_boost", 0.70);
        settings.addProperty("style", 0.06);
        settings.addProperty("use_speaker_boost", true);
        settings.addProperty("speed", 0.96);
        payload.add("voice_settings", settings);
        byte[] pcm = postBinary(url, "xi-api-key", apiKey, payload, "application/octet-stream");
        return pcm16MonoToWav(pcm, 24000);
    }

    private static JsonObject postJson(String url, String apiKey, JsonObject payload, boolean bearer) throws Exception {
        HttpURLConnection connection = openConnection(url);
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        connection.setRequestProperty("Accept", "application/json");
        if (bearer) connection.setRequestProperty("Authorization", "Bearer " + apiKey);
        writePayload(connection, payload);
        int code = connection.getResponseCode();
        byte[] body = readLimited(code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream(), MAX_HTTP_BODY_BYTES);
        connection.disconnect();
        if (code < 200 || code >= 300) throw new IllegalStateException("HTTP " + code + ": " + new String(body, StandardCharsets.UTF_8));
        return JsonParser.parseString(new String(body, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static byte[] postBinary(String url, String authHeader, String authValue, JsonObject payload, String accept) throws Exception {
        HttpURLConnection connection = openConnection(url);
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        connection.setRequestProperty("Accept", accept);
        connection.setRequestProperty(authHeader, authValue);
        writePayload(connection, payload);
        int code = connection.getResponseCode();
        InputStream stream = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
        byte[] body = readLimited(stream, MAX_HTTP_BODY_BYTES);
        connection.disconnect();
        if (code < 200 || code >= 300) throw new IllegalStateException("HTTP " + code + ": " + new String(body, StandardCharsets.UTF_8));
        return body;
    }

    private static HttpURLConnection openConnection(String rawUrl) throws Exception {
        URL parsed = new URL(rawUrl);
        if (!"https".equalsIgnoreCase(parsed.getProtocol())) throw new IllegalArgumentException("Raphael only permits HTTPS endpoints.");
        HttpURLConnection connection = (HttpURLConnection) parsed.openConnection();
        int timeoutMs = GreatSageConfig.SERVER.requestTimeoutSeconds.get() * 1000;
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(timeoutMs);
        connection.setReadTimeout(timeoutMs);
        connection.setDoOutput(true);
        return connection;
    }

    private static void writePayload(HttpURLConnection connection, JsonObject payload) throws Exception {
        byte[] bytes = payload.toString().getBytes(StandardCharsets.UTF_8);
        connection.setFixedLengthStreamingMode(bytes.length);
        try (OutputStream output = connection.getOutputStream()) { output.write(bytes); }
    }

    private static byte[] readLimited(InputStream input, int limit) throws Exception {
        if (input == null) return new byte[0];
        try (InputStream stream = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = stream.read(buffer)) != -1) {
                total += read;
                if (total > limit) throw new IllegalStateException("Remote response too large (> " + limit + " bytes)");
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    private static byte[] pcm16MonoToWav(byte[] pcm, int sampleRate) {
        int dataSize = pcm.length;
        ByteArrayOutputStream out = new ByteArrayOutputStream(dataSize + 44);
        writeAscii(out, "RIFF"); writeLe32(out, 36 + dataSize); writeAscii(out, "WAVE"); writeAscii(out, "fmt ");
        writeLe32(out, 16); writeLe16(out, 1); writeLe16(out, 1); writeLe32(out, sampleRate); writeLe32(out, sampleRate * 2); writeLe16(out, 2); writeLe16(out, 16);
        writeAscii(out, "data"); writeLe32(out, dataSize); out.writeBytes(pcm);
        return out.toByteArray();
    }

    private static void writeAscii(ByteArrayOutputStream out, String text) { out.writeBytes(text.getBytes(StandardCharsets.US_ASCII)); }
    private static void writeLe16(ByteArrayOutputStream out, int value) { out.write(value & 0xFF); out.write((value >>> 8) & 0xFF); }
    private static void writeLe32(ByteArrayOutputStream out, int value) { out.write(value & 0xFF); out.write((value >>> 8) & 0xFF); out.write((value >>> 16) & 0xFF); out.write((value >>> 24) & 0xFF); }

    private static String openAiKey() {
        String env = System.getenv("OPENAI_API_KEY");
        if (env != null && !env.isBlank()) return env.trim();
        String configured = GreatSageConfig.SERVER.openAiApiKey.get();
        return configured == null ? "" : configured.trim();
    }

    private static String elevenLabsKey() {
        String env = System.getenv("ELEVENLABS_API_KEY");
        if (env != null && !env.isBlank()) return env.trim();
        String configured = GreatSageConfig.SERVER.elevenLabsApiKey.get();
        return configured == null ? "" : configured.trim();
    }

    private static String voiceIdentity(String provider, String language) {
        if ("offline".equals(provider) || "piper".equals(provider) || "local".equals(provider)) {
            double length = RafaelLanguageManager.isSpanish(language)
                    ? GreatSageConfig.SERVER.offlineLengthScale.get()
                    : GreatSageConfig.SERVER.offlineEnglishLengthScale.get();
            return OfflineVoiceEngine.voiceIdentity(language) + "|" + length + "|"
                    + GreatSageConfig.SERVER.offlineNoiseScale.get() + "|" + GreatSageConfig.SERVER.offlineNoiseWidth.get();
        }
        if ("elevenlabs".equals(provider)) return GreatSageConfig.SERVER.elevenLabsVoiceId.get() + "|" + GreatSageConfig.SERVER.elevenLabsModel.get();
        return GreatSageConfig.SERVER.openAiVoice.get() + "|" + GreatSageConfig.SERVER.openAiTtsModel.get() + "|" + language;
    }

    private static String sanitizeText(String raw) {
        if (raw == null) return "";
        String text = raw.replace('\r', ' ').replace('\n', ' ').replaceAll("\\s+", " ").trim();
        int max = GreatSageConfig.SERVER.maxResponseChars.get();
        if (text.length() > max) text = text.substring(0, Math.max(1, max - 1)).trim() + "…";
        return text;
    }

    private static String sanitizeLoose(String raw) {
        return raw == null ? "" : raw.replace('\r', ' ').replace('\n', ' ').replaceAll("\\s+", " ").trim();
    }

    private static String normalize(String raw) {
        return sanitizeLoose(raw).toLowerCase(Locale.ROOT)
                .replace('á', 'a').replace('é', 'e').replace('í', 'i').replace('ó', 'o').replace('ú', 'u').replace('ü', 'u');
    }

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) if (value.contains(needle)) return true;
        return false;
    }

    private static String friendlyDimension(String dimension) {
        String value = dimension == null ? "unknown" : dimension;
        if (value.contains("overworld")) return "Overworld";
        if (value.contains("the_nether")) return "Nether";
        if (value.contains("the_end")) return "End";
        int colon = value.indexOf(':');
        return (colon >= 0 ? value.substring(colon + 1) : value).replace('_', ' ');
    }

    private static String oneDecimal(float value) { return String.format(Locale.ROOT, "%.1f", value); }

    private static String emotionFor(String eventType) {
        String event = normalize(eventType);
        if (containsAny(event, "muerte", "death", "salud", "health", "hambre", "hunger", "aire", "air")) return "critical";
        if (containsAny(event, "logro", "hito", "advancement")) return "achievement";
        if (containsAny(event, "conex", "login", "dimension", "respawn", "reapar")) return "sync";
        return "analytical";
    }

    private static String sha256(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte b : hash) builder.append(String.format(Locale.ROOT, "%02x", b));
            return builder.toString();
        } catch (Exception e) {
            return Integer.toHexString(value.hashCode());
        }
    }

    public static String statusSummary(String language) {
        String lang = RafaelLanguageManager.normalize(language);
        boolean es = RafaelLanguageManager.isSpanish(lang);
        String provider = GreatSageConfig.SERVER.ttsProvider.get().trim().toLowerCase(Locale.ROOT);
        String voice = GreatSageConfig.SERVER.enableVoice.get()
                ? OfflineVoiceEngine.statusSummary(lang)
                : (es ? "desactivada" : "disabled");
        String clone = AuthorizedVoiceCloneEngine.statusSummary(lang);
        if (es) {
            return "Raphael v1.4 | Idioma=Español | Cerebro="
                    + (!openAiKey().isBlank() && GreatSageConfig.SERVER.enableGeneratedResponses.get() ? "local + cloud opcional" : "local integrado")
                    + " | Base=" + provider + " -> " + voice + " | Timbre autorizado=" + clone + " | API/Python=NO requeridos";
        }
        return "Raphael v1.4 | Language=English | Brain="
                + (!openAiKey().isBlank() && GreatSageConfig.SERVER.enableGeneratedResponses.get() ? "local + optional cloud" : "integrated local")
                + " | Base=" + provider + " -> " + voice + " | Authorized tone=" + clone + " | API/Python=NOT required";
    }

    public record EventSnapshot(
            String playerName,
            String eventType,
            String detail,
            String fallbackText,
            String language,
            float health,
            float maxHealth,
            int food,
            int armor,
            int experienceLevel,
            String dimension,
            int x,
            int y,
            int z) {}

    public record SpeechResult(String text, byte[] audioWav, String emotion, boolean syntheticVoice, String language) {}
}
