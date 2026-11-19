package com.roadforge.road;

import java.util.ArrayList;
import java.util.List;

/**
 * Поперечный профиль: колонки слева направо (по ходу движения).
 * ref - центральная колонка проезжей части (осевая / середина разделителя), от неё считаются смещения.
 * roadFirst..roadLast - вся проезжая часть вместе с парковкой.
 * depths - для колонок парковки: расстояние от проезжей части (0 = рядом с ней).
 */
public record CrossSection(List<Kind> cols, List<Integer> depths, int ref, int lampLeft, int lampRight,
                           int roadFirst, int roadLast) {

    public int width() {
        return cols.size();
    }

    private static boolean roadKind(Kind k) {
        return k == Kind.ASPHALT || k == Kind.LINE_SOLID || k == Kind.LINE_DASH
                || k == Kind.PARK_EDGE || k == Kind.PARK_BAY;
    }

    /** Колонка газона разделителя, которая касается проезжей части: на ней ставится бордюр. */
    public boolean medianEdge(int i) {
        if (cols.get(i) != Kind.GRASS || i < roadFirst || i > roadLast) {
            return false;
        }
        return (i > 0 && roadKind(cols.get(i - 1))) || (i + 1 < cols.size() && roadKind(cols.get(i + 1)));
    }

    public static CrossSection build(RoadSpec s) {
        List<Kind> c = new ArrayList<>();
        List<Integer> d = new ArrayList<>();
        int n = s.lanes() / 2;
        int pave = s.laneWidth();

        add(c, d, Kind.SIDEWALK_L, s.sidewalkWidth());
        add(c, d, Kind.GRASS, s.shoulderWidth());

        int roadFirst = c.size();
        boolean parking = s.parkType() != ParkType.NONE;
        int pd = Math.max(1, s.parkDepth());
        if (parking && s.parkLeft()) {
            for (int k = pd - 1; k >= 0; k--) {
                c.add(k == 0 ? Kind.PARK_EDGE : Kind.PARK_BAY);
                d.add(k);
            }
        }

        int coreFirst = c.size();
        if (s.medianWidth() > 0) {
            carriageway(c, d, s, n, pave);
            add(c, d, Kind.GRASS, s.medianWidth());
            carriageway(c, d, s, n, pave);
        } else {
            edge(c, d, s);
            half(c, d, s, n, pave);
            boundary(c, d, s, Kind.LINE_SOLID);
            if (s.doubleCenter() && s.markings()) {
                add(c, d, Kind.ASPHALT, 1);              // двойная сплошная: линия, зазор, линия
                boundary(c, d, s, Kind.LINE_SOLID);
            }
            half(c, d, s, n, pave);
            edge(c, d, s);
        }
        int coreLast = c.size() - 1;

        if (parking && s.parkRight()) {
            for (int k = 0; k < pd; k++) {
                c.add(k == 0 ? Kind.PARK_EDGE : Kind.PARK_BAY);
                d.add(k);
            }
        }
        int roadLast = c.size() - 1;

        add(c, d, Kind.GRASS, s.shoulderWidth());
        add(c, d, Kind.SIDEWALK_R, s.sidewalkWidth());

        boolean lamps = s.sidewalkWidth() > 0 && s.lampSpacing() > 0;
        return new CrossSection(List.copyOf(c), List.copyOf(d), (coreFirst + coreLast) / 2,
                lamps ? 0 : -1, lamps ? c.size() - 1 : -1, roadFirst, roadLast);
    }

    private static void carriageway(List<Kind> c, List<Integer> d, RoadSpec s, int n, int pave) {
        edge(c, d, s);
        half(c, d, s, n, pave);
        edge(c, d, s);
    }

    /** n полос: pave, разметка, pave, разметка, ..., pave */
    private static void half(List<Kind> c, List<Integer> d, RoadSpec s, int n, int pave) {
        for (int lane = 0; lane < n; lane++) {
            add(c, d, Kind.ASPHALT, pave);
            if (lane < n - 1) {
                boundary(c, d, s, Kind.LINE_DASH);
            }
        }
    }

    private static void edge(List<Kind> c, List<Integer> d, RoadSpec s) {
        if (s.markings() && s.edgeLines()) {
            add(c, d, Kind.LINE_SOLID, 1);
        }
    }

    /** Колонка между полосами: линия, а без разметки - обычный асфальт (чтобы ширина не менялась). */
    private static void boundary(List<Kind> c, List<Integer> d, RoadSpec s, Kind k) {
        add(c, d, s.markings() ? k : Kind.ASPHALT, 1);
    }

    private static void add(List<Kind> c, List<Integer> d, Kind k, int count) {
        for (int i = 0; i < count; i++) {
            c.add(k);
            d.add(0);
        }
    }
}
