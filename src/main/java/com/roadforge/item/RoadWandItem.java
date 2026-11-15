package com.roadforge.item;

import com.roadforge.RoadConfig;
import com.roadforge.build.RoadActions;
import com.roadforge.network.Net;
import com.roadforge.player.PlayerRoadState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

/**
 * ПКМ по блоку: 1-й раз - начало, 2-й - конец (потом снова начало).
 * Shift+ПКМ - построить дорогу. Настройки - в меню (клавиша K по умолчанию).
 */
public class RoadWandItem extends Item {
    public RoadWandItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (ctx.getLevel().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(ctx.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.PASS;
        }
        if (!player.hasPermissions(RoadConfig.permission())) {
            player.displayClientMessage(Component.literal("§cНет прав на строительство дорог."), true);
            return InteractionResult.FAIL;
        }

        PlayerRoadState st = PlayerRoadState.of(player);
        BlockPos pos = ctx.getClickedPos();

        if (player.isShiftKeyDown()) {
            RoadActions.build(player);
            return InteractionResult.SUCCESS;
        }

        if (st.a == null || st.b != null) {
            st.a = pos;
            st.b = null;
            player.displayClientMessage(Component.literal("§bДорога: ПКМ — начало " + pos.toShortString()
                    + ". Теперь укажи конец."), true);
        } else {
            st.b = pos;
            player.displayClientMessage(Component.literal("§b" + RoadActions.describe(st)
                    + " §7(Shift+ПКМ — построить)"), true);
        }
        Net.sendPoints(player, st);
        return InteractionResult.SUCCESS;
    }
}
