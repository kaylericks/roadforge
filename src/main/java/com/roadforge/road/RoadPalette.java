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

/** Блоки по ролям (бордюр - отдельная роль) + поправки поворота (в четвертях оборота), чтобы подогнать ориентацию моделей. */
public record RoadPalette(
        String asphalt,
        String line,
        String sidewalk,
        String curb,
        String grass,
        String lamp,
        String capPlain,
        String capSolid,
        String capDash,
        String crosswalk,
        String diagonal,
        int turnSidewalk,
        int turnLamp,
        int turnCap,
        int turnLine,
        int turnCross,
        int turnDiag
) {
    public static final RoadPalette DEFAULT = new RoadPalette(
            "routemod:asphalte",              // низкий асфальт (10/16), как и линии
            "routemod:ligne_continue",
            "minecraft:stone_bricks",         // тротуар: обычные блоки
            "routemod:trottoir_stone_brick",  // бордюр: один ряд у проезжей части
            "minecraft:grass_block",
            "routemod:lampe_1",
            "roadmarks:pente_1",
            "roadmarks:pente_1_marquage",
            "roadmarks:pente_1_pointille",
            "routemod:passage_pieton",
            "routemod:ligne_diagonale",
            0, 0, 0, 0, 0, 0
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

    public static Direction turn(Direction d, int turns) {
        Direction r = d;
        for (int i = 0; i < Math.floorMod(turns, 4); i++) {
            r = r.getClockWise();
        }
        return r;
    }

    public BlockState asphaltState(Direction f) {
        return state(asphalt, f);
    }

    public BlockState lineState(Direction f) {
        return state(line, turn(f, turnLine));
    }

    /** Покрытие тротуара (f - направление в сторону проезжей части). */
    public BlockState sidewalkState(Direction f) {
        return state(sidewalk, turn(f, turnSidewalk));
    }

    /** Бордюр: один блок у кромки (f - направление в сторону проезжей части). */
    public BlockState curbState(Direction f) {
        return state(curb, turn(f, turnSidewalk));
    }

    public BlockState grassState() {
        return state(grass, Direction.NORTH);
    }

    public BlockState lampState(Direction f) {
        return state(lamp, turn(f, turnLamp));
    }

    public BlockState capState(String id, Direction f) {
        return state(id, turn(f, turnCap));
    }

    public BlockState crosswalkState(Direction f) {
        return state(crosswalk, turn(f, turnCross));
    }

    public BlockState diagonalState(Direction f) {
        return state(diagonal, turn(f, turnDiag));
    }
}
