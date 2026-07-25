package com.painter;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Single-level, server-side undo for the most recent paint operation per player.
 * Restores block states and best-effort reverses the item economy (refund the
 * placed blocks, remove the blocks that were handed back). Not persisted across
 * a server restart — this is a convenience for "oops", not durable history.
 */
public final class UndoManager {

    /** One painted position: what it was, what we put there, and what we returned to the player. */
    public record Change(BlockPos pos, BlockState oldState, Block placedBlock, Item returnedItem) {}

    private static final Map<UUID, List<Change>> LAST = new HashMap<>();

    private UndoManager() {
    }

    public static void record(ServerPlayer player, List<Change> changes) {
        if (changes.isEmpty()) LAST.remove(player.getUUID());
        else LAST.put(player.getUUID(), changes);
    }

    /** Returns the number of blocks reverted (0 if nothing to undo). */
    public static int undo(ServerPlayer player) {
        List<Change> changes = LAST.remove(player.getUUID());
        if (changes == null || changes.isEmpty()) return 0;

        Level level = player.level();
        boolean creative = player.isCreative();
        int reverted = 0;

        // Reverse order so overlapping edits unwind cleanly.
        for (int i = changes.size() - 1; i >= 0; i--) {
            Change c = changes.get(i);
            // Only revert if the block we placed is still there (don't clobber later edits).
            if (!level.getBlockState(c.pos()).is(c.placedBlock())) continue;

            level.setBlock(c.pos(), c.oldState(), 2);
            reverted++;

            if (!creative) {
                Item placedItem = c.placedBlock().asItem();
                if (placedItem != Items.AIR) giveOrDrop(player, placedItem);   // refund what painting consumed
                if (c.returnedItem() != Items.AIR) removeOne(player, c.returnedItem()); // take back what painting returned
            }
        }
        return reverted;
    }

    private static void giveOrDrop(ServerPlayer player, Item item) {
        ItemStack stack = new ItemStack(item, 1);
        if (!player.getInventory().add(stack)) player.drop(stack, false);
    }

    private static void removeOne(ServerPlayer player, Item item) {
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).is(item)) {
                inv.getItem(i).shrink(1);
                return;
            }
        }
    }
}
