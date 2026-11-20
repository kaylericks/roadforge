package com.roadforge.road;

import com.roadforge.build.Foundation;
import net.minecraft.core.BlockPos;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Маркеры опор моста: три колонны поперёк дороги (у краёв проезжей части и по оси), толщиной 2 блока вдоль. */
final class Piers {
    /** Колонки профиля, на которых стоят опоры. */
    static int[] lateral(CrossSection cs) {
        int a = Math.min(cs.roadFirst() + 1, cs.ref());
        int b = cs.ref();
        int c = Math.max(cs.roadLast() - 1, cs.ref());
        return new int[]{a, b, c};
    }

    /** Добавляет маркер опоры под полотном (deck - слой верха дороги, полотно занимает 2 слоя ниже). */
    static void mark(List<Placement> out, Set<Long> seen, int x, int deckY, int z) {
        BlockPos p = new BlockPos(x, deckY - Foundation.MIN_DEPTH - 1, z);
        if (seen.add(p.asLong())) {
            out.add(new Placement(p, Foundation.PIER_MARK));
        }
    }

    /** Маркер светильника тоннеля: Foundation поставит фонарь в потолок, если над колонкой тоннель. */
    static void markLight(List<Placement> out, Set<Long> seen, int x, int deckY, int z) {
        BlockPos p = new BlockPos(x, deckY + 1, z);
        if (seen.add(p.asLong())) {
            out.add(new Placement(p, Foundation.LIGHT_MARK));
        }
    }

    static Set<Long> newSet() {
        return new HashSet<>();
    }

    private Piers() {}
}
