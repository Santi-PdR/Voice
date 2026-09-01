package com.rafael.network;

import com.rafael.GreatSageMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Supplier;

public class PacketHandler {
    private static final String PROTOCOL_VERSION = "2.0";
    private static final int MAX_TEXT_LENGTH = 4096;
    private static final int MAX_EMOTION_LENGTH = 64;
    public static final int MAX_AUDIO_BYTES = 900_000;

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(new ResourceLocation(GreatSageMod.MOD_ID, "main"), () -> PROTOCOL_VERSION, PROTOCOL_VERSION::equals, PROTOCOL_VERSION::equals);
    private static int packetId = 0;

    public static void register() {
        CHANNEL.messageBuilder(RafaelSpeechPacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT).encoder(RafaelSpeechPacket::encode).decoder(RafaelSpeechPacket::new).consumerNetworkThread(RafaelSpeechPacket::handle).add();
    }

    public static void sendToClient(ServerPlayer player, String text, byte[] audioWav, String emotion, boolean syntheticVoice) {
        byte[] safeAudio = audioWav == null ? new byte[0] : audioWav;
        if (safeAudio.length > MAX_AUDIO_BYTES) {
            GreatSageMod.LOGGER.warn("Paquete de voz descartó {} bytes de audio por exceder el máximo de {}.", safeAudio.length, MAX_AUDIO_BYTES);
            safeAudio = new byte[0];
            syntheticVoice = false;
        }
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new RafaelSpeechPacket(text, safeAudio, emotion, syntheticVoice));
    }

    public static class RafaelSpeechPacket {
        private final String text;
        private final byte[] audioWav;
        private final String emotion;
        private final boolean syntheticVoice;

        public RafaelSpeechPacket(String text, byte[] audioWav, String emotion, boolean syntheticVoice) {
            this.text = text != null ? text : "";
            this.audioWav = audioWav != null ? audioWav : new byte[0];
            this.emotion = emotion != null ? emotion : "analytical";
            this.syntheticVoice = syntheticVoice && this.audioWav.length > 0;
        }

        public RafaelSpeechPacket(FriendlyByteBuf buf) {
            this.text = buf.readUtf(MAX_TEXT_LENGTH);
            this.audioWav = buf.readByteArray(MAX_AUDIO_BYTES);
            this.emotion = buf.readUtf(MAX_EMOTION_LENGTH);
            this.syntheticVoice = buf.readBoolean() && this.audioWav.length > 0;
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeUtf(text, MAX_TEXT_LENGTH);
            buf.writeByteArray(audioWav);
            buf.writeUtf(emotion, MAX_EMOTION_LENGTH);
            buf.writeBoolean(syntheticVoice);
        }

        public boolean handle(Supplier<net.minecraftforge.network.NetworkEvent.Context> supplier) {
            net.minecraftforge.network.NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> com.rafael.client.GreatSageClient.handleRafaelSpeech(text, audioWav, emotion, syntheticVoice));
            context.setPacketHandled(true);
            return true;
        }
    }
}
