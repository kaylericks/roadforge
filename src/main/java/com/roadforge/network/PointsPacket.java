package com.roadforge.network;

import com.roadforge.client.ClientState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Сервер -> клиент: выбранные жезлом точки (для отображения в меню). */
public record PointsPacket(BlockPos a, BlockPos b) {

    public static void encode(PointsPacket p, FriendlyByteBuf buf) {
        buf.writeBoolean(p.a() != null);
        if (p.a() != null) {
            buf.writeBlockPos(p.a());
        }
        buf.writeBoolean(p.b() != null);
        if (p.b() != null) {
            buf.writeBlockPos(p.b());
        }
    }

    public static PointsPacket decode(FriendlyByteBuf buf) {
        BlockPos a = buf.readBoolean() ? buf.readBlockPos() : null;
        BlockPos b = buf.readBoolean() ? buf.readBlockPos() : null;
        return new PointsPacket(a, b);
    }

    public static void handle(PointsPacket p, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientState.setPoints(p.a(), p.b())));
        c.setPacketHandled(true);
    }
}
