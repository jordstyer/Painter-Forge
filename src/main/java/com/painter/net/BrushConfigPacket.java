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
 * Client -> server: apply the full config edited in the GUI to the held Paintbrush.
 * Carries the grid template (row-major, "" = RANDOM), the weighted palette, and the
 * pattern mode. The item's NBT is server-authoritative, so this is how GUI edits land.
 */
public class BrushConfigPacket {

    private final int size;
    private final List<String> cells;
    private final List<String> palIds;
    private final List<Integer> palWeights;
    private final String pattern;

    public BrushConfigPacket(int size, List<String> cells, List<String> palIds, List<Integer> palWeights, String pattern) {
        this.size = size;
        this.cells = cells;
        this.palIds = palIds;
        this.palWeights = palWeights;
        this.pattern = pattern;
    }

    public static void encode(BrushConfigPacket m, FriendlyByteBuf buf) {
        buf.writeVarInt(m.size);
        buf.writeVarInt(m.cells.size());
        for (String s : m.cells) buf.writeUtf(s == null ? "" : s, 256);
        buf.writeVarInt(m.palIds.size());
        for (int i = 0; i < m.palIds.size(); i++) {
            buf.writeUtf(m.palIds.get(i) == null ? "" : m.palIds.get(i), 256);
            buf.writeVarInt(m.palWeights.get(i));
        }
        buf.writeUtf(m.pattern == null ? "RANDOM" : m.pattern, 64);
    }

    public static BrushConfigPacket decode(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
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
        String pattern = buf.readUtf(64);
        return new BrushConfigPacket(size, cells, palIds, palWeights, pattern);
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

            // Grid
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

            // Pattern
            try {
                BrushData.setPattern(stack, PainterMod.PatternMode.valueOf(m.pattern));
            } catch (IllegalArgumentException ignored) {
                // leave pattern unchanged on a bad value
            }
        });
        context.setPacketHandled(true);
    }
}
