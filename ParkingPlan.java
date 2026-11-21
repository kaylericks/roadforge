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
 * Стоянка по контуру: любые точки (многоугольник), внутри асфальт, ряды мест, проезды, газонные полосы и острова.
 * Ряды идут вдоль выбранной оси (по умолчанию - первая сторона контура). Рисунок: проезд, ряд, [полоса], ряд, проезд...
 * Ряды спарены спинами друг к другу, места открыты в сторону своего проезда.
 */
public final class ParkingPlan {
    /** Результат: блоки и число мест. */
    public static final class Result {
        public final List<Placement> blocks = new ArrayList<>();
        public int stalls;
    }

    private static final int GRASS_CELL = 1;

    static double[][] polygon(List<BlockPos> pts) {
        List<double[]> p = new ArrayList<>();
        for (BlockPos b : pts) {
            double x = b.getX() + 0.5;
            double z = b.getZ() + 0.5;
            if (!p.isEmpty()) {
                double[] last = p.get(p.size() - 1);
                if (Math.hypot(x - last[0], z - last[1]) < 0.9) {
                    continue;
                }
            }
            p.add(new double[]{x, z});
        }
        if (p.size() > 1) {
            double[] f = p.get(0);
            double[] l = p.get(p.size() - 1);
            if (Math.hypot(f[0] - l[0], f[1] - l[1]) < 0.9) {
                p.remove(p.size() - 1);
            }
        }
        return p.toArray(new double[0][]);
    }

    static boolean inside(double[][] poly, double x, double z) {
        boolean in = false;
        for (int i = 0, j = poly.length - 1; i < poly.length; j = i++) {
            double xi = poly[i][0];
            double zi = poly[i][1];
            double xj = poly[j][0];
            double zj = poly[j][1];
            if ((zi > z) != (zj > z) && x < (xj - xi) * (z - zi) / (zj - zi) + xi) {
                in = !in;
            }
        }
        return in;
    }

    /** Расстояние от точки до границы и положение вдоль периметра ближайшей точки. */
    static double[] edgeInfo(double[][] poly, double x, double z) {
        double best = Double.MAX_VALUE;
        double bestS = 0;
        double acc = 0;
        for (int i = 0; i < poly.length; i++) {
            double[] a = poly[i];
            double[] b = poly[(i + 1) % poly.length];
            double dx = b[0] - a[0];
            double dz = b[1] - a[1];
            double len2 = dx * dx + dz * dz;
            double t = len2 < 1e-9 ? 0 : Math.max(0, Math.min(1, ((x - a[0]) * dx + (z - a[1]) * dz) / len2));
            double px = a[0] + dx * t;
            double pz = a[1] + dz * t;
            double d = Math.hypot(x - px, z - pz);
            if (d < best) {
                best = d;
                bestS = acc + Math.sqrt(len2) * t;
            }
            acc += Math.sqrt(len2);
        }
        return new double[]{best, bestS};
    }

    public static double area(List<BlockPos> pts) {
        double[][] poly = polygon(pts);
        double a = 0;
        for (int i = 0, j = poly.length - 1; i < poly.length; j = i++) {
            a += poly[j][0] * poly[i][1] - poly[i][0] * poly[j][1];
        }
        return Math.abs(a) / 2;
    }

    public static Result generate(List<BlockPos> pts, RoadSettings st, int clearHeight) {
        Result res = new Result();
        double[][] poly = polygon(pts);
        if (poly.length < 3) {
            return res;
        }
        RoadPalette pal = st.toPalette();
        RoadSpec spec = st.toSpec();
        int y = pts.get(0).getY();
        int ring = Math.max(0, st.sidewalkWidth);

        // оси рядов: u - вдоль проезда, v - поперёк рядов
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
        int bx0 = (int) Math.floor(minX) - ring - 1;
        int bx1 = (int) Math.ceil(maxX) + ring + 1;
        int bz0 = (int) Math.floor(minZ) - ring - 1;
        int bz1 = (int) Math.ceil(maxZ) + ring + 1;
        int w = bx1 - bx0 + 1;
        int h = bz1 - bz0 + 1;
        if ((long) w * h > 400000) {
            return res;                       // слишком большой участок
        }

        int stallW = st.lotStall;
        int depth = st.lotStyle == 2 ? Math.min(st.lotDepth, 3) : st.lotDepth;
        int aisle = st.lotAisle;
        int strip = st.lotStrip;
        int period = aisle + 2 * depth + strip;
        int pitch = st.lotStyle == 2 ? Math.max(stallW, 6) : stallW;       // параллельные места длиннее
        int stallCells = 0;
        Set<Long> treeAt = new HashSet<>();

        Surfaces.Cell[][] g = new Surfaces.Cell[w][h];
        boolean[][] in = new boolean[w][h];
        for (int gx = 0; gx < w; gx++) {
            for (int gz = 0; gz < h; gz++) {
                double cx = bx0 + gx + 0.5;
                double cz = bz0 + gz + 0.5;
                if (!inside(poly, cx, cz)) {
                    continue;
                }
                in[gx][gz] = true;
                double uc = cx * ux + cz * uz - u0;
                double vc = cx * vx + cz * vz - v0;
                int t = Math.floorMod((int) Math.floor(vc) + aisle + depth + strip, period);
                int k = (int) Math.floor(uc / pitch);
                int wInStall = (int) Math.floor(uc) - k * pitch;
                Surfaces.Cell c;
                if (t < aisle) {
                    c = Surfaces.Cell.road(Kind.ASPHALT, pal.asphaltState(Direction.EAST));
                } else {
                    boolean rowA = t < aisle + depth;
                    boolean stripCell = t >= aisle + depth && t < aisle + depth + strip;
                    int dpt = rowA ? t - aisle : t - (aisle + depth + strip);      // от начала своего ряда
                    int fromAisle = rowA ? dpt : depth - 1 - dpt;
                    if (stripCell) {
                        c = Surfaces.Cell.road(Kind.GRASS, pal.grassState());
                        c.median = true;
                        c.tag = GRASS_CELL;
                        if (strip >= 3 && t == aisle + depth + strip / 2 && st.treeSpacing > 0
                                && Math.floorMod((int) Math.floor(uc), st.treeSpacing) == st.treeSpacing / 2) {
                            treeAt.add(((long) gx << 32) ^ gz);
                        }
                    } else if (st.lotIsland > 0 && Math.floorMod(k, st.lotIsland) == st.lotIsland - 1) {
                        c = Surfaces.Cell.road(Kind.GRASS, pal.grassState());
                        c.median = true;
                        c.tag = GRASS_CELL;
                        if (st.treeSpacing > 0 && wInStall == pitch / 2 && fromAisle == depth / 2) {
                            treeAt.add(((long) gx << 32) ^ gz);
                        }
                    } else {
                        c = stallCell(st, pal, spec, pitch, wInStall, fromAisle, depth, ux, uz, vx, vz, uc, k, rowA);
                        if (wInStall == 0 && fromAisle == 0) {
                            res.stalls++;
                        }
                    }
                }
                g[gx][gz] = c;
            }
        }

        // тротуар по периметру
        if (ring > 0) {
            for (int gx = 0; gx < w; gx++) {
                for (int gz = 0; gz < h; gz++) {
                    if (in[gx][gz]) {
                        continue;
                    }
                    double cx = bx0 + gx + 0.5;
                    double cz = bz0 + gz + 0.5;
                    double[] e = edgeInfo(poly, cx, cz);
                    if (e[0] <= ring + 0.2) {
                        Surfaces.Cell c = Surfaces.Cell.paving(Direction.NORTH);
                        c.tag = (int) Math.floor(e[1]);
                        c.lat = e[0] > ring - 0.8 ? -3 : -1;        // -3: внешний ряд (для фонарей)
                        g[gx][gz] = c;
                    }
                }
            }
        }
        Surfaces.resolve(g, Direction.EAST, Direction.SOUTH);

        // блоки
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int gx = 0; gx < w; gx++) {
            for (int gz = 0; gz < h; gz++) {
                if (g[gx][gz] == null) {
                    continue;
                }
                for (int hh = 1; hh <= clearHeight; hh++) {
                    res.blocks.add(new Placement(new BlockPos(bx0 + gx, y + hh, bz0 + gz), air));
                }
            }
        }
        for (int gx = 0; gx < w; gx++) {
            for (int gz = 0; gz < h; gz++) {
                Surfaces.Cell c = g[gx][gz];
                if (c != null) {
                    res.blocks.add(new Placement(new BlockPos(bx0 + gx, y, bz0 + gz), Surfaces.finish(c, pal)));
                }
            }
        }
        // деревья
        for (long key : treeAt) {
            int gx = (int) (key >> 32);
            int gz = (int) key;
            if (g[gx][gz] != null && g[gx][gz].kind == Kind.GRASS && !g[gx][gz].paving) {
                Trees.plant(res.blocks, new BlockPos(bx0 + gx, y, bz0 + gz));
            }
        }
        // фонари на внешнем ряду тротуара
        if (st.lampSpacing > 0 && ring > 0) {
            int sp = st.lampSpacing;
            Set<Long> used = new HashSet<>();
            for (int gx = 0; gx < w; gx++) {
                for (int gz = 0; gz < h; gz++) {
                    Surfaces.Cell c = g[gx][gz];
                    if (c != null && c.paving && !c.curb && c.lat == -3 && Math.floorMod(c.tag, sp) == 0
                            && used.add((long) c.tag)) {
                        res.blocks.add(new Placement(new BlockPos(bx0 + gx, y + 1, bz0 + gz), pal.lampState(c.face)));
                    }
                }
            }
        }
        return res;
    }

    /** Клетка места: асфальт или линия разметки. */
    private static Surfaces.Cell stallCell(RoadSettings st, RoadPalette pal, RoadSpec spec, int pitch, int wInStall,
                                           int fromAisle, int depth, double ux, double uz, double vx, double vz,
                                           double uc, int k, boolean rowA) {
        boolean line = false;
        double tx = vx;       // направление линии на земле
        double tz = vz;
        switch (st.lotStyle) {
            case 1 -> {
                // косые места: линия под 45° (сдвиг на клетку за каждую клетку вглубь)
                int shift = fromAisle;
                int pos = Math.floorMod((int) Math.floor(uc) - shift, pitch);
                line = pos == 0;
                double s = Math.sqrt(0.5);
                tx = (ux + vx) * s;
                tz = (uz + vz) * s;
            }
            case 2 -> {
                // параллельные: поперечные границы мест и линия вдоль проезда
                line = wInStall == 0 || fromAisle == 0;
                if (fromAisle == 0 && wInStall != 0) {
                    tx = ux;
                    tz = uz;
                }
            }
            default -> line = wInStall == 0;
        }
        if (!line) {
            return Surfaces.Cell.road(Kind.ASPHALT, pal.asphaltState(Direction.EAST));
        }
        return Surfaces.Cell.road(Kind.LINE_SOLID, LineMarks.cell(Kind.LINE_SOLID, tx, tz, 0, spec, pal));
    }

    private ParkingPlan() {}
}
