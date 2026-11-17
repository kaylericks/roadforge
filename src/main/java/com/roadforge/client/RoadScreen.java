package com.roadforge.client;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.roadforge.compat.BlockLookup;
import com.roadforge.network.ActionPacket;
import com.roadforge.network.Net;
import com.roadforge.road.BendPlan;
import com.roadforge.road.BuildMode;
import com.roadforge.road.CrossSection;
import com.roadforge.road.IntersectionPlan;
import com.roadforge.road.ParkType;
import com.roadforge.road.Placement;
import com.roadforge.road.RoadPlan;
import com.roadforge.road.RoadPreset;
import com.roadforge.road.RoadSettings;
import com.roadforge.road.RoadSpec;
import com.roadforge.road.RoundaboutPlan;
import com.roadforge.road.Role;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Главное меню: параметры слева (5 вкладок), живое 3D-превью справа. */
public class RoadScreen extends Screen {
    private static final int PANEL_X = 10;
    private static final int PANEL_W = 236;

    private record Label(int x, int y, String text) {}
    private record Icon(int x, int y, String id) {}

    private final RoadSettings s = ClientState.settings();
    private final List<Label> labels = new ArrayList<>();
    private final List<Icon> icons = new ArrayList<>();

    private int tab = 0; // 0 дорога, 1 блоки, 2 парковка, 3 перекрёсток, 4 повороты
    private float yaw = 30f;
    private float pitch = 40f;
    private float zoom = 1f;

    private int px1, py1, px2, py2;

    private List<Placement> cache;
    private float centerX, centerZ, baseScale;

    public RoadScreen() {
        super(Component.literal("RoadForge"));
        ClientState.pushToServer();
    }

    // ------------------------------------------------------------ widgets

    @Override
    protected void init() {
        labels.clear();
        icons.clear();
        px1 = PANEL_X + PANEL_W + 14;
        py1 = 24;
        px2 = width - 10;
        py2 = height - 34;

        int x = PANEL_X + 4;
        tabBtn("Дорога", 0, x, 26, 74);
        tabBtn("Блоки", 1, x + 78, 26, 74);
        tabBtn("Парковка", 2, x + 156, 26, 72);
        tabBtn("Перекрёсток", 3, x, 48, 112);
        tabBtn("Повороты", 4, x + 116, 48, 112);

        int y = 76;
        switch (tab) {
            case 0 -> initRoadTab(x, y);
            case 1 -> initBlocksTab(x, y);
            case 2 -> initParkTab(x, y);
            case 3 -> initCrossTab(x, y);
            default -> initTurnTab(x, y);
        }

        int by = height - 28;
        btn("Построить", PANEL_X + 4, by, 74, () -> Net.CHANNEL.sendToServer(new ActionPacket(ActionPacket.Action.BUILD)));
        btn("Отменить", PANEL_X + 82, by, 74, () -> Net.CHANNEL.sendToServer(new ActionPacket(ActionPacket.Action.UNDO)));
        btn("Сброс точек", PANEL_X + 160, by, 74, () -> Net.CHANNEL.sendToServer(new ActionPacket(ActionPacket.Action.CLEAR)));
        btn("Закрыть", width - 100, by, 90, this::onClose);
    }

    private void tabBtn(String text, int idx, int x, int y, int w) {
        btn((tab == idx ? "§e▶ " : "") + text, x, y, w, () -> {
            tab = idx;
            rebuildWidgets();
        });
    }

    private void initRoadTab(int x, int y) {
        label(x, y + 6, "Тип:");
        int bx = x + 34;
        for (RoadPreset p : RoadPreset.values()) {
            final RoadPreset preset = p;
            btn(s.preset == p ? "§e" + p.title : p.title, bx, y, 66, () -> {
                s.applyPreset(preset);
                changed();
            });
            bx += 68;
        }

        y += 24;
        label(x, y + 6, "Полос:");
        for (int i = 0; i < 3; i++) {
            final int lanes = 2 + i * 2;
            btn((s.lanes == lanes ? "§e" : "") + lanes, x + 60 + i * 44, y, 40, () -> {
                s.lanes = lanes;
                changed();
            });
        }

        y += 26;
        stepper(x, y, "Ширина полосы", String.valueOf(s.laneWidth),
                () -> s.laneWidth = Math.max(2, s.laneWidth - 1),
                () -> s.laneWidth = Math.min(5, s.laneWidth + 1));
        y += 20;
        stepper(x, y, "Тротуар", String.valueOf(s.sidewalkWidth),
                () -> s.sidewalkWidth = Math.max(0, s.sidewalkWidth - 1),
                () -> s.sidewalkWidth = Math.min(8, s.sidewalkWidth + 1));
        y += 20;
        stepper(x, y, "Разделитель (нечёт.)", String.valueOf(s.medianWidth),
                () -> s.medianWidth = s.medianWidth <= 1 ? 0 : s.medianWidth - 2,
                () -> s.medianWidth = s.medianWidth == 0 ? 1 : Math.min(9, s.medianWidth + 2));
        y += 20;
        stepper(x, y, "Обочина (газон)", String.valueOf(s.shoulderWidth),
                () -> s.shoulderWidth = Math.max(0, s.shoulderWidth - 1),
                () -> s.shoulderWidth = Math.min(4, s.shoulderWidth + 1));
        y += 20;
        stepper(x, y, "Шаг фонарей (0=нет)", String.valueOf(s.lampSpacing),
                () -> s.lampSpacing = s.lampSpacing <= 6 ? 0 : s.lampSpacing - 2,
                () -> s.lampSpacing = s.lampSpacing == 0 ? 6 : Math.min(48, s.lampSpacing + 2));

        y += 26;
        toggle(x, y, "Разметка", s.markings, () -> s.markings = !s.markings);
        toggle(x + 118, y, "Двойная осевая", s.doubleCenter, () -> s.doubleCenter = !s.doubleCenter);
        y += 20;
        toggle(x, y, "Краевые линии", s.edgeLines, () -> s.edgeLines = !s.edgeLines);
        toggle(x + 118, y, "Торцы-уклоны", s.endCaps, () -> s.endCaps = !s.endCaps);
        y += 20;
        stepper(x, y, "Переход каждые (0=нет)", String.valueOf(s.crossEvery),
                () -> s.crossEvery = s.crossEvery <= 16 ? 0 : s.crossEvery - 8,
                () -> s.crossEvery = s.crossEvery == 0 ? 24 : Math.min(128, s.crossEvery + 8));
        y += 20;
        stepper(x, y, "Деревья, шаг (0=нет)", String.valueOf(s.treeSpacing),
                () -> s.treeSpacing = s.treeSpacing <= 5 ? 0 : s.treeSpacing - 1,
                () -> s.treeSpacing = s.treeSpacing == 0 ? 8 : Math.min(32, s.treeSpacing + 1));
    }

    private void initBlocksTab(int x, int y) {
        for (Role role : Role.values()) {
            final Role r = role;
            label(x, y + 6, role.title + ":");
            String id = s.get(role);
            String name = BlockLookup.find(id).map(b -> b.getName().getString()).orElse(id);
            btn(font.plainSubstrByWidth(name, 128), x + 62, y, 136, () ->
                    minecraft.setScreen(new BlockPickerScreen(this, r, s.get(r), picked -> {
                        s.set(r, picked);
                        changed();
                    })));
            icons.add(new Icon(x + 204, y + 2, id));
            y += 22;
        }
        label(x, y + 4, "Клик по кнопке: список блоков (моды / обычные).");
    }

    private void initParkTab(int x, int y) {
        label(x, y + 6, "Тип:");
        ParkType[] types = ParkType.values();
        for (int i = 0; i < types.length; i++) {
            final ParkType t = types[i];
            btn((s.parkType == t ? "§e" : "") + t.shortTitle, x + 34 + i * 50, y, 48, () -> {
                s.parkType = t;
                if (t != ParkType.NONE) {
                    s.parkDepth = t.depth;
                    s.parkPitch = t.pitch;
                    if (!s.parkLeft && !s.parkRight) {
                        s.parkRight = true;
                    }
                }
                changed();
            });
        }
        y += 26;
        toggle(x, y, "Справа (по ходу)", s.parkRight, () -> s.parkRight = !s.parkRight);
        toggle(x + 118, y, "Слева", s.parkLeft, () -> s.parkLeft = !s.parkLeft);
        y += 26;
        stepper(x, y, "Глубина места", String.valueOf(s.parkDepth),
                () -> s.parkDepth = Math.max(1, s.parkDepth - 1),
                () -> s.parkDepth = Math.min(6, s.parkDepth + 1));
        y += 22;
        stepper(x, y, "Шаг мест (блоков)", String.valueOf(s.parkPitch),
                () -> s.parkPitch = Math.max(2, s.parkPitch - 1),
                () -> s.parkPitch = Math.min(10, s.parkPitch + 1));
        y += 30;
        label(x, y, "Парковка идёт между полосой и тротуаром.");
        label(x, y + 12, "На перекрёстках она рисуется как асфальт.");
    }

    private void initCrossTab(int x, int y) {
        modeBtn("Дорога", BuildMode.ROAD, x, y, 54);
        modeBtn("Перекр.", BuildMode.INTERSECTION, x + 58, y, 54);
        modeBtn("Кольцо", BuildMode.ROUNDABOUT, x + 116, y, 54);
        modeBtn("Изгиб", BuildMode.CURVE, x + 174, y, 54);
        y += 26;
        if (s.mode == BuildMode.CURVE) {
            initCurveTab(x, y);
            return;
        }
        label(x, y + 6, "Поперечная:");
        for (int i = 0; i < 3; i++) {
            final int lanes = 2 + i * 2;
            btn((s.crossLanes == lanes ? "§e" : "") + lanes, x + 80 + i * 44, y, 40, () -> {
                s.crossLanes = lanes;
                changed();
            });
        }
        y += 26;
        label(x, y + 6, "Ветки:");
        smallToggle(x + 50, y, "С", s.armN, () -> s.armN = !s.armN);
        smallToggle(x + 92, y, "В", s.armE, () -> s.armE = !s.armE);
        smallToggle(x + 134, y, "Ю", s.armS, () -> s.armS = !s.armS);
        smallToggle(x + 176, y, "З", s.armW, () -> s.armW = !s.armW);
        y += 26;
        stepper(x, y, "Длина веток", String.valueOf(s.armLength),
                () -> s.armLength = Math.max(4, s.armLength - 2),
                () -> s.armLength = Math.min(48, s.armLength + 2));
        y += 22;
        if (s.mode == BuildMode.ROUNDABOUT) {
            stepper(x, y, "Радиус острова", String.valueOf(s.islandRadius),
                    () -> s.islandRadius = Math.max(3, s.islandRadius - 1),
                    () -> s.islandRadius = Math.min(14, s.islandRadius + 1));
            y += 22;
            stepper(x, y, "Полос на кольце", String.valueOf(s.ringLanes),
                    () -> s.ringLanes = Math.max(1, s.ringLanes - 1),
                    () -> s.ringLanes = Math.min(2, s.ringLanes + 1));
        } else {
            stepper(x, y, "Радиус углов", String.valueOf(s.cornerRadius),
                    () -> s.cornerRadius = Math.max(0, s.cornerRadius - 1),
                    () -> s.cornerRadius = Math.min(8, s.cornerRadius + 1));
        }
        y += 26;
        toggle(x, y, "Зебры и стоп-линии", s.crosswalks, () -> s.crosswalks = !s.crosswalks);
        y += 28;
        label(x, y, "Главная дорога = настройки вкладки «Дорога».");
        label(x, y + 12, "Центр ставится одним ПКМ жезла,");
        label(x, y + 24, "Shift+ПКМ строит.");
        if (s.mode == BuildMode.ROUNDABOUT) {
            label(x, y + 36, "Кольцо: до 4 веток, вокруг - площадка.");
        } else {
            label(x, y + 36, "Две соседние ветки = поворот по дуге.");
        }
    }

    private void initCurveTab(int x, int y) {
        stepper(x, y, "Радиус дуги", String.valueOf(s.cornerRadius),
                () -> s.cornerRadius = Math.max(0, s.cornerRadius - 1),
                () -> s.cornerRadius = Math.min(8, s.cornerRadius + 1));
        y += 26;
        toggle(x, y, s.curveFirstX ? "От A: вдоль X" : "От A: вдоль Z", true, () -> s.curveFirstX = !s.curveFirstX);
        y += 30;
        label(x, y, "Отметь жезлом точки A и B (ПКМ, ПКМ),");
        label(x, y + 12, "Shift+ПКМ строит. Дорога идёт от A прямо,");
        label(x, y + 24, "поворачивает на 90° дугой и идёт к B.");
        label(x, y + 36, "Ширина - по вкладке «Дорога».");
    }

    private void modeBtn(String text, BuildMode mode, int x, int y, int w) {
        btn((s.mode == mode ? "§e" : "") + text, x, y, w, () -> {
            s.mode = mode;
            changed();
        });
    }

    private void initTurnTab(int x, int y) {
        label(x, y, "Подгонка ориентации моделей (×90°).");
        label(x, y + 12, "Превью справа сразу показывает результат.");
        y += 28;
        stepper(x, y, "Бордюр", deg(s.turnSidewalk), () -> s.turnSidewalk = (s.turnSidewalk + 3) % 4,
                () -> s.turnSidewalk = (s.turnSidewalk + 1) % 4);
        y += 22;
        stepper(x, y, "Фонарь", deg(s.turnLamp), () -> s.turnLamp = (s.turnLamp + 3) % 4,
                () -> s.turnLamp = (s.turnLamp + 1) % 4);
        y += 22;
        stepper(x, y, "Торец-уклон", deg(s.turnCap), () -> s.turnCap = (s.turnCap + 3) % 4,
                () -> s.turnCap = (s.turnCap + 1) % 4);
        y += 22;
        stepper(x, y, "Линия", deg(s.turnLine), () -> s.turnLine = (s.turnLine + 3) % 4,
                () -> s.turnLine = (s.turnLine + 1) % 4);
        y += 22;
        stepper(x, y, "Зебра", deg(s.turnCross), () -> s.turnCross = (s.turnCross + 3) % 4,
                () -> s.turnCross = (s.turnCross + 1) % 4);
        y += 22;
        stepper(x, y, "Диагональ (парковка)", deg(s.turnDiag), () -> s.turnDiag = (s.turnDiag + 3) % 4,
                () -> s.turnDiag = (s.turnDiag + 1) % 4);
        y += 22;
        stepper(x, y, "Бордюр: внешний угол", deg(s.turnCornerOut), () -> s.turnCornerOut = (s.turnCornerOut + 3) % 4,
                () -> s.turnCornerOut = (s.turnCornerOut + 1) % 4);
        y += 22;
        stepper(x, y, "Бордюр: внутр. угол", deg(s.turnCornerIn), () -> s.turnCornerIn = (s.turnCornerIn + 3) % 4,
                () -> s.turnCornerIn = (s.turnCornerIn + 1) % 4);
        y += 22;
        stepper(x, y, "Бордюр: диагональ", deg(s.turnCurbDiago), () -> s.turnCurbDiago = (s.turnCurbDiago + 3) % 4,
                () -> s.turnCurbDiago = (s.turnCurbDiago + 1) % 4);
    }

    private static String deg(int turns) {
        return (turns * 90) + "°";
    }

    private Button btn(String text, int x, int y, int w, Runnable action) {
        return addRenderableWidget(Button.builder(Component.literal(text), b -> action.run())
                .bounds(x, y, w, 20).build());
    }

    private void label(int x, int y, String text) {
        labels.add(new Label(x, y, text));
    }

    private void stepper(int x, int y, String text, String valueText, Runnable dec, Runnable inc) {
        label(x, y + 6, text);
        btn("-", x + 140, y, 20, () -> {
            dec.run();
            changed();
        });
        Button v = btn(valueText, x + 162, y, 36, () -> {});
        v.active = false;
        btn("+", x + 200, y, 20, () -> {
            inc.run();
            changed();
        });
    }

    private void toggle(int x, int y, String text, boolean on, Runnable flip) {
        btn((on ? "§a" : "§c") + text, x, y, 114, () -> {
            flip.run();
            changed();
        });
    }

    private void smallToggle(int x, int y, String text, boolean on, Runnable flip) {
        btn((on ? "§a" : "§c") + text, x, y, 38, () -> {
            flip.run();
            changed();
        });
    }

    private void changed() {
        s.sanitize();
        cache = null;
        ClientState.pushToServer();
        rebuildWidgets();
    }

    // ------------------------------------------------------------ rendering

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.fill(PANEL_X, 24, PANEL_X + PANEL_W + 6, height - 34, 0xAA000000);
        g.fill(px1, py1, px2, py2, 0xFF11161C);

        g.drawString(font, "RoadForge", PANEL_X + 4, 10, 0xFFFFFF);
        g.drawString(font, font.plainSubstrByWidth(ClientState.pointsText(), Math.max(40, width - 120)),
                PANEL_X + 80, 10, 0x8FD0FF);

        for (Label l : labels) {
            g.drawString(font, l.text(), l.x(), l.y(), 0xE0E0E0);
        }
        super.render(g, mx, my, pt);
        for (Icon ic : icons) {
            BlockLookup.find(ic.id()).ifPresent(b -> g.renderItem(new ItemStack(b), ic.x(), ic.y()));
        }

        renderPreview(g);

        RoadSpec spec = s.toSpec();
        CrossSection cs = CrossSection.build(spec);
        g.drawString(font, "Профиль: " + cs.width() + " бл.  ·  ЛКМ-тяни: вращать, колесо: масштаб",
                px1 + 4, py2 + 6, 0xA0A0A0);
    }

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
        } else if (s.mode == BuildMode.CURVE) {
            RoadSettings ps = s.copy();
            int end = BendPlan.radius(ps) + 8;
            raw = BendPlan.generate(BlockPos.ZERO, Direction.WEST, Direction.SOUTH, end, end, false, ps, 0);
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
        return super.mouseScrolled(mx, my, delta);
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
