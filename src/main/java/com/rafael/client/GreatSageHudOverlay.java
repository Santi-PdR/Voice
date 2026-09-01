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
    private static boolean syntheticVoice = false;
    private static long activationTime = 0L;
    private static long displayDuration = 9000L;
    private static int lastTickBucket = -1;
    private static final List<QuantumParticle> PARTICLES = new ArrayList<>();

    static {
        Random random = new Random(0x52414641454CL);
        for (int i = 0; i < 18; i++) PARTICLES.add(new QuantumParticle(random.nextFloat() * (float) (Math.PI * 2.0), 5.0f + random.nextFloat() * 14.0f, 0.00035f + random.nextFloat() * 0.0009f, random.nextBoolean() ? 1.0f : -1.0f, -11.0f + random.nextFloat() * 22.0f, 0.7f + random.nextFloat() * 1.2f, switch (random.nextInt(3)) { case 0 -> 0xFFFFD66B; case 1 -> 0xFF7FE7FF; default -> 0xFFFFFFFF; }));
    }

    private GreatSageHudOverlay() {}
    private record QuantumParticle(float baseAngle, float radius, float angularSpeed, float direction, float yOffset, float size, int color) {}

    public static void updateText(String text, String newEmotion, boolean hasSyntheticVoice) {
        fullText = text == null ? "" : text.trim();
        emotion = newEmotion == null || newEmotion.isBlank() ? "analytical" : newEmotion;
        syntheticVoice = hasSyntheticVoice;
        activationTime = System.currentTimeMillis();
        lastTickBucket = -1;
        int typingMs = Math.max(5, GreatSageClientConfig.CLIENT.typingSpeedMs.get());
        long typingDuration = (long) fullText.length() * typingMs;
        displayDuration = Math.max(6500L, Math.min(17000L, typingDuration + 4200L));
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
        int revealedChars = Math.min(fullText.length(), Math.max(0, (int) ((elapsed - 120L) / typingDelay)));
        String displayedText = fullText.substring(0, revealedChars);
        int tickBucket = revealedChars / 3;
        if (revealedChars > 0 && revealedChars < fullText.length() && tickBucket != lastTickBucket) { lastTickBucket = tickBucket; GreatSageAudioPlayer.playTypewriterTick(); }
        Font font = minecraft.font;
        int maxLines = GreatSageClientConfig.CLIENT.maxHudLines.get();
        int panelWidth = Math.min(360, Math.max(210, logicalWidth - 92));
        int textWidth = panelWidth - 18;
        List<FormattedCharSequence> fullLines = font.split(Component.literal(fullText), textWidth);
        int plannedLines = Math.max(1, Math.min(maxLines, fullLines.size()));
        int panelHeight = 27 + plannedLines * 10;
        int panelX = 16;
        int panelY = Math.max(18, logicalHeight - panelHeight - 92);
        float intro = easeOutCubic(Math.min(1.0f, elapsed / 280.0f));
        panelX -= Math.round((1.0f - intro) * 10.0f);
        int accent = accentForEmotion(emotion);
        int secondary = 0xFFFFD66B;
        graphics.pose().pushPose();
        graphics.pose().scale(scale, scale, 1.0f);
        drawPanel(graphics, font, panelX, panelY, panelWidth, panelHeight, accent, secondary, fade, displayedText, maxLines);
        drawCore(graphics, logicalWidth - 38, logicalHeight - 66, now, revealedChars < fullText.length(), accent, fade);
        graphics.pose().popPose();
    };

    private static void drawPanel(GuiGraphics graphics, Font font, int x, int y, int width, int height, int accent, int secondary, float fade, String displayedText, int maxLines) {
        graphics.fill(x + 2, y + 3, x + width + 3, y + height + 3, withAlpha(0x66000000, fade * 0.72f));
        graphics.fill(x, y, x + width, y + height, withAlpha(0xE6080D16, fade));
        graphics.fill(x, y, x + 3, y + height, withAlpha(accent, fade));
        graphics.fill(x + 3, y, x + width, y + 1, withAlpha(accent, fade * 0.78f));
        graphics.fill(x + 3, y + height - 1, x + width, y + height, withAlpha(secondary, fade * 0.65f));
        for (int scanY = y + 4; scanY < y + height - 2; scanY += 4) graphics.fill(x + 4, scanY, x + width - 4, scanY + 1, withAlpha(0x1600D8FF, fade * 0.32f));
        graphics.drawString(font, Component.literal("RAFAEL // GREAT SAGE"), x + 10, y + 6, withAlpha(accent, fade), false);
        String state = switch (emotion.toLowerCase(Locale.ROOT)) { case "critical" -> "CRITICAL"; case "achievement" -> "MILESTONE"; case "sync" -> "SYNC"; default -> "ANALYSIS"; };
        int stateWidth = font.width(state);
        graphics.drawString(font, Component.literal(state), x + width - stateWidth - 9, y + 6, withAlpha(0xFFB9C7D8, fade * 0.82f), false);
        int contentY = y + 17;
        List<FormattedCharSequence> lines = font.split(Component.literal(displayedText), width - 18);
        int count = Math.min(maxLines, lines.size());
        for (int i = 0; i < count; i++) graphics.drawString(font, lines.get(i), x + 10, contentY + i * 10, withAlpha(0xFFE8F3F7, fade), false);
        if (syntheticVoice && GreatSageClientConfig.CLIENT.showSyntheticVoiceLabel.get()) {
            String label = "SYNTH VOICE";
            int labelWidth = font.width(label);
            graphics.drawString(font, Component.literal(label), x + width - labelWidth - 9, y + height - 9, withAlpha(0xFF8A98A8, fade * 0.68f), false);
        }
    }

    private static void drawCore(GuiGraphics graphics, int coreX, int coreY, long now, boolean processing, int accent, float fade) {
        float pulse = 1.0f + 0.09f * (float) Math.sin(now * 0.009);
        if (processing) pulse += 0.05f * (float) Math.sin(now * 0.027);
        for (int i = 0; i < 6; i++) { double angle = now * 0.0013 + i * Math.PI / 3.0; int endX = coreX + (int) Math.round(Math.cos(angle) * 22.0 * pulse); int endY = coreY + (int) Math.round(Math.sin(angle) * 22.0 * pulse); drawLine(graphics, coreX, coreY, endX, endY, withAlpha(accent, fade * 0.18f)); }
        drawRing(graphics, coreX, coreY, 15.0f * pulse, now * 0.0015, 8, accent, fade * 0.88f);
        drawRing(graphics, coreX, coreY, 10.0f * pulse, -now * 0.0022, 6, 0xFFFFD66B, fade * 0.82f);
        drawRing(graphics, coreX, coreY, 6.0f * pulse, now * 0.0031, 4, 0xFFFFFFFF, fade * 0.72f);
        int glow = Math.max(4, Math.round(6.0f * pulse));
        graphics.fill(coreX - glow, coreY - glow, coreX + glow + 1, coreY + glow + 1, withAlpha(accent, fade * 0.22f));
        graphics.fill(coreX - 3, coreY - 3, coreX + 4, coreY + 4, withAlpha(0xFFF5FCFF, fade * 0.95f));
        graphics.fill(coreX - 1, coreY - 1, coreX + 2, coreY + 2, withAlpha(0xFFFFD66B, fade));
        for (QuantumParticle particle : PARTICLES) { double angle = particle.baseAngle() + now * particle.angularSpeed() * particle.direction(); int px = coreX + (int) Math.round(Math.cos(angle) * particle.radius()); int py = coreY + Math.round(particle.yOffset() + (float) Math.sin(angle * 1.7) * 2.0f); int size = Math.max(1, Math.round(particle.size())); graphics.fill(px, py, px + size, py + size, withAlpha(particle.color(), fade * 0.8f)); }
    }

    private static void drawRing(GuiGraphics graphics, int cx, int cy, float radius, double rotation, int points, int color, float alpha) {
        int previousX = 0, previousY = 0, firstX = 0, firstY = 0;
        for (int i = 0; i < points; i++) {
            double angle = rotation + (Math.PI * 2.0 * i / points); int x = cx + (int) Math.round(Math.cos(angle) * radius); int y = cy + (int) Math.round(Math.sin(angle) * radius);
            if (i == 0) { firstX = x; firstY = y; } else drawLine(graphics, previousX, previousY, x, y, withAlpha(color, alpha));
            graphics.fill(x - 1, y - 1, x + 2, y + 2, withAlpha(color, Math.min(1.0f, alpha + 0.08f))); previousX = x; previousY = y;
        }
        drawLine(graphics, previousX, previousY, firstX, firstY, withAlpha(color, alpha));
    }

    private static void drawLine(GuiGraphics graphics, int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0), sx = x0 < x1 ? 1 : -1, dy = -Math.abs(y1 - y0), sy = y0 < y1 ? 1 : -1, error = dx + dy;
        while (true) { graphics.fill(x0, y0, x0 + 1, y0 + 1, color); if (x0 == x1 && y0 == y1) break; int e2 = 2 * error; if (e2 >= dy) { error += dy; x0 += sx; } if (e2 <= dx) { error += dx; y0 += sy; } }
    }

    private static float calculateFade(long elapsed, long duration) { float fadeIn = Math.min(1.0f, elapsed / 260.0f); float fadeOut = elapsed > duration - 900L ? Math.max(0.0f, (duration - elapsed) / 900.0f) : 1.0f; return easeOutCubic(fadeIn) * fadeOut; }
    private static float easeOutCubic(float value) { float x = Math.max(0.0f, Math.min(1.0f, value)); return 1.0f - (float) Math.pow(1.0f - x, 3.0); }
    private static int accentForEmotion(String value) { return switch (value.toLowerCase(Locale.ROOT)) { case "critical" -> 0xFFFF6178; case "achievement" -> 0xFFFFD66B; case "sync" -> 0xFF73C9FF; default -> 0xFF72E7F2; }; }
    private static int withAlpha(int argb, float multiplier) { int baseAlpha = (argb >>> 24) & 0xFF; int alpha = Math.max(0, Math.min(255, Math.round(baseAlpha * multiplier))); return (alpha << 24) | (argb & 0x00FFFFFF); }
}
