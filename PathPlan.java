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
 * Дорога по ломаной линии под любым углом: точки соединяются прямыми, углы скругляются дугой.
 * Профиль (полосы, разметка, тротуары) тот же, что у обычной дороги; парковки и переходы пока не строятся.
 */
public final class PathPlan {
    /** Шаг выборки центральной линии, блоков (меньше шага - нет дыр на внешней стороне дуги). */
    private static final double STEP = 0.35;

    /** Одна точка выборки центральной линии. */
    public static final class Sample {
        public final double x;
        public final double z;
        public final double tx;     // единичный вектор вдоль движения
        public final double tz;
        public final double s;      // длина пути от начала
        public final int y;

        Sample(double x, double z, double tx, double tz, double s, int y) {
            this.x = x;
            this.z = z;
            this.tx = tx;
            this.tz = tz;
            this.s = s;
            this.y = y;
        }
    }

    /** Результат разбора: выборка центральной линии и радиус скругления, который удалось применить. */
    public static final class Centerline {
        public final List<Sample> samples = new ArrayList<>();
        public double length;
        /** Положения исходных точек вдоль пути и их слои высоты. */
        public double[] ctrlS = new double[0];
        public int[] ctrlY = new int[0];
    }

    private static final class Cand {
        double dist;
        int idx;
        double s;
        double tx;
        double tz;
    }

    /** Радиус скругления оси для данных настроек (внутренний бордюр не меньше cornerRadius). */
    public static double axisRadius(RoadSettings st) {
        RoadSpec spec = st.toSpec();
        CrossSection cs = CrossSection.build(spec);
        double roadHalf = (cs.roadLast() - cs.ref()) + 0.5;
        return Math.max(2, st.cornerRadius) + roadHalf + 1;
    }

    /** Строит центральную линию: прямые между точками, дуги на изломах. */
    public static Centerline centerline(List<BlockPos> pts, double radius) {
        Centerline out = new Centerline();
        // убираем повторяющиеся точки (по x, z)
        List<double[]> p = new ArrayList<>();
        List<Integer> ys = new ArrayList<>();
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
            ys.add(b.getY());
        }
        int n = p.size();
        if (n < 2) {
            return out;
        }
        // узлы пути: начало, затем для каждого внутреннего узла - точка входа и выхода из дуги
        // сегменты: прямая (a -> b) или дуга (центр, радиус, углы)
        List<double[]> segs = new ArrayList<>();   // {type, ...}
        double[] cur = p.get(0);
        double curLen = 0;
        double[] dirPrev = null;
        List<double[]> anchors = new ArrayList<>(); // {x, z, distanceAlongPath}
        for (int i = 1; i < n; i++) {
            double[] a = p.get(i - 1);
            double[] b = p.get(i);
            double dx = b[0] - a[0];
            double dz = b[1] - a[1];
            double len = Math.hypot(dx, dz);
            double[] d = {dx / len, dz / len};
            double startX = cur[0];
            double startZ = cur[1];
            double endX = b[0];
            double endZ = b[1];
            double arcT = 0;
            double r = 0;
            double turn = 0;
            if (i < n - 1) {
                double[] c = p.get(i + 1);
                double ex = c[0] - b[0];
                double ez = c[1] - b[1];
                double elen = Math.hypot(ex, ez);
                double[] d2 = {ex / elen, ez / elen};
                double cross = d[0] * d2[1] - d[1] * d2[0];
                double dot = d[0] * d2[0] + d[1] * d2[1];
                turn = Math.atan2(cross, dot);     // со знаком: >0 - поворот по часовой (в осях x восток, z юг)
                double abs = Math.abs(turn);
                if (abs > 0.02) {
                    r = radius;
                    double t = r * Math.tan(abs / 2);
                    // дуга не должна занимать больше половины соседних участков
                    double avail = Math.min(Math.hypot(b[0] - startX, b[1] - startZ), elen) * 0.5;
                    if (i + 1 < n - 1) {
                        avail = Math.min(avail, elen * 0.5);
                    }
                    if (t > avail) {
                        t = avail;
                        r = t / Math.tan(abs / 2);
                    }
                    arcT = t;
                    endX = b[0] - d[0] * t;
                    endZ = b[1] - d[1] * t;
                }
            }
            segs.add(new double[]{0, startX, startZ, endX, endZ});
            if (arcT > 0) {
                // дуга: центр смещён от конца прямой по нормали в сторону поворота
                double side = turn > 0 ? 1 : -1;      // >0: центр справа от движения
                double nx = -d[1] * side;             // вправо = (-dz, dx)
                double nz = d[0] * side;
                double cx = endX + nx * r;
                double cz = endZ + nz * r;
                double a0 = Math.atan2(endZ - cz, endX - cx);
                segs.add(new double[]{1, cx, cz, r, a0, turn});
                double a1 = a0 + turn;
                cur = new double[]{cx + Math.cos(a1) * r, cz + Math.sin(a1) * r};
            } else {
                cur = new double[]{endX, endZ};
            }
        }
        // последний прямой участок уже добавлен; выборка по сегментам
        double s = 0;
        double[] vertexS = new double[n];   // расстояние вдоль пути до исходных узлов (для высоты)
        // грубо: высоту интерполируем по доле пройденного пути
        double total = 0;
        for (double[] sg : segs) {
            total += sg[0] == 0 ? Math.hypot(sg[3] - sg[1], sg[4] - sg[2]) : Math.abs(sg[5]) * sg[3];
        }
        out.length = total;
        double done = 0;
        for (double[] sg : segs) {
            double segLen = sg[0] == 0 ? Math.hypot(sg[3] - sg[1], sg[4] - sg[2]) : Math.abs(sg[5]) * sg[3];
            int steps = Math.max(1, (int) Math.ceil(segLen / STEP));
            for (int k = 0; k <= steps; k++) {
                if (k == steps && sg != segs.get(segs.size() - 1)) {
                    continue;   // конец сегмента = начало следующего
                }
                double f = (double) k / steps;
                double x;
                double z;
                double tx;
                double tz;
                if (sg[0] == 0) {
                    x = sg[1] + (sg[3] - sg[1]) * f;
                    z = sg[2] + (sg[4] - sg[2]) * f;
                    double len = Math.max(1e-9, segLen);
                    tx = (sg[3] - sg[1]) / len;
                    tz = (sg[4] - sg[2]) / len;
                } else {
                    double ang = sg[4] + sg[5] * f;
                    x = sg[1] + Math.cos(ang) * sg[3];
                    z = sg[2] + Math.sin(ang) * sg[3];
                    double sgn = sg[5] > 0 ? 1 : -1;
                    tx = -Math.sin(ang) * sgn;
                    tz = Math.cos(ang) * sgn;
                }
                double ps = done + segLen * f;
                out.samples.add(new Sample(x, z, tx, tz, ps, 0));
            }
            done += segLen;
        }
        // положения исходных точек: ближайшая выборка оси
        out.ctrlS = new double[n];
        out.ctrlY = new int[n];
        for (int i = 0; i < n; i++) {
            double bestD = Double.MAX_VALUE;
            double bestS = 0;
            for (Sample sm : out.samples) {
                double d = Math.hypot(sm.x - p.get(i)[0], sm.z - p.get(i)[1]);
                if (d < bestD) {
                    bestD = d;
                    bestS = sm.s;
                }
            }
            out.ctrlS[i] = i == 0 ? 0 : (i == n - 1 ? total : bestS);
            out.ctrlY[i] = ys.get(i);
        }
        for (int i = 1; i < n; i++) {
            out.ctrlS[i] = Math.max(out.ctrlS[i], out.ctrlS[i - 1]);
        }
        return out;
    }

    /** Текст ошибки, если уклон пути круче допустимого, иначе null. */
    public static String check(List<BlockPos> pts, RoadSettings st) {
        Centerline cl = centerline(pts, axisRadius(st));
        if (cl.ctrlS.length < 2) {
            return null;
        }
        return Grade.check(cl.ctrlS, cl.ctrlY);
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    private static Direction lineFacing(double tx, double tz) {
        return Math.abs(tx) >= Math.abs(tz) ? Direction.EAST : Direction.NORTH;
    }

    private static Direction cardinal(double vx, double vz) {
        if (Math.abs(vx) >= Math.abs(vz)) {
            return vx > 0 ? Direction.EAST : Direction.WEST;
        }
        return vz > 0 ? Direction.SOUTH : Direction.NORTH;
    }

    public static List<Placement> generate(List<BlockPos> pts, RoadSettings st, int clearHeight) {
        RoadSpec spec = st.toSpec().withEndCaps(false);
        CrossSection cs = CrossSection.build(spec);
        RoadPalette pal = st.toPalette();
        int w = cs.width();
        int ref = cs.ref();
        Centerline line = centerline(pts, axisRadius(st));
        List<Placement> out = new ArrayList<>();
        if (line.samples.size() < 2) {
            return out;
        }
        Grade grade = Grade.of(line.ctrlS, line.ctrlY);

        // 1. растеризация: каждая клетка берёт ближайшую выборку оси
        int reach = Math.max(ref, w - 1 - ref) + 2;
        Map<Long, Cand> best = new HashMap<>();
        for (Sample sm : line.samples) {
            double rx = -sm.tz;      // вправо от движения
            double rz = sm.tx;
            int cx0 = (int) Math.floor(sm.x);
            int cz0 = (int) Math.floor(sm.z);
            for (int x = cx0 - reach; x <= cx0 + reach; x++) {
                for (int z = cz0 - reach; z <= cz0 + reach; z++) {
                    double vx = x + 0.5 - sm.x;
                    double vz = z + 0.5 - sm.z;
                    double dist = Math.hypot(vx, vz);
                    double lateral = vx * rx + vz * rz;
                    int idx = ref + (int) Math.round(lateral);
                    if (idx < 0 || idx >= w) {
                        continue;
                    }
                    long k = key(x, z);
                    Cand c = best.get(k);
                    if (c == null) {
                        c = new Cand();
                        c.dist = Double.MAX_VALUE;
                        best.put(k, c);
                    }
                    if (dist < c.dist) {
                        c.dist = dist;
                        c.idx = idx;
                        c.s = sm.s;
                        c.tx = sm.tx;
                        c.tz = sm.tz;
                    }
                }
            }
        }
        if (best.isEmpty()) {
            return out;
        }

        // 2. сетка клеток
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
        Surfaces.Cell[][] grid = new Surfaces.Cell[x1 - x0 + 1][z1 - z0 + 1];
        int[][] ys = new int[x1 - x0 + 1][z1 - z0 + 1];
        for (Map.Entry<Long, Cand> e : best.entrySet()) {
            int x = (int) (e.getKey() >> 32);
            int z = (int) e.getKey().longValue();
            Cand c = e.getValue();
            Kind kind = cs.cols().get(c.idx);
            Surfaces.Cell cell;
            if (cs.medianEdge(c.idx)) {
                cell = Surfaces.Cell.paving(Direction.NORTH);
            } else if (kind == Kind.SIDEWALK_L || kind == Kind.SIDEWALK_R) {
                // дорога лежит в сторону оси: слева от оси - вправо, справа - влево
                double rx = -c.tz;
                double rz = c.tx;
                Direction toRoad = c.idx < ref ? cardinal(rx, rz) : cardinal(-rx, -rz);
                cell = Surfaces.Cell.paving(toRoad);
            } else {
                if (kind == Kind.PARK_EDGE || kind == Kind.PARK_BAY) {
                    kind = Kind.ASPHALT;
                }
                Direction f = lineFacing(c.tx, c.tz);
                int s = (int) Math.floor(c.s);
                BlockState bs = (kind == Kind.LINE_SOLID || kind == Kind.LINE_DASH)
                        ? LineMarks.cell(kind, c.tx, c.tz, s, spec, pal)
                        : RoadPlan.surfaceState(kind, 0, false, s, 100000, f, f, spec, pal);
                cell = Surfaces.Cell.road(kind, bs);
                cell.median = kind == Kind.GRASS && c.idx >= cs.roadFirst() && c.idx <= cs.roadLast();
            }
            cell.tag = (int) Math.floor(c.s);
            cell.lat = c.idx;
            Grade.Ramp ramp = grade.rampAt(c.s);
            if (ramp != null && !cell.paving
                    && (kind == Kind.ASPHALT || kind == Kind.LINE_SOLID || kind == Kind.LINE_DASH)) {
                Direction travel = cardinal(c.tx, c.tz);
                Direction face = ramp.up ? travel.getOpposite() : travel;
                boolean on = Math.floorMod((int) Math.floor(c.s), spec.dashPeriod()) < spec.dashOn();
                String id = kind == Kind.LINE_SOLID ? pal.capSolid()
                        : (kind == Kind.LINE_DASH && on ? pal.capDash() : pal.capPlain());
                cell.state = pal.capState(id, face);
            }
            grid[x - x0][z - z0] = cell;
            ys[x - x0][z - z0] = grade.layerAt(c.s);
        }
        Surfaces.resolve(grid, Direction.EAST, Direction.SOUTH);

        // 3. блоки
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                Surfaces.Cell c = grid[x - x0][z - z0];
                if (c == null) {
                    continue;
                }
                int y = ys[x - x0][z - z0];
                for (int h = 1; h <= clearHeight; h++) {
                    out.add(new Placement(new BlockPos(x, y + h, z), air));
                }
            }
        }
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                Surfaces.Cell c = grid[x - x0][z - z0];
                if (c != null) {
                    out.add(new Placement(new BlockPos(x, ys[x - x0][z - z0], z), Surfaces.finish(c, pal)));
                }
            }
        }

        // 4. фонари по краям профиля, шаг - вдоль пути
        if (spec.lampSpacing() > 0 && cs.lampLeft() >= 0) {
            int sp = spec.lampSpacing();
            int half = sp / 2;
            Set<Long> used = new HashSet<>();
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    Surfaces.Cell c = grid[x - x0][z - z0];
                    if (c == null || !c.paving || c.curb) {
                        continue;
                    }
                    boolean left = c.lat == cs.lampLeft() && Math.floorMod(c.tag, sp) == half;
                    boolean rightLamp = c.lat == cs.lampRight() && Math.floorMod(c.tag, sp) == 0;
                    if (!left && !rightLamp) {
                        continue;
                    }
                    long key = ((long) c.lat << 40) ^ c.tag;
                    if (!used.add(key)) {
                        continue;
                    }
                    out.add(new Placement(new BlockPos(x, ys[x - x0][z - z0] + 1, z), pal.lampState(c.face)));
                }
            }
        }

        // 5. деревья по центру газона
        if (spec.treeSpacing() > 0) {
            int sp = spec.treeSpacing();
            Set<Long> used = new HashSet<>();
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
                for (int x = x0; x <= x1; x++) {
                    for (int z = z0; z <= z1; z++) {
                        Surfaces.Cell c = grid[x - x0][z - z0];
                        if (c != null && c.lat == mid && c.kind == Kind.GRASS && Math.floorMod(c.tag, sp) == sp / 2
                                && used.add(((long) mid << 40) ^ c.tag)) {
                            Trees.plant(out, new BlockPos(x, ys[x - x0][z - z0], z));
                        }
                    }
                }
                i = j + 1;
            }
        }
        return out;
    }

    /** Длина осевой линии в блоках (для подписи). */
    public static int lengthOf(List<BlockPos> pts, RoadSettings st) {
        return (int) Math.round(centerline(pts, axisRadius(st)).length);
    }

    private PathPlan() {}
}
