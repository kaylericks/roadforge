package com.roadforge.network;

import com.roadforge.RoadForge;
import com.roadforge.player.PlayerRoadState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class Net {
    private static final String PROTOCOL = "4";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(RoadForge.MOD_ID, "main"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals
    );

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++, ApplySettingsPacket.class, ApplySettingsPacket::encode,
                ApplySettingsPacket::decode, ApplySettingsPacket::handle);
        CHANNEL.registerMessage(id++, ActionPacket.class, ActionPacket::encode,
                ActionPacket::decode, ActionPacket::handle);
        CHANNEL.registerMessage(id++, PointsPacket.class, PointsPacket::encode,
                PointsPacket::decode, PointsPacket::handle);
        CHANNEL.registerMessage(id++, PickPacket.class, PickPacket::encode,
                PickPacket::decode, PickPacket::handle);
    }

    public static void sendPoints(ServerPlayer player, PlayerRoadState st) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new PointsPacket(st.a, st.b, new java.util.ArrayList<>(st.path)));
    }

    private Net() {}
}
