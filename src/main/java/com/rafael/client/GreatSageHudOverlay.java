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
    private static final long DISPLAY_DURATION = 14000; // 14 segundos

    // Partículas cuánticas exclusivas para el núcleo luminoso animado en la esquina inferior derecha
    private static final List<QuantumParticle> particles = new ArrayList<>();
    private static final Random random = new Random();

    static {
        for (int i = 0; i < 30; i++) {
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
            radius = random.nextFloat() * 22f + 5f;
            angularSpeed = (random.nextBoolean() ? 1 : -1) * (random.nextFloat() * 0.06f + 0.02f);
            y = random.nextFloat() * 30 - 15;
            verticalSpeed = (random.nextFloat() - 0.5f) * 0.3f;
            size = random.nextFloat() * 2.0f + 0.8f;
            
            int colorChoice = random.nextInt(3);
            if (colorChoice == 0) color = 0xFFFFD700; // Dorado Sabio
            else if (colorChoice == 1) color = 0xFF00FFFF; // Cian Neón
            else color = 0xFFFFFFFF; // Blanco Láser
        }

        public void update() {
            angle += angularSpeed;
            y += verticalSpeed;
            if (Math.abs(y) > 18) {
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

        // --- EFECTO MÁQUINA DE ESCRIBIR (Typewriter) ---
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
        if (elapsed < 350) {
            alpha = elapsed / 350.0f;
        } else if (elapsed > DISPLAY_DURATION - 1000) {
            alpha = (DISPLAY_DURATION - elapsed) / 1000.0f;
        }
        alpha = Math.max(0.0f, Math.min(1.0f, alpha));

        double userScale = GreatSageClientConfig.CLIENT.hudScale.get();

        guiGraphics.pose().pushPose();
        float scale = (float) userScale;
        guiGraphics.pose().scale(scale, scale, 1.0f);

        // =========================================================================
        // 1. NÚCLEO LUMINOSO ANIMADO (Exclusivamente en la esquina inferior derecha)
        // =========================================================================
        int coreX = width - 40;
        int coreY = height - 90;
        long time = now;

        float pulse = 1.0f + 0.2f * (float)Math.sin(time * 0.012);
        if (charIndex < fullText.length()) {
            pulse += 0.15f * (float)Math.sin(time * 0.045);
        }

        // Rayos de energía externa del núcleo
        for (int i = 0; i < 10; i++) {
            double angle = (time * 0.005 + (i * Math.PI / 5.0));
            int rx = (int) (coreX + Math.cos(angle) * (24 * pulse));
            int ry = (int) (coreY + Math.sin(angle) * (24 * pulse));
            guiGraphics.fill(coreX, coreY, rx, ry, 0x6600FFFF);
        }

        // Doble Anillo Rúnico Contrarrotatorio
        float rot1 = time * 0.009f;
        float rot2 = -time * 0.014f;

        for (int i = 0; i < 6; i++) {
            double a1 = rot1 + (i * Math.PI / 3.0);
            int px1 = (int) (coreX + Math.cos(a1) * 16);
            int py1 = (int) (coreY + Math.sin(a1) * 16);
            guiGraphics.fill(px1 - 2, py1 - 2, px1 + 2, py1 + 2, 0xFFFFD700);

            double a2 = rot2 + (i * Math.PI / 3.0);
            int px2 = (int) (coreX + Math.cos(a2) * 10);
            int py2 = (int) (coreY + Math.sin(a2) * 10);
            guiGraphics.fill(px2 - 1, py2 - 1, px2 + 1, py2 + 1, 0xFF00FFFF);
        }

        // Núcleo central brillante
        int coreSize = (int)(5 * pulse);
        guiGraphics.fill(coreX - coreSize - 2, coreY - coreSize - 2, coreX + coreSize + 2, coreY + coreSize + 2, 0x8800FFFF);
        guiGraphics.fill(coreX - coreSize, coreY - coreSize, coreX + coreSize, coreY + coreSize, 0xFFFFFFFF);
        guiGraphics.fill(coreX - 2, coreY - 2, coreX + 2, coreY + 2, 0xFFFFD700);

        // Partículas cuánticas orbitando el núcleo
        for (QuantumParticle p : particles) {
            p.update();
            int partX = (int) (coreX + Math.cos(p.angle) * p.radius);
            int partY = (int) (coreY + p.y);
            guiGraphics.fill(partX, partY, (int)(partX + p.size), (int)(partY + p.size), p.color);
        }

        // =========================================================================
        // 2. CHAT PERSONALIZADO DE RAFAEL (Burbuja de diálogo flotante estilo RPG)
        // =========================================================================
        int chatBoxWidth = 320;
        int chatBoxHeight = 55;
        // Ubicado en la parte inferior central-izquierda (totalmente libre de solapamientos con el chat de Minecraft o el núcleo)
        int chatX = 30;
        int chatY = height - chatBoxHeight - 40;

        // Fondo semitransparente cian-oscuro arcano con bordes de luz nítidos
        guiGraphics.fill(chatX, chatY, chatX + chatBoxWidth, chatY + chatBoxHeight, 0xE601030B);
        
        // Marco brillante arcano (Cian y Dorado)
        guiGraphics.fill(chatX, chatY, chatX + chatBoxWidth, chatY + 2, 0xFF00FFFF);
        guiGraphics.fill(chatX, chatY + chatBoxHeight - 2, chatX + chatBoxWidth, chatY + chatBoxHeight, 0xFFFFD700);
        guiGraphics.fill(chatX, chatY, chatX + 2, chatY + chatBoxHeight, 0xFF00FFFF);
        guiGraphics.fill(chatX + chatBoxWidth - 2, chatY, chatX + chatBoxWidth, chatY + chatBoxHeight, 0xFFFFD700);

        // Título del chat personalizado
        Font font = minecraft.font;
        guiGraphics.drawString(font, Component.literal("§b✦ [ Gran Sabio (Rafael) ] ✦"), chatX + 8, chatY + 6, 0xFFFFD700, true);

        // Texto con efecto máquina de escribir ajustado dentro de la burbuja RPG
        List<FormattedCharSequence> lines = font.split(Component.literal(displayedText), chatBoxWidth - 16);
        int lineY = chatY + 20;
        for (FormattedCharSequence line : lines) {
            if (lineY < chatY + chatBoxHeight - 8) {
                guiGraphics.drawString(font, line, chatX + 8, lineY, 0x00FFFF, false);
                lineY += 12;
            }
        }

        guiGraphics.pose().popPose();
    };
}
