package com.roadforge.build;

import com.roadforge.RoadConfig;
import com.roadforge.player.PlayerRoadState;
import com.roadforge.road.Placement;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Очередь построек (по N блоков за тик) и стек отмены для каждого игрока. */
public final class BuildManager {
    private static final int UNDO_LIMIT = 10;
    private static final int SET_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    private static final ArrayDeque<Job> QUEUE = new ArrayDeque<>();
    private static final Map<UUID, ArrayDeque<UndoEntry>> UNDO = new HashMap<>();

    private record UndoEntry(ServerLevel level, List<BlockPos> pos, List<BlockState> old) {}

    private static final class Job {
        final ServerLevel level;
        final UUID owner;
        final List<Placement> list;
        final boolean record;
        final String label;
        int index = 0;
        int changed = 0;
        int skipped = 0;
        final List<BlockPos> undoPos = new ArrayList<>();
        final List<BlockState> undoOld = new ArrayList<>();

        Job(ServerLevel level, UUID owner, List<Placement> list, boolean record, String label) {
            this.level = level;
            this.owner = owner;
            this.list = list;
            this.record = record;
            this.label = label;
        }
    }

    public static void enqueue(ServerLevel level, UUID owner, List<Placement> list, boolean record, String label) {
        QUEUE.add(new Job(level, owner, list, record, label));
    }

    public static boolean undo(ServerPlayer player) {
        ArrayDeque<UndoEntry> stack = UNDO.get(player.getUUID());
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        UndoEntry e = stack.pop();
        List<Placement> list = new ArrayList<>(e.pos().size());
        for (int i = e.pos().size() - 1; i >= 0; i--) {
            list.add(new Placement(e.pos().get(i), e.old().get(i)));
        }
        enqueue(e.level(), player.getUUID(), list, false, "Отмена");
        return true;
    }

    public static int pending() {
        return QUEUE.size();
    }

    public static void onServerTick(TickEvent.ServerTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END) {
            return;
        }
        Job job = QUEUE.peek();
        if (job == null) {
            return;
        }
        int budget = RoadConfig.BLOCKS_PER_TICK.get();
        while (budget > 0 && job.index < job.list.size()) {
            Placement p = job.list.get(job.index++);
            if (!job.level.isLoaded(p.pos())) {
                job.skipped++;
                continue;
            }
            BlockState old = job.level.getBlockState(p.pos());
            if (old == p.state()) {
                continue;
            }
            if (job.record) {
                job.undoPos.add(p.pos());
                job.undoOld.add(old);
            }
            job.level.setBlock(p.pos(), p.state(), SET_FLAGS);
            job.changed++;
            budget--;
        }
        if (job.index >= job.list.size()) {
            QUEUE.poll();
            finish(job);
        }
    }

    private static void finish(Job job) {
        if (job.record && job.changed > 0) {
            ArrayDeque<UndoEntry> st = UNDO.computeIfAbsent(job.owner, k -> new ArrayDeque<>());
            st.push(new UndoEntry(job.level, job.undoPos, job.undoOld));
            while (st.size() > UNDO_LIMIT) {
                st.removeLast();
            }
        }
        ServerPlayer pl = job.level.getServer().getPlayerList().getPlayer(job.owner);
        if (pl != null) {
            String extra = job.skipped > 0 ? " (пропущено в незагруженных чанках: " + job.skipped + ")" : "";
            pl.sendSystemMessage(Component.literal("§a" + job.label + ": изменено блоков " + job.changed + extra));
        }
    }

    public static void onServerStopping(ServerStoppingEvent ev) {
        QUEUE.clear();
        UNDO.clear();
        PlayerRoadState.clearAll();
    }

    private BuildManager() {}
}
