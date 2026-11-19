package com.roadforge.network;

import com.roadforge.client.ClientState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Сервер -> клиент: выбранные жезлом точки (для отображения в меню). */
public record PointsPacket(BlockPos a, BlockPos b, List<BlockPos> path) {

    public static void encode(PointsPacket p, FriendlyByteBuf buf) {
        buf.writeBoolean(p.a() != null);
        if (p.a() != null) {
            buf.writeBlockPos(p.a());
        }
        buf.writeBoolean(p.b() != null);
        if (p.b() != null) {
            buf.writeBlockPos(p.b());
        }
        buf.writeVarInt(p.path().size());
        for (BlockPos q : p.path()) {
            buf.writeBlockPos(q);
        }
    }

    public static PointsPacket decode(FriendlyByteBuf buf) {
        BlockPos a = buf.readBoolean() ? buf.readBlockPos() : null;
        BlockPos b = buf.readBoolean() ? buf.readBlockPos() : null;
        int n = buf.readVarInt();
        List<BlockPos> path = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            path.add(buf.readBlockPos());
        }
        return new PointsPacket(a, b, path);
    }

    public static void handle(PointsPacket p, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientState.setPoints(p.a(), p.b(), p.path())));
        c.setPacketHandled(true);
    }
}
