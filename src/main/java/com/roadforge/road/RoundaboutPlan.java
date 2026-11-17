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
 * Круговое движение: остров с бордюром и газоном, кольцо в 1-2 полосы (пунктир между полосами),
 * до четырёх веток. Ветки идут по профилям вкладки «Дорога» (С/Ю - «поперечная»), у каждой на входе
 * стоп-линия «уступи дорогу» и зебра. Вокруг кольца - площадка тротуара с бордюром и фонарями.
 */
public final class RoundaboutPlan {

    private static final class Geo {
        final RoadSpec specA;
        final RoadSpec specB;
        final CrossSection csA;
        final CrossSection csB;
        final int rIn;      // радиус острова
        final int rOut;     // внешний радиус кольца
        final int rPlaza;   // внешний радиус площадки тротуара
        final int lane;     // ширина одной полосы кольца
        final int ringLanes;
        final int len;

        Geo(RoadSettings st) {
            RoadSpec base = st.toSpec().withEndCaps(false);
            specA = base;
            specB = base.withLanes(RoadPreset.clampLanes(st.crossLanes));
            csA = CrossSection.build(specA);
            csB = CrossSection.build(specB);
            lane = base.laneWidth();
            ringLanes = Math.max(1, Math.min(2, st.ringLanes));
            rIn = Math.max(3, st.islandRadius);
            int ringWidth = ringLanes * lane + (ringLanes - 1);
            rOut = rIn + ringWidth;
            rPlaza = rOut + Math.max(2, st.sidewalkWidth);
            len = st.armLength;
        }
    }

    private static Kind normalize(Kind k) {
        return (k == Kind.PARK_EDGE || k == Kind.PARK_BAY) ? Kind.ASPHALT : k;
    }

    private static boolean armOn(RoadSettings st, Direction d) {
        return switch (d) {
            case NORTH -> st.armN;
            case EAST -> st.armE;
            case SOUTH -> st.armS;
            default -> st.armW;
        };
    }

    /** Ближайшее кардинальное направление от клетки к центру (dx, dz - смещение клетки от центра). */
    private static Direction towardCenter(int dx, int dz) {
        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx > 0 ? Direction.WEST : Direction.EAST;
        }
        return dz > 0 ? Direction.NORTH : Direction.SOUTH;
    }

    public static List<Placement> generate(BlockPos center, RoadSettings st, int clearHeight) {
        Geo g = new Geo(st);
        RoadPalette pal = st.toPalette();
        int cx = center.getX();
        int cy = center.getY();
        int cz = center.getZ();
        int box = Math.max(g.rPlaza, g.rOut + g.len) + 1;
        int size = 2 * box + 1;
        Surfaces.Cell[][] grid = new Surfaces.Cell[size][size];

        for (int gx = 0; gx < size; gx++) {
            for (int gz = 0; gz < size; gz++) {
                int dx = gx - box;
                int dz = gz - box;
                double d = Math.hypot(dx, dz);
                Surfaces.Cell c = null;

                if (d <= g.rIn) {
                    c = Surfaces.Cell.paving(towardCenter(dx, dz).getOpposite());
                    c.island = true;
                    c.forceCurb = d > g.rIn - 1;      // внешний ряд острова - бордюр, внутри газон
                } else if (d <= g.rOut) {
                    c = ringCell(g, st, pal, dx, dz, d);
                } else {
                    c = armCell(g, st, pal, dx, dz);
                    if (c == null && d <= g.rPlaza) {
                        c = Surfaces.Cell.paving(towardCenter(dx, dz));
                        c.lat = -2;    // метка площадки (для фонарей)
                        c.tag = (int) Math.floor((Math.atan2(dz, dx) + Math.PI) * g.rPlaza);
                    }
                }
                grid[gx][gz] = c;
            }
        }
        Surfaces.resolve(grid, Direction.EAST, Direction.SOUTH);

        List<Placement> out = new ArrayList<>();
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int gx = 0; gx < size; gx++) {
            for (int gz = 0; gz < size; gz++) {
                if (grid[gx][gz] == null) {
                    continue;
                }
                for (int h = 1; h <= clearHeight; h++) {
                    out.add(new Placement(new BlockPos(cx + gx - box, cy + h, cz + gz - box), air));
                }
            }
        }
        for (int gx = 0; gx < size; gx++) {
            for (int gz = 0; gz < size; gz++) {
                Surfaces.Cell c = grid[gx][gz];
                if (c != null) {
                    out.add(new Placement(new BlockPos(cx + gx - box, cy, cz + gz - box), Surfaces.finish(c, pal)));
                }
            }
        }

        if (st.treeSpacing > 0) {
            Trees.plant(out, new BlockPos(cx, cy, cz));
        }

        // фонари: по кругу площадки и вдоль веток
        if (st.lampSpacing > 0) {
            int sp = st.lampSpacing;
            int half = sp / 2;
            Set<Long> used = new HashSet<>();
            for (int gx = 0; gx < size; gx++) {
                for (int gz = 0; gz < size; gz++) {
                    Surfaces.Cell c = grid[gx][gz];
                    if (c == null || !c.paving || c.curb || c.island) {
                        continue;
                    }
                    int dx = gx - box;
                    int dz = gz - box;
                    boolean lamp = false;
                    long key = 0;
                    if (c.lat == -2) {
                        double d = Math.hypot(dx, dz);
                        if (d > g.rPlaza - 1 && Math.floorMod(c.tag, sp) == 0) {
                            lamp = true;
                            key = (1L << 40) ^ c.tag;
                        }
                    } else if (c.lamp != 0) {
                        int m = Math.floorMod(c.tag, sp);
                        if ((c.lamp == 1 && m == half) || (c.lamp == 2 && m == 0)) {
                            lamp = true;
                            key = armKey(dx, dz) ^ ((long) c.lamp << 50) ^ c.tag;
                        }
                    }
                    if (lamp && used.add(key)) {
                        out.add(new Placement(new BlockPos(cx + dx, cy + 1, cz + dz), pal.lampState(c.face)));
                    }
                }
            }
        }
        return out;
    }

    /** Номер ветки по доминирующей оси (чтобы шаг фонарей считался отдельно на каждой ветке). */
    private static long armKey(int dx, int dz) {
        int arm = Math.abs(dx) >= Math.abs(dz) ? (dx > 0 ? 1 : 2) : (dz > 0 ? 3 : 4);
        return (long) arm << 60;
    }

    private static Surfaces.Cell ringCell(Geo g, RoadSettings st, RoadPalette pal, int dx, int dz, double d) {
        int band = (int) Math.ceil(d - g.rIn) - 1;
        int ringWidth = g.rOut - g.rIn;
        band = Math.max(0, Math.min(ringWidth - 1, band));
        if (st.markings && g.ringLanes == 2 && band == g.lane) {
            double phi = Math.atan2(dz, dx) + Math.PI;
            int s = (int) Math.floor(phi * (g.rIn + g.lane + 0.5));
            boolean on = Math.floorMod(s, g.specA.dashPeriod()) < g.specA.dashOn();
            if (on) {
                Direction f = Math.abs(dz) >= Math.abs(dx) ? Direction.EAST : Direction.NORTH;
                return Surfaces.Cell.road(Kind.LINE_DASH, pal.lineState(f));
            }
        }
        return Surfaces.Cell.road(Kind.ASPHALT, pal.asphaltState(Direction.EAST));
    }

    /** Клетка ветки (проезжая часть, зебра, стоп-линия, тротуар) или null. */
    private static Surfaces.Cell armCell(Geo g, RoadSettings st, RoadPalette pal, int dx, int dz) {
        boolean alongX = Math.abs(dx) >= Math.abs(dz);
        Direction out;     // направление ветки наружу
        int along;
        CrossSection cs;
        RoadSpec spec;
        int lateral;       // смещение поперёк ветки (в сторону +Z для веток З/В, +X для С/Ю)
        Direction latPlus; // мировое направление «+lateral»
        Direction lineDir;
        if (alongX) {
            out = dx > 0 ? Direction.EAST : Direction.WEST;
            along = Math.abs(dx);
            cs = g.csA;
            spec = g.specA;
            lateral = dz;
            latPlus = Direction.SOUTH;
            lineDir = Direction.EAST;
        } else {
            out = dz > 0 ? Direction.SOUTH : Direction.NORTH;
            along = Math.abs(dz);
            cs = g.csB;
            spec = g.specB;
            lateral = dx;
            latPlus = Direction.EAST;
            lineDir = Direction.NORTH;
        }
        if (!armOn(st, out) || along > g.rOut + g.len) {
            return null;
        }
        int idx = lateral + cs.ref();
        if (idx < 0 || idx >= cs.width()) {
            return null;
        }
        Kind k = cs.cols().get(idx);
        Surfaces.Cell c;
        if (k == Kind.SIDEWALK_L || k == Kind.SIDEWALK_R) {
            c = Surfaces.Cell.paving(idx < cs.ref() ? latPlus : latPlus.getOpposite());
        } else if (idx < cs.roadFirst() || idx > cs.roadLast()) {
            c = Surfaces.Cell.road(Kind.GRASS, pal.grassState());       // газон-обочина
        } else {
            k = normalize(k);
            int zebraStart = g.rOut + 3;
            boolean room = g.len >= 6;
            // разделительный островок на входе (по оси, поверх зебры): убежище для пешеходов
            boolean noMedian = !hasMedian(cs);
            int half = st.doubleCenter && st.markings ? 1 : 0;
            if (noMedian && room && st.crosswalks && Math.abs(idx - cs.ref()) <= half
                    && along >= g.rOut + 1 && along < zebraStart + RoadPlan.CROSSWALK_DEPTH + 1) {
                c = Surfaces.Cell.paving(latPlus);
                c.tag = along;
                c.lat = idx;
                return c;
            }
            Direction inc = out.getCounterClockWise();
            int incStep = alongX ? inc.getStepZ() : inc.getStepX();
            if (st.crosswalks && room && Surfaces.isRoad(k) && along >= zebraStart
                    && along < zebraStart + RoadPlan.CROSSWALK_DEPTH) {
                c = Surfaces.Cell.road(Kind.CROSSWALK, pal.crosswalkState(out));
            } else if (st.crosswalks && room && k == Kind.ASPHALT && along == g.rOut + 2
                    && (idx - cs.ref()) * incStep > 0 && Math.floorMod(idx, 2) == 0) {
                // «уступи дорогу»: пунктир поперёк входящей полосы
                c = Surfaces.Cell.road(Kind.LINE_DASH, pal.lineState(out.getClockWise()));
            } else {
                c = Surfaces.Cell.road(k, RoadPlan.surfaceState(k, cs.depths().get(idx), idx < cs.ref(),
                        along - g.rOut, 100000, lineDir, latPlus, spec, pal));
            }
        }
        c.tag = along;
        c.lat = idx;
        if (c.paving && spec.lampSpacing() > 0 && cs.lampLeft() >= 0 && along > g.rPlaza) {
            if (idx == cs.lampLeft()) {
                c.lamp = 1;
            } else if (idx == cs.lampRight()) {
                c.lamp = 2;
            }
        }
        return c;
    }

    private static boolean hasMedian(CrossSection cs) {
        for (int i = cs.roadFirst(); i <= cs.roadLast(); i++) {
            if (cs.cols().get(i) == Kind.GRASS) {
                return true;
            }
        }
        return false;
    }

    /** Блоки, с которых можно продолжать дорогу за концами веток. */
    public static String armEnds(BlockPos center, RoadSettings st) {
        Geo g = new Geo(st);
        StringBuilder sb = new StringBuilder();
        Direction[] order = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
        String[] names = {"С", "В", "Ю", "З"};
        int dist = g.rOut + g.len + 1;
        for (int i = 0; i < 4; i++) {
            if (armOn(st, order[i])) {
                sb.append(names[i]).append(": ")
                        .append(center.getX() + order[i].getStepX() * dist).append(' ')
                        .append(center.getY()).append(' ')
                        .append(center.getZ() + order[i].getStepZ() * dist).append("  ");
            }
        }
        return sb.toString().trim();
    }

    /** Размер сетки для оценки числа блоков. */
    public static int footprint(RoadSettings st) {
        Geo g = new Geo(st);
        int size = 2 * (Math.max(g.rPlaza, g.rOut + g.len) + 1) + 1;
        return size * size;
    }

    private RoundaboutPlan() {}
}
