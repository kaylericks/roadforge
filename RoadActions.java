package com.roadforge.build;

import com.roadforge.RoadConfig;
import com.roadforge.player.PlayerRoadState;
import com.roadforge.road.BuildMode;
import com.roadforge.road.CrossSection;
import com.roadforge.road.BendPlan;
import com.roadforge.road.IntersectionPlan;
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
        if (st.settings.mode == BuildMode.CURVE) {
            return "Изгиб: " + st.settings.lanes + " пол. · радиус дуги " + BendPlan.radius(st.settings)
                    + " · сначала вдоль " + (st.settings.curveFirstX ? "X" : "Z");
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
        } else if (st.settings.mode == BuildMode.CURVE) {
            buildCurve(player, st);
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

    /** Дорога из точки A в точку B с одним поворотом на 90° (два прямых участка и дуга). */
    private static void buildCurve(ServerPlayer player, PlayerRoadState st) {
        if (!st.hasBoth()) {
            player.sendSystemMessage(Component.literal("§cСначала отметь начало и конец жезлом (ПКМ по блоку)."));
            return;
        }
        RoadSettings cfg = st.settings;
        BlockPos a = st.a;
        BlockPos b = st.b;
        if (a.getX() == b.getX() || a.getZ() == b.getZ()) {
            player.sendSystemMessage(Component.literal(
                    "§cТочки лежат на одной линии: для прямой дороги используй режим «Дорога»."));
            return;
        }
        BlockPos corner;
        Direction d1;
        Direction d2;
        int dist1;
        int dist2;
        if (cfg.curveFirstX) {
            corner = new BlockPos(b.getX(), a.getY(), a.getZ());
            d1 = a.getX() > b.getX() ? Direction.EAST : Direction.WEST;
            d2 = b.getZ() > a.getZ() ? Direction.SOUTH : Direction.NORTH;
            dist1 = Math.abs(a.getX() - b.getX());
            dist2 = Math.abs(b.getZ() - a.getZ());
        } else {
            corner = new BlockPos(a.getX(), a.getY(), b.getZ());
            d1 = a.getZ() > b.getZ() ? Direction.SOUTH : Direction.NORTH;
            d2 = b.getX() > a.getX() ? Direction.EAST : Direction.WEST;
            dist1 = Math.abs(a.getZ() - b.getZ());
            dist2 = Math.abs(b.getX() - a.getX());
        }
        int rho = BendPlan.radius(cfg);
        if (dist1 < rho || dist2 < rho) {
            player.sendSystemMessage(Component.literal("§cУчастки слишком короткие для поворота: каждый должен быть не короче "
                    + rho + " бл. (сейчас " + dist1 + " и " + dist2 + "). Отодвинь точки или уменьши «Радиус углов»."));
            return;
        }
        List<Placement> plan = BendPlan.generate(corner, d1, d2, dist1, dist2, false, cfg, CLEAR_HEIGHT);
        if (plan.size() > RoadConfig.MAX_BLOCKS_PER_BUILD.get()) {
            player.sendSystemMessage(Component.literal("§cСлишком много блоков (" + plan.size()
                    + "), лимит " + RoadConfig.MAX_BLOCKS_PER_BUILD.get() + "."));
            return;
        }
        BuildManager.enqueue(player.serverLevel(), player.getUUID(), plan, true, "Изгиб построен");
        player.sendSystemMessage(Component.literal("§eСтрою: " + describe(st)));
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
