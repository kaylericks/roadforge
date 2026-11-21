package com.roadforge.road;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Реки (по точкам, как «Путь») и озёра (по контуру). Вода лежит в выемке: дно из песка, вода на один слой ниже берега.
 * По берегам тротуар с бордюром; бордюр и диагональные блоки подбираются так же, как у дорог.
 */
public final class WaterPlan {
    private static final BlockState WATER = Blocks.WATER.defaultBlockState();
    private static final BlockState SAND = Blocks.SAND.defaultBlockState();
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    private static final class Cand {
        double dist = Double.MAX_VALUE;
        int lateral;
        double s;
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    private static int bank(RoadSettings st) {
        return Math.max(1, st.sidewalkWidth);
    }

    /** Радиус скругления оси реки: берег не должен складываться внутри изгиба. */
    public static double axisRadius(RoadSettings st) {
        return st.riverWidth / 2.0 + bank(st) + 2;
    }

    // ------------------------------------------------------------------ река

    public static List<Placement> river(List<BlockPos> pts, RoadSettings st, int clearHeight) {
        List<Placement> out = new ArrayList<>();
        PathPlan.Centerline line = PathPlan.centerline(pts, axisRadius(st));
        if (line.samples.size() < 2) {
            return out;
        }
        RoadPalette pal = st.toPalette();
        int y = pts.get(0).getY();
        int hw = st.riverWidth / 2;
        int bank = bank(st);
        int reach = hw + bank + 2;

        Map<Long, Cand> best = new HashMap<>();
        for (PathPlan.Sample sm : line.samples) {
            double rx = -sm.tz;
            double rz = sm.tx;
            int cx0 = (int) Math.floor(sm.x);
            int cz0 = (int) Math.floor(sm.z);
            for (int x = cx0 - reach; x <= cx0 + reach; x++) {
                for (int z = cz0 - reach; z <= cz0 + reach; z++) {
                    double vx = x + 0.5 - sm.x;
                    double vz = z + 0.5 - sm.z;
                    double dist = vx * vx + vz * vz;
                    int lat = (int) Math.round(vx * rx + vz * rz);
                    if (Math.abs(lat) > hw + bank) {
                        continue;
                    }
                    Cand c = best.computeIfAbsent(key(x, z), k -> new Cand());
                    if (dist < c.dist) {
                        c.dist = dist;
                        c.lateral = lat;
                        c.s = sm.s;
                    }
                }
            }
        }
        if (best.isEmpty()) {
            return out;
        }
        int x0 = Integer.MAX_VALUE;
        int x1 = Integer.MIN_VALUE;
        int z0 = Integer.MAX_VALUE;
        int z1 = Integer.MIN_VALUE;
        for (long k : best.keySet()) {
            int x = (int) (k >> 32);
            int z = (int) k;
            x0 = Math.min(x0, x);
            x1 = Math.max(x1, x);
            z0 = Math.min(z0, z);
            z1 = Math.max(z1, z);
        }
        Surfaces.Cell[][] g = new Surfaces.Cell[x1 - x0 + 1][z1 - z0 + 1];
        for (Map.Entry<Long, Cand> e : best.entrySet()) {
            int x = (int) (e.getKey() >> 32);
            int z = (int) e.getKey().longValue();
            Cand c = e.getValue();
            Surfaces.Cell cell;
            if (Math.abs(c.lateral) <= hw) {
                cell = Surfaces.Cell.road(Kind.WATER, null);
            } else {
                cell = Surfaces.Cell.paving(Direction.NORTH);
            }
            cell.tag = (int) Math.floor(c.s);
            cell.lat = c.lateral;
            g[x - x0][z - z0] = cell;
        }
        emit(out, g, x0, z0, y, st, pal, clearHeight);

        // фонари по внешнему краю берега, в шахматном порядке
        if (st.lampSpacing > 0) {
            int sp = st.lampSpacing;
            Set<Long> used = new HashSet<>();
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    Surfaces.Cell c = g[x - x0][z - z0];
                    if (c == null || !c.paving || c.curb || Math.abs(c.lat) != hw + bank || c.tag < 1) {
                        continue;
                    }
                    int want = c.lat < 0 ? 0 : sp / 2;
                    long k = ((long) (c.lat < 0 ? 1 : 2) << 40) ^ c.tag;
                    if (Math.floorMod(c.tag, sp) == want && used.add(k)) {
                        out.add(new Placement(new BlockPos(x, y + 1, z), pal.lampState(c.face)));
                    }
                }
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ озеро

    public static List<Placement> lake(List<BlockPos> pts, RoadSettings st, int clearHeight) {
        List<Placement> out = new ArrayList<>();
        double[][] poly = ParkingPlan.polygon(pts);
        if (poly.length < 3) {
            return out;
        }
        RoadPalette pal = st.toPalette();
        int y = pts.get(0).getY();
        int bank = bank(st);
        double minX = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE;
        double maxZ = -Double.MAX_VALUE;
        for (double[] p : poly) {
            minX = Math.min(minX, p[0]);
            maxX = Math.max(maxX, p[0]);
            minZ = Math.min(minZ, p[1]);
            maxZ = Math.max(maxZ, p[1]);
        }
        int x0 = (int) Math.floor(minX) - bank - 1;
        int z0 = (int) Math.floor(minZ) - bank - 1;
        int x1 = (int) Math.ceil(maxX) + bank + 1;
        int z1 = (int) Math.ceil(maxZ) + bank + 1;
        if ((long) (x1 - x0 + 1) * (z1 - z0 + 1) > 400000) {
            return out;
        }
        Surfaces.Cell[][] g = new Surfaces.Cell[x1 - x0 + 1][z1 - z0 + 1];
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                double cx = x + 0.5;
                double cz = z + 0.5;
                if (ParkingPlan.inside(poly, cx, cz)) {
                    g[x - x0][z - z0] = Surfaces.Cell.road(Kind.WATER, null);
                } else {
                    double[] e = ParkingPlan.edgeInfo(poly, cx, cz);
                    if (e[0] <= bank + 0.2) {
                        Surfaces.Cell c = Surfaces.Cell.paving(Direction.NORTH);
                        c.tag = (int) Math.floor(e[1]);
                        c.lat = e[0] > bank - 0.8 ? -3 : -1;
                        g[x - x0][z - z0] = c;
                    }
                }
            }
        }
        emit(out, g, x0, z0, y, st, pal, clearHeight);
        if (st.lampSpacing > 0) {
            int sp = st.lampSpacing;
            Set<Long> used = new HashSet<>();
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    Surfaces.Cell c = g[x - x0][z - z0];
                    if (c != null && c.paving && !c.curb && c.lat == -3 && Math.floorMod(c.tag, sp) == 0
                            && used.add((long) c.tag)) {
                        out.add(new Placement(new BlockPos(x, y + 1, z), pal.lampState(c.face)));
                    }
                }
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ общее

    private static void emit(List<Placement> out, Surfaces.Cell[][] g, int x0, int z0, int y, RoadSettings st,
                             RoadPalette pal, int clearHeight) {
        Surfaces.resolve(g, Direction.EAST, Direction.SOUTH);
        int depth = st.riverDepth;
        for (int i = 0; i < g.length; i++) {
            for (int j = 0; j < g[i].length; j++) {
                Surfaces.Cell c = g[i][j];
                if (c == null) {
                    continue;
                }
                int x = x0 + i;
                int z = z0 + j;
                for (int h = 1; h <= clearHeight; h++) {
                    out.add(new Placement(new BlockPos(x, y + h, z), AIR));
                }
                if (c.kind == Kind.WATER && !c.paving) {
                    out.add(new Placement(new BlockPos(x, y, z), AIR));
                    for (int d = depth - 1; d >= 1; d--) {
                        out.add(new Placement(new BlockPos(x, y - d, z), WATER));
                    }
                    out.add(new Placement(new BlockPos(x, y - depth, z), SAND));
                } else {
                    out.add(new Placement(new BlockPos(x, y, z), Surfaces.finish(c, pal)));
                }
            }
        }
    }

    private WaterPlan() {}
}
