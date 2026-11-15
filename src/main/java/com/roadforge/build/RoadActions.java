package com.roadforge.build;

import com.roadforge.RoadConfig;
import com.roadforge.player.PlayerRoadState;
import com.roadforge.road.CrossSection;
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
        RoadSpec spec = st.spec();
        CrossSection cs = CrossSection.build(spec);
        String len = st.hasBoth() ? st.length() + " бл." : "—";
        return "Дорога: " + len + " · полос " + spec.lanes() + " · " + st.settings.preset.title
                + " · ширина " + cs.width() + " бл.";
    }

    public static void build(ServerPlayer player) {
        PlayerRoadState st = PlayerRoadState.of(player);
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
        st.settings.sanitize();
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

    private RoadActions() {}
}
