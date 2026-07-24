package com.painter.client;

import com.painter.BrushData;
import com.painter.ModItems;
import com.painter.PainterMod;
import com.painter.PaletteData;
import com.painter.net.BrushConfigPacket;
import com.painter.net.PainterNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Configuration GUI for the Paintbrush.
 * <ul>
 *   <li>Left: the editable N x N grid template (click a cell to select, right-click = RANDOM).</li>
 *   <li>Right: a searchable, scrollable icon grid of every block.</li>
 *   <li>Bottom: the weighted palette editor (add blocks, per-entry % slider + numeric box).</li>
 * </ul>
 * Picked blocks go to the selected grid cell, or to the palette when "+ Add" mode is on.
 * "Apply" sends everything to the server via {@link BrushConfigPacket}.
 */
public class PaintbrushScreen extends Screen {

    private static final int IMG_W = 320;
    private static final int IMG_H = 272;
    private static final int CELL = 20;
    private static final int SLOT = 18;
    private static final int PCOLS = 7;
    private static final int PROWS = 5;

    private int leftPos;
    private int topPos;
    private int gridX;
    private int gridY;
    private int pickerX;
    private int pickerY;
    private int palStripX;
    private int palStripY;

    private enum PickTarget { CELL, PALETTE }

    // grid state
    private int size = 1;
    private String[] cells = new String[0];
    private int selectedCell = -1;

    // palette state
    private static final class PalEntry {
        final String id;
        int weight;
        final ItemStack icon;
        PalEntry(String id, int weight, ItemStack icon) { this.id = id; this.weight = weight; this.icon = icon; }
    }
    private final List<PalEntry> palette = new ArrayList<>();
    private int selectedPal = -1;

    private PainterMod.PatternMode pattern = PainterMod.PatternMode.RANDOM;
    private PickTarget pickTarget = PickTarget.CELL;

    // picker state
    private int scroll = 0;
    private EditBox searchBox;
    private List<PickEntry> filtered = new ArrayList<>();

    // palette weight controls
    private WeightSlider slider;
    private EditBox weightBox;
    private boolean syncing = false;

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
        this.gridX = leftPos + 16;
        this.gridY = topPos + 28;
        this.pickerX = leftPos + 150;
        this.pickerY = topPos + 38;
        this.palStripX = leftPos + 16;
        this.palStripY = topPos + 196;

        ItemStack brush = this.minecraft.player.getMainHandItem();
        if (!brush.is(ModItems.PAINTBRUSH.get())) {
            this.onClose();
            return;
        }

        // Load grid
        this.size = BrushData.getSize(brush, 1);
        this.cells = new String[size * size];
        for (int row = 0; row < size; row++) {
            for (int col = 0; col < size; col++) {
                Block b = BrushData.getCell(brush, size, row, col);
                cells[row * size + col] = (b == null) ? "" : BuiltInRegistries.BLOCK.getKey(b).toString();
            }
        }

        // Load palette + pattern
        palette.clear();
        if (BrushData.hasPalette(brush)) {
            PaletteData data = BrushData.getPalette(brush);
            if (data != null) {
                data.weights().forEach((block, weight) ->
                        palette.add(new PalEntry(BuiltInRegistries.BLOCK.getKey(block).toString(), weight, new ItemStack(block))));
            }
        }
        this.pattern = BrushData.getPattern(brush, PainterMod.PatternMode.RANDOM);
        this.selectedPal = palette.isEmpty() ? -1 : 0;

        // Search box
        this.searchBox = new EditBox(this.font, pickerX, topPos + 18, PCOLS * CELL - 6, 16, Component.literal("Search"));
        this.searchBox.setHint(Component.literal("Search blocks..."));
        this.searchBox.setResponder(this::updateFilter);
        addRenderableWidget(this.searchBox);

        // Grid buttons
        addRenderableWidget(Button.builder(Component.literal("Set RANDOM"), b -> setSelectedRandom())
                .bounds(gridX, gridY + 5 * CELL + 6, 92, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Clear grid"), b -> clearGrid())
                .bounds(gridX, gridY + 5 * CELL + 26, 92, 18).build());

        // Pattern cycle
        addRenderableWidget(Button.builder(patternLabel(), b -> cyclePattern((Button) b))
                .bounds(pickerX, topPos + 140, PCOLS * CELL - 6, 18).build());

        // Palette add / remove
        addRenderableWidget(Button.builder(Component.literal("+ Add"), b -> pickTarget = PickTarget.PALETTE)
                .bounds(palStripX + 54, topPos + 180, 44, 16).build());
        addRenderableWidget(Button.builder(Component.literal("Remove"), b -> removeSelectedPalette())
                .bounds(palStripX + 100, topPos + 180, 52, 16).build());

        // Palette weight slider + numeric box
        this.slider = new WeightSlider(palStripX, topPos + 226, 210, 16);
        addRenderableWidget(this.slider);
        this.weightBox = new EditBox(this.font, palStripX + 218, topPos + 226, 34, 16, Component.literal("%"));
        this.weightBox.setResponder(this::onWeightTyped);
        addRenderableWidget(this.weightBox);

        // Apply / close
        addRenderableWidget(Button.builder(Component.literal("Apply & Close"), b -> apply())
                .bounds(leftPos + IMG_W - 110, topPos + IMG_H - 22, 100, 18).build());

        refreshWeightControls();
        updateFilter("");
    }

    // ---------------------------------------------------------------- palette helpers

    private void onWeightTyped(String s) {
        if (syncing || selectedPal < 0 || selectedPal >= palette.size()) return;
        try {
            int w = Mth.clamp(Integer.parseInt(s.trim()), 1, 100);
            palette.get(selectedPal).weight = w;
            slider.setFromWeight(w);
        } catch (NumberFormatException ignored) {
            // partial input; ignore until valid
        }
    }

    private void refreshWeightControls() {
        boolean has = selectedPal >= 0 && selectedPal < palette.size();
        int w = has ? palette.get(selectedPal).weight : 0;
        slider.setFromWeight(has ? w : 1);
        syncing = true;
        weightBox.setValue(has ? String.valueOf(w) : "");
        syncing = false;
        weightBox.setEditable(has);
    }

    private void removeSelectedPalette() {
        if (selectedPal >= 0 && selectedPal < palette.size()) {
            palette.remove(selectedPal);
            selectedPal = palette.isEmpty() ? -1 : Math.min(selectedPal, palette.size() - 1);
            refreshWeightControls();
        }
    }

    private void addToPalette(String id) {
        for (PalEntry e : palette) {
            if (e.id.equals(id)) return; // no duplicates
        }
        Block b = BuiltInRegistries.BLOCK.get(new ResourceLocation(id));
        palette.add(new PalEntry(id, 50, new ItemStack(b)));
        selectedPal = palette.size() - 1;
        refreshWeightControls();
    }

    private Component patternLabel() {
        return Component.literal("Pattern: " + switch (pattern) {
            case RANDOM -> "Random";
            case CHECKERBOARD -> "Checker";
            case STRIPES -> "Stripes";
        });
    }

    private void cyclePattern(Button b) {
        pattern = switch (pattern) {
            case RANDOM -> PainterMod.PatternMode.CHECKERBOARD;
            case CHECKERBOARD -> PainterMod.PatternMode.STRIPES;
            case STRIPES -> PainterMod.PatternMode.RANDOM;
        };
        b.setMessage(patternLabel());
    }

    // ---------------------------------------------------------------- grid helpers

    private void setSelectedRandom() {
        if (selectedCell >= 0 && selectedCell < cells.length) cells[selectedCell] = "";
    }

    private void clearGrid() {
        for (int i = 0; i < cells.length; i++) cells[i] = "";
    }

    // ---------------------------------------------------------------- picker

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

    private void pickBlock(String id) {
        if (pickTarget == PickTarget.PALETTE) {
            addToPalette(id);
        } else if (selectedCell >= 0 && selectedCell < cells.length) {
            cells[selectedCell] = id;
        }
    }

    private void apply() {
        List<String> grid = new ArrayList<>(cells.length);
        for (String c : cells) grid.add(c == null ? "" : c);
        List<String> palIds = new ArrayList<>();
        List<Integer> palWeights = new ArrayList<>();
        for (PalEntry e : palette) {
            palIds.add(e.id);
            palWeights.add(e.weight);
        }
        PainterNetwork.CHANNEL.sendToServer(new BrushConfigPacket(size, grid, palIds, palWeights, pattern.name()));
        this.onClose();
    }

    // ---------------------------------------------------------------- render

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        if (cells.length != size * size) { // guard: closed early / not initialized
            super.render(g, mouseX, mouseY, partialTick);
            return;
        }
        g.fill(leftPos, topPos, leftPos + IMG_W, topPos + IMG_H, 0xF0141018);
        g.renderOutline(leftPos, topPos, IMG_W, IMG_H, 0xFF3A3450);

        g.drawString(this.font, "Paintbrush", leftPos + 10, topPos + 8, 0xFFFFFF, false);
        g.drawString(this.font, "Grid " + size + "x" + size, gridX, topPos + 18, 0xA0A0B0, false);
        g.drawString(this.font, "Blocks", pickerX, topPos + 8, 0xA0A0B0, false);

        Component hoverTip = null;

        // grid
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
                    g.renderItem(new ItemStack(BuiltInRegistries.BLOCK.get(new ResourceLocation(id))), cx + 1, cy + 1);
                }
                if (idx == selectedCell && pickTarget == PickTarget.CELL) {
                    g.renderOutline(cx - 1, cy - 1, SLOT + 2, SLOT + 2, 0xFFF5C542);
                }
                if (inBox(mouseX, mouseY, cx, cy, SLOT, SLOT)) {
                    hoverTip = random ? Component.literal("Random")
                            : new ItemStack(BuiltInRegistries.BLOCK.get(new ResourceLocation(id))).getHoverName();
                }
            }
        }

        String target = pickTarget == PickTarget.PALETTE ? "Picking for: §ePalette"
                : (selectedCell < 0 ? "Pick a cell first" : "Picking for: §eCell " + (selectedCell / size) + "," + (selectedCell % size));
        g.drawString(this.font, target, pickerX, topPos + 162, 0xC0C0D0, false);

        // picker
        for (int r = 0; r < PROWS; r++) {
            for (int c = 0; c < PCOLS; c++) {
                int fi = (scroll + r) * PCOLS + c;
                if (fi < 0 || fi >= filtered.size()) continue;
                PickEntry e = filtered.get(fi);
                int cx = pickerX + c * CELL;
                int cy = pickerY + r * CELL;
                g.fill(cx, cy, cx + SLOT, cy + SLOT, 0xFF2A2636);
                g.renderItem(e.icon, cx + 1, cy + 1);
                if (inBox(mouseX, mouseY, cx, cy, SLOT, SLOT)) {
                    g.renderOutline(cx, cy, SLOT, SLOT, 0xFFF5C542);
                    hoverTip = e.icon.getHoverName();
                }
            }
        }
        int barX = pickerX + PCOLS * CELL;
        int barTop = pickerY;
        int barH = PROWS * CELL - 2;
        g.fill(barX, barTop, barX + 4, barTop + barH, 0xFF201C2A);
        int maxS = maxScroll();
        if (maxS > 0) {
            int totalRows = (filtered.size() + PCOLS - 1) / PCOLS;
            int thumbH = Math.max(10, barH * PROWS / totalRows);
            int thumbY = barTop + (barH - thumbH) * scroll / maxS;
            g.fill(barX, thumbY, barX + 4, thumbY + thumbH, 0xFF5A5478);
        }

        // palette strip
        g.drawString(this.font, "Palette", palStripX, topPos + 182, 0xA0A0B0, false);
        for (int i = 0; i < palette.size(); i++) {
            int cx = palStripX + i * CELL;
            int cy = palStripY;
            if (cx + SLOT > leftPos + IMG_W - 8) break; // overflow guard
            PalEntry e = palette.get(i);
            g.fill(cx, cy, cx + SLOT, cy + SLOT, 0xFF2A2636);
            g.renderItem(e.icon, cx + 1, cy + 1);
            if (i == selectedPal) g.renderOutline(cx - 1, cy - 1, SLOT + 2, SLOT + 2, 0xFFF5C542);
            g.drawString(this.font, e.weight + "%", cx, cy + SLOT + 1, 0x909090, false);
            if (inBox(mouseX, mouseY, cx, cy, SLOT, SLOT)) hoverTip = e.icon.getHoverName();
        }
        if (palette.isEmpty()) {
            g.drawString(this.font, "§8(empty — use + Add)", palStripX, palStripY + 4, 0x808080, false);
        }

        super.render(g, mouseX, mouseY, partialTick);

        if (hoverTip != null) g.renderTooltip(this.font, hoverTip, mouseX, mouseY);
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
                        cells[idx] = "";
                    } else {
                        selectedCell = idx;
                        pickTarget = PickTarget.CELL;
                    }
                    return true;
                }
            }
        }

        // palette entries
        for (int i = 0; i < palette.size(); i++) {
            int cx = palStripX + i * CELL;
            int cy = palStripY;
            if (inBox(mouseX, mouseY, cx, cy, SLOT, SLOT)) {
                selectedPal = i;
                refreshWeightControls();
                return true;
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
                    pickBlock(filtered.get(fi).id);
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

    /** Slider bound to the selected palette entry's weight (1–100%). */
    private class WeightSlider extends AbstractSliderButton {
        WeightSlider(int x, int y, int w, int h) {
            super(x, y, w, h, Component.empty(), 0.0);
            updateMessage();
        }

        void setFromWeight(int weight) {
            this.value = Mth.clamp(weight, 1, 100) / 100.0;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            boolean has = selectedPal >= 0 && selectedPal < palette.size();
            setMessage(Component.literal(has ? (int) Math.round(value * 100) + "%" : "--"));
        }

        @Override
        protected void applyValue() {
            if (selectedPal < 0 || selectedPal >= palette.size()) return;
            int w = Mth.clamp((int) Math.round(value * 100), 1, 100);
            palette.get(selectedPal).weight = w;
            syncing = true;
            weightBox.setValue(String.valueOf(w));
            syncing = false;
        }
    }
}
