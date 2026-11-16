package com.roadforge.road;

/** Роли блоков, которые игрок выбирает в меню. */
public enum Role {
    ASPHALT("Асфальт"),
    LINE("Разметка"),
    SIDEWALK("Тротуар"),
    CURB("Бордюр"),
    GRASS("Газон"),
    LAMP("Фонарь"),
    CROSSWALK("Зебра"),
    DIAGONAL("Диагональ");

    public final String title;

    Role(String title) {
        this.title = title;
    }
}
