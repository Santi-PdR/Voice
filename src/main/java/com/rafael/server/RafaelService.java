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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/** Server-side Rafael brain + speech service. Offline voice is the default. */
public final class RafaelService {
    private static final String OPENAI_RESPONSES_URL = "https://api.openai.com/v1/responses";
    private static final String OPENAI_SPEECH_URL = "https://api.openai.com/v1/audio/speech";
    private static final String ELEVENLABS_TTS_BASE = "https://api.elevenlabs.io/v1/text-to-speech/";
    private static final int MAX_HTTP_BODY_BYTES = 2_000_000;
    private static final int MAX_AUDIO_BYTES = 900_000;
    private static final int AUDIO_CACHE_ENTRIES = 48;
    private static final AtomicBoolean WARNED_OPENAI_KEY = new AtomicBoolean(false);
    private static final AtomicBoolean WARNED_ELEVENLABS_KEY = new AtomicBoolean(false);
    private static final AtomicBoolean PREWARM_STARTED = new AtomicBoolean(false);

    private static final ThreadFactory THREAD_FACTORY = new ThreadFactory() {
        private final AtomicInteger counter = new AtomicInteger(1);
        @Override public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "Rafael-Service-" + counter.getAndIncrement());
            thread.setDaemon(true);
            thread.setUncaughtExceptionHandler((t, e) -> GreatSageMod.LOGGER.error("Error no controlado en {}", t.getName(), e));
            return thread;
        }
    };
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2, THREAD_FACTORY);
    private static final Map<String, byte[]> AUDIO_CACHE = Collections.synchronizedMap(
            new LinkedHashMap<String, byte[]>(AUDIO_CACHE_ENTRIES + 1, 0.75f, true) {
                @Override protected boolean removeEldestEntry(Map.Entry<String, byte[]> eldest) { return size() > AUDIO_CACHE_ENTRIES; }
            }
    );

    private static final String RAFAEL_SYSTEM_PROMPT = String.join(" ",
            "Eres Rafael, un sistema de análisis de combate y supervivencia integrado en Minecraft.",
            "Responde siempre en español neutro, con una personalidad serena, precisa, fría, protectora y extremadamente analítica.",
            "Tu respuesta debe sonar como una interfaz inteligente de alto nivel, no como un chatbot.",
            "No uses markdown, emojis, comillas, listas ni nombres de APIs.",
            "No inventes hechos que no estén presentes en el evento.",
            "Prioriza información útil y accionable.",
            "Máximo dos frases breves y compactas."
    );

    private RafaelService() {}

    public static void prewarmVoice() {
        if (!GreatSageConfig.SERVER.enableVoice.get() || !GreatSageConfig.SERVER.prewarmOfflineVoice.get()) return;
        if (!PREWARM_STARTED.compareAndSet(false, true)) return;
        EXECUTOR.execute(() -> {
            try {
                OfflineVoiceEngine.prepare();
            } catch (Exception e) {
                GreatSageMod.LOGGER.warn("Precalentamiento de voz offline no completado; Rafael volverá a intentarlo cuando necesite hablar: {}", e.toString());
            }
        });
    }

    public static void requestSpeech(EventSnapshot snapshot, Consumer<SpeechResult> callback) {
        EXECUTOR.execute(() -> {
            SpeechResult result;
            try {
                String text = buildResponseText(snapshot);
                String emotion = emotionFor(snapshot.eventType());
                byte[] audio = synthesizeVoice(text);
                result = new SpeechResult(text, audio, emotion, audio.length > 0);
            } catch (Exception e) {
                GreatSageMod.LOGGER.warn("Rafael no pudo procesar '{}': {}", snapshot.eventType(), e.toString(), e);
                result = new SpeechResult(sanitizeText(buildLocalResponse(snapshot)), new byte[0], emotionFor(snapshot.eventType()), false);
            }
            try { callback.accept(result); } catch (Exception e) { GreatSageMod.LOGGER.error("No se pudo entregar el resultado de Rafael", e); }
        });
    }

    private static String buildResponseText(EventSnapshot snapshot) {
        String local = sanitizeText(buildLocalResponse(snapshot));
        if (!GreatSageConfig.SERVER.enableGeneratedResponses.get()) return local;
        String apiKey = openAiKey();
        if (apiKey.isBlank()) {
            if (WARNED_OPENAI_KEY.compareAndSet(false, true)) GreatSageMod.LOGGER.info("Rafael usa su cerebro local integrado. Una API externa es opcional y no es necesaria para voz ni funcionamiento normal.");
            return local;
        }
        try {
            JsonObject payload = new JsonObject();
            payload.addProperty("model", GreatSageConfig.SERVER.openAiResponseModel.get());
            payload.addProperty("instructions", RAFAEL_SYSTEM_PROMPT);
            payload.addProperty("input", buildEventPrompt(snapshot));
            payload.addProperty("max_output_tokens", 120);
            JsonObject response = postJson(OPENAI_RESPONSES_URL, apiKey, payload, true);
            String generated = extractResponseText(response);
            if (!generated.isBlank()) return sanitizeText(generated);
        } catch (Exception e) {
            GreatSageMod.LOGGER.warn("Fallo del análisis cloud opcional; Rafael continúa con cerebro local: {}", e.toString());
        }
        return local;
    }

    private static String buildLocalResponse(EventSnapshot snapshot) {
        String event = normalize(snapshot.eventType());
        String detail = sanitizeLoose(snapshot.detail());
        String fallback = sanitizeLoose(snapshot.fallbackText());
        String player = snapshot.playerName();
        float health = snapshot.health();

        if (event.contains("prueba manual")) return localManual(snapshot, detail);
        if (event.contains("prueba de voz")) return "Sistema vocal offline operativo. Canal acústico sincronizado; síntesis local preparada.";
        if (event.contains("muerte")) return fallback.isBlank() ? "Alerta crítica. Firma vital perdida; causa de baja registrada para análisis." : fallback;
        if (event.contains("salud")) return "Advertencia. Integridad vital en nivel crítico: " + oneDecimal(health) + " puntos. Curación, cobertura o retirada inmediata recomendada.";
        if (event.contains("hambre") || event.contains("nutric")) return "Advertencia metabólica. Reservas de alimento en nivel crítico; repón nutrición antes de continuar operaciones de riesgo.";
        if (event.contains("conex")) return "Sincronización completada. Usuario " + player + " reconocido; entorno " + friendlyDimension(snapshot.dimension()) + " enlazado y estable.";
        if (event.contains("reapar") || event.contains("respawn")) return "Regeneración completada. Conciencia restaurada y parámetros vitales reinicializados; análisis del entorno reanudado.";
        if (event.contains("dimensi")) return "Transición dimensional confirmada. Destino: " + friendlyDimension(snapshot.dimension()) + ". Recalibrando parámetros ambientales y de navegación.";
        if (event.contains("modo de juego")) return fallback.isBlank() ? "Parámetros operativos reconfigurados. Nuevo modo registrado sin anomalías." : fallback;
        if (event.contains("logro") || event.contains("hito")) return fallback.isBlank() ? "Nuevo hito registrado. Progreso del usuario actualizado correctamente." : fallback;
        if (event.contains("objeto")) return fallback.isBlank() ? "Cambio de inventario detectado. Objeto descartado del conjunto activo." : fallback;
        return fallback.isBlank() ? "Análisis completado. No se detectan anomalías críticas en los parámetros disponibles." : fallback;
    }

    private static String localManual(EventSnapshot snapshot, String detail) {
        String lower = normalize(detail);
        if (lower.contains("diagnost") || lower.contains("estado") || lower.contains("sistema")) {
            return "Diagnóstico completado. Integridad vital: " + oneDecimal(snapshot.health()) + " de 20; entorno: " + friendlyDimension(snapshot.dimension()) + ". Subsistemas de Rafael operativos.";
        }
        if (lower.contains("salud") || lower.contains("vida") || lower.contains("corazon")) {
            return "Lectura vital actual: " + oneDecimal(snapshot.health()) + " de 20 puntos. " + (snapshot.health() <= 4.0f ? "Nivel crítico; prioriza curación y retirada." : "Parámetros dentro de un margen operativo aceptable.");
        }
        if (lower.contains("donde") || lower.contains("dimension") || lower.contains("entorno")) {
            return "Localización dimensional identificada: " + friendlyDimension(snapshot.dimension()) + ". Sincronización espacial estable.";
        }
        if (lower.contains("peligro") || lower.contains("riesgo")) {
            return snapshot.health() <= 6.0f ? "Riesgo elevado por reserva vital reducida. Recomiendo evitar combate directo hasta recuperar integridad." : "Con los datos disponibles no detecto una condición vital crítica. Mantén vigilancia del entorno.";
        }
        if (detail.isBlank()) return "Consulta recibida. Subsistema analítico local disponible y listo para evaluar telemetría del jugador.";
        return "Consulta registrada. El núcleo local no inventará información externa; puedo evaluar estado, salud, dimensión y eventos detectados en tiempo real.";
    }

    private static String buildEventPrompt(EventSnapshot snapshot) {
        return "Jugador: " + snapshot.playerName() + "\nEvento: " + snapshot.eventType() + "\nDetalle: " + snapshot.detail()
                + "\nSalud: " + String.format(Locale.ROOT, "%.1f/20", snapshot.health()) + "\nDimensión: " + snapshot.dimension();
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
                if (content.has("type") && "output_text".equals(content.get("type").getAsString()) && content.has("text")) return content.get("text").getAsString();
            }
        }
        return "";
    }

    private static byte[] synthesizeVoice(String text) {
        if (!GreatSageConfig.SERVER.enableVoice.get() || text.isBlank()) return new byte[0];
        String provider = GreatSageConfig.SERVER.ttsProvider.get().trim().toLowerCase(Locale.ROOT);
        String cacheKey = sha256(provider + "|" + voiceIdentity(provider) + "|" + text);
        byte[] cached = AUDIO_CACHE.get(cacheKey);
        if (cached != null) return cached.clone();

        byte[] audio = new byte[0];
        try {
            audio = switch (provider) {
                case "offline", "piper", "local" -> OfflineVoiceEngine.synthesize(text);
                case "openai" -> {
                    if (openAiKey().isBlank()) {
                        GreatSageMod.LOGGER.debug("OpenAI TTS no configurado; usando voz offline automática.");
                        yield OfflineVoiceEngine.synthesize(text);
                    }
                    try { yield synthesizeOpenAi(text); }
                    catch (Exception cloudError) {
                        GreatSageMod.LOGGER.warn("OpenAI TTS falló; usando voz offline: {}", cloudError.toString());
                        yield OfflineVoiceEngine.synthesize(text);
                    }
                }
                case "elevenlabs" -> {
                    if (elevenLabsKey().isBlank() || GreatSageConfig.SERVER.elevenLabsVoiceId.get().isBlank()) {
                        GreatSageMod.LOGGER.debug("ElevenLabs no configurado; usando voz offline automática.");
                        yield OfflineVoiceEngine.synthesize(text);
                    }
                    try { yield synthesizeElevenLabs(text); }
                    catch (Exception cloudError) {
                        GreatSageMod.LOGGER.warn("ElevenLabs TTS falló; usando voz offline: {}", cloudError.toString());
                        yield OfflineVoiceEngine.synthesize(text);
                    }
                }
                default -> {
                    GreatSageMod.LOGGER.warn("Proveedor TTS '{}' no reconocido; usando voz offline.", provider);
                    yield OfflineVoiceEngine.synthesize(text);
                }
            };
        } catch (Exception e) {
            GreatSageMod.LOGGER.warn("No se pudo generar voz de Rafael; HUD/texto seguirán funcionando: {}", e.toString());
        }

        if (audio.length > 0 && audio.length <= MAX_AUDIO_BYTES) {
            AUDIO_CACHE.put(cacheKey, audio.clone());
            return audio;
        }
        if (audio.length > MAX_AUDIO_BYTES) GreatSageMod.LOGGER.warn("Audio de Rafael descartado: {} bytes exceden el máximo de {}. Reduce maxResponseChars o aumenta velocidad.", audio.length, MAX_AUDIO_BYTES);
        return new byte[0];
    }

    private static byte[] synthesizeOpenAi(String text) throws Exception {
        String apiKey = openAiKey();
        if (apiKey.isBlank()) return new byte[0];
        JsonObject payload = new JsonObject();
        payload.addProperty("model", GreatSageConfig.SERVER.openAiTtsModel.get());
        payload.addProperty("voice", GreatSageConfig.SERVER.openAiVoice.get());
        payload.addProperty("input", text);
        payload.addProperty("instructions", GreatSageConfig.SERVER.voiceInstructions.get());
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
        settings.addProperty("stability", 0.76);
        settings.addProperty("similarity_boost", 0.70);
        settings.addProperty("style", 0.10);
        settings.addProperty("use_speaker_boost", true);
        settings.addProperty("speed", 0.95);
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

    private static HttpURLConnection openConnection(String url) throws Exception {
        URL parsed = new URL(url);
        if (!"https".equalsIgnoreCase(parsed.getProtocol())) throw new IllegalArgumentException("Rafael solo permite endpoints HTTPS.");
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
            byte[] buffer = new byte[8192]; int total = 0; int read;
            while ((read = stream.read(buffer)) != -1) {
                total += read;
                if (total > limit) throw new IllegalStateException("Respuesta remota demasiado grande (> " + limit + " bytes)");
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    private static byte[] pcm16MonoToWav(byte[] pcm, int sampleRate) {
        int dataSize = pcm.length, byteRate = sampleRate * 2;
        ByteArrayOutputStream out = new ByteArrayOutputStream(dataSize + 44);
        writeAscii(out, "RIFF"); writeLe32(out, 36 + dataSize); writeAscii(out, "WAVE"); writeAscii(out, "fmt ");
        writeLe32(out, 16); writeLe16(out, 1); writeLe16(out, 1); writeLe32(out, sampleRate); writeLe32(out, byteRate); writeLe16(out, 2); writeLe16(out, 16);
        writeAscii(out, "data"); writeLe32(out, dataSize); out.writeBytes(pcm); return out.toByteArray();
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

    private static String voiceIdentity(String provider) {
        if ("offline".equals(provider) || "piper".equals(provider) || "local".equals(provider)) {
            return "daniela-high|" + GreatSageConfig.SERVER.offlineLengthScale.get() + "|" + GreatSageConfig.SERVER.offlineNoiseScale.get() + "|" + GreatSageConfig.SERVER.offlineNoiseWidth.get();
        }
        if ("elevenlabs".equals(provider)) return GreatSageConfig.SERVER.elevenLabsVoiceId.get() + "|" + GreatSageConfig.SERVER.elevenLabsModel.get();
        return GreatSageConfig.SERVER.openAiVoice.get() + "|" + GreatSageConfig.SERVER.openAiTtsModel.get() + "|" + GreatSageConfig.SERVER.voiceInstructions.get();
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

    private static String friendlyDimension(String dimension) {
        String value = dimension == null ? "desconocido" : dimension;
        if (value.contains("overworld")) return "Overworld";
        if (value.contains("the_nether")) return "Nether";
        if (value.contains("the_end")) return "End";
        int colon = value.indexOf(':');
        return (colon >= 0 ? value.substring(colon + 1) : value).replace('_', ' ');
    }

    private static String oneDecimal(float value) { return String.format(Locale.ROOT, "%.1f", value); }

    private static String emotionFor(String eventType) {
        String event = normalize(eventType);
        if (event.contains("muerte") || event.contains("salud") || event.contains("hambre")) return "critical";
        if (event.contains("logro") || event.contains("hito")) return "achievement";
        if (event.contains("conex") || event.contains("dimension") || event.contains("respawn") || event.contains("reapar")) return "sync";
        return "analytical";
    }

    private static String sha256(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte b : hash) builder.append(String.format(Locale.ROOT, "%02x", b));
            return builder.toString();
        } catch (Exception e) { return Integer.toHexString(value.hashCode()); }
    }

    public static String statusSummary() {
        String provider = GreatSageConfig.SERVER.ttsProvider.get().trim().toLowerCase(Locale.ROOT);
        boolean aiKey = !openAiKey().isBlank();
        String voice;
        if (!GreatSageConfig.SERVER.enableVoice.get()) voice = "desactivada";
        else if ("offline".equals(provider) || "piper".equals(provider) || "local".equals(provider)) voice = "offline=" + OfflineVoiceEngine.statusSummary();
        else if ("openai".equals(provider) && aiKey) voice = "OpenAI configurada + fallback offline";
        else if ("elevenlabs".equals(provider) && !elevenLabsKey().isBlank() && !GreatSageConfig.SERVER.elevenLabsVoiceId.get().isBlank()) voice = "ElevenLabs configurada + fallback offline";
        else voice = provider + " no configurada -> offline " + OfflineVoiceEngine.statusSummary();
        return "Rafael v1.2 | Cerebro=" + (GreatSageConfig.SERVER.enableGeneratedResponses.get() && aiKey ? "cloud opcional + local" : "local integrado")
                + " | Voz=" + voice + " | Python/API key=NO requeridos";
    }

    public record EventSnapshot(String playerName, String eventType, String detail, String fallbackText, float health, String dimension) {}
    public record SpeechResult(String text, byte[] audioWav, String emotion, boolean syntheticVoice) {}
}
