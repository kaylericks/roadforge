package com.roadforge.road;

/**
 * Параметры дороги.
 * laneWidth - шаг между линиями разметки в блоках (линия рисуется внутри блока-колонки).
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
        boolean endCaps
) {
    public int dashPeriod() {
        return Math.max(1, dashOn + dashGap);
    }
}
