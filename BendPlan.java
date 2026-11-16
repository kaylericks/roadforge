package com.roadforge.road;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Поворот дороги на 90°: две соседние ветки (например С + В). Дуга постоянной ширины:
 * внутренний бордюр, осевая и внешний бордюр идут по концентрическим дугам, разметка
 * переходит с прямых участков на дугу без разрывов. Ширина - по главной дороге (вкладка «Дорога»).
 */
public final class BendPlan {

    /** Две перпендикулярные ветки или null, если веток не две или они противоположные. */
    public static Direction[] arms(RoadSettings st) {
        Direction[] order = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
        boolean[] on = {st.armN, st.armE, st.armS, st.armW};
        List<Direction> act = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            if (on[i]) {
                act.add(order[i]);
            }
        }
        if (act.size() != 2 || act.get(0).getAxis() == act.get(1).getAxis()) {
            return null;
        }
        return new Direction[]{act.get(0), act.get(1)};
    }

    private static final class Geo {
        final RoadSpec spec;
        final CrossSection cs;
        final int w;
        final int ref;
        final int rho;       // радиус осевой линии
        final int len;

        Geo(RoadSettings st) {
            spec = st.toSpec().withEndCaps(false);
            cs = CrossSection.build(spec);
            w = cs.width();
            ref = cs.ref();
            int dIn = cs.roadLast() - ref;           // от оси до внутреннего края проезжей части
            int dInTotal = w - 1 - ref;               // от оси до внутреннего края тротуара
            int r = Math.max(2, st.cornerRadius);     // радиус внутреннего бордюра проезжей части
            rho = Math.max(r + dIn + 1, dInTotal + 2);
            len = st.armLength;
        }
    }

    private static Direction axisDir(boolean alongX) {
        return alongX ? Direction.EAST : Direction.NORTH;
    }

    /** Радиус осевой линии дуги: минимальная длина каждого прямого участка до угла. */
    public static int radius(RoadSettings st) {
        return new Geo(st).rho;
    }

    public static List<Placement> generate(BlockPos center, RoadSettings st, int clearHeight) {
        Direction[] arms = arms(st);
        if (arms == null) {
            throw new IllegalArgumentException("not a bend");
        }
        Geo g = new Geo(st);
        int end = g.rho + g.len;
        return generate(center, arms[0], arms[1], end, end, st.crosswalks, st, clearHeight);
    }

    /**
     * Поворот с произвольной длиной участков.
     * center - угол пересечения осей; d1, d2 - направления от угла к концам участков;
     * end1, end2 - расстояние от угла до конца участка (включительно, не меньше радиуса дуги).
     */
    public static List<Placement> generate(BlockPos center, Direction d1, Direction d2, int end1, int end2,
                                           boolean zebras, RoadSettings st, int clearHeight) {
        Geo g = new Geo(st);
        RoadPalette pal = st.toPalette();
        int cx = center.getX();
        int cy = center.getY();
        int cz = center.getZ();
        int rho = g.rho;
        int ox = cx + rho * (d1.getStepX() + d2.getStepX());   // центр дуги
        int oz = cz + rho * (d1.getStepZ() + d2.getStepZ());
        int arcLen = (int) Math.round(rho * Math.PI / 2);

        // границы сетки
        int reach = Math.max(end1, end2) + 2;
        int lat = Math.max(g.ref, g.w - 1 - g.ref) + 2;
        int x0 = Integer.MAX_VALUE;
        int x1 = Integer.MIN_VALUE;
        int z0 = Integer.MAX_VALUE;
        int z1 = Integer.MIN_VALUE;
        int[] ab = {-lat, reach};
        for (int a : ab) {
            for (int b : ab) {
                int x = cx + a * d1.getStepX() + b * d2.getStepX();
                int z = cz + a * d1.getStepZ() + b * d2.getStepZ();
                x0 = Math.min(x0, x);
                x1 = Math.max(x1, x);
                z0 = Math.min(z0, z);
                z1 = Math.max(z1, z);
            }
        }

        // направление «входящих» полос (правостороннее движение)
        Direction inc1 = d1.getCounterClockWise();
        int inc1Beta = inc1.getStepX() * d2.getStepX() + inc1.getStepZ() * d2.getStepZ();
        Direction inc2 = d2.getCounterClockWise();
        int inc2Alpha = inc2.getStepX() * d1.getStepX() + inc2.getStepZ() * d1.getStepZ();

        Surfaces.Cell[][] grid = new Surfaces.Cell[x1 - x0 + 1][z1 - z0 + 1];
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                int px = x - cx;
                int pz = z - cz;
                int alpha = px * d1.getStepX() + pz * d1.getStepZ();
                int beta = px * d2.getStepX() + pz * d2.getStepZ();

                int idx;
                int sPath;
                Direction line;
                Direction toRoadOuter;   // куда смотрит проезжая часть для клетки «снаружи» от оси
                int zebra = 0;           // 1 - зебра, 2 - стоп-линия
                if (alpha >= rho) {
                    if (alpha > end1) {
                        continue;
                    }
                    idx = g.ref + beta;
                    sPath = -(alpha - rho);
                    line = axisDir(d1.getAxis() == Direction.Axis.X);
                    toRoadOuter = d2;
                    int along = alpha - rho;
                    if (zebras && along >= 2 && along < 2 + RoadPlan.CROSSWALK_DEPTH) {
                        zebra = 1;
                    } else if (zebras && along == 1 && beta * inc1Beta > 0) {
                        zebra = 2;
                    }
                } else if (beta >= rho) {
                    if (beta > end2) {
                        continue;
                    }
                    idx = g.ref + alpha;
                    sPath = arcLen + (beta - rho);
                    line = axisDir(d2.getAxis() == Direction.Axis.X);
                    toRoadOuter = d1;
                    int along = beta - rho;
                    if (zebras && along >= 2 && along < 2 + RoadPlan.CROSSWALK_DEPTH) {
                        zebra = 1;
                    } else if (zebras && along == 1 && alpha * inc2Alpha > 0) {
                        zebra = 2;
                    }
                } else {
                    int dx = x - ox;
                    int dz = z - oz;
                    double u = Math.hypot(dx, dz);
                    idx = g.ref + (int) Math.round(rho - u);
                    double phi = Math.atan2(-(alpha - rho), -(beta - rho));
                    sPath = (int) Math.floor(rho * phi);
                    boolean alongX = Math.abs(dz) >= Math.abs(dx);
                    line = axisDir(alongX);
                    // к дуге (центру): по доминирующей оси
                    toRoadOuter = Math.abs(dx) >= Math.abs(dz)
                            ? (dx > 0 ? Direction.WEST : Direction.EAST)
                            : (dz > 0 ? Direction.NORTH : Direction.SOUTH);
                }
                if (idx < 0 || idx >= g.w) {
                    continue;
                }
                // idx < ref - снаружи от оси: проезжая часть в сторону toRoadOuter; idx > ref - в противоположную
                Direction toRoad = idx <= g.ref ? toRoadOuter : toRoadOuter.getOpposite();
                if (alpha >= rho || (beta >= rho)) {
                    // на прямых участках toRoadOuter - это ось поперёк дороги, направленная к +idx
                    toRoad = idx <= g.ref ? toRoadOuter : toRoadOuter.getOpposite();
                }
                Kind k = g.cs.cols().get(idx);
                Surfaces.Cell c;
                if (k == Kind.SIDEWALK_L || k == Kind.SIDEWALK_R) {
                    c = Surfaces.Cell.paving(toRoad);
                } else {
                    if (k == Kind.PARK_EDGE || k == Kind.PARK_BAY) {
                        k = Kind.ASPHALT;
                    }
                    if (zebra == 1 && Surfaces.isRoad(k)) {
                        c = Surfaces.Cell.road(Kind.CROSSWALK, pal.crosswalkState(alpha >= rho ? d1 : d2));
                    } else if (zebra == 2 && k == Kind.ASPHALT) {
                        Direction perp = (alpha >= rho ? d1 : d2).getClockWise();
                        c = Surfaces.Cell.road(Kind.LINE_SOLID, pal.lineState(perp));
                    } else {
                        c = Surfaces.Cell.road(k, RoadPlan.surfaceState(k, 0, false, sPath, 100000,
                                line, line, g.spec, pal));
                    }
                }
                c.tag = sPath;
                c.lat = idx;
                grid[x - x0][z - z0] = c;
            }
        }
        Surfaces.resolve(grid, Direction.EAST, Direction.SOUTH);

        List<Placement> out = new ArrayList<>();
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                if (grid[x - x0][z - z0] == null) {
                    continue;
                }
                for (int h = 1; h <= clearHeight; h++) {
                    out.add(new Placement(new BlockPos(x, cy + h, z), air));
                }
            }
        }
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                Surfaces.Cell c = grid[x - x0][z - z0];
                if (c != null) {
                    out.add(new Placement(new BlockPos(x, cy, z), Surfaces.finish(c, pal)));
                }
            }
        }

        // фонари по краям профиля, шаг считается вдоль пути (прямая - дуга - прямая)
        if (g.spec.lampSpacing() > 0 && g.cs.lampLeft() >= 0) {
            int sp = g.spec.lampSpacing();
            int half = sp / 2;
            Set<Long> used = new HashSet<>();
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    Surfaces.Cell c = grid[x - x0][z - z0];
                    if (c == null || !c.paving || c.curb) {
                        continue;
                    }
                    boolean left = c.lat == g.cs.lampLeft() && Math.floorMod(c.tag, sp) == half;
                    boolean rightLamp = c.lat == g.cs.lampRight() && Math.floorMod(c.tag, sp) == 0;
                    if (!left && !rightLamp) {
                        continue;
                    }
                    long key = ((long) c.lat << 32) ^ (c.tag & 0xFFFFFFFFL);
                    if (!used.add(key)) {
                        continue;
                    }
                    out.add(new Placement(new BlockPos(x, cy + 1, z), pal.lampState(c.face)));
                }
            }
        }
        return out;
    }

    /** Блоки, с которых можно продолжать дорогу за концами веток. */
    public static String armEnds(BlockPos center, RoadSettings st) {
        Direction[] arms = arms(st);
        if (arms == null) {
            return "";
        }
        Geo g = new Geo(st);
        StringBuilder sb = new StringBuilder();
        for (Direction d : arms) {
            int dist = g.rho + g.len + 1;
            sb.append(letter(d)).append(": ")
                    .append(center.getX() + d.getStepX() * dist).append(' ')
                    .append(center.getY()).append(' ')
                    .append(center.getZ() + d.getStepZ() * dist).append("  ");
        }
        return sb.toString().trim();
    }

    private static String letter(Direction d) {
        return switch (d) {
            case NORTH -> "С";
            case EAST -> "В";
            case SOUTH -> "Ю";
            default -> "З";
        };
    }

    private BendPlan() {}
}
