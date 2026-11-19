package com.roadforge.road;

import java.util.ArrayList;
import java.util.List;

/**
 * Профиль высоты дороги: подъёмы и спуски.
 * Поверхность дороги (асфальт 10/16) идёт слоями; между слоями стоит один блок-пандус (pente: 1 блок длиной,
 * поднимается на 10/16, начинается на 6/16 выше нижней дороги). Так любой ступенек не больше 6/16 блока
 * (машины проезжают), а уклон не круче «слой за 2 блока» (0,5 на блок).
 */
public final class Grade {
    /** Максимальный уклон: слоёв высоты на блок длины. */
    public static final double MAX_GRADE = 0.5;

    /** Клетка-пандус: up - подъём по ходу пути, before/after - слои до и после. */
    public static final class Ramp {
        public final boolean up;
        public final int before;
        public final int after;

        Ramp(boolean up, int before, int after) {
            this.up = up;
            this.before = before;
            this.after = after;
        }
    }

    private final int startLayer;
    private final List<Double> bounds = new ArrayList<>();
    private final List<Integer> dirs = new ArrayList<>();

    private Grade(int startLayer) {
        this.startLayer = startLayer;
    }

    /**
     * @param s положения контрольных точек вдоль пути (по возрастанию)
     * @param y слои высоты в этих точках
     */
    public static Grade of(double[] s, int[] y) {
        Grade g = new Grade(y[0]);
        for (int i = 0; i + 1 < s.length; i++) {
            int ya = y[i];
            int yb = y[i + 1];
            if (ya == yb) {
                continue;
            }
            int step = yb > ya ? 1 : -1;
            // пересечения уровней k + 0,5: слой меняется, когда высота проходит середину
            for (int level = ya; level != yb; level += step) {
                double t = level + 0.5 * step;
                double pos = s[i] + (t - ya) / (double) (yb - ya) * (s[i + 1] - s[i]);
                g.bounds.add(pos);
                g.dirs.add(step);
            }
        }
        return g;
    }

    /** Текст ошибки, если участок круче допустимого, иначе null. */
    public static String check(double[] s, int[] y) {
        for (int i = 0; i + 1 < s.length; i++) {
            int dy = Math.abs(y[i + 1] - y[i]);
            double len = s[i + 1] - s[i];
            if (dy > 0 && len < dy / MAX_GRADE - 1e-6) {
                return "Слишком крутой уклон: подъём на " + dy + " бл. за " + (int) Math.floor(len)
                        + " бл. длины, нужно не меньше " + (int) Math.ceil(dy / MAX_GRADE)
                        + " (макс. слой за 2 блока).";
            }
        }
        return null;
    }

    /** Слой высоты для положения s вдоль пути (на клетке-пандусе - верхний из двух слоёв). */
    public int layerAt(double s) {
        int layer = startLayer;
        for (int k = 0; k < bounds.size(); k++) {
            double b = bounds.get(k);
            if (s >= b + 1) {
                layer += dirs.get(k);
            } else if (s >= b) {
                return Math.max(layer, layer + dirs.get(k));
            } else {
                break;
            }
        }
        return layer;
    }

    /** Пандус на этом положении или null. */
    public Ramp rampAt(double s) {
        int layer = startLayer;
        for (int k = 0; k < bounds.size(); k++) {
            double b = bounds.get(k);
            if (s >= b + 1) {
                layer += dirs.get(k);
            } else if (s >= b) {
                int d = dirs.get(k);
                return new Ramp(d > 0, layer, layer + d);
            } else {
                break;
            }
        }
        return null;
    }

    public boolean flat() {
        return bounds.isEmpty();
    }
}
