package com.roadforge.road;

import com.roadforge.RoadForge;
import com.roadforge.compat.BlockLookup;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Набор блоков по ролям. На этапе 2 станет редактируемым из меню. */
public record RoadPalette(
        String asphalt,
        String line,
        String sidewalk,
        String grass,
        String lamp,
        String capPlain,
        String capSolid,
        String capDash
) {
    public static final RoadPalette DEFAULT = new RoadPalette(
            "routemod:asphalte",              // низкий асфальт (10/16), как и линии
            "routemod:ligne_continue",
            "routemod:trottoir_stone_brick",
            "minecraft:grass_block",
            "routemod:lampe_1",
            "roadmarks:pente_1",
            "roadmarks:pente_1_marquage",
            "roadmarks:pente_1_pointille"
    );

    private static final Map<String, Optional<Block>> CACHE = new HashMap<>();
    private static final Set<String> WARNED = new HashSet<>();

    public BlockState state(String id, Direction facing) {
        Optional<Block> b = CACHE.computeIfAbsent(id, BlockLookup::find);
        if (b.isEmpty()) {
            if (WARNED.add(id)) {
                RoadForge.LOGGER.warn("RoadForge: block '{}' not found, using stone", id);
            }
            return Blocks.STONE.defaultBlockState();
        }
        return StateUtil.withFacing(b.get().defaultBlockState(), facing);
    }
}
