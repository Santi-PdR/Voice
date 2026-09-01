package com.rafael.network;

import com.rafael.GreatSageMod;
import com.rafael.server.RafaelLanguageManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Supplier;

public final class PacketHandler {
    private static final String PROTOCOL_VERSION = "3.0";
    private static final int MAX_TEXT_LENGTH = 4096;
    private static final int MAX_EMOTION_LENGTH = 64;
    private static final int MAX_LANGUAGE_LENGTH = 16;
    public static final int MAX_AUDIO_BYTES = 900_000;

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(GreatSageMod.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals);

    private static int packetId = 0;

    private PacketHandler() {}

    public static void register() {
        CHANNEL.messageBuilder(RafaelSpeechPacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(RafaelSpeechPacket::encode)
                .decoder(RafaelSpeechPacket::new)
                .consumerNetworkThread(RafaelSpeechPacket::handle)
                .add();

        CHANNEL.messageBuilder(ClientLanguagePacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ClientLanguagePacket::encode)
                .decoder(ClientLanguagePacket::new)
                .consumerNetworkThread(ClientLanguagePacket::handle)
                .add();
    }

    public static void sendLanguageToServer(String minecraftLanguage) {
        CHANNEL.sendToServer(new ClientLanguagePacket(minecraftLanguage));
    }

    public static void sendToClient(ServerPlayer player, String text, byte[] audioWav, String emotion, boolean syntheticVoice, String language) {
        byte[] safeAudio = audioWav == null ? new byte[0] : audioWav;
        if (safeAudio.length > MAX_AUDIO_BYTES) {
            GreatSageMod.LOGGER.warn("Paquete de voz descartó {} bytes de audio por exceder el máximo de {}.", safeAudio.length, MAX_AUDIO_BYTES);
            safeAudio = new byte[0];
            syntheticVoice = false;
        }
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new RafaelSpeechPacket(text, safeAudio, emotion, syntheticVoice, language));
    }

    public static final class ClientLanguagePacket {
        private final String language;

        public ClientLanguagePacket(String language) {
            this.language = language == null ? "en_us" : language;
        }

        public ClientLanguagePacket(FriendlyByteBuf buf) {
            this.language = buf.readUtf(MAX_LANGUAGE_LENGTH);
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeUtf(language, MAX_LANGUAGE_LENGTH);
        }

        public boolean handle(Supplier<net.minecraftforge.network.NetworkEvent.Context> supplier) {
            net.minecraftforge.network.NetworkEvent.Context context = supplier.get();
            ServerPlayer sender = context.getSender();
            context.enqueueWork(() -> {
                if (sender != null) RafaelLanguageManager.update(sender, language);
            });
            context.setPacketHandled(true);
            return true;
        }
    }

    public static final class RafaelSpeechPacket {
        private final String text;
        private final byte[] audioWav;
        private final String emotion;
        private final boolean syntheticVoice;
        private final String language;

        public RafaelSpeechPacket(String text, byte[] audioWav, String emotion, boolean syntheticVoice, String language) {
            this.text = text != null ? text : "";
            this.audioWav = audioWav != null ? audioWav : new byte[0];
            this.emotion = emotion != null ? emotion : "analytical";
            this.syntheticVoice = syntheticVoice && this.audioWav.length > 0;
            this.language = RafaelLanguageManager.normalize(language);
        }

        public RafaelSpeechPacket(FriendlyByteBuf buf) {
            this.text = buf.readUtf(MAX_TEXT_LENGTH);
            this.audioWav = buf.readByteArray(MAX_AUDIO_BYTES);
            this.emotion = buf.readUtf(MAX_EMOTION_LENGTH);
            this.syntheticVoice = buf.readBoolean() && this.audioWav.length > 0;
            this.language = buf.readUtf(MAX_LANGUAGE_LENGTH);
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeUtf(text, MAX_TEXT_LENGTH);
            buf.writeByteArray(audioWav);
            buf.writeUtf(emotion, MAX_EMOTION_LENGTH);
            buf.writeBoolean(syntheticVoice);
            buf.writeUtf(language, MAX_LANGUAGE_LENGTH);
        }

        public boolean handle(Supplier<net.minecraftforge.network.NetworkEvent.Context> supplier) {
            net.minecraftforge.network.NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> com.rafael.client.GreatSageClient.handleRafaelSpeech(text, audioWav, emotion, syntheticVoice, language));
            context.setPacketHandled(true);
            return true;
        }
    }
}
