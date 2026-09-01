package com.rafael.config;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public class GreatSageConfig {
    public static class Server {
        public final ForgeConfigSpec.ConfigValue<String> aiEndpointUrl;
        public final ForgeConfigSpec.ConfigValue<String> apiKey;
        public final ForgeConfigSpec.BooleanValue enableAI;
        public final ForgeConfigSpec.BooleanValue announceItemDrops;
        public final ForgeConfigSpec.BooleanValue announcePlayerDeath;
        public final ForgeConfigSpec.BooleanValue announceLowHealth;

        public Server(ForgeConfigSpec.Builder builder) {
            builder.push("Great Sage AI Server Configuration (Managed by Server Owner)");

            aiEndpointUrl = builder
                    .comment("URL del servidor de IA configurado por el owner del servidor para evaluar eventos y generar la voz de Rafael.")
                    .define("aiEndpointUrl", "http://localhost:8000/rafael/evaluate");

            apiKey = builder
                    .comment("Clave API opcional para autenticarse con el servidor de IA del owner.")
                    .define("apiKey", "");

            enableAI = builder
                    .comment("Activar o desactivar las respuestas automáticas de Rafael impulsadas por IA.")
                    .define("enableAI", true);

            announceItemDrops = builder
                    .comment("Permitir que Rafael analice y anuncie items droppeados especiales.")
                    .define("announceItemDrops", true);

            announcePlayerDeath = builder
                    .comment("Permitir que Rafael analice la causa de muerte del jugador.")
                    .define("announcePlayerDeath", true);

            announceLowHealth = builder
                    .comment("Permitir que Rafael advierta cuando el jugador tenga vida crítica.")
                    .define("announceLowHealth", true);

            builder.pop();
        }
    }

    public static final Server SERVER;
    public static final ForgeConfigSpec SERVER_SPEC;

    static {
        Pair<Server, ForgeConfigSpec> specPair = new ForgeConfigSpec.Builder().configure(Server::new);
        SERVER = specPair.getLeft();
        SERVER_SPEC = specPair.getRight();
    }
}
