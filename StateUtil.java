package com.roadforge.road;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

public final class StateUtil {
    /** Ставит свойство "facing" (если оно есть и допускает такое значение). */
    @SuppressWarnings("unchecked")
    public static BlockState withFacing(BlockState state, Direction dir) {
        for (Property<?> p : state.getProperties()) {
            if (p.getName().equals("facing") && p.getValueClass() == Direction.class) {
                Property<Direction> dp = (Property<Direction>) p;
                if (dp.getPossibleValues().contains(dir)) {
                    return state.setValue(dp, dir);
                }
            }
        }
        return state;
    }

    private StateUtil() {}
}
