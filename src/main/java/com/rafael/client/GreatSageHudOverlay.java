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
import java.util.Random;

public class GreatSageHudOverlay {
    private static String fullText = "";
    private static String displayedText = "";
    private static long activationTime = 0;
    private static long lastCharTime = 0;
    private static int charIndex = 0;
    private static final long DISPLAY_DURATION = 10000; // 10 segundos

    // Partículas cuánticas compactas exclusivas para el núcleo luminoso
    private static final List<QuantumParticle> particles = new ArrayList<>();
    private static final Random random = new Random();

    static {
        for (int i = 0; i < 20; i++) {
            particles.add(new QuantumParticle());
        }
    }

    private static class QuantumParticle {
        float angle, radius, angularSpeed;
        float y, verticalSpeed;
        int color;
        float size;

        public QuantumParticle() {
            reset();
        }

        public void reset() {
            angle = random.nextFloat() * (float)(Math.PI * 2);
            radius = random.nextFloat() * 16f + 4f;
            angularSpeed = (random.nextBoolean() ? 1 : -1) * (random.nextFloat() * 0.05f + 0.02f);
            y = random.nextFloat() * 24 - 12;
            verticalSpeed = (random.nextFloat() - 0.5f) * 0.2f;
            size = random.nextFloat() * 1.5f + 0.6f;
            
            int colorChoice = random.nextInt(3);
            if (colorChoice == 0) color = 0xFFFFD700; // Dorado Sabio
            else if (colorChoice == 1) color = 0xFF00FFFF; // Cian Neón
            else color = 0xFFFFFFFF; // Blanco Láser
        }

        public void update() {
            angle += angularSpeed;
            y += verticalSpeed;
            if (Math.abs(y) > 12) {
                verticalSpeed *= -1;
            }
        }
    }

    public static void updateText(String text) {
        fullText = text != null ? text : "";
        displayedText = "";
        charIndex = 0;
        activationTime = System.currentTimeMillis();
        lastCharTime = activationTime;
    }

    public static final IGuiOverlay HUD_OVERLAY = (gui, guiGraphics, partialTick, width, height) -> {
        long now = System.currentTimeMillis();
        long elapsed = now - activationTime;

        if (fullText.isEmpty() || elapsed >= DISPLAY_DURATION) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui) {
            return;
        }

        // --- EFECTO MÁQUINA DE ESCRIBIR ---
        int typingDelay = GreatSageClientConfig.CLIENT.typingSpeedMs.get();
        if (charIndex < fullText.length() && (now - lastCharTime) >= typingDelay) {
            displayedText += fullText.charAt(charIndex);
            charIndex++;
            lastCharTime = now;
            if (GreatSageClientConfig.CLIENT.enableTypewriterSound.get() && charIndex % 2 == 0) {
                GreatSageAudioPlayer.playTypewriterTick();
            }
        }

        // --- TRANSICIONES Y EASING ---
        float alpha = 1.0f;
        if (elapsed < 300) {
            alpha = elapsed / 300.0f;
        } else if (elapsed > DISPLAY_DURATION - 800) {
            alpha = (DISPLAY_DURATION - elapsed) / 800.0f;
        }
        alpha = Math.max(0.0f, Math.min(1.0f, alpha));

        double userScale = GreatSageClientConfig.CLIENT.hudScale.get();

        guiGraphics.pose().pushPose();
        float scale = (float) userScale;
        guiGraphics.pose().scale(scale, scale, 1.0f);

        // =========================================================================
        // 1. NÚCLEO LUMINOSO ANIMADO COMPACTO (Esquina inferior derecha)
        // =========================================================================
        int coreX = width - 30;
        int coreY = height - 70;
        long time = now;

        float pulse = 1.0f + 0.15f * (float)Math.sin(time * 0.012);
        if (charIndex < fullText.length()) {
            pulse += 0.1f * (float)Math.sin(time * 0.04);
        }

        // Rayos de energía externa del núcleo
        for (int i = 0; i < 8; i++) {
            double angle = (time * 0.005 + (i * Math.PI / 4.0));
            int rx = (int) (coreX + Math.cos(angle) * (18 * pulse));
            int ry = (int) (coreY + Math.sin(angle) * (18 * pulse));
            guiGraphics.fill(coreX, coreY, rx, ry, 0x5500FFFF);
        }

        // Anillo rúnico contrarrotatorio compacto
        float rot1 = time * 0.01f;
        float rot2 = -time * 0.015f;

        for (int i = 0; i < 4; i++) {
            double a1 = rot1 + (i * Math.PI / 2.0);
            int px1 = (int) (coreX + Math.cos(a1) * 12);
            int py1 = (int) (coreY + Math.sin(a1) * 12);
            guiGraphics.fill(px1 - 1, py1 - 1, px1 + 1, py1 + 1, 0xFFFFD700);

            double a2 = rot2 + (i * Math.PI / 2.0);
            int px2 = (int) (coreX + Math.cos(a2) * 7);
            int py2 = (int) (coreY + Math.sin(a2) * 7);
            guiGraphics.fill(px2 - 1, py2 - 1, px2 + 1, py2 + 1, 0xFF00FFFF);
        }

        // Núcleo central brillante
        int coreSize = (int)(3.5f * pulse);
        guiGraphics.fill(coreX - coreSize - 1, coreY - coreSize - 1, coreX + coreSize + 1, coreY + coreSize + 1, 0x7700FFFF);
        guiGraphics.fill(coreX - coreSize, coreY - coreSize, coreX + coreSize, coreY + coreSize, 0xFFFFFFFF);
        guiGraphics.fill(coreX - 1, coreY - 1, coreX + 1, coreY + 1, 0xFFFFD700);

        // Partículas cuánticas compactas
        for (QuantumParticle p : particles) {
            p.update();
            int partX = (int) (coreX + Math.cos(p.angle) * p.radius);
            int partY = (int) (coreY + p.y);
            guiGraphics.fill(partX, partY, (int)(partX + p.size), (int)(partY + p.size), p.color);
        }

        // =========================================================================
        // 2. CHAT PERSONALIZADO COMPACTO Y ELEGANTE (Esquina inferior izquierda / área limpia)
        // =========================================================================
        int chatBoxWidth = 280;
        int chatBoxHeight = 42;
        int chatX = 15;
        int chatY = height - chatBoxHeight - 35;

        // Fondo semitransparente minimalista
        guiGraphics.fill(chatX, chatY, chatX + chatBoxWidth, chatY + chatBoxHeight, 0xE601030B);
        
        // Bordes finos arcanos
        guiGraphics.fill(chatX, chatY, chatX + chatBoxWidth, chatY + 1, 0xFF00FFFF);
        guiGraphics.fill(chatX, chatY + chatBoxHeight - 1, chatX + chatBoxWidth, chatY + chatBoxHeight, 0xFFFFD700);
        guiGraphics.fill(chatX, chatY, chatX + 1, chatY + chatBoxHeight, 0xFF00FFFF);
        guiGraphics.fill(chatX + chatBoxWidth - 1, chatY, chatX + chatBoxWidth, chatY + chatBoxHeight, 0xFFFFD700);

        // Título compacto
        Font font = minecraft.font;
        guiGraphics.drawString(font, Component.literal("§b✦ [ Gran Sabio ] ✦"), chatX + 6, chatY + 4, 0xFFFFD700, true);

        // Texto ajustado compacto
        List<FormattedCharSequence> lines = font.split(Component.literal(displayedText), chatBoxWidth - 12);
        int lineY = chatY + 15;
        for (FormattedCharSequence line : lines) {
            if (lineY < chatY + chatBoxHeight - 4) {
                guiGraphics.drawString(font, line, chatX + 6, lineY, 0x00FFFF, false);
                lineY += 10;
            }
        }

        guiGraphics.pose().popPose();
    };
}
