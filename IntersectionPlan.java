package com.roadforge.road;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Перекрёсток: главная дорога вдоль X (ветки З/В) и поперечная вдоль Z (ветки С/Ю).
 * Любая комбинация веток: крест, Т-образный, поворот.
 * Центр - блок, на который кликнули (центральная колонка обеих дорог).
 */
public final class IntersectionPlan {
    public static final int CROSSWALK_DEPTH = 3;

    private static final class C {
        Kind kind;
        BlockState state;
        Direction face;
        boolean curb;

        C(Kind kind, BlockState state, Direction face) {
            this.kind = kind;
            this.state = state;
            this.face = face;
        }
    }

    private static final class Geo {
        final int cx, cy, cz;
        final int az0, az1, bx0, bx1;                 // проезжая часть A (по Z) и B (по X)
        final int azFull0, azFull1, bxFull0, bxFull1; // полный профиль с тротуарами
        final CrossSection csA, csB;
        final RoadSpec specA, specB;

        Geo(BlockPos center, RoadSettings st) {
            RoadSpec base = st.toSpec().withEndCaps(false);
            specA = base;
            specB = base.withLanes(RoadPreset.clampLanes(st.crossLanes));
            csA = CrossSection.build(specA);
            csB = CrossSection.build(specB);
            cx = center.getX();
            cy = center.getY();
            cz = center.getZ();
            int refA = csA.ref();
            int refB = csB.ref();
            azFull0 = cz - refA;
            azFull1 = cz + csA.width() - 1 - refA;
            bxFull0 = cx - refB;
            bxFull1 = cx + csB.width() - 1 - refB;
            az0 = cz + csA.roadFirst() - refA;
            az1 = cz + csA.roadLast() - refA;
            bx0 = cx + csB.roadFirst() - refB;
            bx1 = cx + csB.roadLast() - refB;
        }
    }

    public static List<Placement> generate(BlockPos center, RoadSettings st, int clearHeight) {
        if (BendPlan.arms(st) != null) {
            return BendPlan.generate(center, st, clearHeight);   // две соседние ветки = поворот
        }
        Geo g = new Geo(center, st);
        RoadPalette pal = st.toPalette();
        int len = st.armLength;
        boolean armN = st.armN, armE = st.armE, armS = st.armS, armW = st.armW;

        int axMin = armW ? g.bx0 - len : g.bx0;
        int axMax = armE ? g.bx1 + len : g.bx1;
        int bzMin = armN ? g.az0 - len : g.az0;
        int bzMax = armS ? g.az1 + len : g.az1;
        // тротуары закрывают сторону, где ветки нет
        int axSideMin = armW ? axMin : g.bxFull0;
        int axSideMax = armE ? axMax : g.bxFull1;
        int bzSideMin = armN ? bzMin : g.azFull0;
        int bzSideMax = armS ? bzMax : g.azFull1;

        int pad = Math.max(st.cornerRadius, 1) + 1;
        int x0 = Math.min(axSideMin, g.bxFull0) - pad;
        int x1 = Math.max(axSideMax, g.bxFull1) + pad;
        int z0 = Math.min(bzSideMin, g.azFull0) - pad;
        int z1 = Math.max(bzSideMax, g.azFull1) + pad;
        C[][] grid = new C[x1 - x0 + 1][z1 - z0 + 1];

        // ---------- 1. классификация клеток ----------
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                Kind ka = null;
                int ia = -1;
                boolean rwA = false;
                if (x >= axSideMin && x <= axSideMax && z >= g.azFull0 && z <= g.azFull1) {
                    ia = z - g.cz + g.csA.ref();
                    Kind k = g.csA.cols().get(ia);
                    boolean ext = x >= axMin && x <= axMax;
                    boolean road = ia >= g.csA.roadFirst() && ia <= g.csA.roadLast();
                    if (road) {
                        if (ext) {
                            ka = k;
                            rwA = true;
                        }
                    } else if (isSidewalk(k) || (ext && k == Kind.GRASS)) {
                        ka = k;
                    }
                }
                Kind kb = null;
                int ib = -1;
                boolean rwB = false;
                if (z >= bzSideMin && z <= bzSideMax && x >= g.bxFull0 && x <= g.bxFull1) {
                    ib = x - g.cx + g.csB.ref();
                    Kind k = g.csB.cols().get(ib);
                    boolean ext = z >= bzMin && z <= bzMax;
                    boolean road = ib >= g.csB.roadFirst() && ib <= g.csB.roadLast();
                    if (road) {
                        if (ext) {
                            kb = k;
                            rwB = true;
                        }
                    } else if (isSidewalk(k) || (ext && k == Kind.GRASS)) {
                        kb = k;
                    }
                }

                C cell = null;
                if (rwA && rwB) {
                    cell = new C(Kind.ASPHALT, pal.asphaltState(Direction.EAST), null);
                } else if (rwA) {
                    int s = x < g.bx0 ? g.bx0 - 1 - x : x - g.bx1 - 1;
                    Kind k = normalize(ka);
                    cell = new C(k, RoadPlan.surfaceState(k, g.csA.depths().get(ia), ia < g.csA.ref(), s, 100000,
                            Direction.EAST, Direction.SOUTH, g.specA, pal), null);
                } else if (rwB) {
                    int s = z < g.az0 ? g.az0 - 1 - z : z - g.az1 - 1;
                    Kind k = normalize(kb);
                    cell = new C(k, RoadPlan.surfaceState(k, g.csB.depths().get(ib), ib < g.csB.ref(), s, 100000,
                            Direction.NORTH, Direction.EAST, g.specB, pal), null);
                } else if (isSidewalk(ka) || isSidewalk(kb)) {
                    Direction face;
                    if (isSidewalk(ka)) {
                        face = z < g.az0 ? Direction.SOUTH : Direction.NORTH;
                    } else {
                        face = x < g.bx0 ? Direction.EAST : Direction.WEST;
                    }
                    cell = new C(Kind.SIDEWALK_L, null, face);
                } else if (ka == Kind.GRASS || kb == Kind.GRASS) {
                    cell = new C(Kind.GRASS, pal.grassState(), null);
                }
                grid[x - x0][z - z0] = cell;
            }
        }

        // ---------- 2. скругление углов (там, где сходятся две ветки) ----------
        int r = Math.min(st.cornerRadius, 8);
        if (r > 0) {
            BlockState plain = pal.asphaltState(Direction.EAST);
            if (armN && armW) fillet(grid, x0, z0, g.bx0, g.az0, -1, -1, r, plain);
            if (armN && armE) fillet(grid, x0, z0, g.bx1, g.az0, 1, -1, r, plain);
            if (armS && armW) fillet(grid, x0, z0, g.bx0, g.az1, -1, 1, r, plain);
            if (armS && armE) fillet(grid, x0, z0, g.bx1, g.az1, 1, 1, r, plain);
        }

        // ---------- 3. зебры и стоп-линии ----------
        if (st.crosswalks) {
            Direction[] ds = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
            boolean[] on = {armN, armE, armS, armW};
            for (int k = 0; k < 4; k++) {
                if (!on[k]) {
                    continue;
                }
                Direction d = ds[k];
                boolean alongX = d.getAxis() == Direction.Axis.X;
                int edge = alongX ? (d == Direction.EAST ? g.bx1 : g.bx0) : (d == Direction.SOUTH ? g.az1 : g.az0);
                int step = alongX ? d.getStepX() : d.getStepZ();
                int lat0 = alongX ? g.az0 : g.bx0;
                int lat1 = alongX ? g.az1 : g.bx1;
                double mid = (lat0 + lat1) / 2.0;
                Direction inc = d.getCounterClockWise();       // сторона въезжающих полос (правостороннее движение)
                int incStep = alongX ? inc.getStepZ() : inc.getStepX();

                for (int s = 0; s < CROSSWALK_DEPTH; s++) {
                    for (int lat = lat0; lat <= lat1; lat++) {
                        int px = alongX ? edge + step * (1 + s) : lat;
                        int pz = alongX ? lat : edge + step * (1 + s);
                        C c = get(grid, x0, z0, px, pz);
                        if (c != null && isRoadKind(c.kind)) {
                            c.kind = Kind.CROSSWALK;
                            c.state = pal.crosswalkState(d);
                        }
                    }
                }
                for (int lat = lat0; lat <= lat1; lat++) {
                    if ((lat - mid) * incStep <= 0) {
                        continue;
                    }
                    int px = alongX ? edge + step * (1 + CROSSWALK_DEPTH) : lat;
                    int pz = alongX ? lat : edge + step * (1 + CROSSWALK_DEPTH);
                    C c = get(grid, x0, z0, px, pz);
                    if (c != null && c.kind == Kind.ASPHALT) {
                        c.kind = Kind.LINE_SOLID;
                        c.state = pal.lineState(d.getClockWise());   // линия поперёк полос
                    }
                }
            }
        }

        // ---------- 4. направление бордюра: в сторону соседнего асфальта ----------
        Direction[] order = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                C c = grid[x - x0][z - z0];
                if (c == null || c.kind != Kind.SIDEWALK_L) {
                    continue;
                }
                Direction toRoad = null;
                boolean ramp = false;
                for (Direction d : order) {
                    C n = get(grid, x0, z0, x + d.getStepX(), z + d.getStepZ());
                    if (n == null) {
                        continue;
                    }
                    if (n.kind == Kind.CROSSWALK) {
                        ramp = true;                       // у зебры бордюр убираем (съезд)
                        if (toRoad == null) {
                            toRoad = d;
                        }
                    } else if (isRoadKind(n.kind) && toRoad == null) {
                        toRoad = d;
                    }
                }
                if (toRoad != null) {
                    c.face = toRoad;
                }
                c.curb = toRoad != null && !ramp;
            }
        }

        // ---------- 5. список блоков ----------
        List<Placement> out = new ArrayList<>();
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                if (grid[x - x0][z - z0] == null) {
                    continue;
                }
                for (int h = 1; h <= clearHeight; h++) {
                    out.add(new Placement(new BlockPos(x, g.cy + h, z), air));
                }
            }
        }
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                C c = grid[x - x0][z - z0];
                if (c == null) {
                    continue;
                }
                BlockState bs = c.state;
                if (c.kind == Kind.SIDEWALK_L) {
                    bs = c.curb ? pal.curbState(c.face) : pal.sidewalkState(c.face);
                }
                out.add(new Placement(new BlockPos(x, g.cy, z), bs));
            }
        }

        // ---------- 6. фонари вдоль веток ----------
        RoadSpec sp = g.specA;
        if (sp.lampSpacing() > 0 && sp.sidewalkWidth() > 0) {
            int step = sp.lampSpacing();
            int half = step / 2;
            for (int s = 1; s < len - 1; s++) {
                boolean aHalf = s % step == half;
                boolean aZero = s % step == 0;
                if (armE) {
                    lamp(out, grid, x0, z0, g.bx1 + 1 + s, g.azFull0, g.cy, aHalf, Direction.SOUTH, pal);
                    lamp(out, grid, x0, z0, g.bx1 + 1 + s, g.azFull1, g.cy, aZero, Direction.NORTH, pal);
                }
                if (armW) {
                    lamp(out, grid, x0, z0, g.bx0 - 1 - s, g.azFull0, g.cy, aHalf, Direction.SOUTH, pal);
                    lamp(out, grid, x0, z0, g.bx0 - 1 - s, g.azFull1, g.cy, aZero, Direction.NORTH, pal);
                }
                if (armN) {
                    lamp(out, grid, x0, z0, g.bxFull0, g.az0 - 1 - s, g.cy, aHalf, Direction.EAST, pal);
                    lamp(out, grid, x0, z0, g.bxFull1, g.az0 - 1 - s, g.cy, aZero, Direction.WEST, pal);
                }
                if (armS) {
                    lamp(out, grid, x0, z0, g.bxFull0, g.az1 + 1 + s, g.cy, aHalf, Direction.EAST, pal);
                    lamp(out, grid, x0, z0, g.bxFull1, g.az1 + 1 + s, g.cy, aZero, Direction.WEST, pal);
                }
            }
        }
        return out;
    }

    /** Координаты блоков, с которых можно продолжать дорогу за концом каждой ветки. */
    public static String armEnds(BlockPos center, RoadSettings st) {
        if (BendPlan.arms(st) != null) {
            return BendPlan.armEnds(center, st);
        }
        Geo g = new Geo(center, st);
        int len = st.armLength;
        StringBuilder sb = new StringBuilder();
        if (st.armN) sb.append("С: ").append(g.cx).append(' ').append(g.cy).append(' ').append(g.az0 - len - 1).append("  ");
        if (st.armE) sb.append("В: ").append(g.bx1 + len + 1).append(' ').append(g.cy).append(' ').append(g.cz).append("  ");
        if (st.armS) sb.append("Ю: ").append(g.cx).append(' ').append(g.cy).append(' ').append(g.az1 + len + 1).append("  ");
        if (st.armW) sb.append("З: ").append(g.bx0 - len - 1).append(' ').append(g.cy).append(' ').append(g.cz).append("  ");
        return sb.toString().trim();
    }

    // ---------- helpers ----------

    private static void lamp(List<Placement> out, C[][] grid, int x0, int z0, int x, int z, int cy,
                             boolean place, Direction face, RoadPalette pal) {
        if (!place) {
            return;
        }
        C c = get(grid, x0, z0, x, z);
        if (c != null && c.kind == Kind.SIDEWALK_L) {
            out.add(new Placement(new BlockPos(x, cy + 1, z), pal.lampState(face)));
        }
    }

    private static void fillet(C[][] grid, int x0, int z0, int ex, int ez, int sx, int sz, int r, BlockState plain) {
        for (int dx = 1; dx <= r; dx++) {
            for (int dz = 1; dz <= r; dz++) {
                double u = dx - 0.5;
                double v = dz - 0.5;
                double rr = r - 0.5;
                if ((u - r) * (u - r) + (v - r) * (v - r) > rr * rr) {
                    put(grid, x0, z0, ex + sx * dx, ez + sz * dz, new C(Kind.ASPHALT, plain, null));
                }
            }
        }
    }

    private static C get(C[][] g, int x0, int z0, int x, int z) {
        int i = x - x0;
        int j = z - z0;
        if (i < 0 || j < 0 || i >= g.length || j >= g[0].length) {
            return null;
        }
        return g[i][j];
    }

    private static void put(C[][] g, int x0, int z0, int x, int z, C c) {
        int i = x - x0;
        int j = z - z0;
        if (i < 0 || j < 0 || i >= g.length || j >= g[0].length) {
            return;
        }
        g[i][j] = c;
    }

    private static boolean isSidewalk(Kind k) {
        return k == Kind.SIDEWALK_L || k == Kind.SIDEWALK_R;
    }

    private static boolean isRoadKind(Kind k) {
        return k == Kind.ASPHALT || k == Kind.LINE_SOLID || k == Kind.LINE_DASH || k == Kind.CROSSWALK;
    }

    /** В перекрёстках парковка рисуется как обычный асфальт. */
    private static Kind normalize(Kind k) {
        return (k == Kind.PARK_EDGE || k == Kind.PARK_BAY) ? Kind.ASPHALT : k;
    }

    private IntersectionPlan() {}
}
