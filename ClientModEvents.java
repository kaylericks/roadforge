package com.roadforge.client;

import com.roadforge.RoadForge;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/** Регистрация клавиши (появится в Настройки -> Управление -> RoadForge). */
@Mod.EventBusSubscriber(modid = RoadForge.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientModEvents {
    public static final KeyMapping OPEN_MENU =
            new KeyMapping("key.roadforge.open_menu", GLFW.GLFW_KEY_K, "key.categories.roadforge");

    public static final KeyMapping HEIGHT_UP =
            new KeyMapping("key.roadforge.height_up", GLFW.GLFW_KEY_PAGE_UP, "key.categories.roadforge");
    public static final KeyMapping HEIGHT_DOWN =
            new KeyMapping("key.roadforge.height_down", GLFW.GLFW_KEY_PAGE_DOWN, "key.categories.roadforge");

    @SubscribeEvent
    public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(OPEN_MENU);
        event.register(HEIGHT_UP);
        event.register(HEIGHT_DOWN);
    }

    private ClientModEvents() {}
}
