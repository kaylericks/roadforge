package com.roadforge.road;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.roadforge.RoadForge;
import com.roadforge.compat.BlockLookup;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Files;
import java.nio.file.Path;

/** Все настройки (то, что редактируется в меню). Хранится на клиенте и синхронизируется на сервер. */
public class RoadSettings {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // --- дорога ---
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

    // --- парковка ---
    public ParkType parkType = ParkType.NONE;
    public boolean parkLeft = false;
    public boolean parkRight = false;
    public int parkDepth = 2;
    public int parkPitch = 6;

    // --- переходы на дороге ---
    public int crossEvery = 0;          // шаг пешеходных переходов, блоков (0 - нет)
    public int guard = 0;               // ограждения: 0 нет, 1 разделитель, 2 края, 3 везде
    public int treeSpacing = 0;         // шаг деревьев на газоне и острове кольца (0 - нет)

    // --- перекрёсток ---
    public BuildMode mode = BuildMode.ROAD;
    public int crossLanes = 2;
    public int armLength = 14;
    public boolean armN = true;
    public boolean armE = true;
    public boolean armS = true;
    public boolean armW = true;
    public int cornerRadius = 3;
    public boolean crosswalks = true;

    // --- кольцо ---
    public int islandRadius = 6;        // радиус центрального острова
    public int ringLanes = 2;           // полос на кольце (1 или 2)

    // --- блоки ---
    public String asphalt = RoadPalette.DEFAULT.asphalt();
    public String line = RoadPalette.DEFAULT.line();
    public String sidewalk = RoadPalette.DEFAULT.sidewalk();
    public String curb = RoadPalette.DEFAULT.curb();
    public String grass = RoadPalette.DEFAULT.grass();
    public String lamp = RoadPalette.DEFAULT.lamp();
    public String guardrail = RoadPalette.DEFAULT.guardrail();
    public String capPlain = RoadPalette.DEFAULT.capPlain();
    public String capSolid = RoadPalette.DEFAULT.capSolid();
    public String capDash = RoadPalette.DEFAULT.capDash();
    public String crosswalk = RoadPalette.DEFAULT.crosswalk();
    public String diagonal = RoadPalette.DEFAULT.diagonal();

    // --- повороты моделей (четверти оборота) ---
    public int turnSidewalk = 0;
    public int turnLamp = 0;
    public int turnCap = 0;
    public int turnLine = 0;
    public int turnCross = 0;
    public int turnDiag = 0;
    public int turnGuard = 0;
    public int turnCornerOut = 0;       // внешний угловой блок бордюра
    public int turnCornerIn = 0;        // внутренний угловой блок бордюра
    public int turnCurbDiago = 0;       // диагональный блок бордюра

    public RoadSettings() {
        applyPreset(RoadPreset.CITY);
    }

    /** Подставляет значения шаблона (блоки, парковку и перекрёсток не трогает). */
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
                sidewalkWidth, shoulderWidth, lampSpacing, dashOn, dashGap, endCaps,
                parkType, parkLeft, parkRight, parkDepth, parkPitch, crossEvery, treeSpacing, guard);
    }

    public RoadPalette toPalette() {
        return new RoadPalette(asphalt, line, sidewalk, curb, grass, lamp, capPlain, capSolid, capDash,
                crosswalk, diagonal, guardrail, turnSidewalk, turnLamp, turnCap, turnLine, turnCross, turnDiag,
                turnCornerOut, turnCornerIn, turnCurbDiago, turnGuard);
    }

    public String get(Role r) {
        return switch (r) {
            case ASPHALT -> asphalt;
            case LINE -> line;
            case SIDEWALK -> sidewalk;
            case CURB -> curb;
            case GRASS -> grass;
            case LAMP -> lamp;
            case GUARD -> guardrail;
            case CROSSWALK -> crosswalk;
            case DIAGONAL -> diagonal;
        };
    }

    public void set(Role r, String id) {
        switch (r) {
            case ASPHALT -> asphalt = id;
            case LINE -> line = id;
            case SIDEWALK -> sidewalk = id;
            case CURB -> curb = id;
            case GRASS -> grass = id;
            case LAMP -> lamp = id;
            case GUARD -> guardrail = id;
            case CROSSWALK -> crosswalk = id;
            case DIAGONAL -> diagonal = id;
        }
    }

    /** Ограничивает значения и заменяет неизвестные блоки на стандартные. */
    public void sanitize() {
        if (preset == null) {
            preset = RoadPreset.CITY;
        }
        if (parkType == null) {
            parkType = ParkType.NONE;
        }
        if (mode == null) {
            mode = BuildMode.ROAD;
        }
        lanes = RoadPreset.clampLanes(lanes);
        crossLanes = RoadPreset.clampLanes(crossLanes);
        laneWidth = clamp(laneWidth, 2, 5);
        medianWidth = clamp(medianWidth, 0, 9);
        if (medianWidth > 0 && medianWidth % 2 == 0) {
            medianWidth++;                       // нечётная ширина: симметричный профиль
        }
        sidewalkWidth = clamp(sidewalkWidth, 0, 8);
        shoulderWidth = clamp(shoulderWidth, 0, 4);
        lampSpacing = lampSpacing <= 0 ? 0 : clamp(lampSpacing, 6, 48);
        dashOn = clamp(dashOn, 1, 4);
        dashGap = clamp(dashGap, 1, 6);
        parkDepth = clamp(parkDepth, 1, 6);
        parkPitch = clamp(parkPitch, 2, 10);
        crossEvery = crossEvery <= 0 ? 0 : clamp(crossEvery, 16, 128);
        treeSpacing = treeSpacing <= 0 ? 0 : clamp(treeSpacing, 5, 32);
        islandRadius = clamp(islandRadius, 3, 14);
        ringLanes = clamp(ringLanes, 1, 2);
        armLength = clamp(armLength, 4, 48);
        cornerRadius = clamp(cornerRadius, 0, 8);
        turnSidewalk = Math.floorMod(turnSidewalk, 4);
        turnLamp = Math.floorMod(turnLamp, 4);
        turnCap = Math.floorMod(turnCap, 4);
        turnLine = Math.floorMod(turnLine, 4);
        turnCross = Math.floorMod(turnCross, 4);
        turnDiag = Math.floorMod(turnDiag, 4);
        turnGuard = Math.floorMod(turnGuard, 4);
        guard = clamp(guard, 0, 3);
        turnCornerOut = Math.floorMod(turnCornerOut, 4);
        turnCornerIn = Math.floorMod(turnCornerIn, 4);
        turnCurbDiago = Math.floorMod(turnCurbDiago, 4);

        RoadPalette d = RoadPalette.DEFAULT;
        asphalt = fix(asphalt, d.asphalt());
        line = fix(line, d.line());
        sidewalk = fix(sidewalk, d.sidewalk());
        curb = fix(curb, d.curb());
        grass = fix(grass, d.grass());
        lamp = fix(lamp, d.lamp());
        guardrail = fix(guardrail, d.guardrail());
        capPlain = fix(capPlain, d.capPlain());
        capSolid = fix(capSolid, d.capSolid());
        capDash = fix(capDash, d.capDash());
        crosswalk = fix(crosswalk, d.crosswalk());
        diagonal = fix(diagonal, d.diagonal());
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
            try {
                String text = Files.readString(p);
                RoadSettings s = GSON.fromJson(text, RoadSettings.class);
                if (s != null) {
                    // старый конфиг (до роли «Бордюр»): тротуар целиком был бордюрным блоком
                    if (!text.contains("\"curb\"") && RoadPalette.DEFAULT.curb().equals(s.sidewalk)) {
                        s.sidewalk = RoadPalette.DEFAULT.sidewalk();
                    }
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

        b.writeEnum(parkType);
        b.writeBoolean(parkLeft);
        b.writeBoolean(parkRight);
        b.writeVarInt(parkDepth);
        b.writeVarInt(parkPitch);
        b.writeVarInt(crossEvery);
        b.writeVarInt(treeSpacing);

        b.writeEnum(mode);
        b.writeVarInt(crossLanes);
        b.writeVarInt(armLength);
        b.writeBoolean(armN);
        b.writeBoolean(armE);
        b.writeBoolean(armS);
        b.writeBoolean(armW);
        b.writeVarInt(cornerRadius);
        b.writeBoolean(crosswalks);
        b.writeVarInt(islandRadius);
        b.writeVarInt(ringLanes);

        b.writeUtf(asphalt);
        b.writeUtf(line);
        b.writeUtf(sidewalk);
        b.writeUtf(curb);
        b.writeUtf(grass);
        b.writeUtf(lamp);
        b.writeUtf(capPlain);
        b.writeUtf(capSolid);
        b.writeUtf(capDash);
        b.writeUtf(crosswalk);
        b.writeUtf(diagonal);

        b.writeVarInt(turnSidewalk);
        b.writeVarInt(turnLamp);
        b.writeVarInt(turnCap);
        b.writeVarInt(turnLine);
        b.writeVarInt(turnCross);
        b.writeVarInt(turnDiag);
        b.writeVarInt(turnGuard);
        b.writeVarInt(guard);
        b.writeUtf(guardrail);
        b.writeVarInt(turnCornerOut);
        b.writeVarInt(turnCornerIn);
        b.writeVarInt(turnCurbDiago);
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

        s.parkType = b.readEnum(ParkType.class);
        s.parkLeft = b.readBoolean();
        s.parkRight = b.readBoolean();
        s.parkDepth = b.readVarInt();
        s.parkPitch = b.readVarInt();
        s.crossEvery = b.readVarInt();
        s.treeSpacing = b.readVarInt();

        s.mode = b.readEnum(BuildMode.class);
        s.crossLanes = b.readVarInt();
        s.armLength = b.readVarInt();
        s.armN = b.readBoolean();
        s.armE = b.readBoolean();
        s.armS = b.readBoolean();
        s.armW = b.readBoolean();
        s.cornerRadius = b.readVarInt();
        s.crosswalks = b.readBoolean();
        s.islandRadius = b.readVarInt();
        s.ringLanes = b.readVarInt();

        s.asphalt = b.readUtf();
        s.line = b.readUtf();
        s.sidewalk = b.readUtf();
        s.curb = b.readUtf();
        s.grass = b.readUtf();
        s.lamp = b.readUtf();
        s.capPlain = b.readUtf();
        s.capSolid = b.readUtf();
        s.capDash = b.readUtf();
        s.crosswalk = b.readUtf();
        s.diagonal = b.readUtf();

        s.turnSidewalk = b.readVarInt();
        s.turnLamp = b.readVarInt();
        s.turnCap = b.readVarInt();
        s.turnLine = b.readVarInt();
        s.turnCross = b.readVarInt();
        s.turnDiag = b.readVarInt();
        s.turnGuard = b.readVarInt();
        s.guard = b.readVarInt();
        s.guardrail = b.readUtf();
        s.turnCornerOut = b.readVarInt();
        s.turnCornerIn = b.readVarInt();
        s.turnCurbDiago = b.readVarInt();
        return s;
    }
}
