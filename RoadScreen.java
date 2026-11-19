package com.roadforge.client;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.roadforge.compat.BlockLookup;
import com.roadforge.network.ActionPacket;
import com.roadforge.network.Net;
import com.roadforge.road.BuildMode;
import com.roadforge.road.CrossSection;
import com.roadforge.road.IntersectionPlan;
import com.roadforge.road.ParkType;
import com.roadforge.road.PathPlan;
import com.roadforge.road.Placement;
import com.roadforge.road.RoadPlan;
import com.roadforge.road.RoadPreset;
import com.roadforge.road.RoadSettings;
import com.roadforge.road.RoadSpec;
import com.roadforge.road.RoundaboutPlan;
import com.roadforge.road.Role;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.IntConsumer;

/**
 * Главное меню в стиле редактора: слева инструменты (режимы), в центре живое 3D-превью,
 * справа панель свойств с разделами и поиском, внизу кнопки «Построить» (Enter) и «Отменить постройку».
 */
public class RoadScreen extends Screen {
    private static final int LEFT_W = 156;
    private static final int RIGHT_W = 292;
    private static final int ROW_H = 22;
    private static final int HEAD_H = 20;

    private static final int COL_BG = 0xE6161A20;
    private static final int COL_PANEL = 0xF01B2028;
    private static final int COL_ROW = 0xFF262B34;
    private static final int COL_ROW_HOT = 0xFF313845;
    private static final int COL_ACCENT = 0xFF3B6FE0;
    private static final int COL_TEXT = 0xFFE6E9EF;
    private static final int COL_DIM = 0xFF8D96A5;

    private enum T { HEADER, SEG, STEP, TOGGLE, BLOCK, INFO, NOTE }

    private static final class Row {
        T type;
        String section = "";
        String label = "";
        List<String> opts = List.of();
        int sel;
        IntConsumer onSel;
        String value = "";
        Runnable dec;
        Runnable inc;
        Runnable click;
        boolean on;
        String blockId;
    }

    private record Hit(int x0, int y0, int x1, int y1, Runnable act) {
        boolean in(double x, double y) {
            return x >= x0 && x < x1 && y >= y0 && y < y1;
        }
    }

    private final RoadSettings s = ClientState.settings();
    private final List<Row> rows = new ArrayList<>();
    private final Set<String> collapsed = new HashSet<>(Set.of("ПОВОРОТЫ БЛОКОВ"));
    private final List<Hit> hits = new ArrayList<>();
    private EditBox search;
    private String query = "";
    private int scroll;

    private float yaw = 30f;
    private float pitch = 40f;
    private float zoom = 1f;

    private int px1, py1, px2, py2;     // область превью
    private int rx, ry1, ry2;           // правая панель: левый край, верх и низ списка

    private List<Placement> cache;
    private float centerX, centerZ, baseScale;

    public RoadScreen() {
        super(Component.literal("RoadForge"));
        ClientState.pushToServer();
    }

    // ------------------------------------------------------------ строки панели

    private Row row(T type, String section, String label) {
        Row r = new Row();
        r.type = type;
        r.section = section;
        r.label = label;
        rows.add(r);
        return r;
    }

    private void header(String title) {
        row(T.HEADER, title, title);
    }

    private void seg(String section, String label, List<String> opts, int sel, IntConsumer on) {
        Row r = row(T.SEG, section, label);
        r.opts = opts;
        r.sel = sel;
        r.onSel = on;
    }

    private void step(String section, String label, String value, Runnable dec, Runnable inc) {
        Row r = row(T.STEP, section, label);
        r.value = value;
        r.dec = dec;
        r.inc = inc;
    }

    private void toggle(String section, String label, boolean on, Runnable flip) {
        Row r = row(T.TOGGLE, section, label);
        r.on = on;
        r.click = flip;
    }

    private void info(String section, String label, String value) {
        Row r = row(T.INFO, section, label);
        r.value = value;
    }

    private void note(String section, String text) {
        row(T.NOTE, section, text);
    }

    private void turnStep(String label, int value, IntConsumer set) {
        step("ПОВОРОТЫ БЛОКОВ", label, (value * 90) + "°", () -> set.accept((value + 3) % 4),
                () -> set.accept((value + 1) % 4));
    }

    private void buildRows() {
        rows.clear();
        BuildMode mode = s.mode;
        RoadSpec spec = s.toSpec();
        CrossSection cs = CrossSection.build(spec);

        // --- дорога ---
        String sec = mode == BuildMode.ROAD ? "ДОРОГА" : (mode == BuildMode.PATH ? "ДОРОГА (ПУТЬ)" : "ГЛАВНАЯ ДОРОГА");
        header(sec);
        RoadPreset[] presets = RoadPreset.values();
        List<String> pn = new ArrayList<>();
        for (RoadPreset p : presets) {
            pn.add(p.title);
        }
        seg(sec, "Тип", pn, s.preset.ordinal(), i -> s.applyPreset(presets[i]));
        seg(sec, "Полосы", List.of("2", "4", "6"), Math.max(0, Math.min(2, s.lanes / 2 - 1)), i -> s.lanes = 2 + i * 2);
        info(sec, "Ширина", cs.width() + " бл.");
        step(sec, "Ширина полосы", String.valueOf(s.laneWidth),
                () -> s.laneWidth = Math.max(2, s.laneWidth - 1), () -> s.laneWidth = Math.min(5, s.laneWidth + 1));
        step(sec, "Тротуар", String.valueOf(s.sidewalkWidth),
                () -> s.sidewalkWidth = Math.max(0, s.sidewalkWidth - 1),
                () -> s.sidewalkWidth = Math.min(8, s.sidewalkWidth + 1));
        step(sec, "Разделитель (нечёт.)", String.valueOf(s.medianWidth),
                () -> s.medianWidth = s.medianWidth <= 1 ? 0 : s.medianWidth - 2,
                () -> s.medianWidth = s.medianWidth == 0 ? 1 : Math.min(9, s.medianWidth + 2));
        step(sec, "Обочина (газон)", String.valueOf(s.shoulderWidth),
                () -> s.shoulderWidth = Math.max(0, s.shoulderWidth - 1),
                () -> s.shoulderWidth = Math.min(4, s.shoulderWidth + 1));
        if (mode != BuildMode.ROUNDABOUT) {
            seg(sec, "Ограждения", List.of("Нет", "Разд.", "Края", "Всё"), s.guard, i -> s.guard = i);
        }
        step(sec, "Фонари, шаг (0=нет)", String.valueOf(s.lampSpacing),
                () -> s.lampSpacing = s.lampSpacing <= 6 ? 0 : s.lampSpacing - 2,
                () -> s.lampSpacing = s.lampSpacing == 0 ? 6 : Math.min(48, s.lampSpacing + 2));
        step(sec, "Деревья, шаг (0=нет)", String.valueOf(s.treeSpacing),
                () -> s.treeSpacing = s.treeSpacing <= 5 ? 0 : s.treeSpacing - 1,
                () -> s.treeSpacing = s.treeSpacing == 0 ? 8 : Math.min(32, s.treeSpacing + 1));
        if (mode == BuildMode.ROAD) {
            step(sec, "Переход, шаг (0=нет)", String.valueOf(s.crossEvery),
                    () -> s.crossEvery = s.crossEvery <= 16 ? 0 : s.crossEvery - 8,
                    () -> s.crossEvery = s.crossEvery == 0 ? 24 : Math.min(128, s.crossEvery + 8));
        }
        toggle(sec, "Разметка", s.markings, () -> s.markings = !s.markings);
        toggle(sec, "Двойная осевая", s.doubleCenter, () -> s.doubleCenter = !s.doubleCenter);
        toggle(sec, "Краевые линии", s.edgeLines, () -> s.edgeLines = !s.edgeLines);
        if (mode == BuildMode.ROAD) {
            toggle(sec, "Торцы-уклоны", s.endCaps, () -> s.endCaps = !s.endCaps);
        }

        // --- парковка ---
        if (mode == BuildMode.ROAD) {
            String ps = "ПАРКОВКА";
            header(ps);
            ParkType[] types = ParkType.values();
            List<String> tn = new ArrayList<>();
            for (ParkType t : types) {
                tn.add(t.shortTitle);
            }
            seg(ps, "Тип", tn, s.parkType.ordinal(), i -> {
                ParkType t = types[i];
                s.parkType = t;
                if (t != ParkType.NONE) {
                    s.parkDepth = t.depth;
                    s.parkPitch = t.pitch;
                    if (!s.parkLeft && !s.parkRight) {
                        s.parkRight = true;
                    }
                }
            });
            toggle(ps, "Справа (по ходу)", s.parkRight, () -> s.parkRight = !s.parkRight);
            toggle(ps, "Слева", s.parkLeft, () -> s.parkLeft = !s.parkLeft);
            step(ps, "Глубина места", String.valueOf(s.parkDepth),
                    () -> s.parkDepth = Math.max(1, s.parkDepth - 1), () -> s.parkDepth = Math.min(6, s.parkDepth + 1));
            step(ps, "Шаг мест", String.valueOf(s.parkPitch),
                    () -> s.parkPitch = Math.max(2, s.parkPitch - 1), () -> s.parkPitch = Math.min(10, s.parkPitch + 1));
        }

        // --- путь ---
        if (mode == BuildMode.PATH) {
            String ps = "ИЗГИБЫ";
            header(ps);
            step(ps, "Радиус скругления", String.valueOf(s.cornerRadius),
                    () -> s.cornerRadius = Math.max(0, s.cornerRadius - 1),
                    () -> s.cornerRadius = Math.min(8, s.cornerRadius + 1));
            note(ps, "Углы любые, на изломах дуга. Ширина - по разделу «Дорога».");
        }

        // --- перекрёсток / кольцо ---
        if (mode == BuildMode.INTERSECTION || mode == BuildMode.ROUNDABOUT) {
            String cx = mode == BuildMode.ROUNDABOUT ? "КОЛЬЦО" : "ПЕРЕКРЁСТОК";
            header(cx);
            seg(cx, "Поперечная, полос", List.of("2", "4", "6"),
                    Math.max(0, Math.min(2, s.crossLanes / 2 - 1)), i -> s.crossLanes = 2 + i * 2);
            toggle(cx, "Ветка север", s.armN, () -> s.armN = !s.armN);
            toggle(cx, "Ветка восток", s.armE, () -> s.armE = !s.armE);
            toggle(cx, "Ветка юг", s.armS, () -> s.armS = !s.armS);
            toggle(cx, "Ветка запад", s.armW, () -> s.armW = !s.armW);
            step(cx, "Длина веток", String.valueOf(s.armLength),
                    () -> s.armLength = Math.max(4, s.armLength - 2), () -> s.armLength = Math.min(48, s.armLength + 2));
            if (mode == BuildMode.ROUNDABOUT) {
                step(cx, "Радиус острова", String.valueOf(s.islandRadius),
                        () -> s.islandRadius = Math.max(3, s.islandRadius - 1),
                        () -> s.islandRadius = Math.min(14, s.islandRadius + 1));
                seg(cx, "Полос на кольце", List.of("1", "2"), Math.max(0, Math.min(1, s.ringLanes - 1)),
                        i -> s.ringLanes = i + 1);
            } else {
                step(cx, "Радиус углов", String.valueOf(s.cornerRadius),
                        () -> s.cornerRadius = Math.max(0, s.cornerRadius - 1),
                        () -> s.cornerRadius = Math.min(8, s.cornerRadius + 1));
                note(cx, "Две соседние ветки = поворот по дуге.");
            }
            toggle(cx, "Зебры и стоп-линии", s.crosswalks, () -> s.crosswalks = !s.crosswalks);
        }

        // --- блоки ---
        String bs = "БЛОКИ";
        header(bs);
        for (Role role : Role.values()) {
            Row r = row(T.BLOCK, bs, role.title);
            final Role rr = role;
            r.blockId = s.get(role);
            r.click = () -> minecraft.setScreen(new BlockPickerScreen(this, rr, s.get(rr), picked -> {
                s.set(rr, picked);
                changed();
            }));
        }

        // --- подгонка ориентации ---
        String ts = "ПОВОРОТЫ БЛОКОВ";
        header(ts);
        note(ts, "Подгонка ориентации моделей (×90°). Превью сразу показывает результат.");
        turnStep("Бордюр", s.turnSidewalk, v -> s.turnSidewalk = v);
        turnStep("Бордюр: внешний угол", s.turnCornerOut, v -> s.turnCornerOut = v);
        turnStep("Бордюр: внутр. угол", s.turnCornerIn, v -> s.turnCornerIn = v);
        turnStep("Бордюр: диагональ", s.turnCurbDiago, v -> s.turnCurbDiago = v);
        turnStep("Ограждение", s.turnGuard, v -> s.turnGuard = v);
        turnStep("Фонарь", s.turnLamp, v -> s.turnLamp = v);
        turnStep("Торец-уклон", s.turnCap, v -> s.turnCap = v);
        turnStep("Линия", s.turnLine, v -> s.turnLine = v);
        turnStep("Зебра", s.turnCross, v -> s.turnCross = v);
        turnStep("Диагональ (парковка)", s.turnDiag, v -> s.turnDiag = v);
    }

    private void changed() {
        s.sanitize();
        cache = null;
        ClientState.pushToServer();
        buildRows();
    }

    // ------------------------------------------------------------ init

    @Override
    protected void init() {
        px1 = LEFT_W + 16;
        py1 = 28;
        px2 = width - RIGHT_W - 14;
        py2 = height - 40;
        rx = width - RIGHT_W - 6;
        ry1 = 54;
        ry2 = height - 40;
        search = new EditBox(font, rx + 8, 30, RIGHT_W - 16, 16, Component.literal("Поиск"));
        search.setHint(Component.literal("Найти параметр..."));
        search.setResponder(t -> {
            query = t.toLowerCase(Locale.ROOT).trim();
            scroll = 0;
        });
        addRenderableWidget(search);
        buildRows();
    }

    // ------------------------------------------------------------ отрисовка

    private void box(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
        g.fill(x0, y0, x1, y1, color);
    }

    private void button(GuiGraphics g, int x, int y, int w, int h, String text, int color, int mx, int my, Runnable act) {
        boolean hot = mx >= x && mx < x + w && my >= y && my < y + h;
        box(g, x, y, x + w, y + h, hot ? lighten(color) : color);
        g.drawCenteredString(font, font.plainSubstrByWidth(text, w - 6), x + w / 2, y + (h - 8) / 2, COL_TEXT);
        hits.add(new Hit(x, y, x + w, y + h, act));
    }

    private static int lighten(int c) {
        int r = Math.min(255, ((c >> 16) & 255) + 22);
        int g = Math.min(255, ((c >> 8) & 255) + 22);
        int b = Math.min(255, (c & 255) + 22);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        hits.clear();

        // верхняя строка
        g.drawString(font, "RoadForge", 10, 10, 0xFFFFFFFF);
        g.drawString(font, font.plainSubstrByWidth(ClientState.pointsText(), Math.max(40, width - 160)), 82, 10, 0xFF8FD0FF);

        renderLeft(g, mx, my);

        box(g, px1, py1, px2, py2, 0xFF11161C);
        renderPreview(g);
        RoadSpec spec = s.toSpec();
        CrossSection cs = CrossSection.build(spec);
        g.drawString(font, "Профиль " + cs.width() + " бл.   ЛКМ-тяни: вращать   колесо: масштаб", px1 + 4, py2 + 5, COL_DIM);

        renderRight(g, mx, my);
        renderBottom(g, mx, my);
        super.render(g, mx, my, pt);     // поле поиска
    }

    private void renderLeft(GuiGraphics g, int mx, int my) {
        int x = 8;
        int y = 28;
        box(g, x, y, x + LEFT_W, height - 40, COL_PANEL);
        g.drawString(font, "ИНСТРУМЕНТЫ", x + 8, y + 7, COL_DIM);
        y += 22;
        String[][] tools = {
                {"Дорога", "две точки, прямая"},
                {"Путь", "ломаная, любые углы"},
                {"Перекрёсток", "центр + ветки, поворот"},
                {"Кольцо", "остров, ветки, переходы"}
        };
        BuildMode[] modes = {BuildMode.ROAD, BuildMode.PATH, BuildMode.INTERSECTION, BuildMode.ROUNDABOUT};
        for (int i = 0; i < tools.length; i++) {
            final BuildMode m = modes[i];
            boolean sel = s.mode == m;
            boolean hot = mx >= x + 6 && mx < x + LEFT_W - 6 && my >= y && my < y + 34;
            box(g, x + 6, y, x + LEFT_W - 6, y + 34, sel ? 0xFF22345F : (hot ? COL_ROW_HOT : COL_ROW));
            if (sel) {
                box(g, x + 6, y, x + 9, y + 34, COL_ACCENT);
            }
            g.drawString(font, tools[i][0], x + 16, y + 6, COL_TEXT);
            g.drawString(font, font.plainSubstrByWidth(tools[i][1], LEFT_W - 28), x + 16, y + 19, COL_DIM);
            hits.add(new Hit(x + 6, y, x + LEFT_W - 6, y + 34, () -> {
                s.mode = m;
                scroll = 0;
                changed();
            }));
            y += 38;
        }
        y += 6;
        g.drawString(font, "ЖЕЗЛ", x + 8, y, COL_DIM);
        y += 14;
        String[] help = {"ПКМ - точка (можно", "целиться в воздух)", "Shift+ПКМ - построить", "ЛКМ - убрать точку",
                "Клавиша K - это меню"};
        for (String line : help) {
            g.drawString(font, line, x + 8, y, 0xFFB5BCC8);
            y += 11;
        }
        y += 8;
        button(g, x + 6, y, LEFT_W - 12, 20, "Сбросить точки", COL_ROW, mx, my,
                () -> Net.CHANNEL.sendToServer(new ActionPacket(ActionPacket.Action.CLEAR)));
    }

    private boolean visible(Row r) {
        if (query.isEmpty()) {
            return r.type == T.HEADER || !collapsed.contains(r.section);
        }
        if (r.type == T.HEADER) {
            return false;       // при поиске заголовки скрыты, остаются совпавшие строки
        }
        return r.label.toLowerCase(Locale.ROOT).contains(query) || r.section.toLowerCase(Locale.ROOT).contains(query);
    }

    private void renderRight(GuiGraphics g, int mx, int my) {
        box(g, rx, 28, rx + RIGHT_W, height - 40, COL_PANEL);
        g.drawString(font, "СВОЙСТВА", rx + 8, 18, COL_DIM);

        List<Row> vis = new ArrayList<>();
        for (Row r : rows) {
            if (visible(r) || (query.isEmpty() && r.type == T.HEADER)) {
                vis.add(r);
            }
        }
        int total = 0;
        for (Row r : vis) {
            total += r.type == T.HEADER ? HEAD_H : ROW_H;
        }
        int viewH = ry2 - ry1;
        int maxScroll = Math.max(0, total - viewH);
        scroll = Mth.clamp(scroll, 0, maxScroll);

        g.enableScissor(rx, ry1, rx + RIGHT_W, ry2);
        int y = ry1 - scroll;
        int cx0 = rx + 8;
        int cx1 = rx + RIGHT_W - 8;
        int ctrlW = 150;
        int ctrlX = cx1 - ctrlW;
        for (Row r : vis) {
            int h = r.type == T.HEADER ? HEAD_H : ROW_H;
            if (y + h < ry1 || y > ry2) {
                y += h;
                continue;
            }
            switch (r.type) {
                case HEADER -> {
                    boolean closed = collapsed.contains(r.section);
                    boolean hot = mx >= cx0 && mx < cx1 && my >= y && my < y + h && my >= ry1 && my < ry2;
                    g.drawString(font, (closed ? "▶ " : "▼ ") + r.label, cx0, y + 6, hot ? 0xFFFFFFFF : 0xFF9FB4D8);
                    final String sec = r.section;
                    hits.add(new Hit(cx0, Math.max(y, ry1), cx1, Math.min(y + h, ry2), () -> {
                        if (!collapsed.remove(sec)) {
                            collapsed.add(sec);
                        }
                    }));
                }
                case NOTE -> g.drawString(font, font.plainSubstrByWidth(r.label, cx1 - cx0), cx0, y + 7, COL_DIM);
                case INFO -> {
                    g.drawString(font, r.label, cx0, y + 7, COL_DIM);
                    g.drawString(font, r.value, cx1 - font.width(r.value), y + 7, COL_TEXT);
                }
                case SEG -> {
                    g.drawString(font, r.label, cx0, y + 7, COL_TEXT);
                    int n = r.opts.size();
                    int sw = ctrlW / n;
                    for (int i = 0; i < n; i++) {
                        int x0 = ctrlX + i * sw;
                        int x1 = i == n - 1 ? ctrlX + ctrlW : x0 + sw;
                        boolean sel = i == r.sel;
                        boolean hot = mx >= x0 && mx < x1 && my >= y + 1 && my < y + h - 1 && my >= ry1 && my < ry2;
                        box(g, x0, y + 2, x1 - 1, y + h - 2, sel ? COL_ACCENT : (hot ? COL_ROW_HOT : COL_ROW));
                        g.drawCenteredString(font, font.plainSubstrByWidth(r.opts.get(i), sw - 4), (x0 + x1) / 2,
                                y + 7, COL_TEXT);
                        final int idx = i;
                        final IntConsumer cb = r.onSel;
                        hits.add(new Hit(x0, Math.max(y + 2, ry1), x1, Math.min(y + h - 2, ry2), () -> {
                            cb.accept(idx);
                            changed();
                        }));
                    }
                }
                case STEP -> {
                    g.drawString(font, r.label, cx0, y + 7, COL_TEXT);
                    int bw = 22;
                    int vx0 = ctrlX + bw + 2;
                    int vx1 = ctrlX + ctrlW - bw - 2;
                    stepBtn(g, ctrlX, y + 2, bw, h - 4, "‹", mx, my, r.dec);
                    box(g, vx0, y + 2, vx1, y + h - 2, COL_ROW);
                    g.drawCenteredString(font, r.value, (vx0 + vx1) / 2, y + 7, COL_TEXT);
                    stepBtn(g, ctrlX + ctrlW - bw, y + 2, bw, h - 4, "›", mx, my, r.inc);
                }
                case TOGGLE -> {
                    g.drawString(font, r.label, cx0, y + 7, COL_TEXT);
                    int sx = cx1 - 30;
                    box(g, sx, y + 4, sx + 30, y + h - 4, r.on ? COL_ACCENT : COL_ROW);
                    box(g, r.on ? sx + 17 : sx + 2, y + 6, r.on ? sx + 28 : sx + 13, y + h - 6, 0xFFE6E9EF);
                    final Runnable flip = r.click;
                    hits.add(new Hit(cx0, Math.max(y, ry1), cx1, Math.min(y + h, ry2), () -> {
                        flip.run();
                        changed();
                    }));
                }
                case BLOCK -> {
                    g.drawString(font, r.label, cx0, y + 7, COL_TEXT);
                    boolean hot = mx >= ctrlX && mx < cx1 && my >= y + 1 && my < y + h - 1 && my >= ry1 && my < ry2;
                    box(g, ctrlX, y + 1, cx1, y + h - 1, hot ? COL_ROW_HOT : COL_ROW);
                    String name = BlockLookup.find(r.blockId).map(b -> b.getName().getString()).orElse(r.blockId);
                    g.drawString(font, font.plainSubstrByWidth(name, ctrlW - 26), ctrlX + 4, y + 7, COL_TEXT);
                    final int iy = y + 3;
                    BlockLookup.find(r.blockId).ifPresent(b -> g.renderItem(new ItemStack(b), cx1 - 18, iy));
                    final Runnable open = r.click;
                    hits.add(new Hit(ctrlX, Math.max(y + 1, ry1), cx1, Math.min(y + h - 1, ry2), open));
                }
            }
            y += h;
        }
        g.disableScissor();
        if (total > viewH) {
            int barH = Math.max(18, viewH * viewH / total);
            int barY = ry1 + (viewH - barH) * scroll / Math.max(1, maxScroll);
            box(g, rx + RIGHT_W - 4, barY, rx + RIGHT_W - 2, barY + barH, 0xFF4A5366);
        }
        if (vis.isEmpty()) {
            g.drawString(font, "Ничего не найдено", rx + 10, ry1 + 8, COL_DIM);
        }
    }

    private void stepBtn(GuiGraphics g, int x, int y, int w, int h, String text, int mx, int my, Runnable act) {
        boolean hot = mx >= x && mx < x + w && my >= y && my < y + h && my >= ry1 && my < ry2;
        box(g, x, y, x + w, y + h, hot ? COL_ROW_HOT : COL_ROW);
        g.drawCenteredString(font, text, x + w / 2, y + (h - 8) / 2, COL_TEXT);
        hits.add(new Hit(x, Math.max(y, ry1), x + w, Math.min(y + h, ry2), () -> {
            act.run();
            changed();
        }));
    }

    private void renderBottom(GuiGraphics g, int mx, int my) {
        int y = height - 32;
        box(g, 0, height - 36, width, height, 0xF00F1217);
        button(g, 10, y, 150, 22, "Отменить постройку", COL_ROW, mx, my,
                () -> Net.CHANNEL.sendToServer(new ActionPacket(ActionPacket.Action.UNDO)));
        button(g, rx + RIGHT_W - 150 - 76, y, 76, 22, "Закрыть", COL_ROW, mx, my, this::onClose);
        button(g, rx + RIGHT_W - 150, y, 150, 22, "Строить   Enter", COL_ACCENT, mx, my,
                () -> Net.CHANNEL.sendToServer(new ActionPacket(ActionPacket.Action.BUILD)));
    }

    // ------------------------------------------------------------ превью

    private void rebuildPreview() {
        List<Placement> raw;
        if (s.mode == BuildMode.INTERSECTION) {
            RoadSettings ps = s.copy();
            ps.armLength = Math.min(ps.armLength, 8);
            raw = IntersectionPlan.generate(BlockPos.ZERO, ps, 0);
        } else if (s.mode == BuildMode.ROUNDABOUT) {
            RoadSettings ps = s.copy();
            ps.armLength = Math.min(ps.armLength, 8);
            raw = RoundaboutPlan.generate(BlockPos.ZERO, ps, 0);
        } else if (s.mode == BuildMode.PATH) {
            RoadSettings ps = s.copy();
            int leg = (int) Math.ceil(PathPlan.axisRadius(ps)) + 10;
            raw = PathPlan.generate(java.util.List.of(new BlockPos(0, 0, 0), new BlockPos(leg, 0, 0),
                    new BlockPos(leg + leg / 2, 0, leg)), ps, 0);
        } else {
            RoadSpec spec = s.toSpec();
            CrossSection cs = CrossSection.build(spec);
            int len = spec.lampSpacing() > 0 ? Mth.clamp(spec.lampSpacing() * 2, 16, 40) : 16;
            if (spec.crossEvery() > 0) {
                len = Math.max(len, Math.min(72, spec.crossEvery() / 2 + RoadPlan.CROSSWALK_DEPTH + 6));
            }
            raw = RoadPlan.generate(BlockPos.ZERO, Direction.SOUTH, len, spec, cs, s.toPalette(), 0);
        }

        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (Placement p : raw) {
            minX = Math.min(minX, p.pos().getX());
            maxX = Math.max(maxX, p.pos().getX());
            minZ = Math.min(minZ, p.pos().getZ());
            maxZ = Math.max(maxZ, p.pos().getZ());
        }
        if (raw.isEmpty()) {
            minX = maxX = minZ = maxZ = 0;
        }

        Map<BlockPos, BlockState> world = new LinkedHashMap<>();
        BlockState grass = Blocks.GRASS_BLOCK.defaultBlockState();
        for (int x = minX - 4; x <= maxX + 4; x++) {
            for (int z = minZ - 3; z <= maxZ + 3; z++) {
                world.put(new BlockPos(x, 0, z), grass);
            }
        }
        for (Placement p : raw) {
            world.put(p.pos(), p.state());
        }

        cache = new ArrayList<>(world.size());
        world.forEach((pos, st) -> cache.add(new Placement(pos, st)));

        centerX = (minX + maxX + 1) / 2f;
        centerZ = (minZ + maxZ + 1) / 2f;
        float extent = Math.max(maxX - minX + 9, maxZ - minZ + 7);
        baseScale = Math.max(3f, Math.min(px2 - px1, py2 - py1) * 0.9f / extent);
    }

    private void renderPreview(GuiGraphics g) {
        if (cache == null) {
            rebuildPreview();
        }
        int cx = (px1 + px2) / 2;
        int cy = (py1 + py2) / 2;
        float scale = baseScale * zoom;

        g.enableScissor(px1, py1, px2, py2);
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(cx, cy, 1000f);
        pose.scale(scale, -scale, scale);
        pose.mulPose(Axis.XP.rotationDegrees(pitch));
        pose.mulPose(Axis.YP.rotationDegrees(yaw));
        pose.translate(-centerX, -0.5f, -centerZ);

        RenderSystem.enableDepthTest();
        Lighting.setupFor3DItems();
        MultiBufferSource.BufferSource buffers = g.bufferSource();
        BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();

        for (Placement p : cache) {
            pose.pushPose();
            pose.translate(p.pos().getX(), p.pos().getY(), p.pos().getZ());
            dispatcher.renderSingleBlock(p.state(), pose, buffers,
                    LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
        g.flush();
        pose.popPose();
        Lighting.setupForFlatItems();
        g.disableScissor();
        RenderSystem.clear(256, Minecraft.ON_OSX);
    }

    // ------------------------------------------------------------ input

    private boolean inPreview(double mx, double my) {
        return mx >= px1 && mx < px2 && my >= py1 && my < py2;
    }

    private boolean inList(double mx, double my) {
        return mx >= rx && mx < rx + RIGHT_W && my >= ry1 && my < ry2;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            for (int i = hits.size() - 1; i >= 0; i--) {
                Hit h = hits.get(i);
                if (h.in(mx, my)) {
                    h.act().run();
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (button == 0 && inPreview(mx, my)) {
            yaw += (float) dx * 0.7f;
            pitch = Mth.clamp(pitch + (float) dy * 0.7f, 5f, 90f);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (inPreview(mx, my)) {
            zoom = Mth.clamp(zoom * (delta > 0 ? 1.1f : 0.9f), 0.3f, 5f);
            return true;
        }
        if (inList(mx, my)) {
            scroll -= (int) (delta * 24);
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if ((key == 257 || key == 335) && !search.isFocused()) {
            Net.CHANNEL.sendToServer(new ActionPacket(ActionPacket.Action.BUILD));
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        ClientState.save();
        super.onClose();
    }
}
