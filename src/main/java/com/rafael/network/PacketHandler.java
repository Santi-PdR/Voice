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
    private static final String PROTOCOL_VERSION = "1.0";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(GreatSageMod.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int packetId = 0;

    public static void register() {
        CHANNEL.messageBuilder(RafaelSpeechPacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(RafaelSpeechPacket::encode)
                .decoder(RafaelSpeechPacket::new)
                .consumerNetworkThread(RafaelSpeechPacket::handle)
                .add();
    }

    public static void sendToClient(ServerPlayer player, String text, String audioUrl, String emotion) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new RafaelSpeechPacket(text, audioUrl, emotion));
    }

    public static class RafaelSpeechPacket {
        private final String text;
        private final String audioUrl;
        private final String emotion;

        public RafaelSpeechPacket(String text, String audioUrl, String emotion) {
            this.text = text;
            this.audioUrl = audioUrl != null ? audioUrl : "";
            this.emotion = emotion != null ? emotion : "analytical";
        }

        public RafaelSpeechPacket(FriendlyByteBuf buf) {
            this.text = buf.readUtf();
            this.audioUrl = buf.readUtf();
            this.emotion = buf.readUtf();
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeUtf(text);
            buf.writeUtf(audioUrl);
            buf.writeUtf(emotion);
        }

        public boolean handle(Supplier<net.minecraftforge.network.NetworkEvent.Context> supplier) {
            net.minecraftforge.network.NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> {
                com.rafael.client.GreatSageClient.handleRafaelSpeech(text, audioUrl, emotion);
            });
            context.setPacketHandled(true);
            return true;
        }
    }
}
