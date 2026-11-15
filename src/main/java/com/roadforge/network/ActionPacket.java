package com.roadforge.network;

import com.roadforge.RoadConfig;
import com.roadforge.build.BuildManager;
import com.roadforge.build.RoadActions;
import com.roadforge.player.PlayerRoadState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Клиент -> сервер: кнопки меню. */
public record ActionPacket(Action action) {
    public enum Action { BUILD, UNDO, CLEAR }

    public static void encode(ActionPacket p, FriendlyByteBuf buf) {
        buf.writeEnum(p.action());
    }

    public static ActionPacket decode(FriendlyByteBuf buf) {
        return new ActionPacket(buf.readEnum(Action.class));
    }

    public static void handle(ActionPacket p, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        c.enqueueWork(() -> {
            ServerPlayer sp = c.getSender();
            if (sp == null || !sp.hasPermissions(RoadConfig.permission())) {
                return;
            }
            switch (p.action()) {
                case BUILD -> RoadActions.build(sp);
                case UNDO -> {
                    if (BuildManager.undo(sp)) {
                        sp.sendSystemMessage(Component.literal("§eОтменяю последнюю постройку..."));
                    } else {
                        sp.sendSystemMessage(Component.literal("§cОтменять нечего."));
                    }
                }
                case CLEAR -> {
                    PlayerRoadState st = PlayerRoadState.of(sp);
                    st.a = null;
                    st.b = null;
                    Net.sendPoints(sp, st);
                }
            }
        });
        c.setPacketHandled(true);
    }
}
