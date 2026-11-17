package com.roadforge.road;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/** Превращает параметры дороги в список блоков (прямой участок вдоль оси). */
public final class RoadPlan {
    /** Глубина (вдоль дороги) пешеходного перехода. */
    public static final int CROSSWALK_DEPTH = 3;

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

        // 2. клетки поверхности
        Surfaces.Cell[][] g = new Surfaces.Cell[length][w];
        for (int s = 0; s < length; s++) {
            for (int i = 0; i < w; i++) {
                g[s][i] = cell(cs, i, s, length, dir, right, spec, pal);
            }
        }
        addCrosswalks(g, cs, length, dir, right, spec, pal);
        Surfaces.resolve(g, dir, right);

        // 3. поверхность
        for (int s = 0; s < length; s++) {
            for (int i = 0; i < w; i++) {
                out.add(new Placement(at(start, dir, right, s, i - cs.ref()), Surfaces.finish(g[s][i], pal)));
            }
        }

        // 3b. деревья по центру каждой полосы газона (разделитель, обочина)
        if (spec.treeSpacing() > 0) {
            int sp = spec.treeSpacing();
            for (int i = 0; i < w; ) {
                if (cs.cols().get(i) != Kind.GRASS) {
                    i++;
                    continue;
                }
                int j = i;
                while (j + 1 < w && cs.cols().get(j + 1) == Kind.GRASS) {
                    j++;
                }
                int mid = (i + j) / 2;
                for (int s = 2; s < length - 2; s++) {
                    if (s % sp == sp / 2 && g[s][mid].kind == Kind.GRASS) {
                        Trees.plant(out, at(start, dir, right, s, mid - cs.ref()));
                    }
                }
                i = j + 1;
            }
        }

        // 4. фонари (на крайних колонках тротуара, в шахматном порядке)
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

    private static boolean isParking(Kind k) {
        return k == Kind.PARK_EDGE || k == Kind.PARK_BAY;
    }

    /** Колонка парковки у торца превращается в тротуар по диагонали (въезд «косым срезом»). */
    private static boolean parkTapered(int depth, int s, int length) {
        int off = 1 + depth / 2;          // короткий срез: 2 колонки вглубь на каждые 2 блока длины
        return s < off || s > length - 1 - off;
    }

    private static Surfaces.Cell cell(CrossSection cs, int i, int s, int length, Direction dir, Direction right,
                                      RoadSpec spec, RoadPalette pal) {
        Kind k = cs.cols().get(i);
        int depth = cs.depths().get(i);
        boolean leftSide = i < cs.ref();
        Direction towardRoad = leftSide ? right : right.getOpposite();
        if (k == Kind.SIDEWALK_L || k == Kind.SIDEWALK_R) {
            return Surfaces.Cell.paving(k == Kind.SIDEWALK_L ? right : right.getOpposite());
        }
        if (isParking(k) && parkTapered(depth, s, length)) {
            return Surfaces.Cell.paving(towardRoad);
        }
        return Surfaces.Cell.road(k, surfaceState(k, depth, leftSide, s, length, dir, right, spec, pal));
    }

    /** Пешеходные переходы посреди дороги: зебра, стоп-линии, пониженный бордюр, «островок» на разделителе. */
    private static void addCrosswalks(Surfaces.Cell[][] g, CrossSection cs, int length, Direction dir,
                                      Direction right, RoadSpec spec, RoadPalette pal) {
        int every = spec.crossEvery();
        if (every <= 0) {
            return;
        }
        for (int c0 = every / 2; c0 + CROSSWALK_DEPTH + 1 < length - 1; c0 += every) {
            if (c0 < 2) {
                continue;
            }
            for (int s = c0; s < c0 + CROSSWALK_DEPTH; s++) {
                for (int i = cs.roadFirst(); i <= cs.roadLast(); i++) {
                    Kind k = g[s][i].kind;
                    if (Surfaces.isRoad(k)) {
                        g[s][i] = Surfaces.Cell.road(Kind.CROSSWALK, pal.crosswalkState(dir));
                    } else if (k == Kind.GRASS) {
                        g[s][i] = Surfaces.Cell.paving(i < cs.ref() ? right : right.getOpposite());
                    }
                }
            }
            // стоп-линии: на своей стороне перед зеброй
            for (int i = cs.ref() + 1; i <= cs.roadLast(); i++) {
                stopLine(g, c0 - 1, i, right, pal);
            }
            for (int i = cs.roadFirst(); i < cs.ref(); i++) {
                stopLine(g, c0 + CROSSWALK_DEPTH, i, right, pal);
            }
        }
    }

    private static void stopLine(Surfaces.Cell[][] g, int s, int i, Direction right, RoadPalette pal) {
        if (s < 0 || s >= g.length || g[s][i].kind != Kind.ASPHALT) {
            return;
        }
        g[s][i] = Surfaces.Cell.road(Kind.LINE_SOLID, pal.lineState(right));
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
                    : parkingState(k, depth, leftSide, s, length, dir, right, spec, pal);
            default -> pal.asphaltState(dir);
        };
    }

    /**
     * Разметка парковки. Места считаются от начала полной глубины (после косого среза у торца),
     * последняя линия замыкает последнее целое место.
     */
    private static BlockState parkingState(Kind k, int depth, boolean leftSide, int s, int length,
                                           Direction dir, Direction right, RoadSpec spec, RoadPalette pal) {
        int pd = Math.max(1, spec.parkDepth());
        int pitch = Math.max(2, spec.parkPitch());
        int first = 1 + (pd - 1) / 2;
        int last = length - 1 - first;
        int stalls = Math.max(0, last - first) / pitch;
        int sp = s - first;
        boolean boundary = sp >= 0 && sp <= stalls * pitch && sp % pitch == 0;
        switch (spec.parkType()) {
            case PARALLEL:
                // сплошная линия вдоль, отделяющая парковку от полосы, и короткие поперечные границы мест
                if (k == Kind.PARK_EDGE) {
                    return pal.lineState(dir);
                }
                return boundary ? pal.lineState(right) : pal.asphaltState(dir);
            case PERPENDICULAR:
                return boundary ? pal.lineState(right) : pal.asphaltState(dir);
            case ANGLED: {
                // диагональные линии: со сдвигом на 1 ряд на каждую колонку вглубь
                int shift = leftSide ? -depth : depth;
                boolean on = Math.floorMod(sp - shift, pitch) == 0;
                Direction f = right;                       // обе стороны наклонены одинаково (как односторонняя парковка)
                return on ? pal.diagonalState(f) : pal.asphaltState(dir);
            }
            default:
                return pal.asphaltState(dir);
        }
    }

    private RoadPlan() {}
}
