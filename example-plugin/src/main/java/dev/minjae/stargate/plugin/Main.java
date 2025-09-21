package dev.minjae.stargate.plugin;

import alemiz.stargate.server.StarGateServer;
import dev.minjae.stargate.plugin.packet.TestPacket;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main implements Plugin {

    private Logger logger = LoggerFactory.getLogger(Main.class);

    @Override
    public void onEnable(@NotNull StarGateServer server) {
        server.getProtocolCodec().registerPacket(TestPacket.PACKET_ID, TestPacket.class);
        logger.info("Registered TestPacket and enabled the plugin!");
    }

    @Override
    public void onDisable() {
        logger.info("Plugin Disabled!");
    }
}