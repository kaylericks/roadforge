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
    private static java.util.List<BlockPos> path = new java.util.ArrayList<>();

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

    public static void setPoints(BlockPos pa, BlockPos pb, java.util.List<BlockPos> pp) {
        a = pa;
        b = pb;
        path = new java.util.ArrayList<>(pp);
    }

    public static BlockPos pointA() {
        return a;
    }

    public static BlockPos pointB() {
        return b;
    }

    public static java.util.List<BlockPos> path() {
        return path;
    }

    public static String pointsText() {
        if (settings().mode == com.roadforge.road.BuildMode.PARKING) {
            return path.isEmpty() ? "Точек нет: ПКМ жезлом по углам стоянки"
                    : "Точек: " + path.size() + (path.size() < 3 ? " — нужно минимум 3" : "  площадь ~"
                    + (int) com.roadforge.road.ParkingPlan.area(path) + " бл.²");
        }
        if (settings().mode == com.roadforge.road.BuildMode.PATH) {
            return path.isEmpty() ? "Точек нет: ПКМ жезлом (можно по воздуху)"
                    : "Точек: " + path.size() + (path.size() < 2 ? " — добавь ещё" : "  длина ~"
                    + com.roadforge.road.PathPlan.lengthOf(path, settings()) + " бл.");
        }
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
