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
    /** Маркер плана: «здесь светильник тоннеля» (в мир не ставится). */
    public static final BlockState LIGHT_MARK = Blocks.BARRIER.defaultBlockState();

    /**
     * Высота тоннеля внутри (0 - без облицовки). Задаётся на время одной постройки перед постановкой в очередь:
     * там, где над дорогой твёрдая земля, делается проход, облицовка стен и потолка и светильники.
     */
    public static volatile int tunnelHeight = 0;

    private static final BlockState BASE = Blocks.BLACK_CONCRETE.defaultBlockState();
    private static final BlockState PIER = Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState();
    private static final BlockState LINING = Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState();
    private static final BlockState LANTERN = Blocks.SEA_LANTERN.defaultBlockState();
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    private static boolean empty(ServerLevel level, BlockPos pos) {
        BlockState st = level.getBlockState(pos);
        return st.isAir() || st.canBeReplaced() || !st.getFluidState().isEmpty();
    }

    /** Возвращает список: сначала подложка и опоры, потом исходный план (без маркеров). */
    public static List<Placement> withBase(ServerLevel level, List<Placement> plan) {
        Map<Long, BlockPos> lowest = new HashMap<>();
        Set<Long> taken = new HashSet<>();
        List<BlockPos> piers = new ArrayList<>();
        List<BlockPos> lights = new ArrayList<>();
        List<Placement> clean = new ArrayList<>(plan.size());
        for (Placement p : plan) {
            BlockPos pos = p.pos();
            if (p.state() == PIER_MARK || p.state().equals(PIER_MARK)) {
                piers.add(pos);
                continue;
            }
            if (p.state() == LIGHT_MARK || p.state().equals(LIGHT_MARK)) {
                lights.add(pos);
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
        if (tunnelHeight > 0) {
            addTunnel(level, lowest, taken, lights, out);
        }
        out.addAll(clean);
        return out;
    }

    private static long colKey(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    /** Облицовка тоннеля там, где над дорогой твёрдая земля: проход, стены, потолок, светильники. */
    private static void addTunnel(ServerLevel level, Map<Long, BlockPos> lowest, Set<Long> taken,
                                  List<BlockPos> lights, List<Placement> out) {
        int hgt = tunnelHeight;
        Set<Long> covered = new HashSet<>();
        for (Map.Entry<Long, BlockPos> e : lowest.entrySet()) {
            BlockPos top = e.getValue();
            BlockPos ceil = new BlockPos(top.getX(), top.getY() + hgt + 1, top.getZ());
            if (ceil.getY() < level.getMaxBuildHeight() && level.isLoaded(ceil) && !empty(level, ceil)) {
                covered.add(e.getKey());
            }
        }
        int[][] around = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        Set<Long> done = new HashSet<>();
        for (long key : covered) {
            BlockPos top = lowest.get(key);
            int x = top.getX();
            int z = top.getZ();
            int deck = top.getY();
            for (int y = deck + 1; y <= deck + hgt; y++) {
                BlockPos pos = new BlockPos(x, y, z);
                if (!taken.contains(pos.asLong())) {
                    out.add(new Placement(pos, AIR));
                }
            }
            BlockPos ceil = new BlockPos(x, deck + hgt + 1, z);
            if (done.add(ceil.asLong())) {
                out.add(new Placement(ceil, LINING));
            }
            for (int[] d : around) {
                int nx = x + d[0];
                int nz = z + d[1];
                if (lowest.containsKey(colKey(nx, nz))) {
                    continue;                         // соседняя колонка - тоже дорога
                }
                for (int y = deck; y <= deck + hgt + 1; y++) {
                    BlockPos np = new BlockPos(nx, y, nz);
                    if (level.isLoaded(np) && !empty(level, np) && done.add(np.asLong())) {
                        out.add(new Placement(np, LINING));    // стена только там, где здесь земля
                    }
                }
            }
        }
        for (BlockPos m : lights) {
            long key = colKey(m.getX(), m.getZ());
            BlockPos top = lowest.get(key);
            if (top != null && covered.contains(key)) {
                out.add(new Placement(new BlockPos(m.getX(), top.getY() + hgt + 1, m.getZ()), LANTERN));
            }
        }
    }

    private Foundation() {}
}
