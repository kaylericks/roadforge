package com.roadforge.network;

import com.roadforge.RoadConfig;
import com.roadforge.build.RoadActions;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Клиент -> сервер: клик жезлом по точке, найденной лучом на любом расстоянии (в том числе по воздуху). */
public record PickPacket(Action action, BlockPos pos) {
    public enum Action { CLICK, BUILD, UNDO_POINT }

    /** Максимальное расстояние до точки, блоков. */
    public static final int MAX_DISTANCE = 600;

    public static void encode(PickPacket p, FriendlyByteBuf buf) {
        buf.writeEnum(p.action());
        buf.writeBlockPos(p.pos());
    }

    public static PickPacket decode(FriendlyByteBuf buf) {
        return new PickPacket(buf.readEnum(Action.class), buf.readBlockPos());
    }

    public static void handle(PickPacket p, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        c.enqueueWork(() -> {
            ServerPlayer sp = c.getSender();
            if (sp == null || !sp.hasPermissions(RoadConfig.permission())) {
                return;
            }
            if (p.action() == Action.UNDO_POINT) {
                RoadActions.removeLastPoint(sp);
                return;
            }
            if (p.action() == Action.BUILD) {
                RoadActions.build(sp);
                return;
            }
            BlockPos pos = p.pos();
            double dx = pos.getX() + 0.5 - sp.getX();
            double dz = pos.getZ() + 0.5 - sp.getZ();
            if (Math.hypot(dx, dz) > MAX_DISTANCE || !sp.serverLevel().isLoaded(pos)
                    || pos.getY() < sp.serverLevel().getMinBuildHeight()
                    || pos.getY() >= sp.serverLevel().getMaxBuildHeight()) {
                return;
            }
            RoadActions.click(sp, pos);
        });
        c.setPacketHandled(true);
    }
}
