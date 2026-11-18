package com.roadforge.road;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.List;

/** Контур будущей постройки для подсветки в мире (чистая геометрия, без рисования). */
public final class OutlinePlan {
    /** Отрезки {x1, z1, x2, z2} в координатах мира. */
    public final List<double[]> lines = new ArrayList<>();
    /** Отмеченные точки {x, z}. */
    public final List<double[]> posts = new ArrayList<>();
    public double y;
    public String label = "";

    private void rect(double cx, double cz, Direction dir, double a0, double a1, double l0, double l1) {
        Direction right = dir.getClockWise();
        double[][] c = new double[4][];
        double[][] ab = {{a0, l0}, {a1, l0}, {a1, l1}, {a0, l1}};
        for (int i = 0; i < 4; i++) {
            c[i] = new double[]{cx + dir.getStepX() * ab[i][0] + right.getStepX() * ab[i][1],
                    cz + dir.getStepZ() * ab[i][0] + right.getStepZ() * ab[i][1]};
        }
        for (int i = 0; i < 4; i++) {
            double[] p = c[i];
            double[] q = c[(i + 1) % 4];
            lines.add(new double[]{p[0], p[1], q[0], q[1]});
        }
    }

    private void circle(double cx, double cz, double r) {
        int n = Math.max(24, (int) (r * 6));
        for (int i = 0; i < n; i++) {
            double a0 = 2 * Math.PI * i / n;
            double a1 = 2 * Math.PI * (i + 1) / n;
            lines.add(new double[]{cx + Math.cos(a0) * r, cz + Math.sin(a0) * r,
                    cx + Math.cos(a1) * r, cz + Math.sin(a1) * r});
        }
    }

    private static Direction directionOf(BlockPos a, BlockPos b) {
        int dx = b.getX() - a.getX();
        int dz = b.getZ() - a.getZ();
        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx >= 0 ? Direction.EAST : Direction.WEST;
        }
        return dz >= 0 ? Direction.SOUTH : Direction.NORTH;
    }

    private static int lengthOf(BlockPos a, BlockPos b) {
        return Math.max(Math.abs(b.getX() - a.getX()), Math.abs(b.getZ() - a.getZ())) + 1;
    }

    private static boolean arm(RoadSettings st, Direction d) {
        return switch (d) {
            case NORTH -> st.armN;
            case EAST -> st.armE;
            case SOUTH -> st.armS;
            default -> st.armW;
        };
    }

    /**
     * @param a    первая точка / центр
     * @param b    вторая точка (режим «Дорога»)
     * @param path точки режима «Путь»
     * @param aim  точка под прицелом (может быть null)
     */
    public static OutlinePlan compute(RoadSettings st, BlockPos a, BlockPos b, List<BlockPos> path, BlockPos aim) {
        OutlinePlan o = new OutlinePlan();
        RoadSpec spec = st.toSpec();
        CrossSection cs = CrossSection.build(spec);
        int w = cs.width();
        int ref = cs.ref();
        switch (st.mode) {
            case ROAD -> {
                if (a == null) {
                    if (aim != null) {
                        o.y = aim.getY();
                        o.rect(aim.getX() + 0.5, aim.getZ() + 0.5, Direction.SOUTH, -0.5, 0.5, -ref - 0.5, w - ref - 0.5);
                    }
                    o.label = "Дорога: отметь начало";
                    break;
                }
                BlockPos end = b != null ? b : aim;
                o.y = a.getY();
                o.posts.add(new double[]{a.getX() + 0.5, a.getZ() + 0.5});
                if (end == null) {
                    break;
                }
                Direction dir = directionOf(a, end);
                int len = lengthOf(a, end);
                o.rect(a.getX() + 0.5, a.getZ() + 0.5, dir, -0.5, len - 0.5, -ref - 0.5, w - ref - 0.5);
                if (b != null) {
                    o.posts.add(new double[]{b.getX() + 0.5, b.getZ() + 0.5});
                }
                o.label = "Дорога: " + len + " м · полос " + spec.lanes();
            }
            case PATH -> {
                List<BlockPos> pts = new ArrayList<>(path);
                if (aim != null) {
                    pts.add(aim);
                }
                for (BlockPos p : path) {
                    o.posts.add(new double[]{p.getX() + 0.5, p.getZ() + 0.5});
                }
                if (pts.isEmpty()) {
                    o.label = "Путь: отметь точки";
                    break;
                }
                o.y = pts.get(pts.size() - 1).getY();
                if (pts.size() < 2) {
                    o.label = "Путь: добавь ещё точку";
                    break;
                }
                PathPlan.Centerline cl = PathPlan.centerline(pts, PathPlan.axisRadius(st));
                if (cl.samples.size() < 2) {
                    o.label = "Путь: добавь ещё точку";
                    break;
                }
                double left = -ref - 0.5;
                double rightEdge = w - ref - 0.5;
                double[] pl = null;
                double[] pr = null;
                List<PathPlan.Sample> sm = cl.samples;
                for (int i = 0; i < sm.size(); i += 3) {
                    PathPlan.Sample s = sm.get(i);
                    // вправо = (-tz, tx): позиция = p + right * lateral
                    double[] l = {s.x - s.tz * left, s.z + s.tx * left};
                    double[] r = {s.x - s.tz * rightEdge, s.z + s.tx * rightEdge};
                    if (pl != null) {
                        o.lines.add(new double[]{pl[0], pl[1], l[0], l[1]});
                        o.lines.add(new double[]{pr[0], pr[1], r[0], r[1]});
                    }
                    pl = l;
                    pr = r;
                }
                PathPlan.Sample last = sm.get(sm.size() - 1);
                double[] l = {last.x - last.tz * left, last.z + last.tx * left};
                double[] r = {last.x - last.tz * rightEdge, last.z + last.tx * rightEdge};
                if (pl != null) {
                    o.lines.add(new double[]{pl[0], pl[1], l[0], l[1]});
                    o.lines.add(new double[]{pr[0], pr[1], r[0], r[1]});
                }
                o.lines.add(new double[]{l[0], l[1], r[0], r[1]});
                PathPlan.Sample first = sm.get(0);
                o.lines.add(new double[]{first.x - first.tz * left, first.z + first.tx * left,
                        first.x - first.tz * rightEdge, first.z + first.tx * rightEdge});
                o.label = "Путь: " + (int) Math.round(cl.length) + " м · полос " + spec.lanes()
                        + " · точек " + path.size();
            }
            case INTERSECTION, ROUNDABOUT -> {
                BlockPos c = a != null ? a : aim;
                if (c == null) {
                    o.label = "Укажи центр";
                    break;
                }
                o.y = c.getY();
                if (a != null) {
                    o.posts.add(new double[]{a.getX() + 0.5, a.getZ() + 0.5});
                }
                double cx = c.getX() + 0.5;
                double cz = c.getZ() + 0.5;
                CrossSection csB = CrossSection.build(spec.withLanes(RoadPreset.clampLanes(st.crossLanes)));
                double reach;
                if (st.mode == BuildMode.ROUNDABOUT) {
                    int[] r = RoundaboutPlan.radii(st);
                    o.circle(cx, cz, r[0] + 0.5);
                    o.circle(cx, cz, r[1] + 0.5);
                    o.circle(cx, cz, r[2] + 0.5);
                    reach = r[1] + st.armLength + 0.5;
                    o.label = "Кольцо: остров R" + st.islandRadius + " · полос на кольце " + st.ringLanes;
                } else {
                    reach = Math.max(w, csB.width()) / 2.0 + st.armLength;
                    o.label = BendPlan.arms(st) != null ? "Поворот: полос " + spec.lanes()
                            : "Перекрёсток: " + spec.lanes() + " × " + st.crossLanes + " полос";
                }
                for (Direction d : new Direction[]{Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}) {
                    if (!arm(st, d)) {
                        continue;
                    }
                    boolean alongX = d.getAxis() == Direction.Axis.X;
                    CrossSection ac = alongX ? cs : csB;
                    o.rect(cx, cz, d, 0, reach, -ac.ref() - 0.5, ac.width() - ac.ref() - 0.5);
                }
            }
            default -> {
            }
        }
        return o;
    }

    private OutlinePlan() {}
}
