package com.roadforge.road;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.roadforge.RoadForge;
import com.roadforge.compat.BlockLookup;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;

/** Все настройки дороги (то, что редактируется в меню). Хранится на клиенте и синхронизируется на сервер. */
public class RoadSettings {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public RoadPreset preset = RoadPreset.CITY;
    public int lanes = 4;
    public int laneWidth = 3;
    public int medianWidth = 0;
    public int sidewalkWidth = 3;
    public int shoulderWidth = 0;
    public int lampSpacing = 12;
    public int dashOn = 1;
    public int dashGap = 3;
    public boolean markings = true;
    public boolean edgeLines = false;
    public boolean doubleCenter = true;
    public boolean endCaps = true;

    public String asphalt = RoadPalette.DEFAULT.asphalt();
    public String line = RoadPalette.DEFAULT.line();
    public String sidewalk = RoadPalette.DEFAULT.sidewalk();
    public String grass = RoadPalette.DEFAULT.grass();
    public String lamp = RoadPalette.DEFAULT.lamp();
    public String capPlain = RoadPalette.DEFAULT.capPlain();
    public String capSolid = RoadPalette.DEFAULT.capSolid();
    public String capDash = RoadPalette.DEFAULT.capDash();

    public RoadSettings() {
        applyPreset(RoadPreset.CITY);
    }

    /** Подставляет значения шаблона (блоки не трогает). */
    public void applyPreset(RoadPreset p) {
        RoadSpec sp = p.create(lanes);
        preset = p;
        lanes = sp.lanes();
        laneWidth = sp.laneWidth();
        markings = sp.markings();
        edgeLines = sp.edgeLines();
        doubleCenter = sp.doubleCenter();
        medianWidth = sp.medianWidth();
        sidewalkWidth = sp.sidewalkWidth();
        shoulderWidth = sp.shoulderWidth();
        lampSpacing = sp.lampSpacing();
        dashOn = sp.dashOn();
        dashGap = sp.dashGap();
        endCaps = sp.endCaps();
    }

    public RoadSpec toSpec() {
        return new RoadSpec(preset.key, lanes, laneWidth, markings, edgeLines, doubleCenter, medianWidth,
                sidewalkWidth, shoulderWidth, lampSpacing, dashOn, dashGap, endCaps);
    }

    public RoadPalette toPalette() {
        return new RoadPalette(asphalt, line, sidewalk, grass, lamp, capPlain, capSolid, capDash);
    }

    public String get(Role r) {
        return switch (r) {
            case ASPHALT -> asphalt;
            case LINE -> line;
            case SIDEWALK -> sidewalk;
            case GRASS -> grass;
            case LAMP -> lamp;
        };
    }

    public void set(Role r, String id) {
        switch (r) {
            case ASPHALT -> asphalt = id;
            case LINE -> line = id;
            case SIDEWALK -> sidewalk = id;
            case GRASS -> grass = id;
            case LAMP -> lamp = id;
        }
    }

    /** Ограничивает значения и заменяет неизвестные блоки на стандартные. */
    public void sanitize() {
        if (preset == null) {
            preset = RoadPreset.CITY;
        }
        lanes = RoadPreset.clampLanes(lanes);
        laneWidth = clamp(laneWidth, 2, 5);
        medianWidth = clamp(medianWidth, 0, 9);
        sidewalkWidth = clamp(sidewalkWidth, 0, 8);
        shoulderWidth = clamp(shoulderWidth, 0, 4);
        lampSpacing = lampSpacing <= 0 ? 0 : clamp(lampSpacing, 6, 48);
        dashOn = clamp(dashOn, 1, 4);
        dashGap = clamp(dashGap, 1, 6);

        RoadPalette d = RoadPalette.DEFAULT;
        asphalt = fix(asphalt, d.asphalt());
        line = fix(line, d.line());
        sidewalk = fix(sidewalk, d.sidewalk());
        grass = fix(grass, d.grass());
        lamp = fix(lamp, d.lamp());
        capPlain = fix(capPlain, d.capPlain());
        capSolid = fix(capSolid, d.capSolid());
        capDash = fix(capDash, d.capDash());
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static String fix(String id, String def) {
        return (id != null && BlockLookup.find(id).isPresent()) ? id : def;
    }

    public RoadSettings copy() {
        return GSON.fromJson(GSON.toJson(this), RoadSettings.class);
    }

    // ---------- файл (клиент) ----------

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("roadforge-client.json");
    }

    public static RoadSettings load() {
        Path p = file();
        if (Files.exists(p)) {
            try (Reader r = Files.newBufferedReader(p)) {
                RoadSettings s = GSON.fromJson(r, RoadSettings.class);
                if (s != null) {
                    s.sanitize();
                    return s;
                }
            } catch (Exception e) {
                RoadForge.LOGGER.warn("RoadForge: cannot read {}: {}", p, e.toString());
            }
        }
        return new RoadSettings();
    }

    public void save() {
        try {
            Files.writeString(file(), GSON.toJson(this));
        } catch (Exception e) {
            RoadForge.LOGGER.warn("RoadForge: cannot save settings: {}", e.toString());
        }
    }

    // ---------- сеть ----------

    public void encode(FriendlyByteBuf b) {
        b.writeEnum(preset);
        b.writeVarInt(lanes);
        b.writeVarInt(laneWidth);
        b.writeVarInt(medianWidth);
        b.writeVarInt(sidewalkWidth);
        b.writeVarInt(shoulderWidth);
        b.writeVarInt(lampSpacing);
        b.writeVarInt(dashOn);
        b.writeVarInt(dashGap);
        b.writeBoolean(markings);
        b.writeBoolean(edgeLines);
        b.writeBoolean(doubleCenter);
        b.writeBoolean(endCaps);
        b.writeUtf(asphalt);
        b.writeUtf(line);
        b.writeUtf(sidewalk);
        b.writeUtf(grass);
        b.writeUtf(lamp);
        b.writeUtf(capPlain);
        b.writeUtf(capSolid);
        b.writeUtf(capDash);
    }

    public static RoadSettings decode(FriendlyByteBuf b) {
        RoadSettings s = new RoadSettings();
        s.preset = b.readEnum(RoadPreset.class);
        s.lanes = b.readVarInt();
        s.laneWidth = b.readVarInt();
        s.medianWidth = b.readVarInt();
        s.sidewalkWidth = b.readVarInt();
        s.shoulderWidth = b.readVarInt();
        s.lampSpacing = b.readVarInt();
        s.dashOn = b.readVarInt();
        s.dashGap = b.readVarInt();
        s.markings = b.readBoolean();
        s.edgeLines = b.readBoolean();
        s.doubleCenter = b.readBoolean();
        s.endCaps = b.readBoolean();
        s.asphalt = b.readUtf();
        s.line = b.readUtf();
        s.sidewalk = b.readUtf();
        s.grass = b.readUtf();
        s.lamp = b.readUtf();
        s.capPlain = b.readUtf();
        s.capSolid = b.readUtf();
        s.capDash = b.readUtf();
        return s;
    }
}
