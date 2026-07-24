package com.painter;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

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
    private static final String PROFILE = "profile";

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

    // --- Helpers ---

    /** True if this stack is a brush that carries any Painter configuration. */
    public static boolean isConfiguredBrush(ItemStack stack) {
        return !stack.isEmpty()
                && stack.getItem() == Items.BRUSH
                && (hasPalette(stack) || hasSize(stack));
    }
}
