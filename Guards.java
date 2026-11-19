package com.roadforge.road;

/** Где ставить ограждения: на бордюре по краю разделителя и/или по краям проезжей части. */
final class Guards {
    /** guard: 0 нет, 1 разделитель, 2 края, 3 везде. */
    static boolean wants(int guard, Surfaces.Cell c) {
        if (guard <= 0 || c == null || !c.paving || !c.curb || c.island) {
            return false;
        }
        if (c.medianEdge) {
            return (guard & 1) != 0;
        }
        return (guard & 2) != 0;
    }

    private Guards() {}
}
