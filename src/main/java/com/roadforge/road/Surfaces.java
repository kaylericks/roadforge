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
        boolean ramp;         // рядом зебра: бордюра нет (съезд)
        int shape;            // вид бордюра: STRAIGHT / OUTER / INNER / DIAGO
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

    static final int STRAIGHT = 0;
    static final int OUTER = 1;     // выпуклый угол тротуара (дорога с двух соседних сторон)
    static final int INNER = 2;     // вогнутый угол (дорога только по диагонали)
    static final int DIAGO = 3;     // 45° участок (цепочка выпуклых углов по диагонали)

    private static final Direction[] HORIZ = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

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
                c.ramp = ramp;
                c.curb = c.forceCurb || (toRoad != null && !ramp);
                c.shape = STRAIGHT;
            }
        }
        shapes(g, di, dj);
    }

    private static int[] step(Direction d, Direction di, Direction dj) {
        if (d == di) {
            return new int[]{1, 0};
        }
        if (d == di.getOpposite()) {
            return new int[]{-1, 0};
        }
        if (d == dj) {
            return new int[]{0, 1};
        }
        return new int[]{0, -1};
    }

    private static Cell at(Cell[][] g, int i, int j) {
        if (i < 0 || j < 0 || i >= g.length || j >= g[i].length) {
            return null;
        }
        return g[i][j];
    }

    private static int bit(Direction d) {
        for (int k = 0; k < 4; k++) {
            if (HORIZ[k] == d) {
                return 1 << k;
            }
        }
        return 0;
    }

    /** Битовая маска соседей-проезжей-части по четырём сторонам. */
    private static int roadMask(Cell[][] g, int i, int j, Direction di, Direction dj) {
        int m = 0;
        for (Direction d : HORIZ) {
            int[] st = step(d, di, dj);
            Cell n = at(g, i + st[0], j + st[1]);
            if (n != null && isRoad(n.kind)) {
                m |= bit(d);
            }
        }
        return m;
    }

    /** Если дорога ровно с двух соседних сторон (a и a.cw) - возвращает a. */
    private static Direction cornerFacing(int m) {
        if (Integer.bitCount(m) != 2) {
            return null;
        }
        for (Direction a : HORIZ) {
            if ((m & bit(a)) != 0 && (m & bit(a.getClockWise())) != 0) {
                return a;
            }
        }
        return null;
    }

    /**
     * Второй проход: подбирает вид бордюра. Угол тротуара: одиночный - внешний угловой блок, цепочка по
     * диагонали - диагональные блоки; вогнутый угол - внутренний угловой блок. Направление блока:
     * дорога лежит между a и a.cw (для прямого - в сторону a).
     */
    static void shapes(Cell[][] g, Direction di, Direction dj) {
        for (int i = 0; i < g.length; i++) {
            for (int j = 0; j < g[i].length; j++) {
                Cell c = g[i][j];
                if (c == null || !c.paving || c.island && !c.curb) {
                    continue;
                }
                int m = roadMask(g, i, j, di, dj);
                if (c.curb) {
                    Direction a = cornerFacing(m);
                    if (a == null) {
                        continue;
                    }
                    c.face = a;
                    c.shape = OUTER;
                    int[] sa = step(a, di, dj);
                    int[] sb = step(a.getClockWise(), di, dj);
                    int wi = sa[0] - sb[0];
                    int wj = sa[1] - sb[1];
                    if (isCornerOf(g, i + wi, j + wj, a, di, dj) || isCornerOf(g, i - wi, j - wj, a, di, dj)) {
                        c.shape = DIAGO;
                    }
                } else if (m == 0 && !c.ramp) {
                    Direction found = null;
                    int count = 0;
                    for (Direction a : HORIZ) {
                        int[] sa = step(a, di, dj);
                        int[] sb = step(a.getClockWise(), di, dj);
                        Cell diag = at(g, i + sa[0] + sb[0], j + sa[1] + sb[1]);
                        Cell n1 = at(g, i + sa[0], j + sa[1]);
                        Cell n2 = at(g, i + sb[0], j + sb[1]);
                        if (diag != null && isRoad(diag.kind) && n1 != null && n1.paving && n2 != null && n2.paving) {
                            found = a;
                            count++;
                        }
                    }
                    if (count == 1) {
                        c.curb = true;
                        c.shape = INNER;
                        c.face = found;
                    }
                }
            }
        }
        dropInnerNextToDiago(g, di, dj);
    }

    /** Внутренний угол рядом с диагональным блоком не нужен: диагональ уже замыкает линию бордюра. */
    private static void dropInnerNextToDiago(Cell[][] g, Direction di, Direction dj) {
        for (int i = 0; i < g.length; i++) {
            for (int j = 0; j < g[i].length; j++) {
                Cell c = g[i][j];
                if (c == null || !c.paving || c.shape != INNER) {
                    continue;
                }
                for (Direction d : HORIZ) {
                    int[] st = step(d, di, dj);
                    Cell n = at(g, i + st[0], j + st[1]);
                    if (n != null && n.paving && n.curb && n.shape == DIAGO) {
                        c.curb = false;
                        c.shape = STRAIGHT;
                        break;
                    }
                }
            }
        }
    }

    private static boolean isCornerOf(Cell[][] g, int i, int j, Direction a, Direction di, Direction dj) {
        Cell c = at(g, i, j);
        return c != null && c.paving && cornerFacing(roadMask(g, i, j, di, dj)) == a;
    }

    /** Итоговый блок клетки. */
    static BlockState finish(Cell c, RoadPalette pal) {
        if (!c.paving) {
            return c.state;
        }
        if (c.curb) {
            return switch (c.shape) {
                case OUTER -> pal.curbOuterState(c.face);
                case INNER -> pal.curbInnerState(c.face);
                case DIAGO -> pal.curbDiagoState(c.face);
                default -> pal.curbState(c.face);
            };
        }
        if (c.island) {
            return pal.grassState();
        }
        return pal.sidewalkState(c.face);
    }

    private Surfaces() {}
}
