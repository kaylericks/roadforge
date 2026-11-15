package com.roadforge.build;

import com.roadforge.RoadConfig;
import com.roadforge.player.PlayerRoadState;
import com.roadforge.road.BuildMode;
import com.roadforge.road.CrossSection;
import com.roadforge.road.IntersectionPlan;
import com.roadforge.road.Placement;
import com.roadforge.road.RoadPlan;
import com.roadforge.road.RoadSpec;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/** Общая логика для команд, жезла и пакетов меню. */
public final class RoadActions {
    public static final int CLEAR_HEIGHT = 3;

    public static String describe(PlayerRoadState st) {
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
        List<Placement> plan = RoadPlan.generate(st.a, st.direction(), len, spec, cs,
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

    private RoadActions() {}
}
