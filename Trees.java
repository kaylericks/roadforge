package com.roadforge.road;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/** Простое дерево из ванильных блоков: ствол 4 блока и шапка листвы (листья не опадают). */
public final class Trees {
    private static final BlockState LOG = Blocks.OAK_LOG.defaultBlockState();
    private static final BlockState LEAVES = Blocks.OAK_LEAVES.defaultBlockState()
            .setValue(LeavesBlock.PERSISTENT, true);

    /** ground - блок газона, на котором стоит дерево. */
    public static void plant(List<Placement> out, BlockPos ground) {
        for (int h = 1; h <= 4; h++) {
            out.add(new Placement(ground.above(h), LOG));
        }
        for (int dy = 3; dy <= 6; dy++) {
            int r = dy <= 4 ? 2 : (dy == 5 ? 1 : 1);
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    boolean corner = Math.abs(dx) == r && Math.abs(dz) == r;
                    if (dy <= 4 && dx == 0 && dz == 0) {
                        continue;                         // ствол
                    }
                    if ((dy <= 4 && corner && r == 2) || (dy == 6 && Math.abs(dx) + Math.abs(dz) > 1)) {
                        continue;                         // скруглённые углы и верхушка-крестик
                    }
                    out.add(new Placement(new BlockPos(ground.getX() + dx, ground.getY() + dy, ground.getZ() + dz), LEAVES));
                }
            }
        }
    }

    private Trees() {}
}
