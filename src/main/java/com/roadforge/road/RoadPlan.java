package com.roadforge.road;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/** Превращает параметры дороги в список блоков (прямой участок вдоль оси). */
public final class RoadPlan {

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
                out.add(new Placement(pos, surfaceState(cs.cols().get(i), cs.depths().get(i), i < cs.ref(),
                        s, length, dir, right, spec, pal)));
            }
        }

        // 3. фонари (на крайних колонках тротуара, в шахматном порядке)
        if (spec.lampSpacing() > 0 && cs.lampLeft() >= 0) {
            int sp = spec.lampSpacing();
            int half = sp / 2;
            for (int s = 1; s < length - 1; s++) {
                if (s % sp == half) {
                    BlockPos p = at(start, dir, right, s, cs.lampLeft() - cs.ref()).above();
                    out.add(new Placement(p, pal.lampState(right)));
                }
                if (s % sp == 0) {
                    BlockPos p = at(start, dir, right, s, cs.lampRight() - cs.ref()).above();
                    out.add(new Placement(p, pal.lampState(right.getOpposite())));
                }
            }
        }
        return out;
    }

    private static BlockPos at(BlockPos start, Direction dir, Direction right, int along, int lateral) {
        return start.relative(dir, along).relative(right, lateral);
    }

    /**
     * Блок для колонки профиля.
     * s - позиция вдоль дороги, depth - глубина колонки парковки, leftSide - колонка слева от оси.
     */
    public static BlockState surfaceState(Kind k, int depth, boolean leftSide, int s, int length,
                                          Direction dir, Direction right, RoadSpec spec, RoadPalette pal) {
        boolean cap = spec.endCaps() && length >= 2 && (s == 0 || s == length - 1);
        Direction capFacing = (s == 0) ? dir.getOpposite() : dir;
        boolean dashOn = Math.floorMod(s, spec.dashPeriod()) < spec.dashOn();

        return switch (k) {
            case ASPHALT -> cap ? pal.capState(pal.capPlain(), capFacing) : pal.asphaltState(dir);
            case LINE_SOLID -> cap ? pal.capState(pal.capSolid(), capFacing) : pal.lineState(dir);
            case LINE_DASH -> cap ? pal.capState(pal.capDash(), capFacing)
                    : (dashOn ? pal.lineState(dir) : pal.asphaltState(dir));
            case SIDEWALK_L -> pal.sidewalkState(right);
            case SIDEWALK_R -> pal.sidewalkState(right.getOpposite());
            case GRASS -> pal.grassState();
            case PARK_EDGE, PARK_BAY -> cap ? pal.capState(pal.capPlain(), capFacing)
                    : parkingState(k, depth, leftSide, s, dir, right, spec, pal);
            default -> pal.asphaltState(dir);
        };
    }

    private static BlockState parkingState(Kind k, int depth, boolean leftSide, int s,
                                           Direction dir, Direction right, RoadSpec spec, RoadPalette pal) {
        int pitch = Math.max(2, spec.parkPitch());
        switch (spec.parkType()) {
            case PARALLEL:
                // сплошная линия вдоль, отделяющая парковку от полосы
                return k == Kind.PARK_EDGE ? pal.lineState(dir) : pal.asphaltState(dir);
            case PERPENDICULAR:
                // разделители мест поперёк дороги
                return (Math.floorMod(s, pitch) == 0) ? pal.lineState(right) : pal.asphaltState(dir);
            case ANGLED: {
                // диагональные линии: со сдвигом на 1 ряд на каждую колонку вглубь
                int shift = leftSide ? -depth : depth;
                boolean on = Math.floorMod(s - shift, pitch) == 0;
                Direction f = leftSide ? right : dir;      // зеркально для другой стороны
                return on ? pal.diagonalState(f) : pal.asphaltState(dir);
            }
            default:
                return pal.asphaltState(dir);
        }
    }

    private RoadPlan() {}
}
