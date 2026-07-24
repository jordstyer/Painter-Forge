package com.painter.net;

import com.painter.BrushData;
import com.painter.ModItems;
import com.painter.PainterMod;
import com.painter.PaletteData;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Client -> server: apply the full config edited in the GUI to the held Paintbrush —
 * size, shape, pattern, grid template, weighted palette, and mask. The item's NBT is
 * server-authoritative, so this is how GUI edits land.
 */
public class BrushConfigPacket {

    private final int size;
    private final String shape;
    private final String mode;
    private final List<String> cells;
    private final List<String> palIds;
    private final List<Integer> palWeights;
    private final List<String> maskIds;

    public BrushConfigPacket(int size, String shape, String mode, List<String> cells,
                             List<String> palIds, List<Integer> palWeights, List<String> maskIds) {
        this.size = size;
        this.shape = shape;
        this.mode = mode;
        this.cells = cells;
        this.palIds = palIds;
        this.palWeights = palWeights;
        this.maskIds = maskIds;
    }

    public static void encode(BrushConfigPacket m, FriendlyByteBuf buf) {
        buf.writeVarInt(m.size);
        buf.writeUtf(m.shape == null ? "SQUARE" : m.shape, 64);
        buf.writeUtf(m.mode == null ? "RANDOMIZE" : m.mode, 64);
        buf.writeVarInt(m.cells.size());
        for (String s : m.cells) buf.writeUtf(s == null ? "" : s, 256);
        buf.writeVarInt(m.palIds.size());
        for (int i = 0; i < m.palIds.size(); i++) {
            buf.writeUtf(m.palIds.get(i) == null ? "" : m.palIds.get(i), 256);
            buf.writeVarInt(m.palWeights.get(i));
        }
        buf.writeVarInt(m.maskIds.size());
        for (String s : m.maskIds) buf.writeUtf(s == null ? "" : s, 256);
    }

    public static BrushConfigPacket decode(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        String shape = buf.readUtf(64);
        String mode = buf.readUtf(64);
        int cellCount = Math.max(0, Math.min(buf.readVarInt(), 64));
        List<String> cells = new ArrayList<>(cellCount);
        for (int i = 0; i < cellCount; i++) cells.add(buf.readUtf(256));
        int palCount = Math.max(0, Math.min(buf.readVarInt(), 256));
        List<String> palIds = new ArrayList<>(palCount);
        List<Integer> palWeights = new ArrayList<>(palCount);
        for (int i = 0; i < palCount; i++) {
            palIds.add(buf.readUtf(256));
            palWeights.add(buf.readVarInt());
        }
        int maskCount = Math.max(0, Math.min(buf.readVarInt(), 256));
        List<String> maskIds = new ArrayList<>(maskCount);
        for (int i = 0; i < maskCount; i++) maskIds.add(buf.readUtf(256));
        return new BrushConfigPacket(size, shape, mode, cells, palIds, palWeights, maskIds);
    }

    public static void handle(BrushConfigPacket m, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;
            if (m.size < 1 || m.size > 5) return;
            if (m.cells.size() != m.size * m.size) return;

            ItemStack stack = player.getMainHandItem();
            if (!stack.is(ModItems.PAINTBRUSH.get())) return;

            BrushData.setSize(stack, m.size);
            try {
                BrushData.setShape(stack, PainterMod.BrushShape.valueOf(m.shape));
            } catch (IllegalArgumentException ignored) {
            }
            try {
                BrushData.setMode(stack, PainterMod.BrushMode.valueOf(m.mode));
            } catch (IllegalArgumentException ignored) {
            }

            BrushData.applyGrid(stack, m.size, m.cells);

            // Palette
            Map<Block, Integer> weights = new HashMap<>();
            for (int i = 0; i < m.palIds.size(); i++) {
                ResourceLocation id = ResourceLocation.tryParse(m.palIds.get(i));
                if (id == null) continue;
                Block b = BuiltInRegistries.BLOCK.get(id);
                int w = Math.max(0, Math.min(m.palWeights.get(i), 100));
                if (b != Blocks.AIR && w > 0) weights.put(b, w);
            }
            if (weights.isEmpty()) BrushData.removePalette(stack);
            else BrushData.setPalette(stack, new PaletteData(weights));

            // Mask (a set of blocks; store each with weight 1)
            Map<Block, Integer> mask = new HashMap<>();
            for (String s : m.maskIds) {
                ResourceLocation id = ResourceLocation.tryParse(s);
                if (id == null) continue;
                Block b = BuiltInRegistries.BLOCK.get(id);
                if (b != Blocks.AIR) mask.put(b, 1);
            }
            if (mask.isEmpty()) BrushData.removeMask(stack);
            else BrushData.setMask(stack, new PaletteData(mask));
        });
        context.setPacketHandled(true);
    }
}
