package com.roadforge.road;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Шаблоны дорог: выставляют стартовые значения параметров. */
public enum RoadPreset {
    CITY("city", "Городская"),
    ARTERIAL("arterial", "Магистраль"),
    DACHA("dacha", "Дачная");

    public final String key;
    public final String title;

    RoadPreset(String key, String title) {
        this.key = key;
        this.title = title;
    }

    public static List<String> keys() {
        return Arrays.stream(values()).map(p -> p.key).toList();
    }

    public static Optional<RoadPreset> byKey(String key) {
        String k = key.toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(p -> p.key.equals(k)).findFirst();
    }

    /** Число полос: чётное, от 2 до 6. */
    public static int clampLanes(int n) {
        int v = Math.max(2, Math.min(6, n));
        return v - (v % 2);
    }

    public RoadSpec create(int requestedLanes) {
        int l = clampLanes(requestedLanes);
        ParkType pt = ParkType.NONE;
        return switch (this) {
            //                preset      lanes           lw  marks edge   dblCtr   median sw shd lamps on gap caps  park  cross
            case CITY ->     new RoadSpec("city",     l,            3, true,  false, l >= 4, 0, 3, 0, 12, 1, 3, true, pt, false, false, 2, 6, 0, 0);
            case ARTERIAL -> new RoadSpec("arterial", Math.max(4, l), 3, true,  false, false,  3, 3, 0, 16, 1, 3, true, pt, false, false, 2, 6, 0, 0);
            case DACHA ->    new RoadSpec("dacha",    2,            3, false, false, false,  0, 0, 1, 0,  1, 3, true, pt, false, false, 2, 6, 0, 0);
        };
    }
}
