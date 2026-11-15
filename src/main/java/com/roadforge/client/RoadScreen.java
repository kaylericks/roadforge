package com.roadforge.client;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.roadforge.compat.BlockLookup;
import com.roadforge.network.ActionPacket;
import com.roadforge.network.Net;
import com.roadforge.road.CrossSection;
import com.roadforge.road.Placement;
import com.roadforge.road.RoadPlan;
import com.roadforge.road.RoadPreset;
import com.roadforge.road.RoadSettings;
import com.roadforge.road.RoadSpec;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Главное меню: параметры дороги слева, живое 3D-превью справа. */
public class RoadScreen extends Screen {
    private static final int PANEL_X = 10;
    private static final int PANEL_W = 236;

    private record Label(int x, int y, String text) {}
    private record Icon(int x, int y, String id) {}

    private final RoadSettings s = ClientState.settings();
    private final List<Label> labels = new ArrayList<>();
    private final List<Icon> icons = new ArrayList<>();

    private int tab = 0; // 0 - дорога, 1 - блоки
    private float yaw = 30f;
    private float pitch = 40f;
    private float zoom = 1f;

    private int px1, py1, px2, py2;

    private List<Placement> cache;
    private float centerX, centerZ, baseScale;
    private int previewLen;
    private int previewWidth;

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
        int y = 28;
        btn(tab == 0 ? "§e▶ Дорога" : "Дорога", x, y, 112, () -> {
            tab = 0;
            rebuildWidgets();
        });
        btn(tab == 1 ? "§e▶ Блоки" : "Блоки", x + 118, y, 112, () -> {
            tab = 1;
            rebuildWidgets();
        });

        y += 28;
        if (tab == 0) {
            initRoadTab(x, y);
        } else {
            initBlocksTab(x, y);
        }

        int by = height - 28;
        btn("Построить", PANEL_X + 4, by, 74, () -> Net.CHANNEL.sendToServer(new ActionPacket(ActionPacket.Action.BUILD)));
        btn("Отменить", PANEL_X + 82, by, 74, () -> Net.CHANNEL.sendToServer(new ActionPacket(ActionPacket.Action.UNDO)));
        btn("Сброс точек", PANEL_X + 160, by, 74, () -> Net.CHANNEL.sendToServer(new ActionPacket(ActionPacket.Action.CLEAR)));
        btn("Закрыть", width - 100, by, 90, this::onClose);
    }

    private void initRoadTab(int x, int y) {
        label(x, y + 6, "Тип:");
        int bx = x + 34;
        for (RoadPreset p : RoadPreset.values()) {
            final RoadPreset preset = p;
            String t = s.preset == p ? "§e" + p.title : p.title;
            btn(t, bx, y, 66, () -> {
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
        stepper(x, y, "Ширина полосы", s.laneWidth, () -> s.laneWidth = Math.max(2, s.laneWidth - 1),
                () -> s.laneWidth = Math.min(5, s.laneWidth + 1));
        y += 22;
        stepper(x, y, "Тротуар", s.sidewalkWidth, () -> s.sidewalkWidth = Math.max(0, s.sidewalkWidth - 1),
                () -> s.sidewalkWidth = Math.min(8, s.sidewalkWidth + 1));
        y += 22;
        stepper(x, y, "Газон-разделитель", s.medianWidth, () -> s.medianWidth = Math.max(0, s.medianWidth - 1),
                () -> s.medianWidth = Math.min(9, s.medianWidth + 1));
        y += 22;
        stepper(x, y, "Обочина (газон)", s.shoulderWidth, () -> s.shoulderWidth = Math.max(0, s.shoulderWidth - 1),
                () -> s.shoulderWidth = Math.min(4, s.shoulderWidth + 1));
        y += 22;
        stepper(x, y, "Шаг фонарей (0=нет)", s.lampSpacing,
                () -> s.lampSpacing = s.lampSpacing <= 6 ? 0 : s.lampSpacing - 2,
                () -> s.lampSpacing = s.lampSpacing == 0 ? 6 : Math.min(48, s.lampSpacing + 2));

        y += 26;
        toggle(x, y, "Разметка", s.markings, () -> s.markings = !s.markings);
        toggle(x + 118, y, "Двойная осевая", s.doubleCenter, () -> s.doubleCenter = !s.doubleCenter);
        y += 22;
        toggle(x, y, "Краевые линии", s.edgeLines, () -> s.edgeLines = !s.edgeLines);
        toggle(x + 118, y, "Торцы-уклоны", s.endCaps, () -> s.endCaps = !s.endCaps);
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
            y += 26;
        }
        label(x, y + 4, "Клик по кнопке — список блоков (моды / обычные).");
    }

    private Button btn(String text, int x, int y, int w, Runnable action) {
        return addRenderableWidget(Button.builder(Component.literal(text), b -> action.run())
                .bounds(x, y, w, 20).build());
    }

    private void label(int x, int y, String text) {
        labels.add(new Label(x, y, text));
    }

    private void stepper(int x, int y, String text, int value, Runnable dec, Runnable inc) {
        label(x, y + 6, text);
        btn("-", x + 150, y, 20, () -> {
            dec.run();
            changed();
        });
        Button v = btn(String.valueOf(value), x + 172, y, 28, () -> {});
        v.active = false;
        btn("+", x + 202, y, 20, () -> {
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
        g.drawString(font, "Ширина профиля: " + cs.width() + " бл.  ·  ЛКМ-тяни: вращать, колесо: масштаб",
                px1 + 4, py2 + 6, 0xA0A0A0);
    }

    private void rebuildPreview() {
        RoadSpec spec = s.toSpec();
        CrossSection cs = CrossSection.build(spec);
        previewLen = spec.lampSpacing() > 0 ? Mth.clamp(spec.lampSpacing() * 2, 16, 40) : 16;
        previewWidth = cs.width();

        List<Placement> raw = RoadPlan.generate(BlockPos.ZERO, Direction.SOUTH, previewLen, spec, cs,
                s.toPalette(), 0);

        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        for (Placement p : raw) {
            minX = Math.min(minX, p.pos().getX());
            maxX = Math.max(maxX, p.pos().getX());
        }

        // фон: газон вокруг дороги
        Map<BlockPos, BlockState> world = new LinkedHashMap<>();
        BlockState grass = Blocks.GRASS_BLOCK.defaultBlockState();
        for (int x = minX - 4; x <= maxX + 4; x++) {
            for (int z = -3; z < previewLen + 3; z++) {
                world.put(new BlockPos(x, 0, z), grass);
            }
        }
        for (Placement p : raw) {
            world.put(p.pos(), p.state());
        }

        cache = new ArrayList<>(world.size());
        world.forEach((pos, st) -> cache.add(new Placement(pos, st)));

        centerX = (minX + maxX + 1) / 2f;
        centerZ = previewLen / 2f;
        float extent = Math.max(maxX - minX + 9, previewLen + 3);
        baseScale = Math.max(4f, Math.min(px2 - px1, py2 - py1) * 0.9f / extent);
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
