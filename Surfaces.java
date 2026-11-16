package com.roadforge.road;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Общая логика покрытия: клетка плана и выбор «бордюр / покрытие тротуара».
 * Правило простое: клетка тротуара, которая касается проезжей части, - один блок бордюра;
 * все остальные клетки тротуара - обычное покрытие. Рядом с зеброй бордюр убирается (съезд).
 */
final class Surfaces {

    /** Одна клетка плана. */
    static final class Cell {
        Kind kind;            // для проезжей части - её тип; для тротуара - SIDEWALK_L
        BlockState state;     // готовый блок (не для тротуара)
        Direction face;       // для тротуара: в какую сторону проезжая часть
        boolean paving;       // тротуар (бордюр или покрытие - решает resolve)
        boolean island;       // клетка острова: внутри вместо покрытия - газон
        boolean forceCurb;    // всегда бордюр (кольцо вокруг острова)
        boolean curb;         // итог: бордюр
        int tag;              // служебное поле: положение вдоль дороги (для фонарей)
        int lat = -1;         // служебное поле: номер колонки профиля
        int lamp = 0;         // служебное поле: 1 - левая колонка фонарей, 2 - правая

        static Cell road(Kind kind, BlockState state) {
            Cell c = new Cell();
            c.kind = kind;
            c.state = state;
            return c;
        }

        static Cell paving(Direction face) {
            Cell c = new Cell();
            c.kind = Kind.SIDEWALK_L;
            c.face = face;
            c.paving = true;
            return c;
        }
    }

    static boolean isRoad(Kind k) {
        return k == Kind.ASPHALT || k == Kind.LINE_SOLID || k == Kind.LINE_DASH
                || k == Kind.PARK_EDGE || k == Kind.PARK_BAY;
    }

    static boolean isRoadOrZebra(Kind k) {
        return isRoad(k) || k == Kind.CROSSWALK;
    }

    /**
     * Определяет бордюры. di - мировое направление, в котором растёт первый индекс сетки,
     * dj - второй индекс.
     */
    static void resolve(Cell[][] g, Direction di, Direction dj) {
        Direction[] dirs = {di, di.getOpposite(), dj, dj.getOpposite()};
        int[] stepI = {1, -1, 0, 0};
        int[] stepJ = {0, 0, 1, -1};
        for (int i = 0; i < g.length; i++) {
            for (int j = 0; j < g[i].length; j++) {
                Cell c = g[i][j];
                if (c == null || !c.paving) {
                    continue;
                }
                Direction toRoad = null;
                boolean ramp = false;
                for (int k = 0; k < 4; k++) {
                    int ni = i + stepI[k];
                    int nj = j + stepJ[k];
                    if (ni < 0 || nj < 0 || ni >= g.length || nj >= g[ni].length) {
                        continue;
                    }
                    Cell n = g[ni][nj];
                    if (n == null) {
                        continue;
                    }
                    if (n.kind == Kind.CROSSWALK) {
                        ramp = true;
                        if (toRoad == null) {
                            toRoad = dirs[k];
                        }
                    } else if (isRoad(n.kind) && toRoad == null) {
                        toRoad = dirs[k];
                    }
                }
                if (toRoad != null) {
                    c.face = toRoad;
                }
                c.curb = c.forceCurb || (toRoad != null && !ramp);
            }
        }
    }

    /** Итоговый блок клетки. */
    static BlockState finish(Cell c, RoadPalette pal) {
        if (!c.paving) {
            return c.state;
        }
        if (c.curb) {
            return pal.curbState(c.face);
        }
        if (c.island) {
            return pal.grassState();
        }
        return pal.sidewalkState(c.face);
    }

    private Surfaces() {}
}
