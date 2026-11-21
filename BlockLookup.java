package com.roadforge.compat;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Optional;

/** Мягкий доступ к блокам RouteMod / Road Markings по ID, без compile-зависимости. */
public final class BlockLookup {
    public static final String ROUTEMOD = "routemod";
    public static final String ROADMARKS = "roadmarks";

    public static boolean isLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    /** Возвращает блок, только если он реально есть в реестре (а не воздух по умолчанию). */
    public static Optional<Block> find(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null || !ForgeRegistries.BLOCKS.containsKey(rl)) {
            return Optional.empty();
        }
        return Optional.ofNullable(ForgeRegistries.BLOCKS.getValue(rl));
    }

    private BlockLookup() {}
}
