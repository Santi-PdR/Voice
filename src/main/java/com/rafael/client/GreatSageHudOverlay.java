package com.rafael.client;

import com.rafael.config.GreatSageClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public final class GreatSageHudOverlay {
    private static String fullText = "";
    private static String emotion = "analytical";
    private static String language = "en";
    private static boolean syntheticVoice = false;
    private static long activationTime = 0L;
    private static long displayDuration = 9000L;
    private static int lastTickBucket = -1;
    private static final List<QuantumParticle> PARTICLES = new ArrayList<>();

    static {
        Random random = new Random(0x5241504841454CL);
        for (int i = 0; i < 20; i++) {
            PARTICLES.add(new QuantumParticle(
                    random.nextFloat() * (float) (Math.PI * 2.0),
                    5.0f + random.nextFloat() * 15.0f,
                    0.00032f + random.nextFloat() * 0.00095f,
                    random.nextBoolean() ? 1.0f : -1.0f,
                    -12.0f + random.nextFloat() * 24.0f,
                    0.7f + random.nextFloat() * 1.3f,
                    switch (random.nextInt(4)) {
                        case 0 -> 0xFFFFD66B;
                        case 1 -> 0xFF79E9FF;
                        case 2 -> 0xFFA8F5FF;
                        default -> 0xFFFFFFFF;
                    }));
        }
    }

    private GreatSageHudOverlay() {}
    private record QuantumParticle(float baseAngle, float radius, float angularSpeed, float direction, float yOffset, float size, int color) {}

    public static void updateText(String text, String newEmotion, boolean hasSyntheticVoice, String newLanguage) {
        fullText = text == null ? "" : text.trim();
        emotion = newEmotion == null || newEmotion.isBlank() ? "analytical" : newEmotion;
        language = newLanguage == null || newLanguage.isBlank() ? "en" : newLanguage.toLowerCase(Locale.ROOT);
        syntheticVoice = hasSyntheticVoice;
        activationTime = System.currentTimeMillis();
        lastTickBucket = -1;
        int typingMs = Math.max(5, GreatSageClientConfig.CLIENT.typingSpeedMs.get());
        long typingDuration = (long) fullText.length() * typingMs;
        displayDuration = Math.max(6000L, Math.min(16000L, typingDuration + 3900L));
    }

    public static final IGuiOverlay HUD_OVERLAY = (gui, graphics, partialTick, width, height) -> {
        if (fullText.isEmpty()) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui) return;

        long now = System.currentTimeMillis();
        long elapsed = now - activationTime;
        if (elapsed < 0L || elapsed >= displayDuration) return;

        float fade = calculateFade(elapsed, displayDuration);
        float scale = (float) GreatSageClientConfig.CLIENT.hudScale.get().doubleValue();
        int logicalWidth = Math.max(1, Math.round(width / scale));
        int logicalHeight = Math.max(1, Math.round(height / scale));
        int typingDelay = Math.max(5, GreatSageClientConfig.CLIENT.typingSpeedMs.get());
        int revealedChars = Math.min(fullText.length(), Math.max(0, (int) ((elapsed - 100L) / typingDelay)));
        boolean processing = revealedChars < fullText.length();
        String displayedText = fullText.substring(0, revealedChars);
        if (processing && revealedChars > 0 && ((elapsed / 360L) & 1L) == 0L) displayedText += " |";

        int tickBucket = revealedChars / 4;
        if (revealedChars > 0 && processing && tickBucket != lastTickBucket) {
            lastTickBucket = tickBucket;
            GreatSageAudioPlayer.playTypewriterTick();
        }

        Font font = minecraft.font;
        int maxLines = GreatSageClientConfig.CLIENT.maxHudLines.get();
        int panelWidth = Math.min(370, Math.max(215, logicalWidth - 92));
        int textWidth = panelWidth - 20;
        List<FormattedCharSequence> fullLines = font.split(Component.literal(fullText), textWidth);
        int plannedLines = Math.max(1, Math.min(maxLines, fullLines.size()));
        int panelHeight = 31 + plannedLines * 10;
        int panelX = 16;
        int panelY = Math.max(18, logicalHeight - panelHeight - 88);
        float intro = easeOutCubic(Math.min(1.0f, elapsed / 260.0f));
        panelX -= Math.round((1.0f - intro) * 12.0f);

        int accent = accentForEmotion(emotion);
        int secondary = 0xFFFFD66B;
        graphics.pose().pushPose();
        graphics.pose().scale(scale, scale, 1.0f);
        drawPanel(graphics, font, panelX, panelY, panelWidth, panelHeight, accent, secondary, fade, displayedText, maxLines, elapsed, processing);
        drawCore(graphics, logicalWidth - 38, logicalHeight - 64, now, processing, accent, fade);
        graphics.pose().popPose();
    };

    private static void drawPanel(GuiGraphics graphics, Font font, int x, int y, int width, int height,
                                  int accent, int secondary, float fade, String displayedText, int maxLines,
                                  long elapsed, boolean processing) {
        float opacity = GreatSageClientConfig.CLIENT.hudOpacity.get().floatValue();
        float chromeFade = fade * opacity;

        graphics.fill(x + 3, y + 3, x + width + 4, y + height + 4, withAlpha(0x66000000, chromeFade * 0.75f));
        graphics.fill(x, y, x + width, y + height, withAlpha(0xE7080D16, chromeFade));
        graphics.fill(x, y, x + 3, y + height, withAlpha(accent, fade));
        graphics.fill(x + 3, y, x + width, y + 1, withAlpha(accent, chromeFade * 0.85f));
        graphics.fill(x + 3, y + height - 1, x + width, y + height, withAlpha(secondary, chromeFade * 0.60f));

        for (int scanY = y + 4; scanY < y + height - 3; scanY += 4) {
            graphics.fill(x + 4, scanY, x + width - 4, scanY + 1, withAlpha(0x1600D8FF, chromeFade * 0.26f));
        }

        boolean es = isSpanish();
        String title = es ? "RAFAEL // GRAN SABIO" : "RAPHAEL // GREAT SAGE";
        graphics.drawString(font, Component.literal(title), x + 10, y + 6, withAlpha(accent, fade), false);

        String state = localizedState(es);
        if (GreatSageClientConfig.CLIENT.showLanguageIndicator.get()) state += es ? "  ES" : "  EN";
        int stateWidth = font.width(state);
        graphics.drawString(font, Component.literal(state), x + width - stateWidth - 9, y + 6, withAlpha(0xFFC0CDD9, fade * 0.82f), false);

        // Small analytical bus under the header: it moves while text is being processed, then locks.
        int busY = y + 15;
        graphics.fill(x + 10, busY, x + width - 10, busY + 1, withAlpha(0x334F6B7C, chromeFade));
        int busWidth = Math.max(8, width / 7);
        int travel = Math.max(1, width - 20 - busWidth);
        int busX = processing ? x + 10 + (int) ((elapsed / 5L) % (travel * 2L)) : x + width - 10 - busWidth;
        if (processing && busX > x + 10 + travel) busX = x + 10 + travel - (busX - (x + 10 + travel));
        graphics.fill(busX, busY, busX + busWidth, busY + 1, withAlpha(accent, fade * 0.75f));

        int contentY = y + 20;
        List<FormattedCharSequence> lines = font.split(Component.literal(displayedText), width - 20);
        int count = Math.min(maxLines, lines.size());
        for (int i = 0; i < count; i++) {
            graphics.drawString(font, lines.get(i), x + 10, contentY + i * 10, withAlpha(0xFFEAF5F8, fade), false);
        }

        if (syntheticVoice && GreatSageClientConfig.CLIENT.showSyntheticVoiceLabel.get()) {
            String label = es ? "VOZ LOCAL // ES" : "LOCAL VOICE // EN";
            int labelWidth = font.width(label);
            graphics.drawString(font, Component.literal(label), x + width - labelWidth - 9, y + height - 9,
                    withAlpha(0xFF91A2B2, fade * 0.72f), false);
        }

        // Response lifetime indicator. It also makes the panel feel intentionally timed rather than abruptly disposable.
        float remaining = Math.max(0.0f, Math.min(1.0f, 1.0f - (System.currentTimeMillis() - activationTime) / (float) displayDuration));
        int lifeWidth = Math.max(1, Math.round((width - 20) * remaining));
        graphics.fill(x + 10, y + height - 3, x + 10 + lifeWidth, y + height - 2, withAlpha(accent, fade * 0.34f));
    }

    private static void drawCore(GuiGraphics graphics, int coreX, int coreY, long now, boolean processing, int accent, float fade) {
        float pulse = 1.0f + 0.075f * (float) Math.sin(now * 0.009);
        if (processing) pulse += 0.045f * (float) Math.sin(now * 0.026);

        for (int i = 0; i < 8; i++) {
            double angle = now * 0.00115 + i * Math.PI / 4.0;
            int endX = coreX + (int) Math.round(Math.cos(angle) * 23.0 * pulse);
            int endY = coreY + (int) Math.round(Math.sin(angle) * 23.0 * pulse);
            drawLine(graphics, coreX, coreY, endX, endY, withAlpha(accent, fade * 0.14f));
        }

        drawRing(graphics, coreX, coreY, 16.0f * pulse, now * 0.00135, 10, accent, fade * 0.86f);
        drawRing(graphics, coreX, coreY, 11.0f * pulse, -now * 0.00205, 7, 0xFFFFD66B, fade * 0.78f);
        drawRing(graphics, coreX, coreY, 6.5f * pulse, now * 0.0030, 5, 0xFFFFFFFF, fade * 0.70f);

        int glow = Math.max(4, Math.round(6.0f * pulse));
        graphics.fill(coreX - glow, coreY - glow, coreX + glow + 1, coreY + glow + 1, withAlpha(accent, fade * 0.19f));
        graphics.fill(coreX - 3, coreY - 3, coreX + 4, coreY + 4, withAlpha(0xFFF6FCFF, fade * 0.94f));
        graphics.fill(coreX - 1, coreY - 1, coreX + 2, coreY + 2, withAlpha(0xFFFFD66B, fade));

        for (QuantumParticle particle : PARTICLES) {
            double angle = particle.baseAngle() + now * particle.angularSpeed() * particle.direction();
            int px = coreX + (int) Math.round(Math.cos(angle) * particle.radius());
            int py = coreY + Math.round(particle.yOffset() + (float) Math.sin(angle * 1.65) * 2.0f);
            int size = Math.max(1, Math.round(particle.size()));
            graphics.fill(px, py, px + size, py + size, withAlpha(particle.color(), fade * 0.77f));
        }
    }

    private static String localizedState(boolean es) {
        return switch (emotion.toLowerCase(Locale.ROOT)) {
            case "critical" -> es ? "CRÍTICO" : "CRITICAL";
            case "achievement" -> es ? "HITO" : "MILESTONE";
            case "sync" -> es ? "SINCRONÍA" : "SYNC";
            default -> es ? "ANÁLISIS" : "ANALYSIS";
        };
    }

    private static boolean isSpanish() { return language.startsWith("es"); }

    private static void drawRing(GuiGraphics graphics, int cx, int cy, float radius, double rotation, int points, int color, float alpha) {
        int previousX = 0, previousY = 0, firstX = 0, firstY = 0;
        for (int i = 0; i < points; i++) {
            double angle = rotation + (Math.PI * 2.0 * i / points);
            int x = cx + (int) Math.round(Math.cos(angle) * radius);
            int y = cy + (int) Math.round(Math.sin(angle) * radius);
            if (i == 0) { firstX = x; firstY = y; }
            else drawLine(graphics, previousX, previousY, x, y, withAlpha(color, alpha));
            graphics.fill(x - 1, y - 1, x + 2, y + 2, withAlpha(color, Math.min(1.0f, alpha + 0.08f)));
            previousX = x;
            previousY = y;
        }
        drawLine(graphics, previousX, previousY, firstX, firstY, withAlpha(color, alpha));
    }

    private static void drawLine(GuiGraphics graphics, int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0), sx = x0 < x1 ? 1 : -1;
        int dy = -Math.abs(y1 - y0), sy = y0 < y1 ? 1 : -1;
        int error = dx + dy;
        while (true) {
            graphics.fill(x0, y0, x0 + 1, y0 + 1, color);
            if (x0 == x1 && y0 == y1) break;
            int e2 = 2 * error;
            if (e2 >= dy) { error += dy; x0 += sx; }
            if (e2 <= dx) { error += dx; y0 += sy; }
        }
    }

    private static float calculateFade(long elapsed, long duration) {
        float fadeIn = Math.min(1.0f, elapsed / 230.0f);
        float fadeOut = elapsed > duration - 820L ? Math.max(0.0f, (duration - elapsed) / 820.0f) : 1.0f;
        return easeOutCubic(fadeIn) * fadeOut;
    }

    private static float easeOutCubic(float value) {
        float x = Math.max(0.0f, Math.min(1.0f, value));
        return 1.0f - (float) Math.pow(1.0f - x, 3.0);
    }

    private static int accentForEmotion(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "critical" -> 0xFFFF6178;
            case "achievement" -> 0xFFFFD66B;
            case "sync" -> 0xFF73C9FF;
            default -> 0xFF72E7F2;
        };
    }

    private static int withAlpha(int argb, float multiplier) {
        int baseAlpha = (argb >>> 24) & 0xFF;
        int alpha = Math.max(0, Math.min(255, Math.round(baseAlpha * multiplier)));
        return (alpha << 24) | (argb & 0x00FFFFFF);
    }
}
