package com.roadforge.road;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Разметка на дороге под произвольным углом: на участках около 45° ставится диагональный блок линии,
 * на остальных - прямой по ближайшей оси. Диагональный блок facing=NORTH/SOUTH рисует «\\» (северо-запад - юго-восток),
 * EAST/WEST - «/» (северо-восток - юго-запад).
 */
final class LineMarks {
    private static final double TAN_LOW = 0.4142;    // tan 22.5°
    private static final double TAN_HIGH = 2.4142;   // tan 67.5°

    /**
     * @param tx, tz вектор вдоль дороги (x - восток, z - юг)
     * @param s      положение вдоль дороги для пунктира
     */
    static BlockState cell(Kind kind, double tx, double tz, int s, RoadSpec spec, RoadPalette pal) {
        if (kind == Kind.ASPHALT) {
            return pal.asphaltState(Direction.EAST);
        }
        boolean on = kind == Kind.LINE_SOLID || Math.floorMod(s, spec.dashPeriod()) < spec.dashOn();
        if (!on) {
            return pal.asphaltState(Direction.EAST);
        }
        double ax = Math.abs(tx);
        double az = Math.abs(tz);
        double ratio = az / Math.max(1e-9, ax);
        if (ratio > TAN_LOW && ratio < TAN_HIGH) {
            return pal.diagonalState(tx * tz > 0 ? Direction.NORTH : Direction.EAST);
        }
        return pal.lineState(ax >= az ? Direction.EAST : Direction.NORTH);
    }

    private LineMarks() {}
}
