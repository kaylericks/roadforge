package com.roadforge.road;

import java.util.ArrayList;
import java.util.List;

/** Поперечный профиль: колонки слева направо (по ходу движения). */
public record CrossSection(List<Kind> cols, int ref, int lampLeft, int lampRight) {

    public int width() {
        return cols.size();
    }

    public static CrossSection build(RoadSpec s) {
        List<Kind> c = new ArrayList<>();
        int n = s.lanes() / 2;          // полос в одну сторону
        int pave = s.laneWidth();       // колонок асфальта в одной полосе

        repeat(c, Kind.SIDEWALK_L, s.sidewalkWidth());
        repeat(c, Kind.GRASS, s.shoulderWidth());

        if (s.medianWidth() > 0) {
            carriageway(c, s, n, pave);
            repeat(c, Kind.GRASS, s.medianWidth());
            carriageway(c, s, n, pave);
        } else {
            edge(c, s);
            half(c, s, n, pave);
            boundary(c, s, Kind.LINE_SOLID);          // осевая
            if (s.doubleCenter()) {
                boundary(c, s, Kind.LINE_SOLID);      // двойная сплошная
            }
            half(c, s, n, pave);
            edge(c, s);
        }

        repeat(c, Kind.GRASS, s.shoulderWidth());
        repeat(c, Kind.SIDEWALK_R, s.sidewalkWidth());

        boolean lamps = s.sidewalkWidth() > 0 && s.lampSpacing() > 0;
        return new CrossSection(List.copyOf(c), c.size() / 2, lamps ? 0 : -1, lamps ? c.size() - 1 : -1);
    }

    private static void carriageway(List<Kind> c, RoadSpec s, int n, int pave) {
        edge(c, s);
        half(c, s, n, pave);
        edge(c, s);
    }

    /** n полос: pave, разметка, pave, разметка, ..., pave */
    private static void half(List<Kind> c, RoadSpec s, int n, int pave) {
        for (int lane = 0; lane < n; lane++) {
            repeat(c, Kind.ASPHALT, pave);
            if (lane < n - 1) {
                boundary(c, s, Kind.LINE_DASH);
            }
        }
    }

    /** Краевая линия (по умолчанию выключена: на краю сразу бордюр). */
    private static void edge(List<Kind> c, RoadSpec s) {
        if (s.markings() && s.edgeLines()) {
            c.add(Kind.LINE_SOLID);
        }
    }

    private static void boundary(List<Kind> c, RoadSpec s, Kind k) {
        if (s.markings()) {
            c.add(k);
        }
    }

    private static void repeat(List<Kind> c, Kind k, int count) {
        for (int i = 0; i < count; i++) {
            c.add(k);
        }
    }
}
