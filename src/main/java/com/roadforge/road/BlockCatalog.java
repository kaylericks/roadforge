package com.roadforge.road;

import com.roadforge.compat.BlockLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Списки блоков для выбора в меню: модовые (RouteMod / Road Markings) и обычные. */
public final class BlockCatalog {
    private static final Map<Role, List<String>> VANILLA = Map.of(
            Role.ASPHALT, List.of("minecraft:gray_concrete", "minecraft:black_concrete",
                    "minecraft:light_gray_concrete", "minecraft:stone", "minecraft:smooth_stone",
                    "minecraft:polished_andesite", "minecraft:polished_blackstone", "minecraft:deepslate_tiles"),
            Role.LINE, List.of("minecraft:white_concrete", "minecraft:light_gray_concrete",
                    "minecraft:yellow_concrete", "minecraft:quartz_block"),
            Role.SIDEWALK, List.of("minecraft:stone_bricks", "minecraft:smooth_stone", "minecraft:polished_andesite",
                    "minecraft:quartz_block", "minecraft:bricks", "minecraft:smooth_sandstone",
                    "minecraft:cobblestone", "minecraft:light_gray_concrete"),
            Role.GRASS, List.of("minecraft:grass_block", "minecraft:moss_block", "minecraft:dirt",
                    "minecraft:coarse_dirt", "minecraft:podzol"),
            Role.LAMP, List.of("minecraft:end_rod", "minecraft:lantern", "minecraft:sea_lantern", "minecraft:glowstone")
    );

    public static List<String> mods(Role role) {
        List<String> out = new ArrayList<>();
        for (ResourceLocation rl : ForgeRegistries.BLOCKS.getKeys()) {
            String ns = rl.getNamespace();
            if (!ns.equals(BlockLookup.ROUTEMOD) && !ns.equals(BlockLookup.ROADMARKS)) {
                continue;
            }
            if (matches(role, rl.getPath())) {
                out.add(rl.toString());
            }
        }
        Collections.sort(out);
        return out;
    }

    public static List<String> vanilla(Role role) {
        List<String> out = new ArrayList<>();
        for (String id : VANILLA.get(role)) {
            if (BlockLookup.find(id).isPresent()) {
                out.add(id);
            }
        }
        return out;
    }

    private static boolean matches(Role role, String p) {
        return switch (role) {
            case ASPHALT -> p.startsWith("asphalte") || p.startsWith("revetement_");
            case LINE -> p.equals("ligne_continue");
            case SIDEWALK -> p.matches("trottoir_(stone|stone_brick|brick|sandstone|smooth_stone|grass)")
                    || p.startsWith("revetement_");
            case GRASS -> p.equals("trottoir_grass");
            case LAMP -> p.startsWith("lampe_");
        };
    }

    private BlockCatalog() {}
}
