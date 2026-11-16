package com.roadforge.client;

import com.roadforge.network.ApplySettingsPacket;
import com.roadforge.network.Net;
import com.roadforge.player.PlayerRoadState;
import com.roadforge.road.RoadSettings;
import net.minecraft.core.BlockPos;

/** Клиентское состояние меню: настройки и точки, полученные от сервера. */
public final class ClientState {
    private static RoadSettings settings;
    private static BlockPos a;
    private static BlockPos b;

    public static RoadSettings settings() {
        if (settings == null) {
            settings = RoadSettings.load();
        }
        return settings;
    }

    public static void save() {
        settings().save();
    }

    public static void pushToServer() {
        Net.CHANNEL.sendToServer(new ApplySettingsPacket(settings().copy()));
    }

    public static void setPoints(BlockPos pa, BlockPos pb) {
        a = pa;
        b = pb;
    }

    public static String pointsText() {
        if (settings().mode.isCentered()) {
            return a == null ? "Центр не выбран: ПКМ жезлом по блоку" : "Центр: " + a.toShortString();
        }
        if (a == null) {
            return "Точки не выбраны: ПКМ жезлом по блоку";
        }
        if (b == null) {
            return "A: " + a.toShortString() + "  — укажи конец";
        }
        return "A: " + a.toShortString() + "  B: " + b.toShortString()
                + "  длина " + PlayerRoadState.lengthOf(a, b) + " бл.";
    }

    private ClientState() {}
}
