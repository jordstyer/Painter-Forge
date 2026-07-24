package com.painter.client;

import com.painter.BrushData;
import com.painter.ModItems;
import com.painter.net.GridUpdatePacket;
import com.painter.net.PainterNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Configuration GUI for the Paintbrush. Left: the editable N x N grid template.
 * Right: a searchable, scrollable icon grid of every block. Click a grid cell to
 * select it, then click a block to assign it (right-click a cell = RANDOM). "Apply"
 * sends the grid to the server (see {@link GridUpdatePacket}).
 */
public class PaintbrushScreen extends Screen {

    // --- layout ---
    private static final int IMG_W = 300;
    private static final int IMG_H = 214;
    private static final int CELL = 20;       // grid cell pitch
    private static final int SLOT = 18;       // drawn slot size
    private static final int PCOLS = 6;       // picker columns
    private static final int PROWS = 6;       // picker visible rows

    private int leftPos;
    private int topPos;
    private int gridX;
    private int gridY;
    private int pickerX;
    private int pickerY; // top of the icon area

    // --- state ---
    private int size = 1;
    private String[] cells = new String[0]; // row-major, "" = RANDOM
    private int selectedCell = -1;
    private int scroll = 0;

    private EditBox searchBox;
    private List<PickEntry> filtered = new ArrayList<>();

    /** One entry per placeable block, cached across screen instances. */
    private record PickEntry(Block block, ItemStack icon, String id, String search) {}

    private static List<PickEntry> ALL;

    public PaintbrushScreen() {
        super(Component.literal("Paintbrush"));
    }

    private static List<PickEntry> allBlocks() {
        if (ALL == null) {
            List<PickEntry> list = new ArrayList<>();
            for (Block block : BuiltInRegistries.BLOCK) {
                ItemStack icon = new ItemStack(block);
                if (icon.isEmpty() || block.asItem() == Items.AIR) continue;
                String id = BuiltInRegistries.BLOCK.getKey(block).toString();
                String search = (id + " " + block.getName().getString()).toLowerCase(Locale.ROOT);
                list.add(new PickEntry(block, icon, id, search));
            }
            list.sort(Comparator.comparing(PickEntry::id));
            ALL = list;
        }
        return ALL;
    }

    @Override
    protected void init() {
        this.leftPos = (this.width - IMG_W) / 2;
        this.topPos = (this.height - IMG_H) / 2;
        this.gridX = leftPos + 18;
        this.gridY = topPos + 40;
        this.pickerX = leftPos + 150;
        this.pickerY = topPos + 48;

        ItemStack brush = this.minecraft.player.getMainHandItem();
        if (!brush.is(ModItems.PAINTBRUSH.get())) {
            this.onClose();
            return;
        }

        this.size = BrushData.getSize(brush, 1);
        this.cells = new String[size * size];
        for (int row = 0; row < size; row++) {
            for (int col = 0; col < size; col++) {
                Block b = BrushData.getCell(brush, size, row, col);
                cells[row * size + col] = (b == null) ? "" : BuiltInRegistries.BLOCK.getKey(b).toString();
            }
        }

        // Search box
        this.searchBox = new EditBox(this.font, pickerX, topPos + 26, PCOLS * CELL - 6, 16, Component.literal("Search"));
        this.searchBox.setHint(Component.literal("Search blocks..."));
        this.searchBox.setResponder(s -> updateFilter(s));
        addRenderableWidget(this.searchBox);
        setInitialFocus(this.searchBox);

        // Buttons
        int btnY = topPos + IMG_H - 24;
        addRenderableWidget(Button.builder(Component.literal("Set RANDOM"), b -> setSelectedRandom())
                .bounds(gridX, gridY + size * CELL + 10, 92, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Clear grid"), b -> clearGrid())
                .bounds(leftPos + 10, btnY, 90, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Apply & Close"), b -> apply())
                .bounds(leftPos + IMG_W - 110, btnY, 100, 18).build());

        updateFilter("");
    }

    private void updateFilter(String query) {
        String q = query == null ? "" : query.toLowerCase(Locale.ROOT).trim();
        List<PickEntry> result = new ArrayList<>();
        for (PickEntry e : allBlocks()) {
            if (q.isEmpty() || e.search.contains(q)) result.add(e);
        }
        this.filtered = result;
        this.scroll = 0;
    }

    private int maxScroll() {
        int rows = (filtered.size() + PCOLS - 1) / PCOLS;
        return Math.max(0, rows - PROWS);
    }

    private void setSelectedRandom() {
        if (selectedCell >= 0 && selectedCell < cells.length) cells[selectedCell] = "";
    }

    private void clearGrid() {
        for (int i = 0; i < cells.length; i++) cells[i] = "";
    }

    private void apply() {
        List<String> list = new ArrayList<>(cells.length);
        for (String c : cells) list.add(c == null ? "" : c);
        PainterNetwork.CHANNEL.sendToServer(new GridUpdatePacket(size, list));
        this.onClose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);

        // Panel
        g.fill(leftPos, topPos, leftPos + IMG_W, topPos + IMG_H, 0xF0141018);
        g.renderOutline(leftPos, topPos, IMG_W, IMG_H, 0xFF3A3450);

        g.drawString(this.font, "Paintbrush", leftPos + 10, topPos + 10, 0xFFFFFF, false);
        g.drawString(this.font, "Grid " + size + "x" + size, gridX, topPos + 28, 0xA0A0B0, false);
        g.drawString(this.font, "Blocks", pickerX, topPos + 14, 0xA0A0B0, false);

        Component hoverTip = null;

        // --- grid ---
        for (int row = 0; row < size; row++) {
            for (int col = 0; col < size; col++) {
                int idx = row * size + col;
                int cx = gridX + col * CELL;
                int cy = gridY + row * CELL;
                String id = cells[idx];
                boolean random = id == null || id.isEmpty();

                g.fill(cx, cy, cx + SLOT, cy + SLOT, random ? 0xFF262233 : 0xFF39344A);
                g.renderOutline(cx, cy, SLOT, SLOT, 0xFF000000);

                if (random) {
                    g.drawCenteredString(this.font, "?", cx + SLOT / 2, cy + 5, 0xFF8A8AA0);
                } else {
                    Block b = BuiltInRegistries.BLOCK.get(new net.minecraft.resources.ResourceLocation(id));
                    g.renderItem(new ItemStack(b), cx + 1, cy + 1);
                }

                if (idx == selectedCell) {
                    g.renderOutline(cx - 1, cy - 1, SLOT + 2, SLOT + 2, 0xFFF5C542);
                }
                if (inBox(mouseX, mouseY, cx, cy, SLOT, SLOT)) {
                    hoverTip = random ? Component.literal("Random") : new ItemStack(
                            BuiltInRegistries.BLOCK.get(new net.minecraft.resources.ResourceLocation(id))).getHoverName();
                }
            }
        }

        String sel = selectedCell < 0 ? "Select a cell..."
                : "Cell " + (selectedCell / size) + "," + (selectedCell % size);
        g.drawString(this.font, sel, gridX + 96, gridY + size * CELL + 15, 0xC0C0D0, false);

        // --- picker ---
        int gridAreaX = pickerX;
        for (int r = 0; r < PROWS; r++) {
            for (int c = 0; c < PCOLS; c++) {
                int fi = (scroll + r) * PCOLS + c;
                if (fi < 0 || fi >= filtered.size()) continue;
                PickEntry e = filtered.get(fi);
                int cx = gridAreaX + c * CELL;
                int cy = pickerY + r * CELL;

                g.fill(cx, cy, cx + SLOT, cy + SLOT, 0xFF2A2636);
                g.renderItem(e.icon, cx + 1, cy + 1);
                if (inBox(mouseX, mouseY, cx, cy, SLOT, SLOT)) {
                    g.renderOutline(cx, cy, SLOT, SLOT, 0xFFF5C542);
                    hoverTip = e.icon.getHoverName();
                }
            }
        }

        // scrollbar
        int barX = pickerX + PCOLS * CELL;
        int barTop = pickerY;
        int barH = PROWS * CELL - 2;
        g.fill(barX, barTop, barX + 4, barTop + barH, 0xFF201C2A);
        int maxS = maxScroll();
        if (maxS > 0) {
            int thumbH = Math.max(10, barH * PROWS / ((filtered.size() + PCOLS - 1) / PCOLS));
            int thumbY = barTop + (barH - thumbH) * scroll / maxS;
            g.fill(barX, thumbY, barX + 4, thumbY + thumbH, 0xFF5A5478);
        }

        g.drawString(this.font, filtered.size() + " blocks", pickerX, pickerY + PROWS * CELL, 0x808090, false);

        super.render(g, mouseX, mouseY, partialTick);

        if (hoverTip != null) {
            g.renderTooltip(this.font, hoverTip, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;

        // grid cells
        for (int row = 0; row < size; row++) {
            for (int col = 0; col < size; col++) {
                int cx = gridX + col * CELL;
                int cy = gridY + row * CELL;
                if (inBox(mouseX, mouseY, cx, cy, SLOT, SLOT)) {
                    int idx = row * size + col;
                    if (button == 1) {
                        cells[idx] = ""; // right-click = random
                    } else {
                        selectedCell = idx;
                    }
                    return true;
                }
            }
        }

        // picker icons
        for (int r = 0; r < PROWS; r++) {
            for (int c = 0; c < PCOLS; c++) {
                int fi = (scroll + r) * PCOLS + c;
                if (fi < 0 || fi >= filtered.size()) continue;
                int cx = pickerX + c * CELL;
                int cy = pickerY + r * CELL;
                if (inBox(mouseX, mouseY, cx, cy, SLOT, SLOT)) {
                    if (selectedCell >= 0 && selectedCell < cells.length) {
                        cells[selectedCell] = filtered.get(fi).id;
                    }
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (inBox(mouseX, mouseY, pickerX, pickerY, PCOLS * CELL, PROWS * CELL)) {
            scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(delta)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private static boolean inBox(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
