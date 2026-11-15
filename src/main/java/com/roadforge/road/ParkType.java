package com.roadforge.road;

public enum ParkType {
    NONE("Нет", "Нет", 0, 6),
    PARALLEL("Параллельная", "Парал.", 2, 6),
    ANGLED("Под углом", "Угол", 4, 3),
    PERPENDICULAR("Перпендикулярная", "Перп.", 5, 3);

    public final String title;
    public final String shortTitle;
    public final int depth;
    public final int pitch;

    ParkType(String title, String shortTitle, int depth, int pitch) {
        this.title = title;
        this.shortTitle = shortTitle;
        this.depth = depth;
        this.pitch = pitch;
    }
}
