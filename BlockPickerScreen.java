package com.roadforge.client;

import com.roadforge.compat.BlockLookup;
import com.roadforge.road.BlockCatalog;
import com.roadforge.road.Role;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;

/** Выбор блока из списка (модовые / обычные) с поиском. */
public class BlockPickerScreen extends Screen {
    private static final int ROW_H = 24;

    private final Screen parent;
    private final Role role;
    private final String current;
    private final Consumer<String> onPick;

    private EditBox search;
    private Button modsBtn;
    private Button vanillaBtn;
    private int mode = 0; // 0 - моды, 1 - обычные
    private String query = "";
    private int scroll = 0;
    private List<String> filtered = new ArrayList<>();

    public BlockPickerScreen(Screen parent, Role role, String current, Consumer<String> onPick) {
        super(Component.literal("Блок: " + role.title));
        this.parent = parent;
        this.role = role;
        this.current = current;
        this.onPick = onPick;
    }

    @Override
    protected void init() {
        int cx = width / 2;
        search = new EditBox(font, cx - 120, 24, 240, 18, Component.literal("Поиск"));
        search.setValue(query);
        search.setResponder(t -> {
            query = t;
            scroll = 0;
            refilter();
        });
        addRenderableWidget(search);

        modsBtn = addRenderableWidget(Button.builder(Component.literal("Моды"), b -> setMode(0))
                .bounds(cx - 120, 46, 118, 20).build());
        vanillaBtn = addRenderableWidget(Button.builder(Component.literal("Обычные"), b -> setMode(1))
                .bounds(cx + 2, 46, 118, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Назад"), b -> minecraft.setScreen(parent))
                .bounds(cx - 50, height - 26, 100, 20).build());

        setInitialFocus(search);
        setMode(mode);
    }

    private void setMode(int m) {
        mode = m;
        modsBtn.setMessage(Component.literal(mode == 0 ? "§eМоды" : "Моды"));
        vanillaBtn.setMessage(Component.literal(mode == 1 ? "§eОбычные" : "Обычные"));
        scroll = 0;
        refilter();
    }

    private void refilter() {
        List<String> base = mode == 0 ? BlockCatalog.mods(role) : BlockCatalog.vanilla(role);
        String q = query.toLowerCase(Locale.ROOT).trim();
        filtered = new ArrayList<>();
        for (String id : base) {
            if (q.isEmpty() || id.toLowerCase(Locale.ROOT).contains(q) || nameOf(id).toLowerCase(Locale.ROOT).contains(q)) {
                filtered.add(id);
            }
        }
    }

    private static String nameOf(String id) {
        return BlockLookup.find(id).map(b -> b.getName().getString()).orElse(id);
    }

    private int listTop() {
        return 72;
    }

    private int listBottom() {
        return height - 32;
    }

    private int rows() {
        return Math.max(1, (listBottom() - listTop()) / ROW_H);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.drawCenteredString(font, "Выбор блока: " + role.title, width / 2, 10, 0xFFFFFF);

        int left = width / 2 - 120;
        int right = width / 2 + 120;
        g.fill(left - 2, listTop() - 2, right + 2, listBottom() + 2, 0xAA000000);

        int max = Math.max(0, filtered.size() - rows());
        scroll = Mth.clamp(scroll, 0, max);

        for (int i = 0; i < rows(); i++) {
            int idx = scroll + i;
            if (idx >= filtered.size()) {
                break;
            }
            String id = filtered.get(idx);
            int y = listTop() + i * ROW_H;
            boolean hover = mx >= left && mx < right && my >= y && my < y + ROW_H;
            if (hover) {
                g.fill(left, y, right, y + ROW_H, 0x55FFFFFF);
            }
            if (id.equals(current)) {
                g.fill(left, y, left + 2, y + ROW_H, 0xFFFFD040);
            }
            Optional<Block> b = BlockLookup.find(id);
            b.ifPresent(block -> g.renderItem(new ItemStack(block), left + 4, y + 4));
            g.drawString(font, font.plainSubstrByWidth(nameOf(id), 190), left + 26, y + 3, 0xFFFFFF);
            g.drawString(font, font.plainSubstrByWidth(id, 190), left + 26, y + 13, 0x909090);
        }
        if (filtered.isEmpty()) {
            g.drawCenteredString(font, "Ничего не найдено", width / 2, listTop() + 10, 0xAAAAAA);
        }
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) {
            return true;
        }
        int left = width / 2 - 120;
        int right = width / 2 + 120;
        if (button == 0 && mx >= left && mx < right && my >= listTop() && my < listTop() + rows() * ROW_H) {
            int idx = scroll + (int) ((my - listTop()) / ROW_H);
            if (idx >= 0 && idx < filtered.size()) {
                onPick.accept(filtered.get(idx));
                minecraft.setScreen(parent);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        scroll -= (int) Math.signum(delta);
        scroll = Mth.clamp(scroll, 0, Math.max(0, filtered.size() - rows()));
        return true;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
