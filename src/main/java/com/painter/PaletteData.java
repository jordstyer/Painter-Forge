package com.painter;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.HashMap;
import java.util.Map;

/**
 * Holds a map of Block -> weight (percentage).
 *
 * <p>On Fabric 1.21 this was persisted through a data-component Codec. Data
 * components do not exist in 1.20.1, so instead we (de)serialize directly to a
 * {@link CompoundTag} that lives inside the ItemStack's NBT (see {@link BrushData}).
 * Each entry is stored as a key of the block's registry id mapped to an int weight.</p>
 */
public record PaletteData(Map<Block, Integer> weights) {

    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        weights.forEach((block, weight) ->
                tag.putInt(BuiltInRegistries.BLOCK.getKey(block).toString(), weight));
        return tag;
    }

    public static PaletteData fromNbt(CompoundTag tag) {
        Map<Block, Integer> map = new HashMap<>();
        for (String key : tag.getAllKeys()) {
            ResourceLocation id = ResourceLocation.tryParse(key);
            if (id == null) continue;
            Block block = BuiltInRegistries.BLOCK.get(id);
            if (block != Blocks.AIR) {
                map.put(block, tag.getInt(key));
            }
        }
        return new PaletteData(map);
    }
}
