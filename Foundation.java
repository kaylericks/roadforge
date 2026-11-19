package com.roadforge.build;

import com.roadforge.road.Placement;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Подложка из чёрного бетона под постройкой и опоры мостов.
 * Под каждой колонкой сначала MIN_DEPTH слоёв бетона. Дальше смотрим на землю под ней:
 * - пустота не глубже BRIDGE_GAP - засыпаем до земли (насыпь на подъёмах, мелкая вода);
 * - глубже - это мост: ниже полотна ничего не льём, а опоры ставятся по маркерам плана
 *   ({@link #PIER_MARK}), каждая опора идёт вниз до твёрдой земли или дна реки.
 */
public final class Foundation {
    /** Минимум слоёв бетона под каждой колонкой (толщина полотна моста). */
    public static final int MIN_DEPTH = 2;
    /** Максимум слоёв насыпи. */
    public static final int MAX_DEPTH = 48;
    /** Если пустота под полотном глубже - строим мост (без насыпи). */
    public static final int BRIDGE_GAP = 3;
    /** Предельная глубина поиска земли (для опор и измерения пустоты). */
    public static final int MAX_SCAN = 160;

    /** Маркер плана: «здесь начинается опора моста» (в мир не ставится). */
    public static final BlockState PIER_MARK = Blocks.STRUCTURE_VOID.defaultBlockState();

    private static final BlockState BASE = Blocks.BLACK_CONCRETE.defaultBlockState();
    private static final BlockState PIER = Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState();

    private static boolean empty(ServerLevel level, BlockPos pos) {
        BlockState st = level.getBlockState(pos);
        return st.isAir() || st.canBeReplaced() || !st.getFluidState().isEmpty();
    }

    /** Возвращает список: сначала подложка и опоры, потом исходный план (без маркеров). */
    public static List<Placement> withBase(ServerLevel level, List<Placement> plan) {
        Map<Long, BlockPos> lowest = new HashMap<>();
        Set<Long> taken = new HashSet<>();
        List<BlockPos> piers = new ArrayList<>();
        List<Placement> clean = new ArrayList<>(plan.size());
        for (Placement p : plan) {
            BlockPos pos = p.pos();
            if (p.state() == PIER_MARK || p.state().equals(PIER_MARK)) {
                piers.add(pos);
                continue;
            }
            clean.add(p);
            taken.add(pos.asLong());
            if (p.state().isAir()) {
                continue;
            }
            long key = ((long) pos.getX() << 32) ^ (pos.getZ() & 0xFFFFFFFFL);
            lowest.merge(key, pos, (a, b) -> a.getY() <= b.getY() ? a : b);
        }

        List<Placement> out = new ArrayList<>(clean.size() + lowest.size() * (MIN_DEPTH + 1));
        for (BlockPos top : lowest.values()) {
            // полотно: MIN_DEPTH слоёв
            int placed = 0;
            for (int d = 1; d <= MIN_DEPTH; d++) {
                BlockPos pos = top.below(d);
                if (pos.getY() < level.getMinBuildHeight()) {
                    break;
                }
                if (!taken.contains(pos.asLong())) {
                    out.add(new Placement(pos, BASE));
                }
                placed = d;
            }
            // пустота под полотном
            int gap = 0;
            for (int d = MIN_DEPTH + 1; d <= MIN_DEPTH + MAX_SCAN; d++) {
                BlockPos pos = top.below(d);
                if (pos.getY() < level.getMinBuildHeight() || !level.isLoaded(pos) || !empty(level, pos)) {
                    break;
                }
                gap++;
            }
            if (gap > BRIDGE_GAP) {
                continue;                       // мост: дальше только опоры
            }
            for (int d = MIN_DEPTH + 1; d <= MIN_DEPTH + Math.min(gap, MAX_DEPTH); d++) {
                BlockPos pos = top.below(d);
                if (!taken.contains(pos.asLong())) {
                    out.add(new Placement(pos, BASE));
                }
            }
        }

        // опоры: от маркера вниз до земли
        Set<Long> pierDone = new HashSet<>();
        for (BlockPos start : piers) {
            for (int d = 0; d <= MAX_SCAN; d++) {
                BlockPos pos = start.below(d);
                if (pos.getY() < level.getMinBuildHeight() || !level.isLoaded(pos)) {
                    break;
                }
                if (d > 0 && !empty(level, pos)) {
                    break;                      // дошли до земли
                }
                if (pierDone.add(pos.asLong()) && !taken.contains(pos.asLong())) {
                    out.add(new Placement(pos, PIER));
                }
            }
        }
        out.addAll(clean);
        return out;
    }

    private Foundation() {}
}
