package com.roadforge.road;

/** Тип одной «колонки» поперечного профиля (вид сверху, поперёк дороги). */
public enum Kind {
    ASPHALT,
    LINE_SOLID,
    LINE_DASH,
    SIDEWALK_L,
    SIDEWALK_R,
    GRASS,
    PARK_EDGE,   // колонка парковки рядом с проезжей частью
    PARK_BAY,    // остальные колонки парковки
    CROSSWALK,   // только в перекрёстках
    WATER        // вода реки или озера (бордюр берега ставится как у проезжей части)
}
