package com.roadforge.road;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/** Превращает параметры дороги в список блоков (прямой участок вдоль оси). */
public final class RoadPlan {

    /**
     * @param start  блок центра дороги в начале (слой, на котором будет поверхность дороги)
     * @param dir    направление дороги (горизонтальное)
     * @param length длина в блоках (включая оба конца)
     */
    public static List<Placement> generate(BlockPos start, Direction dir, int length, RoadSpec spec,
                                           CrossSection cs, RoadPalette pal, int clearHeight) {
        int w = cs.width();
        List<Placement> out = new ArrayList<>(length * w * (2 + clearHeight));
        Direction right = dir.getClockWise();
        BlockState air = Blocks.AIR.defaultBlockState();

        // 1. очистка пространства над дорогой
        for (int s = 0; s < length; s++) {
            for (int i = 0; i < w; i++) {
                BlockPos base = at(start, dir, right, s, i - cs.ref());
                for (int h = 1; h <= clearHeight; h++) {
                    out.add(new Placement(base.above(h), air));
                }
            }
        }

        // 2. поверхность
        for (int s = 0; s < length; s++) {
            for (int i = 0; i < w; i++) {
                BlockPos pos = at(start, dir, right, s, i - cs.ref());
                out.add(new Placement(pos, surface(cs.cols().get(i), s, length, dir, right, spec, pal)));
            }
        }

        // 3. фонари (на крайних колонках тротуара, в шахматном порядке)
        if (spec.lampSpacing() > 0 && cs.lampLeft() >= 0) {
            int sp = spec.lampSpacing();
            int half = sp / 2;
            for (int s = 1; s < length - 1; s++) {
                if (s % sp == half) {
                    BlockPos p = at(start, dir, right, s, cs.lampLeft() - cs.ref()).above();
                    out.add(new Placement(p, pal.state(pal.lamp(), right)));
                }
                if (s % sp == 0) {
                    BlockPos p = at(start, dir, right, s, cs.lampRight() - cs.ref()).above();
                    out.add(new Placement(p, pal.state(pal.lamp(), right.getOpposite())));
                }
            }
        }
        return out;
    }

    private static BlockPos at(BlockPos start, Direction dir, Direction right, int along, int lateral) {
        return start.relative(dir, along).relative(right, lateral);
    }

    private static BlockState surface(Kind k, int s, int length, Direction dir, Direction right,
                                      RoadSpec spec, RoadPalette pal) {
        boolean cap = spec.endCaps() && length >= 2 && (s == 0 || s == length - 1);
        Direction capFacing = (s == 0) ? dir.getOpposite() : dir;
        boolean dashOn = (s % spec.dashPeriod()) < spec.dashOn();

        return switch (k) {
            case ASPHALT -> cap ? pal.state(pal.capPlain(), capFacing) : pal.state(pal.asphalt(), dir);
            case LINE_SOLID -> cap ? pal.state(pal.capSolid(), capFacing) : pal.state(pal.line(), dir);
            case LINE_DASH -> cap ? pal.state(pal.capDash(), capFacing)
                    : (dashOn ? pal.state(pal.line(), dir) : pal.state(pal.asphalt(), dir));
            case SIDEWALK_L -> pal.state(pal.sidewalk(), right);
            case SIDEWALK_R -> pal.state(pal.sidewalk(), right.getOpposite());
            case GRASS -> pal.state(pal.grass(), dir);
        };
    }

    private RoadPlan() {}
}
