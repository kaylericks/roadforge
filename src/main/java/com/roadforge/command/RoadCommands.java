package com.roadforge.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.roadforge.RoadConfig;
import com.roadforge.build.BuildManager;
import com.roadforge.build.RoadActions;
import com.roadforge.compat.BlockLookup;
import com.roadforge.network.Net;
import com.roadforge.player.PlayerRoadState;
import com.roadforge.road.RoadPreset;
import com.roadforge.road.StateUtil;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public final class RoadCommands {
    /** Ключевые блоки, которые нужны генератору. */
    private static final List<String> PROBE_IDS = List.of(
            "routemod:asphalte",
            "routemod:asphalte_bloc",
            "routemod:ligne_continue",
            "routemod:ligne_diagonale",
            "routemod:passage_pieton",
            "routemod:trottoir_stone",
            "routemod:trottoir_stone_brick",
            "routemod:coin_exterieur_trottoir_stone",
            "routemod:lampe_1",
            "routemod:glissiere_securite",
            "routemod:poteau_feu_tricolore",
            "roadmarks:ligne_continue",
            "roadmarks:ligne_t",
            "roadmarks:pente_1",
            "roadmarks:pente_1_marquage",
            "roadmarks:pente_1_pointille"
    );

    /** Образцы для /roadforge sample (все с facing=north). */
    private static final List<String> SAMPLE_IDS = List.of(
            "routemod:asphalte",
            "routemod:ligne_continue",
            "routemod:trottoir_stone_brick",
            "routemod:lampe_1",
            "roadmarks:pente_1",
            "roadmarks:pente_1_marquage",
            "roadmarks:pente_1_pointille",
            "routemod:passage_pieton"
    );

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("roadforge")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("check").executes(ctx -> check(ctx.getSource())))
                .then(Commands.literal("sample").executes(ctx -> sample(ctx.getSource()))));

        d.register(Commands.literal("road")
                .requires(s -> s.hasPermission(RoadConfig.permission()))
                .then(Commands.literal("preset")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(RoadPreset.keys(), b))
                                .executes(ctx -> setPreset(ctx.getSource().getPlayerOrException(),
                                        StringArgumentType.getString(ctx, "name")))))
                .then(Commands.literal("lanes")
                        .then(Commands.argument("count", IntegerArgumentType.integer(2, 6))
                                .executes(ctx -> setLanes(ctx.getSource().getPlayerOrException(),
                                        IntegerArgumentType.getInteger(ctx, "count")))))
                .then(Commands.literal("info").executes(ctx -> info(ctx.getSource().getPlayerOrException())))
                .then(Commands.literal("build").executes(ctx -> {
                    RoadActions.build(ctx.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("undo").executes(ctx -> undo(ctx.getSource().getPlayerOrException())))
                .then(Commands.literal("clear").executes(ctx -> clear(ctx.getSource().getPlayerOrException()))));
    }

    // ---------- /road ----------

    private static int setPreset(ServerPlayer p, String name) {
        Optional<RoadPreset> preset = RoadPreset.byKey(name);
        if (preset.isEmpty()) {
            msg(p, "§cНеизвестный тип. Доступно: " + String.join(", ", RoadPreset.keys()));
            return 0;
        }
        PlayerRoadState st = PlayerRoadState.of(p);
        st.settings.applyPreset(preset.get());
        msg(p, "§e" + RoadActions.describe(st));
        return 1;
    }

    private static int setLanes(ServerPlayer p, int count) {
        PlayerRoadState st = PlayerRoadState.of(p);
        st.settings.lanes = RoadPreset.clampLanes(count);
        msg(p, "§e" + RoadActions.describe(st));
        return 1;
    }

    private static int info(ServerPlayer p) {
        PlayerRoadState st = PlayerRoadState.of(p);
        msg(p, "§e" + RoadActions.describe(st));
        if (st.a != null) {
            msg(p, "§7A: " + st.a.toShortString() + (st.b != null ? "  B: " + st.b.toShortString() : ""));
        }
        return 1;
    }

    private static int undo(ServerPlayer p) {
        if (BuildManager.undo(p)) {
            msg(p, "§eОтменяю последнюю постройку...");
            return 1;
        }
        msg(p, "§cОтменять нечего.");
        return 0;
    }

    private static int clear(ServerPlayer p) {
        PlayerRoadState st = PlayerRoadState.of(p);
        st.a = null;
        st.b = null;
        Net.sendPoints(p, st);
        msg(p, "§eТочки сброшены.");
        return 1;
    }

    // ---------- /roadforge ----------

    private static int check(CommandSourceStack src) {
        send(src, "§eRoadForge: проверка блоков");
        send(src, "routemod: " + (BlockLookup.isLoaded(BlockLookup.ROUTEMOD) ? "§aзагружен" : "§cнет")
                + "§r, roadmarks: " + (BlockLookup.isLoaded(BlockLookup.ROADMARKS) ? "§aзагружен" : "§cнет"));

        int found = 0;
        for (String id : PROBE_IDS) {
            Optional<Block> block = BlockLookup.find(id);
            if (block.isPresent()) {
                found++;
                String props = block.get().getStateDefinition().getProperties().stream()
                        .map(Property::getName)
                        .collect(Collectors.joining(", "));
                send(src, "§a✔ §r" + id + " §7[" + (props.isEmpty() ? "-" : props) + "]");
            } else {
                send(src, "§c✘ §r" + id);
            }
        }
        send(src, "§eНайдено " + found + " из " + PROBE_IDS.size()
                + " (лимит длины в конфиге: " + RoadConfig.MAX_ROAD_LENGTH.get() + ")");
        return found;
    }

    /** Ставит ряд образцов (facing=north) перед игроком, чтобы проверить ориентацию моделей. */
    private static int sample(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        ServerLevel level = p.serverLevel();
        BlockPos base = p.blockPosition().relative(Direction.NORTH, 4);
        StringBuilder names = new StringBuilder();
        int i = 0;
        for (String id : SAMPLE_IDS) {
            Optional<Block> b = BlockLookup.find(id);
            if (b.isPresent()) {
                BlockPos pos = base.relative(Direction.EAST, i * 2);
                level.setBlock(pos, StateUtil.withFacing(b.get().defaultBlockState(), Direction.NORTH), 3);
                names.append(i + 1).append("=").append(id).append("  ");
            }
            i++;
        }
        send(src, "§eОбразцы (facing=north) к северу от тебя, слева направо: §7" + names);
        return 1;
    }

    private static void msg(ServerPlayer p, String text) {
        p.sendSystemMessage(Component.literal(text));
    }

    private static void send(CommandSourceStack src, String text) {
        src.sendSuccess(() -> Component.literal(text), false);
    }

    private RoadCommands() {}
}
