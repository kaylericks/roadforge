package com.roadforge.player;

import com.roadforge.road.RoadSettings;
import com.roadforge.road.RoadSpec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Выбранные точки и настройки каждого игрока на сервере. */
public final class PlayerRoadState {
    private static final Map<UUID, PlayerRoadState> STATES = new ConcurrentHashMap<>();

    public BlockPos a;
    public BlockPos b;
    /** Точки режима «Путь» по порядку. */
    public final List<BlockPos> path = new ArrayList<>();
    public RoadSettings settings = new RoadSettings();

    public static PlayerRoadState of(ServerPlayer p) {
        return STATES.computeIfAbsent(p.getUUID(), id -> new PlayerRoadState());
    }

    public static void clearAll() {
        STATES.clear();
    }

    public RoadSpec spec() {
        return settings.toSpec();
    }

    public boolean hasBoth() {
        return a != null && b != null;
    }

    public Direction direction() {
        return directionOf(a, b);
    }

    public int length() {
        return lengthOf(a, b);
    }

    /** Направление по доминирующей оси A -> B. */
    public static Direction directionOf(BlockPos a, BlockPos b) {
        if (a == null || b == null) {
            return Direction.EAST;
        }
        int dx = b.getX() - a.getX();
        int dz = b.getZ() - a.getZ();
        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx >= 0 ? Direction.EAST : Direction.WEST;
        }
        return dz >= 0 ? Direction.SOUTH : Direction.NORTH;
    }

    public static int lengthOf(BlockPos a, BlockPos b) {
        if (a == null || b == null) {
            return 0;
        }
        return Math.max(Math.abs(b.getX() - a.getX()), Math.abs(b.getZ() - a.getZ())) + 1;
    }
}
