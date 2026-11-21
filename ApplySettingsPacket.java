package com.roadforge.network;

import com.roadforge.RoadConfig;
import com.roadforge.player.PlayerRoadState;
import com.roadforge.road.RoadSettings;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Клиент -> сервер: текущие настройки из меню. */
public record ApplySettingsPacket(RoadSettings settings) {

    public static void encode(ApplySettingsPacket p, FriendlyByteBuf buf) {
        p.settings().encode(buf);
    }

    public static ApplySettingsPacket decode(FriendlyByteBuf buf) {
        return new ApplySettingsPacket(RoadSettings.decode(buf));
    }

    public static void handle(ApplySettingsPacket p, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        c.enqueueWork(() -> {
            ServerPlayer sp = c.getSender();
            if (sp == null || !sp.hasPermissions(RoadConfig.permission())) {
                return;
            }
            RoadSettings s = p.settings();
            s.sanitize();
            PlayerRoadState.of(sp).settings = s;
        });
        c.setPacketHandled(true);
    }
}
