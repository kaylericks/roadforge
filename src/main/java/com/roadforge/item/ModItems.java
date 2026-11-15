package com.roadforge.item;

import com.roadforge.RoadForge;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, RoadForge.MOD_ID);

    public static final RegistryObject<Item> ROAD_WAND =
            ITEMS.register("road_wand", () -> new RoadWandItem(new Item.Properties().stacksTo(1)));

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }

    private ModItems() {}
}
