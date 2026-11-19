package com.roadforge.client;

import com.roadforge.road.BuildMode;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Точка под прицелом на любом расстоянии.
 * Первая точка - блок, в который попал луч, плюс смещение по высоте. Следующие точки лежат на горизонтальной
 * плоскости на высоте последней точки плюс смещение, поэтому дорогу можно вести на любой высоте (мосты, эстакады).
 * Смещение меняется Ctrl+колесо мыши или клавишами Page Up / Page Down и сбрасывается после каждой точки.
 */
public final class Aim {
    public static final double DISTANCE = 300;
    /** Смещение по высоте относительно земли (первая точка) или последней точки (остальные). */
    public static int heightOffset = 0;

    /** Последняя отмеченная точка, от высоты которой считается плоскость; null - точек ещё нет. */
    public static BlockPos anchor() {
        BuildMode mode = ClientState.settings().mode;
        if (mode == BuildMode.PATH) {
            return ClientState.path().isEmpty() ? null : ClientState.path().get(ClientState.path().size() - 1);
        }
        if (mode == BuildMode.ROAD) {
            return ClientState.pointB() == null ? ClientState.pointA() : null;
        }
        return null;
    }

    public static BlockPos pick(Minecraft mc) {
        Player p = mc.player;
        if (p == null || mc.level == null) {
            return null;
        }
        BlockPos anchor = anchor();
        Vec3 eye = p.getEyePosition(1.0F);
        Vec3 look = p.getViewVector(1.0F);
        if (anchor == null) {
            HitResult hit = p.pick(DISTANCE, 1.0F, false);
            if (hit.getType() == HitResult.Type.BLOCK) {
                return ((BlockHitResult) hit).getBlockPos().above(heightOffset);
            }
            return plane(eye, look, p.blockPosition().getY() - 1 + heightOffset);
        }
        return plane(eye, look, anchor.getY() + heightOffset);
    }

    private static BlockPos plane(Vec3 eye, Vec3 look, int layer) {
        if (look.y >= -0.01) {
            return null;
        }
        double t = (layer + 1.0 - eye.y) / look.y;
        if (t <= 0 || t > DISTANCE * 2) {
            return null;
        }
        Vec3 at = eye.add(look.scale(t));
        return new BlockPos(Mth.floor(at.x), layer, Mth.floor(at.z));
    }

    /** Текст для подписи: от чего считается высота. */
    public static String heightLabel() {
        String base = anchor() == null ? "земли" : "последней точки";
        String sign = heightOffset > 0 ? "+" : "";
        return "Высота от " + base + ": " + sign + heightOffset + "  (Ctrl+колесо, PgUp/PgDn)";
    }

    private Aim() {}
}
