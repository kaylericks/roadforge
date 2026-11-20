package com.roadforge.build;

import com.roadforge.RoadConfig;
import com.roadforge.network.Net;
import com.roadforge.player.PlayerRoadState;
import com.roadforge.road.BuildMode;
import com.roadforge.road.CrossSection;
import com.roadforge.road.BendPlan;
import com.roadforge.road.IntersectionPlan;
import com.roadforge.road.ParkingPlan;
import com.roadforge.road.PathPlan;
import com.roadforge.road.Placement;
import com.roadforge.road.RoadPlan;
import com.roadforge.road.RoundaboutPlan;
import com.roadforge.road.RoadSpec;
import com.roadforge.road.RoadSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/** Общая логика для команд, жезла и пакетов меню. */
public final class RoadActions {
    public static final int CLEAR_HEIGHT = 3;

    public static String describe(PlayerRoadState st) {
        if (st.settings.mode == BuildMode.PARKING) {
            return "Парковка: " + st.path.size() + " т. · площадь ~" + (int) ParkingPlan.area(st.path) + " бл.²";
        }
        if (st.settings.mode == BuildMode.PATH) {
            CrossSection pcs = CrossSection.build(st.spec());
            return "Путь: " + st.path.size() + " т. · ~" + PathPlan.lengthOf(st.path, st.settings) + " бл. · полос "
                    + st.spec().lanes() + " · ширина " + pcs.width() + " бл.";
        }
        if (st.settings.mode == BuildMode.ROUNDABOUT) {
            return "Кольцо: остров R" + st.settings.islandRadius + " · полос кольца " + st.settings.ringLanes
                    + " · ветки " + arms(st);
        }
        if (st.settings.mode == BuildMode.INTERSECTION && BendPlan.arms(st.settings) != null) {
            return "Поворот: " + st.settings.lanes + " пол. · ветки " + arms(st);
        }
        if (st.settings.mode == BuildMode.INTERSECTION) {
            return "Перекрёсток: главная " + st.settings.lanes + " пол. · поперечная " + st.settings.crossLanes
                    + " пол. · ветки " + arms(st);
        }
        RoadSpec spec = st.spec();
        CrossSection cs = CrossSection.build(spec);
        String len = st.hasBoth() ? st.length() + " бл." : "—";
        return "Дорога: " + len + " · полос " + spec.lanes() + " · " + st.settings.preset.title
                + " · ширина " + cs.width() + " бл.";
    }

    private static String arms(PlayerRoadState st) {
        StringBuilder sb = new StringBuilder();
        if (st.settings.armN) sb.append('С');
        if (st.settings.armE) sb.append('В');
        if (st.settings.armS) sb.append('Ю');
        if (st.settings.armW) sb.append('З');
        return sb.length() == 0 ? "нет" : sb.toString();
    }

    public static void build(ServerPlayer player) {
        PlayerRoadState st = PlayerRoadState.of(player);
        st.settings.sanitize();
        if (st.settings.mode == BuildMode.INTERSECTION) {
            buildIntersection(player, st);
        } else if (st.settings.mode == BuildMode.ROUNDABOUT) {
            buildRoundabout(player, st);
        } else if (st.settings.mode == BuildMode.PATH) {
            buildPath(player, st);
        } else if (st.settings.mode == BuildMode.PARKING) {
            buildParking(player, st);
        } else {
            buildRoad(player, st);
        }
    }

    private static void buildRoad(ServerPlayer player, PlayerRoadState st) {
        if (!st.hasBoth()) {
            player.sendSystemMessage(Component.literal("§cСначала отметь начало и конец жезлом (ПКМ по блоку)."));
            return;
        }
        int len = st.length();
        if (len > RoadConfig.MAX_ROAD_LENGTH.get()) {
            player.sendSystemMessage(Component.literal("§cСлишком длинная дорога: " + len
                    + " (максимум " + RoadConfig.MAX_ROAD_LENGTH.get() + ")."));
            return;
        }
        RoadSpec spec = st.spec();
        CrossSection cs = CrossSection.build(spec);
        long estimate = (long) len * cs.width() * (2 + CLEAR_HEIGHT);
        if (estimate > RoadConfig.MAX_BLOCKS_PER_BUILD.get()) {
            player.sendSystemMessage(Component.literal("§cСлишком много блоков (~" + estimate
                    + "), лимит " + RoadConfig.MAX_BLOCKS_PER_BUILD.get() + "."));
            return;
        }
        int endY = st.b.getY();
        String steep = com.roadforge.road.Grade.check(new double[]{0, Math.max(1, len - 1)},
                new int[]{st.a.getY(), endY});
        if (steep != null) {
            player.sendSystemMessage(Component.literal("§c" + steep));
            return;
        }
        List<Placement> plan = RoadPlan.generate(st.a, st.direction(), len, endY, spec, cs,
                st.settings.toPalette(), CLEAR_HEIGHT);
        BuildManager.enqueue(player.serverLevel(), player.getUUID(), plan, true, "Дорога построена");
        player.sendSystemMessage(Component.literal("§eСтрою: " + describe(st)
                + " (направление " + st.direction().getName() + ")"));
    }

    private static void buildIntersection(ServerPlayer player, PlayerRoadState st) {
        if (st.a == null) {
            player.sendSystemMessage(Component.literal("§cСначала отметь центр перекрёстка жезлом (ПКМ по блоку)."));
            return;
        }
        if (!(st.settings.armN || st.settings.armE || st.settings.armS || st.settings.armW)) {
            player.sendSystemMessage(Component.literal("§cВключи хотя бы одну ветку перекрёстка."));
            return;
        }
        List<Placement> plan = IntersectionPlan.generate(st.a, st.settings, CLEAR_HEIGHT);
        if (plan.size() > RoadConfig.MAX_BLOCKS_PER_BUILD.get()) {
            player.sendSystemMessage(Component.literal("§cСлишком много блоков (" + plan.size()
                    + "), лимит " + RoadConfig.MAX_BLOCKS_PER_BUILD.get() + "."));
            return;
        }
        BuildManager.enqueue(player.serverLevel(), player.getUUID(), plan, true, "Перекрёсток построен");
        player.sendSystemMessage(Component.literal("§eСтрою: " + describe(st)));
        player.sendSystemMessage(Component.literal("§7Продолжай дорогу за концами веток, начиная с блоков: "
                + IntersectionPlan.armEnds(st.a, st.settings)));
    }

    /** Стоянка по контуру из отмеченных точек. */
    private static void buildParking(ServerPlayer player, PlayerRoadState st) {
        if (st.path.size() < 3) {
            player.sendSystemMessage(Component.literal("§cНужно минимум три точки контура (ПКМ жезлом)."));
            return;
        }
        st.settings.sanitize();
        ParkingPlan.Result res = ParkingPlan.generate(st.path, st.settings, CLEAR_HEIGHT);
        if (res.blocks.isEmpty()) {
            player.sendSystemMessage(Component.literal("§cКонтур слишком большой или вырожденный."));
            return;
        }
        if (res.blocks.size() > RoadConfig.MAX_BLOCKS_PER_BUILD.get()) {
            player.sendSystemMessage(Component.literal("§cСлишком много блоков (" + res.blocks.size()
                    + "), лимит " + RoadConfig.MAX_BLOCKS_PER_BUILD.get() + "."));
            return;
        }
        BuildManager.enqueue(player.serverLevel(), player.getUUID(), res.blocks, true, "Стоянка построена");
        player.sendSystemMessage(Component.literal("§eСтрою: " + describe(st) + " · мест ~" + res.stalls));
        st.path.clear();
        Net.sendPoints(player, st);
    }

    /** Дорога по ломаной: любые углы, на изломах дуга. */
    private static void buildPath(ServerPlayer player, PlayerRoadState st) {
        if (st.path.size() < 2) {
            player.sendSystemMessage(Component.literal("§cНужно минимум две точки пути (ПКМ жезлом)."));
            return;
        }
        int len = PathPlan.lengthOf(st.path, st.settings);
        if (len > RoadConfig.MAX_ROAD_LENGTH.get()) {
            player.sendSystemMessage(Component.literal("§cСлишком длинный путь: " + len
                    + " (максимум " + RoadConfig.MAX_ROAD_LENGTH.get() + ")."));
            return;
        }
        st.settings.sanitize();
        String steep = PathPlan.check(st.path, st.settings);
        if (steep != null) {
            player.sendSystemMessage(Component.literal("§c" + steep));
            return;
        }
        List<Placement> plan = PathPlan.generate(st.path, st.settings, CLEAR_HEIGHT);
        if (plan.size() > RoadConfig.MAX_BLOCKS_PER_BUILD.get()) {
            player.sendSystemMessage(Component.literal("§cСлишком много блоков (" + plan.size()
                    + "), лимит " + RoadConfig.MAX_BLOCKS_PER_BUILD.get() + "."));
            return;
        }
        BuildManager.enqueue(player.serverLevel(), player.getUUID(), plan, true, "Путь построен");
        player.sendSystemMessage(Component.literal("§eСтрою: " + describe(st)));
        // последняя точка остаётся началом следующего участка
        BlockPos last = st.path.get(st.path.size() - 1);
        st.path.clear();
        st.path.add(last);
        Net.sendPoints(player, st);
    }

    /** Клик жезлом по точке: ставит начало / конец / центр / очередную точку пути. */
    public static void click(ServerPlayer player, BlockPos pos) {
        PlayerRoadState st = PlayerRoadState.of(player);
        BuildMode mode = st.settings.mode;
        if (mode.isCentered()) {
            st.a = pos;
            st.b = null;
            player.displayClientMessage(Component.literal("§bЦентр: " + pos.toShortString()
                    + " §7(Shift+ПКМ — построить)"), true);
        } else if (mode == BuildMode.PATH || mode == BuildMode.PARKING) {
            st.path.add(pos);
            player.displayClientMessage(Component.literal("§b" + describe(st)
                    + " §7(Shift+ПКМ — построить, ЛКМ — убрать точку)"), true);
        } else if (st.a == null || st.b != null) {
            st.a = pos;
            st.b = null;
            player.displayClientMessage(Component.literal("§bДорога: начало " + pos.toShortString()
                    + ". Теперь укажи конец."), true);
        } else {
            st.b = pos;
            player.displayClientMessage(Component.literal("§b" + describe(st)
                    + " §7(Shift+ПКМ — построить)"), true);
        }
        Net.sendPoints(player, st);
    }

    /** ЛКМ жезлом: убирает последнюю отмеченную точку. */
    public static void removeLastPoint(ServerPlayer player) {
        PlayerRoadState st = PlayerRoadState.of(player);
        if (st.settings.mode == BuildMode.PATH || st.settings.mode == BuildMode.PARKING) {
            if (!st.path.isEmpty()) {
                st.path.remove(st.path.size() - 1);
            }
        } else if (st.b != null) {
            st.b = null;
        } else {
            st.a = null;
        }
        Net.sendPoints(player, st);
        player.displayClientMessage(Component.literal("§7Точка убрана."), true);
    }

    private static void buildRoundabout(ServerPlayer player, PlayerRoadState st) {
        if (st.a == null) {
            player.sendSystemMessage(Component.literal("§cСначала отметь центр кольца жезлом (ПКМ по блоку)."));
            return;
        }
        if (!(st.settings.armN || st.settings.armE || st.settings.armS || st.settings.armW)) {
            player.sendSystemMessage(Component.literal("§cВключи хотя бы одну ветку кольца."));
            return;
        }
        long estimate = (long) RoundaboutPlan.footprint(st.settings) * (2 + CLEAR_HEIGHT);
        if (estimate > RoadConfig.MAX_BLOCKS_PER_BUILD.get()) {
            player.sendSystemMessage(Component.literal("§cСлишком много блоков (~" + estimate
                    + "), лимит " + RoadConfig.MAX_BLOCKS_PER_BUILD.get() + "."));
            return;
        }
        List<Placement> plan = RoundaboutPlan.generate(st.a, st.settings, CLEAR_HEIGHT);
        BuildManager.enqueue(player.serverLevel(), player.getUUID(), plan, true, "Кольцо построено");
        player.sendSystemMessage(Component.literal("§eСтрою: " + describe(st)));
        player.sendSystemMessage(Component.literal("§7Продолжай дорогу за концами веток, начиная с блоков: "
                + RoundaboutPlan.armEnds(st.a, st.settings)));
    }

    private RoadActions() {}
}
