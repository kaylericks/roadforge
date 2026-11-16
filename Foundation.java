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
 * Подложка из чёрного бетона под всей постройкой (дорога, тротуары, газон, парковки, перекрёстки).
 * Работает над готовым списком блоков, поэтому не зависит от того, кто его сгенерировал.
 */
public final class Foundation {
    /** Минимум слоёв бетона под каждой колонкой. */
    public static final int MIN_DEPTH = 2;
    /** Максимум слоёв: дальше вниз бетон не льём, даже если там пустота. */
    public static final int MAX_DEPTH = 48;

    private static final BlockState BASE = Blocks.BLACK_CONCRETE.defaultBlockState();

    /** Возвращает список: сначала подложка, потом исходный план. */
    public static List<Placement> withBase(ServerLevel level, List<Placement> plan) {
        // для каждой колонки (x, z) берём самый нижний непустой блок плана - это и есть поверхность
        Map<Long, BlockPos> lowest = new HashMap<>();
        Set<Long> taken = new HashSet<>();
        for (Placement p : plan) {
            BlockPos pos = p.pos();
            taken.add(pos.asLong());
            if (p.state().isAir()) {
                continue;
            }
            long key = ((long) pos.getX() << 32) ^ (pos.getZ() & 0xFFFFFFFFL);
            lowest.merge(key, pos, (a, b) -> a.getY() <= b.getY() ? a : b);
        }

        List<Placement> out = new ArrayList<>(plan.size() + lowest.size() * (MIN_DEPTH + 1));
        for (BlockPos top : lowest.values()) {
            for (int d = 1; d <= MAX_DEPTH; d++) {
                BlockPos pos = top.below(d);
                if (pos.getY() < level.getMinBuildHeight()) {
                    break;
                }
                if (taken.contains(pos.asLong())) {
                    continue; // этот блок план уже ставит сам
                }
                if (d > MIN_DEPTH) {
                    if (!level.isLoaded(pos)) {
                        break;
                    }
                    BlockState st = level.getBlockState(pos);
                    boolean empty = st.isAir() || st.canBeReplaced() || !st.getFluidState().isEmpty();
                    if (!empty) {
                        break; // дошли до твёрдой земли
                    }
                }
                out.add(new Placement(pos, BASE));
            }
        }
        out.addAll(plan);
        return out;
    }

    private Foundation() {}
}
