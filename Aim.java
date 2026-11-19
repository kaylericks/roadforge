package com.roadforge.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Точка под прицелом на любом расстоянии: блок, в который попал луч, иначе - плоскость слоя дороги. */
public final class Aim {
    public static final double DISTANCE = 300;

    /**
     * @param planeY слой, на котором строится дорога (если луч уходит в небо, точка берётся на этой плоскости)
     */
    public static BlockPos pick(Minecraft mc, int planeY) {
        Player p = mc.player;
        if (p == null || mc.level == null) {
            return null;
        }
        HitResult hit = p.pick(DISTANCE, 1.0F, false);
        if (hit.getType() == HitResult.Type.BLOCK) {
            return ((BlockHitResult) hit).getBlockPos();
        }
        Vec3 eye = p.getEyePosition(1.0F);
        Vec3 look = p.getViewVector(1.0F);
        if (look.y >= -0.01) {
            return null;
        }
        double t = (planeY + 1.0 - eye.y) / look.y;
        if (t <= 0 || t > DISTANCE * 2) {
            return null;
        }
        Vec3 at = eye.add(look.scale(t));
        return new BlockPos(Mth.floor(at.x), planeY, Mth.floor(at.z));
    }

    /** Слой для плоскости: высота последней отмеченной точки, иначе уровень под ногами. */
    public static int planeY(Minecraft mc) {
        BlockPos last = null;
        if (!ClientState.path().isEmpty()) {
            last = ClientState.path().get(ClientState.path().size() - 1);
        } else if (ClientState.pointB() != null) {
            last = ClientState.pointB();
        } else if (ClientState.pointA() != null) {
            last = ClientState.pointA();
        }
        if (last != null) {
            return last.getY();
        }
        return mc.player == null ? 64 : mc.player.blockPosition().getY() - 1;
    }

    private Aim() {}
}
