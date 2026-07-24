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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Full configuration GUI for the Paintbrush (Shift + right-click to open).
 *
 * <p>Flow: multi-select blocks in the picker (they stay highlighted), then press
 * <b>+ Palette</b> or <b>+ Mask</b> to add them; blocks already in the palette/mask
 * grey out (differently). Select a palette entry to make it the active paint block —
 * in <b>Custom</b> mode, click grid cells to stamp it (right-click a cell = random).
 * In <b>Randomize</b> mode the grid is ignored and every paint draws fresh from the
 * palette. "Apply" ships it all to the server.</p>
 */
public class PaintbrushScreen extends Screen {

    private static final int IMG_W = 340;
    private static final int IMG_H = 306;
    private static final int CELL = 20;
    private static final int SLOT = 18;
    private static final int PCOLS = 8;
    private static final int PROWS = 5;

    private int leftPos, topPos, gridX, gridY, pickerX, pickerY, palStripY, maskStripY;
    private int palLabelY, maskLabelY, selCountY;

    // grid
    private int size = 1;
    private String[] cells = new String[0];

    // palette (weights normalized to sum 100)
    private static final class PalEntry {
        final String id; int weight; final ItemStack icon;
        PalEntry(String id, int weight, ItemStack icon) { this.id = id; this.weight = weight; this.icon = icon; }
    }
    private final List<PalEntry> palette = new ArrayList<>();
    private int selectedPal = -1;

    // mask (a set)
    private record IconEntry(String id, ItemStack icon) {}
    private final List<IconEntry> mask = new ArrayList<>();
    private int selectedMask = -1;
    private PainterMod.MaskMode maskMode = PainterMod.MaskMode.INCLUDE;

    private PainterMod.BrushShape shape = PainterMod.BrushShape.SQUARE;
    private PainterMod.BrushMode mode = PainterMod.BrushMode.RANDOMIZE;

    // picker
    private int scroll = 0;
    private EditBox searchBox;
    private List<PickEntry> filtered = new ArrayList<>();
    private final Set<String> pickerSelection = new LinkedHashSet<>();

    private WeightSlider slider;
    private EditBox weightBox;
    private boolean syncing = false;
    private boolean draggingScrollbar = false;

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
        this.gridX = leftPos + 14;
        this.gridY = topPos + 34;
        this.pickerX = leftPos + 150;
        this.pickerY = topPos + 38;               // picker: 38 -> 138
        this.selCountY = topPos + 158;             // "N sel" indicator: 158 -> 166
        this.palLabelY = topPos + 168;             // "Palette" label: 168 -> 176
        this.palStripY = topPos + 178;             // palette icons + %: 178 -> 206
        this.maskLabelY = topPos + 228;            // "Mask" label: 228 -> 236
        this.maskStripY = topPos + 238;            // mask icons: 238 -> 258

        ItemStack brush = this.minecraft.player.getMainHandItem();
        if (!brush.is(ModItems.PAINTBRUSH.get())) { this.onClose(); return; }

        this.size = Mth.clamp(BrushData.getSize(brush, 1), 1, 5);
        this.shape = BrushData.getShape(brush, PainterMod.BrushShape.SQUARE);
        this.mode = BrushData.getMode(brush, PainterMod.BrushMode.RANDOMIZE);
        rebuildCells(brush);

        palette.clear();
        if (BrushData.hasPalette(brush)) {
            PaletteData data = BrushData.getPalette(brush);
            if (data != null) data.weights().forEach((block, weight) ->
                    palette.add(new PalEntry(BuiltInRegistries.BLOCK.getKey(block).toString(), weight, new ItemStack(block))));
        }
        normalizeAll();
        this.selectedPal = palette.isEmpty() ? -1 : 0;

        mask.clear();
        if (BrushData.hasMask(brush)) {
            PaletteData data = BrushData.getMask(brush);
            if (data != null) data.weights().forEach((block, weight) ->
                    mask.add(new IconEntry(BuiltInRegistries.BLOCK.getKey(block).toString(), new ItemStack(block))));
        }
        this.maskMode = BrushData.getMaskMode(brush, PainterMod.MaskMode.INCLUDE);

        // ---- widgets ----
        this.searchBox = new EditBox(this.font, pickerX, topPos + 18, PCOLS * CELL - 6, 16, Component.literal("Search"));
        this.searchBox.setHint(Component.literal("Search blocks..."));
        this.searchBox.setResponder(this::updateFilter);
        addRenderableWidget(this.searchBox);

        // ---- left column: mode / size / shape / grid ops / active paint block ----
        addRenderableWidget(Button.builder(modeLabel(), b -> toggleMode((Button) b)).bounds(gridX, topPos + 140, 120, 18).build());
        addRenderableWidget(Button.builder(Component.literal("-"), b -> changeSize(-1)).bounds(gridX + 44, topPos + 162, 16, 16).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> changeSize(1)).bounds(gridX + 80, topPos + 162, 16, 16).build());
        addRenderableWidget(Button.builder(shapeLabel(), b -> cycleShape((Button) b)).bounds(gridX, topPos + 184, 120, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Clear grid"), b -> clearGrid()).bounds(gridX, topPos + 206, 120, 18).build());

        // ---- right column: picker add buttons ----
        addRenderableWidget(Button.builder(Component.literal("+ Palette"), b -> addSelectionToPalette()).bounds(pickerX, topPos + 142, 72, 16).build());
        addRenderableWidget(Button.builder(Component.literal("+ Mask"), b -> addSelectionToMask()).bounds(pickerX + 76, topPos + 142, 60, 16).build());

        // ---- palette row: weight slider + numeric box + Remove (below the strip+labels) ----
        // Budget from pickerX is ~182px (panel edge minus margin); slider 86 + box 30 + Remove 48 + 2 gaps of 4 = 172.
        int palControlY = palStripY + 30; // strip (18) + %-text (~8) + gap
        this.slider = new WeightSlider(pickerX, palControlY, 86, 16);
        addRenderableWidget(this.slider);
        this.weightBox = new EditBox(this.font, pickerX + 90, palControlY, 30, 16, Component.literal("%"));
        this.weightBox.setResponder(this::onWeightTyped);
        addRenderableWidget(this.weightBox);
        addRenderableWidget(Button.builder(Component.literal("Remove"), b -> removeSelectedPalette()).bounds(pickerX + 124, palControlY, 48, 16).build());

        // ---- mask row: Remove + Include/Exclude toggle (below the strip) ----
        int maskControlY = maskStripY + 22;
        addRenderableWidget(Button.builder(Component.literal("Remove"), b -> removeSelectedMask()).bounds(pickerX, maskControlY, 44, 16).build());
        addRenderableWidget(Button.builder(maskModeLabel(), b -> toggleMaskMode((Button) b)).bounds(pickerX + 48, maskControlY, 114, 16).build());

        addRenderableWidget(Button.builder(Component.literal("Apply & Close"), b -> apply()).bounds(leftPos + IMG_W - 114, topPos + IMG_H - 24, 104, 18).build());

        refreshWeightControls();
        updateFilter("");
    }

    private void rebuildCells(ItemStack brush) {
        this.cells = new String[size * size];
        for (int row = 0; row < size; row++) {
            for (int col = 0; col < size; col++) {
                Block b = BrushData.getCell(brush, size, row, col);
                cells[row * size + col] = (b == null) ? "" : BuiltInRegistries.BLOCK.getKey(b).toString();
            }
        }
    }

    // ---- size / shape / mode ----

    private void changeSize(int delta) {
        int newSize = Mth.clamp(size + delta, 1, 5);
        if (newSize == size) return;
        String[] old = cells;
        int oldSize = size;
        size = newSize;
        cells = new String[size * size];
        for (int i = 0; i < cells.length; i++) cells[i] = "";
        for (int row = 0; row < Math.min(oldSize, size); row++)
            for (int col = 0; col < Math.min(oldSize, size); col++)
                cells[row * size + col] = old[row * oldSize + col];
    }

    private Component shapeLabel() {
        return Component.literal("Shape: " + switch (shape) {
            case SQUARE -> "Square"; case CIRCLE -> "Circle"; case DIAMOND -> "Diamond";
        });
    }

    private void cycleShape(Button b) {
        shape = switch (shape) {
            case SQUARE -> PainterMod.BrushShape.CIRCLE;
            case CIRCLE -> PainterMod.BrushShape.DIAMOND;
            case DIAMOND -> PainterMod.BrushShape.SQUARE;
        };
        b.setMessage(shapeLabel());
    }

    private Component modeLabel() {
        return Component.literal("Mode: " + (mode == PainterMod.BrushMode.RANDOMIZE ? "Randomize" : "Custom"));
    }

    private void toggleMode(Button b) {
        mode = (mode == PainterMod.BrushMode.RANDOMIZE) ? PainterMod.BrushMode.CUSTOM : PainterMod.BrushMode.RANDOMIZE;
        b.setMessage(modeLabel());
    }

    private Component maskModeLabel() {
        return Component.literal(maskMode == PainterMod.MaskMode.INCLUDE ? "Mask: Include" : "Mask: Exclude");
    }

    private void toggleMaskMode(Button b) {
        maskMode = (maskMode == PainterMod.MaskMode.INCLUDE) ? PainterMod.MaskMode.EXCLUDE : PainterMod.MaskMode.INCLUDE;
        b.setMessage(maskModeLabel());
    }

    // ---- palette ----

    private void normalizeAll() {
        int n = palette.size();
        if (n == 0) return;
        int sum = 0;
        for (PalEntry e : palette) sum += e.weight;
        if (sum <= 0) {
            int each = 100 / n, extra = 100 - each * n;
            for (int i = 0; i < n; i++) palette.get(i).weight = each + (i < extra ? 1 : 0);
            return;
        }
        int assigned = 0;
        for (PalEntry e : palette) { e.weight = Math.round(100f * e.weight / sum); assigned += e.weight; }
        palette.get(n - 1).weight = Math.max(0, palette.get(n - 1).weight + (100 - assigned));
    }

    private void normalizeKeeping(int keep, int targetVal) {
        int n = palette.size();
        if (n == 0) return;
        if (n == 1) { palette.get(0).weight = 100; return; }
        targetVal = Mth.clamp(targetVal, 0, 100);
        int remaining = 100 - targetVal;
        int sumOthers = 0;
        for (int i = 0; i < n; i++) if (i != keep) sumOthers += palette.get(i).weight;
        palette.get(keep).weight = targetVal;
        if (sumOthers <= 0) {
            int each = remaining / (n - 1), extra = remaining - each * (n - 1), k = 0;
            for (int i = 0; i < n; i++) if (i != keep) palette.get(i).weight = each + (k++ < extra ? 1 : 0);
        } else {
            int assigned = 0, last = -1;
            for (int i = 0; i < n; i++) if (i != keep) {
                int w = Math.round(remaining * (palette.get(i).weight / (float) sumOthers));
                palette.get(i).weight = w; assigned += w; last = i;
            }
            if (last >= 0) palette.get(last).weight = Math.max(0, palette.get(last).weight + (remaining - assigned));
        }
    }

    private boolean isInPalette(String id) {
        for (PalEntry e : palette) if (e.id.equals(id)) return true;
        return false;
    }

    private void addSelectionToPalette() {
        for (String id : pickerSelection) {
            if (isInPalette(id)) continue;
            Block b = BuiltInRegistries.BLOCK.get(new ResourceLocation(id));
            palette.add(new PalEntry(id, 1, new ItemStack(b)));
        }
        pickerSelection.clear();
        normalizeAll();
        selectedPal = palette.isEmpty() ? -1 : palette.size() - 1;
        refreshWeightControls();
    }

    private void removeSelectedPalette() {
        if (selectedPal < 0 || selectedPal >= palette.size()) return;
        palette.remove(selectedPal);
        selectedPal = palette.isEmpty() ? -1 : Math.min(selectedPal, palette.size() - 1);
        normalizeAll();
        refreshWeightControls();
    }

    private void onWeightTyped(String s) {
        if (syncing || selectedPal < 0 || selectedPal >= palette.size()) return;
        try {
            int w = Mth.clamp(Integer.parseInt(s.trim()), 0, 100);
            normalizeKeeping(selectedPal, w);
            slider.setFromWeight(palette.get(selectedPal).weight);
        } catch (NumberFormatException ignored) {
        }
    }

    private void refreshWeightControls() {
        boolean has = selectedPal >= 0 && selectedPal < palette.size();
        slider.setFromWeight(has ? palette.get(selectedPal).weight : 1);
        syncing = true;
        weightBox.setValue(has ? String.valueOf(palette.get(selectedPal).weight) : "");
        syncing = false;
        weightBox.setEditable(has);
    }

    // ---- mask ----

    private boolean isInMask(String id) {
        for (IconEntry e : mask) if (e.id().equals(id)) return true;
        return false;
    }

    private void addSelectionToMask() {
        for (String id : pickerSelection) {
            if (isInMask(id)) continue;
            Block b = BuiltInRegistries.BLOCK.get(new ResourceLocation(id));
            mask.add(new IconEntry(id, new ItemStack(b)));
        }
        pickerSelection.clear();
        selectedMask = mask.isEmpty() ? -1 : mask.size() - 1;
    }

    private void removeSelectedMask() {
        if (selectedMask < 0 || selectedMask >= mask.size()) return;
        mask.remove(selectedMask);
        selectedMask = mask.isEmpty() ? -1 : Math.min(selectedMask, mask.size() - 1);
    }

    // ---- grid ----

    private void clearGrid() {
        for (int i = 0; i < cells.length; i++) cells[i] = "";
    }

    // ---- picker ----

    private void updateFilter(String query) {
        String q = query == null ? "" : query.toLowerCase(Locale.ROOT).trim();
        List<PickEntry> result = new ArrayList<>();
        for (PickEntry e : allBlocks()) if (q.isEmpty() || e.search.contains(q)) result.add(e);
        this.filtered = result;
        this.scroll = 0;
    }

    private int maxScroll() {
        int rows = (filtered.size() + PCOLS - 1) / PCOLS;
        return Math.max(0, rows - PROWS);
    }

    private void apply() {
        List<String> grid = new ArrayList<>(cells.length);
        for (String c : cells) grid.add(c == null ? "" : c);
        List<String> palIds = new ArrayList<>();
        List<Integer> palWeights = new ArrayList<>();
        for (PalEntry e : palette) { palIds.add(e.id); palWeights.add(e.weight); }
        List<String> maskIds = new ArrayList<>();
        for (IconEntry e : mask) maskIds.add(e.id());
        PainterNetwork.CHANNEL.sendToServer(new BrushConfigPacket(size, shape.name(), mode.name(), grid, palIds, palWeights, maskIds, maskMode.name()));
        this.onClose();
    }

    private boolean inShape(int row, int col) {
        int radius = (size - 1) / 2, min = -radius;
        int a = row + min, b = col + min;
        double offset = (size % 2 == 0) ? 0.5 : 0.0;
        double x = a - offset, y = b - offset, r = size / 2.0;
        return switch (shape) {
            case SQUARE -> true;
            case CIRCLE -> (x * x + y * y) < (r * r);
            case DIAMOND -> (Math.abs(x) + Math.abs(y)) < r;
        };
    }

    // ---- render ----

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        if (cells.length != size * size) { super.render(g, mouseX, mouseY, partialTick); return; }

        g.fill(leftPos, topPos, leftPos + IMG_W, topPos + IMG_H, 0xF0141018);
        g.renderOutline(leftPos, topPos, IMG_W, IMG_H, 0xFF3A3450);

        boolean custom = mode == PainterMod.BrushMode.CUSTOM;
        g.drawString(this.font, "Paintbrush", leftPos + 10, topPos + 8, 0xFFFFFF, false);
        g.drawString(this.font, custom ? "Grid (custom)" : "Grid (ignored)", gridX, topPos + 22, custom ? 0xA0A0B0 : 0x707070, false);
        g.drawString(this.font, "Blocks", pickerX, topPos + 8, 0xA0A0B0, false);
        g.drawString(this.font, "Size:", gridX, topPos + 166, 0xC0C0D0, false);
        g.drawString(this.font, String.valueOf(size), gridX + 66, topPos + 166, 0xFFFFFF, false);

        Component hoverTip = null;

        // grid
        for (int row = 0; row < size; row++) {
            for (int col = 0; col < size; col++) {
                int idx = row * size + col;
                int cx = gridX + col * CELL, cy = gridY + row * CELL;
                String id = cells[idx];
                boolean random = id == null || id.isEmpty();
                g.fill(cx, cy, cx + SLOT, cy + SLOT, random ? 0xFF262233 : 0xFF39344A);
                g.renderOutline(cx, cy, SLOT, SLOT, 0xFF000000);
                if (random) g.drawCenteredString(this.font, "?", cx + SLOT / 2, cy + 5, 0xFF8A8AA0);
                else g.renderItem(new ItemStack(BuiltInRegistries.BLOCK.get(new ResourceLocation(id))), cx + 1, cy + 1);
                if (!custom || !inShape(row, col)) g.fill(cx, cy, cx + SLOT, cy + SLOT, 0xAA0E0C14); // inactive/out-of-shape
                if (inBox(mouseX, mouseY, cx, cy, SLOT, SLOT)) {
                    hoverTip = random ? Component.literal("Random")
                            : new ItemStack(BuiltInRegistries.BLOCK.get(new ResourceLocation(id))).getHoverName();
                }
            }
        }

        // active paint block (custom mode)
        if (custom) {
            g.drawString(this.font, "Paint:", gridX, topPos + 230, 0xC0C0D0, false);
            if (selectedPal >= 0 && selectedPal < palette.size()) {
                PalEntry e = palette.get(selectedPal);
                g.renderItem(e.icon, gridX + 36, topPos + 226);
                g.drawString(this.font, trim(e.icon.getHoverName().getString(), 12), gridX + 56, topPos + 230, 0xFFFFFF, false);
            } else {
                g.drawString(this.font, "§8pick a palette block", gridX + 36, topPos + 230, 0x808080, false);
            }
        }

        // picker
        for (int r = 0; r < PROWS; r++) {
            for (int c = 0; c < PCOLS; c++) {
                int fi = (scroll + r) * PCOLS + c;
                if (fi < 0 || fi >= filtered.size()) continue;
                PickEntry e = filtered.get(fi);
                int cx = pickerX + c * CELL, cy = pickerY + r * CELL;
                g.fill(cx, cy, cx + SLOT, cy + SLOT, 0xFF2A2636);
                g.renderItem(e.icon, cx + 1, cy + 1);
                if (isInPalette(e.id)) g.fill(cx, cy, cx + SLOT, cy + SLOT, 0xB0123A18);        // in palette (green)
                else if (isInMask(e.id)) g.fill(cx, cy, cx + SLOT, cy + SLOT, 0xB03A2410);      // in mask (amber)
                else if (pickerSelection.contains(e.id)) g.renderOutline(cx, cy, SLOT, SLOT, 0xFFF5C542); // selected
                if (inBox(mouseX, mouseY, cx, cy, SLOT, SLOT)) hoverTip = e.icon.getHoverName();
            }
        }
        int barX = pickerX + PCOLS * CELL, barTop = pickerY, barH = PROWS * CELL - 2;
        g.fill(barX, barTop, barX + 4, barTop + barH, 0xFF201C2A);
        int maxS = maxScroll();
        if (maxS > 0) {
            int totalRows = (filtered.size() + PCOLS - 1) / PCOLS;
            int thumbH = Math.max(10, barH * PROWS / totalRows);
            int thumbY = barTop + (barH - thumbH) * scroll / maxS;
            g.fill(barX, thumbY, barX + 4, thumbY + thumbH, 0xFF5A5478);
        }
        if (!pickerSelection.isEmpty())
            g.drawString(this.font, pickerSelection.size() + " selected", pickerX, selCountY, 0xF5C542, false);

        // palette
        g.drawString(this.font, "Palette (=100%)", pickerX, palLabelY, 0xA0A0B0, false);
        for (int i = 0; i < palette.size(); i++) {
            int cx = pickerX + i * CELL, cy = palStripY;
            if (cx + SLOT > leftPos + IMG_W - 8) break;
            PalEntry e = palette.get(i);
            g.fill(cx, cy, cx + SLOT, cy + SLOT, 0xFF2A2636);
            g.renderItem(e.icon, cx + 1, cy + 1);
            if (i == selectedPal) g.renderOutline(cx - 1, cy - 1, SLOT + 2, SLOT + 2, 0xFFF5C542);
            g.drawString(this.font, e.weight + "%", cx, cy + SLOT + 1, 0x909090, false);
            if (inBox(mouseX, mouseY, cx, cy, SLOT, SLOT)) hoverTip = e.icon.getHoverName();
        }
        if (palette.isEmpty()) g.drawString(this.font, "§8(select blocks, then + Palette)", pickerX, palStripY + 4, 0x808080, false);

        // mask
        g.drawString(this.font, "Mask", pickerX, maskLabelY, 0xA0A0B0, false);
        for (int i = 0; i < mask.size(); i++) {
            int cx = pickerX + i * CELL, cy = maskStripY;
            if (cx + SLOT > leftPos + IMG_W - 8) break;
            IconEntry e = mask.get(i);
            g.fill(cx, cy, cx + SLOT, cy + SLOT, 0xFF2A2636);
            g.renderItem(e.icon(), cx + 1, cy + 1);
            if (i == selectedMask) g.renderOutline(cx - 1, cy - 1, SLOT + 2, SLOT + 2, 0xFFF5C542);
            if (inBox(mouseX, mouseY, cx, cy, SLOT, SLOT)) hoverTip = e.icon().getHoverName();
        }
        if (mask.isEmpty()) g.drawString(this.font, "§8(none — paints over anything)", pickerX, maskStripY + 4, 0x808080, false);

        super.render(g, mouseX, mouseY, partialTick);
        if (hoverTip != null) g.renderTooltip(this.font, hoverTip, mouseX, mouseY);
    }

    private static String trim(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "..";
    }

    private int scrollbarX() { return pickerX + PCOLS * CELL; }
    private int scrollbarTop() { return pickerY; }
    private int scrollbarHeight() { return PROWS * CELL - 2; }

    /** Jumps the scroll position to wherever the bar was clicked/dragged to. */
    private void scrollToMouseY(double mouseY) {
        int maxS = maxScroll();
        if (maxS <= 0) return;
        double frac = (mouseY - scrollbarTop()) / (double) scrollbarHeight();
        scroll = Mth.clamp((int) Math.round(frac * maxS), 0, maxS);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;

        // scrollbar: click track or thumb to jump, and start a drag
        if (button == 0 && inBox(mouseX, mouseY, scrollbarX(), scrollbarTop(), 6, scrollbarHeight())) {
            draggingScrollbar = true;
            scrollToMouseY(mouseY);
            return true;
        }

        // grid (custom mode only)
        if (mode == PainterMod.BrushMode.CUSTOM) {
            for (int row = 0; row < size; row++) {
                for (int col = 0; col < size; col++) {
                    int cx = gridX + col * CELL, cy = gridY + row * CELL;
                    if (inBox(mouseX, mouseY, cx, cy, SLOT, SLOT)) {
                        int idx = row * size + col;
                        if (button == 1) cells[idx] = "";
                        else if (selectedPal >= 0 && selectedPal < palette.size()) cells[idx] = palette.get(selectedPal).id;
                        return true;
                    }
                }
            }
        }
        // palette entries
        for (int i = 0; i < palette.size(); i++) {
            int cx = pickerX + i * CELL, cy = palStripY;
            if (inBox(mouseX, mouseY, cx, cy, SLOT, SLOT)) { selectedPal = i; refreshWeightControls(); return true; }
        }
        // mask entries
        for (int i = 0; i < mask.size(); i++) {
            int cx = pickerX + i * CELL, cy = maskStripY;
            if (inBox(mouseX, mouseY, cx, cy, SLOT, SLOT)) { selectedMask = i; return true; }
        }
        // picker (toggle multi-select; ignore already-added)
        for (int r = 0; r < PROWS; r++) {
            for (int c = 0; c < PCOLS; c++) {
                int fi = (scroll + r) * PCOLS + c;
                if (fi < 0 || fi >= filtered.size()) continue;
                int cx = pickerX + c * CELL, cy = pickerY + r * CELL;
                if (inBox(mouseX, mouseY, cx, cy, SLOT, SLOT)) {
                    String id = filtered.get(fi).id;
                    if (isInPalette(id) || isInMask(id)) return true;
                    if (!pickerSelection.remove(id)) pickerSelection.add(id);
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingScrollbar && button == 0) {
            scrollToMouseY(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) draggingScrollbar = false;
        return super.mouseReleased(mouseX, mouseY, button);
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

    private class WeightSlider extends AbstractSliderButton {
        WeightSlider(int x, int y, int w, int h) { super(x, y, w, h, Component.empty(), 0.0); updateMessage(); }

        void setFromWeight(int weight) { this.value = Mth.clamp(weight, 0, 100) / 100.0; updateMessage(); }

        @Override protected void updateMessage() {
            boolean has = selectedPal >= 0 && selectedPal < palette.size();
            setMessage(Component.literal(has ? (int) Math.round(value * 100) + "%" : "--"));
        }

        @Override protected void applyValue() {
            if (selectedPal < 0 || selectedPal >= palette.size()) return;
            int w = Mth.clamp((int) Math.round(value * 100), 0, 100);
            normalizeKeeping(selectedPal, w);
            syncing = true;
            weightBox.setValue(String.valueOf(palette.get(selectedPal).weight));
            syncing = false;
        }
    }
}
