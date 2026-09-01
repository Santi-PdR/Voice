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

    // Partículas cuánticas exclusivas para el núcleo luminoso animado
    private static final List<QuantumParticle> particles = new ArrayList<>();
    private static final Random random = new Random();

    static {
        for (int i = 0; i < 35; i++) {
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
            radius = random.nextFloat() * 24f + 6f;
            angularSpeed = (random.nextBoolean() ? 1 : -1) * (random.nextFloat() * 0.06f + 0.02f);
            y = random.nextFloat() * 36 - 18;
            verticalSpeed = (random.nextFloat() - 0.5f) * 0.4f;
            size = random.nextFloat() * 2.2f + 1.0f;
            
            int colorChoice = random.nextInt(3);
            if (colorChoice == 0) color = 0xFFFFD700; // Dorado Sabio
            else if (colorChoice == 1) color = 0xFF00FFFF; // Cian Neón
            else color = 0xFFFFFFFF; // Blanco Láser
        }

        public void update() {
            angle += angularSpeed;
            y += verticalSpeed;
            if (Math.abs(y) > 22) {
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
        if (elapsed < 400) {
            alpha = elapsed / 400.0f;
        } else if (elapsed > DISPLAY_DURATION - 1000) {
            alpha = (DISPLAY_DURATION - elapsed) / 1000.0f;
        }
        alpha = Math.max(0.0f, Math.min(1.0f, alpha));

        // Dimensiones del cuadro de diálogo personalizado (Ubicado en la esquina inferior derecha para no solapar el chat de la izquierda)
        double userScale = GreatSageClientConfig.CLIENT.hudScale.get();
        int boxWidth = 340;
        int boxHeight = 75;
        
        int x = width - boxWidth - 20;
        int y = height - boxHeight - 45;

        guiGraphics.pose().pushPose();

        float scale = (float) userScale;
        if (elapsed < 300) {
            float t = elapsed / 300.0f;
            scale *= (0.85f + (0.15f * t));
        }
        guiGraphics.pose().scale(scale, scale, 1.0f);

        // 1. Fondo del cuadro de diálogo personalizado (Estilo novela visual / RPG arcano)
        guiGraphics.fill(x, y, x + boxWidth, y + boxHeight, 0xF2020510);

        // Líneas de escaneo holográficas sutiles
        long time = now;
        int scanLineY = y + (int)((time * 0.05) % boxHeight);
        guiGraphics.fill(x, scanLineY, x + boxWidth, scanLineY + 1, 0x1500FFFF);

        // 2. NÚCLEO LUMINOSO ANIMADO UBICADO EXCLUSIVAMENTE EN EL EXTREMO DERECHO
        int coreX = x + boxWidth - 35;
        int coreY = y + (boxHeight / 2);

        float pulse = 1.0f + 0.18f * (float)Math.sin(time * 0.01);
        if (charIndex < fullText.length()) {
            pulse += 0.12f * (float)Math.sin(time * 0.04);
        }

        // Rayos de energía externa del núcleo
        for (int i = 0; i < 10; i++) {
            double angle = (time * 0.004 + (i * Math.PI / 5.0));
            int rx = (int) (coreX + Math.cos(angle) * (22 * pulse));
            int ry = (int) (coreY + Math.sin(angle) * (22 * pulse));
            guiGraphics.fill(coreX, coreY, rx, ry, 0x6000FFFF);
        }

        // Doble Anillo Rúnico Concéntrico Contrarrotatorio
        float rot1 = time * 0.008f;
        float rot2 = -time * 0.012f;

        for (int i = 0; i < 6; i++) {
            double a1 = rot1 + (i * Math.PI / 3.0);
            int px1 = (int) (coreX + Math.cos(a1) * 15);
            int py1 = (int) (coreY + Math.sin(a1) * 15);
            guiGraphics.fill(px1 - 2, py1 - 2, px1 + 2, py1 + 2, 0xFFFFD700);

            double a2 = rot2 + (i * Math.PI / 3.0);
            int px2 = (int) (coreX + Math.cos(a2) * 9);
            int py2 = (int) (coreY + Math.sin(a2) * 9);
            guiGraphics.fill(px2 - 1, py2 - 1, px2 + 1, py2 + 1, 0xFF00FFFF);
        }

        // Núcleo central brillante
        int coreSize = (int)(4 * pulse);
        guiGraphics.fill(coreX - coreSize - 2, coreY - coreSize - 2, coreX + coreSize + 2, coreY + coreSize + 2, 0x7700FFFF);
        guiGraphics.fill(coreX - coreSize, coreY - coreSize, coreX + coreSize, coreY + coreSize, 0xFFFFFFFF);
        guiGraphics.fill(coreX - 2, coreY - 2, coreX + 2, coreY + 2, 0xFFFFD700);

        // Partículas cuánticas orbitando exclusivamente el núcleo derecho
        for (QuantumParticle p : particles) {
            p.update();
            int partX = (int) (coreX + Math.cos(p.angle) * p.radius);
            int partY = (int) (coreY + p.y);
            if (partX > x + 10 && partX < x + boxWidth - 10 && partY > y + 5 && partY < y + boxHeight - 5) {
                guiGraphics.fill(partX, partY, (int)(partX + p.size), (int)(partY + p.size), p.color);
            }
        }

        // 3. Bordes energéticos arcanos de alta definición para la caja de texto
        guiGraphics.fill(x, y, x + boxWidth, y + 2, 0xFF00FFFF); // Superior cian
        guiGraphics.fill(x, y + boxHeight - 2, x + boxWidth, y + boxHeight, 0xFFFFD700); // Inferior dorado
        guiGraphics.fill(x, y, x + 2, y + boxHeight, 0xFF00FFFF); // Izquierdo
        guiGraphics.fill(x + boxWidth - 2, y, x + boxWidth, y + boxHeight, 0xFFFFD700); // Derecho

        // Esquinas ornamentadas
        guiGraphics.fill(x, y, x + 5, y + 5, 0xFFFFD700);
        guiGraphics.fill(x + boxWidth - 5, y, x + boxWidth, y + 5, 0xFFFFD700);
        guiGraphics.fill(x, y + boxHeight - 5, x + 5, y + boxHeight, 0xFFFFD700);
        guiGraphics.fill(x + boxWidth - 5, y + boxHeight - 5, x + boxWidth, y + boxHeight, 0xFFFFD700);

        // 4. Texto del chat personalizado con fuentes especiales y efectos (Sin solaparse con el núcleo derecho)
        Font font = minecraft.font;
        guiGraphics.drawString(font, Component.literal("§b✦ [ Gran Sabio (Rafael) ] ✦"), x + 10, y + 7, 0xFFFFD700, true);

        List<FormattedCharSequence> lines = font.split(Component.literal(displayedText), boxWidth - 85);
        int lineY = y + 22;
        for (FormattedCharSequence line : lines) {
            if (lineY < y + boxHeight - 8) {
                guiGraphics.drawString(font, line, x + 10, lineY, 0x00FFFF, false);
                lineY += 12;
            }
        }

        guiGraphics.pose().popPose();
    };
}
