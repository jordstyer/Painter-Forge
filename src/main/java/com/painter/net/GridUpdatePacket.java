package com.painter.net;

import com.painter.BrushData;
import com.painter.ModItems;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Client -> server: replace the held Paintbrush's grid template with the given cells.
 * {@code cells} is row-major, length {@code size*size}; "" means a RANDOM cell.
 */
public class GridUpdatePacket {

    private final int size;
    private final List<String> cells;

    public GridUpdatePacket(int size, List<String> cells) {
        this.size = size;
        this.cells = cells;
    }

    public static void encode(GridUpdatePacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.size);
        buf.writeVarInt(msg.cells.size());
        for (String s : msg.cells) {
            buf.writeUtf(s == null ? "" : s, 256);
        }
    }

    public static GridUpdatePacket decode(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        int count = buf.readVarInt();
        // Guard against a malicious/oversized payload before allocating.
        count = Math.max(0, Math.min(count, 64));
        List<String> cells = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            cells.add(buf.readUtf(256));
        }
        return new GridUpdatePacket(size, cells);
    }

    public static void handle(GridUpdatePacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;
            // Validate: sizes must agree and be in the supported range.
            if (msg.size < 1 || msg.size > 5) return;
            if (msg.cells.size() != msg.size * msg.size) return;

            ItemStack stack = player.getMainHandItem();
            if (!stack.is(ModItems.PAINTBRUSH.get())) return;

            BrushData.applyGrid(stack, msg.size, msg.cells);
        });
        context.setPacketHandled(true);
    }
}
