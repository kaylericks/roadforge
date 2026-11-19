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
 * Режим «Дорога»: ПКМ - начало, ещё ПКМ - конец. Режим «Перекрёсток»: ПКМ - центр.
 * Shift+ПКМ - построить. Настройки - в меню (клавиша K по умолчанию).
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

        if (player.isShiftKeyDown()) {
            RoadActions.build(player);
        } else {
            RoadActions.click(player, ctx.getClickedPos());
        }
        return InteractionResult.SUCCESS;
    }
}
