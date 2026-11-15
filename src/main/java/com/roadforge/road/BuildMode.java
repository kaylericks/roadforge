package com.roadforge.road;

public enum BuildMode {
    ROAD("Дорога"),
    INTERSECTION("Перекрёсток");

    public final String title;

    BuildMode(String title) {
        this.title = title;
    }
}
