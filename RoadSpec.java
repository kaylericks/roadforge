package com.roadforge.road;

/**
 * Параметры дороги.
 * laneWidth - колонок асфальта в одной полосе; между полосами колонка разметки.
 * treeSpacing - шаг деревьев на газоне (разделитель, обочина), 0 - без деревьев.
 * pierSpacing - шаг опор моста вдоль дороги, 0 - без опор (мост определяется по пустоте под дорогой).
 * guard - ограждения: 0 нет, 1 по разделителю, 2 по краям, 3 везде.
 * crossEvery - шаг пешеходных переходов на дороге в блоках (0 - без переходов).
 */
public record RoadSpec(
        String preset,
        int lanes,
        int laneWidth,
        boolean markings,
        boolean edgeLines,
        boolean doubleCenter,
        int medianWidth,
        int sidewalkWidth,
        int shoulderWidth,
        int lampSpacing,
        int dashOn,
        int dashGap,
        boolean endCaps,
        ParkType parkType,
        boolean parkLeft,
        boolean parkRight,
        int parkDepth,
        int parkPitch,
        int crossEvery,
        int treeSpacing,
        int guard,
        int pierSpacing
) {
    public int dashPeriod() {
        return Math.max(1, dashOn + dashGap);
    }

    public RoadSpec withEndCaps(boolean caps) {
        return new RoadSpec(preset, lanes, laneWidth, markings, edgeLines, doubleCenter, medianWidth,
                sidewalkWidth, shoulderWidth, lampSpacing, dashOn, dashGap, caps,
                parkType, parkLeft, parkRight, parkDepth, parkPitch, crossEvery, treeSpacing, guard, pierSpacing);
    }

    public RoadSpec withLanes(int l) {
        return new RoadSpec(preset, l, laneWidth, markings, edgeLines, doubleCenter, medianWidth,
                sidewalkWidth, shoulderWidth, lampSpacing, dashOn, dashGap, endCaps,
                parkType, parkLeft, parkRight, parkDepth, parkPitch, crossEvery, treeSpacing, guard, pierSpacing);
    }
}
