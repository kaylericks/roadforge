package com.roadforge;

import net.minecraftforge.common.ForgeConfigSpec;

/** Серверные лимиты. Читать значения можно только после загрузки конфига (в командах/пакетах). */
public final class RoadConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.IntValue MAX_ROAD_LENGTH;
    public static final ForgeConfigSpec.IntValue MAX_BLOCKS_PER_BUILD;
    public static final ForgeConfigSpec.IntValue BLOCKS_PER_TICK;
    public static final ForgeConfigSpec.IntValue PERMISSION_LEVEL;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.push("limits");
        MAX_ROAD_LENGTH = b.comment("Максимальная длина одной дороги в блоках")
                .defineInRange("maxRoadLength", 512, 8, 4096);
        MAX_BLOCKS_PER_BUILD = b.comment("Максимум блоков, которые можно поставить за одну постройку")
                .defineInRange("maxBlocksPerBuild", 200_000, 1_000, 5_000_000);
        BLOCKS_PER_TICK = b.comment("Сколько блоков ставить за тик (чтобы не лагало)")
                .defineInRange("blocksPerTick", 2_000, 100, 50_000);
        b.pop();

        b.push("permissions");
        PERMISSION_LEVEL = b.comment("Необходимый уровень прав (0 - все, 2 - операторы)")
                .defineInRange("permissionLevel", 2, 0, 4);
        b.pop();

        SPEC = b.build();
    }

    /** Уровень прав; пока конфиг не загружен - 2. */
    public static int permission() {
        try {
            return PERMISSION_LEVEL.get();
        } catch (IllegalStateException e) {
            return 2;
        }
    }

    private RoadConfig() {}
}
