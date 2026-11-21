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
 * Подземная стоянка в один этаж. Контур и разметка мест - как у обычной стоянки, но пол лежит на lotLevelH ниже
 * первой точки, над ним потолок, по краю стены, колонны и светильники в потолке. Въезд - пандус вдоль первого
 * проезда от края контура (в начале первого проезда, со стороны минимального u): открытая траншея, которая
 * спускается на этаж с уклоном слой за 2 блока. Сверху остаётся природная земля.
 */
public final class UndergroundPlan {
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState LINING = Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState();
    private static final BlockState LANTERN = Blocks.SEA_LANTERN.defaultBlockState();

    public static ParkingPlan.Result generate(List<BlockPos> pts, RoadSettings st, int clearHeight) {
        ParkingPlan.Result res = new ParkingPlan.Result();
        double[][] poly = ParkingPlan.polygon(pts);
        if (poly.length < 3) {
            return res;
        }
        int y0 = pts.get(0).getY();
        int hDepth = st.lotLevelH;
        int yf = y0 - hDepth;

        // пол и разметка мест: обычная стоянка, опущенная вниз и без зелени, тротуара и фонарей
        RoadSettings f = st.duplicate();
        f.sidewalkWidth = 0;
        f.lotStrip = 0;
        f.lotIsland = 0;
        f.treeSpacing = 0;
        f.lampSpacing = 0;
        f.lotUnderground = false;
        List<BlockPos> lowered = new ArrayList<>();
        for (BlockPos p : pts) {
            lowered.add(new BlockPos(p.getX(), yf, p.getZ()));
        }
        ParkingPlan.Result floor = ParkingPlan.generate(lowered, f, 0);
        if (floor.blocks.isEmpty()) {
            return res;
        }
        res.stalls = floor.stalls;

        RoadPalette pal = st.toPalette();
        RoadSpec spec = st.toSpec();
        // та же система осей, что в ParkingPlan
        double theta = st.lotAngle < 0
                ? Math.atan2(poly[1][1] - poly[0][1], poly[1][0] - poly[0][0])
                : Math.toRadians(st.lotAngle);
        double ux = Math.cos(theta);
        double uz = Math.sin(theta);
        double vx = -uz;
        double vz = ux;
        double u0 = Double.MAX_VALUE;
        double v0 = Double.MAX_VALUE;
        double minX = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE;
        double maxZ = -Double.MAX_VALUE;
        for (double[] p : poly) {
            u0 = Math.min(u0, p[0] * ux + p[1] * uz);
            v0 = Math.min(v0, p[0] * vx + p[1] * vz);
            minX = Math.min(minX, p[0]);
            maxX = Math.max(maxX, p[0]);
            minZ = Math.min(minZ, p[1]);
            maxZ = Math.max(maxZ, p[1]);
        }
        int bx0 = (int) Math.floor(minX) - 2;
        int bx1 = (int) Math.ceil(maxX) + 2;
        int bz0 = (int) Math.floor(minZ) - 2;
        int bz1 = (int) Math.ceil(maxZ) + 2;
        if ((long) (bx1 - bx0 + 1) * (bz1 - bz0 + 1) > 400000) {
            return res;
        }
        int depth = st.lotStyle == 2 ? Math.min(st.lotDepth, 3) : st.lotDepth;
        int aisle = st.lotAisle;
        int period = aisle + 2 * depth;
        int pitch = st.lotStyle == 2 ? Math.max(st.lotStall, 6) : st.lotStall;
        int rampLen = 2 * hDepth;
        Grade grade = Grade.of(new double[]{0, rampLen}, new int[]{y0, yf});

        // первый проезд и начало пандуса: самая «ранняя» по u клетка этого проезда внутри контура
        double minU = Double.MAX_VALUE;
        for (int x = bx0; x <= bx1; x++) {
            for (int z = bz0; z <= bz1; z++) {
                double cx = x + 0.5;
                double cz = z + 0.5;
                if (!ParkingPlan.inside(poly, cx, cz)) {
                    continue;
                }
                double vc = cx * vx + cz * vz - v0;
                int fv = (int) Math.floor(vc);
                if (fv >= depth && fv < depth + aisle) {
                    minU = Math.min(minU, cx * ux + cz * uz - u0);
                }
            }
        }

        Set<Long> rampCols = new HashSet<>();
        List<Placement> extra = new ArrayList<>();
        Direction travel = Math.abs(ux) >= Math.abs(uz)
                ? (ux >= 0 ? Direction.EAST : Direction.WEST)
                : (uz >= 0 ? Direction.SOUTH : Direction.NORTH);
        List<BlockPos> pillars = new ArrayList<>();
        List<BlockPos> lights = new ArrayList<>();
        for (int x = bx0; x <= bx1; x++) {
            for (int z = bz0; z <= bz1; z++) {
                double cx = x + 0.5;
                double cz = z + 0.5;
                boolean in = ParkingPlan.inside(poly, cx, cz);
                double uc = cx * ux + cz * uz - u0;
                double vc = cx * vx + cz * vz - v0;
                int fv = (int) Math.floor(vc);
                boolean band = fv >= depth && fv < depth + aisle;
                if (!in) {
                    double[] e = ParkingPlan.edgeInfo(poly, cx, cz);
                    if (e[0] > 1.2) {
                        continue;
                    }
                    if (band && uc < minU + 1.5) {
                        continue;                       // перед пандусом стену не ставим: здесь подъезд
                    }
                    for (int yy = yf; yy <= y0 - 1; yy++) {
                        extra.add(new Placement(new BlockPos(x, yy, z), LINING));
                    }
                    continue;
                }
                double rampU = uc - minU;
                if (band && rampU >= 0 && rampU < rampLen) {
                    rampCols.add(((long) x << 32) ^ (z & 0xFFFFFFFFL));
                    int layer = grade.layerAt(rampU);
                    Grade.Ramp rp = grade.rampAt(rampU);
                    BlockState bs = rp == null ? pal.asphaltState(Direction.EAST) : pal.capState(pal.capPlain(), travel);
                    extra.add(new Placement(new BlockPos(x, layer, z), bs));
                    for (int yy = layer + 1; yy <= y0 + Math.max(0, clearHeight); yy++) {
                        extra.add(new Placement(new BlockPos(x, yy, z), AIR));      // открытая траншея
                    }
                    continue;
                }
                // обычная клетка: объём между полом и потолком, потолок на y0-1, сверху остаётся земля
                for (int yy = yf + 1; yy <= y0 - 2; yy++) {
                    extra.add(new Placement(new BlockPos(x, yy, z), AIR));
                }
                extra.add(new Placement(new BlockPos(x, y0 - 1, z), LINING));
                int t = Math.floorMod(fv + aisle + depth, period);
                int k = (int) Math.floor(uc / pitch);
                int w = (int) Math.floor(uc) - k * pitch;
                if (t >= aisle && w == 0 && Math.floorMod(k, 3) == 0 && (t == aisle + depth - 1 || t == aisle + depth)) {
                    pillars.add(new BlockPos(x, yf, z));
                }
                if (t == aisle / 2 && Math.floorMod((int) Math.floor(uc), 8) == 4) {
                    lights.add(new BlockPos(x, y0 - 1, z));
                }
            }
        }
        // пол: убираем из обычного пола клетки пандуса, ставим остальное
        for (Placement p : floor.blocks) {
            long key = ((long) p.pos().getX() << 32) ^ (p.pos().getZ() & 0xFFFFFFFFL);
            if (!rampCols.contains(key)) {
                res.blocks.add(p);
            }
        }
        res.blocks.addAll(extra);
        for (BlockPos p : pillars) {
            for (int yy = yf + 1; yy <= y0 - 2; yy++) {
                res.blocks.add(new Placement(new BlockPos(p.getX(), yy, p.getZ()), LINING));
            }
        }
        for (BlockPos p : lights) {
            res.blocks.add(new Placement(p, LANTERN));
        }
        return res;
    }

    private UndergroundPlan() {}
}
