package com.painter;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Replacement for the Fabric data-component system used in the 1.21 version.
 *
 * <p>1.20.1 has no data components, so every piece of brush configuration is
 * stored inside a single {@code PainterData} compound tag on the ItemStack's NBT.
 * This class centralizes all reads/writes so the rest of the mod never touches
 * the raw NBT layout directly.</p>
 */
public final class BrushData {

    private static final String ROOT = "PainterData";
    private static final String PALETTE = "palette";
    private static final String MASK = "mask";
    private static final String SIZE = "size";
    private static final String SHAPE = "shape";
    private static final String PATTERN = "pattern";
    private static final String PROFILE = "profile";
    private static final String GRID = "grid";
    private static final String GRID_SIZE = "gridSize";

    private BrushData() {
    }

    /** The mutable root compound, created on demand. */
    private static CompoundTag root(ItemStack stack) {
        return stack.getOrCreateTagElement(ROOT);
    }

    /** The root compound if it already exists, otherwise null (no mutation). */
    private static CompoundTag rootOrNull(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(ROOT, Tag.TAG_COMPOUND)) return null;
        return tag.getCompound(ROOT);
    }

    /** Removes the whole PainterData tag if it ends up empty, keeping stacks clean. */
    private static void pruneIfEmpty(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null) return;
        if (tag.contains(ROOT, Tag.TAG_COMPOUND) && tag.getCompound(ROOT).isEmpty()) {
            tag.remove(ROOT);
        }
    }

    // --- Palette ---

    public static boolean hasPalette(ItemStack stack) {
        CompoundTag r = rootOrNull(stack);
        return r != null && r.contains(PALETTE, Tag.TAG_COMPOUND);
    }

    public static PaletteData getPalette(ItemStack stack) {
        CompoundTag r = rootOrNull(stack);
        if (r == null || !r.contains(PALETTE, Tag.TAG_COMPOUND)) return null;
        return PaletteData.fromNbt(r.getCompound(PALETTE));
    }

    public static void setPalette(ItemStack stack, PaletteData data) {
        root(stack).put(PALETTE, data.toNbt());
    }

    public static void removePalette(ItemStack stack) {
        CompoundTag r = rootOrNull(stack);
        if (r != null) r.remove(PALETTE);
        pruneIfEmpty(stack);
    }

    // --- Mask ---

    public static boolean hasMask(ItemStack stack) {
        CompoundTag r = rootOrNull(stack);
        return r != null && r.contains(MASK, Tag.TAG_COMPOUND);
    }

    public static PaletteData getMask(ItemStack stack) {
        CompoundTag r = rootOrNull(stack);
        if (r == null || !r.contains(MASK, Tag.TAG_COMPOUND)) return null;
        return PaletteData.fromNbt(r.getCompound(MASK));
    }

    public static void setMask(ItemStack stack, PaletteData data) {
        root(stack).put(MASK, data.toNbt());
    }

    public static void removeMask(ItemStack stack) {
        CompoundTag r = rootOrNull(stack);
        if (r != null) r.remove(MASK);
        pruneIfEmpty(stack);
    }

    // --- Size ---

    public static boolean hasSize(ItemStack stack) {
        CompoundTag r = rootOrNull(stack);
        return r != null && r.contains(SIZE, Tag.TAG_INT);
    }

    public static int getSize(ItemStack stack, int fallback) {
        CompoundTag r = rootOrNull(stack);
        return (r != null && r.contains(SIZE, Tag.TAG_INT)) ? r.getInt(SIZE) : fallback;
    }

    public static void setSize(ItemStack stack, int size) {
        root(stack).putInt(SIZE, size);
    }

    // --- Shape ---

    public static PainterMod.BrushShape getShape(ItemStack stack, PainterMod.BrushShape fallback) {
        CompoundTag r = rootOrNull(stack);
        if (r == null || !r.contains(SHAPE, Tag.TAG_STRING)) return fallback;
        try {
            return PainterMod.BrushShape.valueOf(r.getString(SHAPE).toUpperCase());
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    public static void setShape(ItemStack stack, PainterMod.BrushShape shape) {
        root(stack).putString(SHAPE, shape.name());
    }

    // --- Pattern mode ---

    public static PainterMod.PatternMode getPattern(ItemStack stack, PainterMod.PatternMode fallback) {
        CompoundTag r = rootOrNull(stack);
        if (r == null || !r.contains(PATTERN, Tag.TAG_STRING)) return fallback;
        try {
            return PainterMod.PatternMode.valueOf(r.getString(PATTERN).toUpperCase());
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    public static void setPattern(ItemStack stack, PainterMod.PatternMode pattern) {
        root(stack).putString(PATTERN, pattern.name());
    }

    // --- Active profile ---

    public static boolean hasProfile(ItemStack stack) {
        CompoundTag r = rootOrNull(stack);
        return r != null && r.contains(PROFILE, Tag.TAG_STRING);
    }

    public static String getProfile(ItemStack stack) {
        CompoundTag r = rootOrNull(stack);
        return (r != null && r.contains(PROFILE, Tag.TAG_STRING)) ? r.getString(PROFILE) : null;
    }

    public static void setProfile(ItemStack stack, String name) {
        root(stack).putString(PROFILE, name);
    }

    public static void removeProfile(ItemStack stack) {
        CompoundTag r = rootOrNull(stack);
        if (r != null) r.remove(PROFILE);
        pruneIfEmpty(stack);
    }

    // --- Grid (per-cell template) ---
    //
    // The brush footprint is a size x size grid. Each cell holds either a specific
    // block id or the empty string, which means RANDOM (fall back to the palette).
    // The grid is stored row-major and is tied to the size it was built at; if the
    // brush size changes, a grid built at a different size is ignored (treated as
    // all-RANDOM) until edited again — but a same-size grid is preserved.

    /** Returns the grid list for the given size, creating/resetting it if absent or stale. */
    private static ListTag ensureGrid(ItemStack stack, int size) {
        CompoundTag r = root(stack);
        ListTag grid = r.getList(GRID, Tag.TAG_STRING);
        if (r.getInt(GRID_SIZE) != size || grid.size() != size * size) {
            grid = new ListTag();
            for (int i = 0; i < size * size; i++) grid.add(StringTag.valueOf(""));
            r.put(GRID, grid);
            r.putInt(GRID_SIZE, size);
        }
        return grid;
    }

    /**
     * The block assigned to cell (row, col) for the current size, or null if the cell
     * is RANDOM / unset / the stored grid doesn't match this size.
     */
    public static Block getCell(ItemStack stack, int size, int row, int col) {
        CompoundTag r = rootOrNull(stack);
        if (r == null || r.getInt(GRID_SIZE) != size) return null;
        ListTag grid = r.getList(GRID, Tag.TAG_STRING);
        int idx = row * size + col;
        if (idx < 0 || idx >= grid.size()) return null;
        String id = grid.getString(idx);
        if (id.isEmpty()) return null;
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) return null;
        Block b = BuiltInRegistries.BLOCK.get(rl);
        return b == Blocks.AIR ? null : b;
    }

    /** Sets cell (row, col) to a specific block, or to RANDOM when block is null. */
    public static void setCell(ItemStack stack, int size, int row, int col, Block block) {
        ListTag grid = ensureGrid(stack, size);
        int idx = row * size + col;
        if (idx < 0 || idx >= grid.size()) return;
        String value = (block == null) ? "" : BuiltInRegistries.BLOCK.getKey(block).toString();
        grid.set(idx, StringTag.valueOf(value));
    }

    /** Sets every cell to the same block (or RANDOM when block is null). */
    public static void fillGrid(ItemStack stack, int size, Block block) {
        ListTag grid = ensureGrid(stack, size);
        String value = (block == null) ? "" : BuiltInRegistries.BLOCK.getKey(block).toString();
        for (int i = 0; i < grid.size(); i++) grid.set(i, StringTag.valueOf(value));
    }

    /**
     * Overwrites the whole grid from a row-major list of block ids ("" = RANDOM).
     * Used when applying edits from the GUI. Entries beyond size*size are ignored.
     */
    public static void applyGrid(ItemStack stack, int size, java.util.List<String> cells) {
        ListTag grid = ensureGrid(stack, size);
        for (int i = 0; i < grid.size(); i++) {
            String value = (i < cells.size() && cells.get(i) != null) ? cells.get(i) : "";
            grid.set(i, StringTag.valueOf(value));
        }
    }

    /** True if a grid exists for this size and has at least one non-RANDOM cell. */
    public static boolean hasGridCells(ItemStack stack, int size) {
        CompoundTag r = rootOrNull(stack);
        if (r == null || r.getInt(GRID_SIZE) != size) return false;
        ListTag grid = r.getList(GRID, Tag.TAG_STRING);
        for (int i = 0; i < grid.size(); i++) {
            if (!grid.getString(i).isEmpty()) return true;
        }
        return false;
    }

    public static void clearGrid(ItemStack stack) {
        CompoundTag r = rootOrNull(stack);
        if (r != null) {
            r.remove(GRID);
            r.remove(GRID_SIZE);
        }
        pruneIfEmpty(stack);
    }

    // --- Helpers ---

    /** True if this stack is a Paintbrush that carries any Painter configuration. */
    public static boolean isConfiguredBrush(ItemStack stack) {
        return !stack.isEmpty()
                && stack.is(ModItems.PAINTBRUSH.get())
                && (hasPalette(stack) || hasSize(stack));
    }
}
