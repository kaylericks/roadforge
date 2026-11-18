package com.roadforge.road;

public enum BuildMode {
    ROAD("Дорога"),
    INTERSECTION("Перекрёсток"),
    ROUNDABOUT("Кольцо"),
    PATH("Путь");

    public final String title;

    BuildMode(String title) {
        this.title = title;
    }

    /** Режимы, где игрок отмечает одну точку (центр), а не два конца дороги. */
    public boolean isCentered() {
        return this == INTERSECTION || this == ROUNDABOUT;
    }
}
