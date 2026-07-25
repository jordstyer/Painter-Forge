package com.painter;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;

/**
 * Right-click handling for the Paintbrush:
 * <ul>
 *   <li><b>Shift + right-click</b> (on a block) opens the configuration GUI.</li>
 *   <li><b>Right-click</b> (no sneak) paints — server-authoritative, only cancels
 *       the interaction when a paint actually happens so ordinary block use survives.</li>
 * </ul>
 * Right-clicking the air is handled by {@link PaintbrushItem#use} (sneak = open GUI).
 *
 * <p>Painting is rate-limited via vanilla's item-cooldown system ({@link
 * net.minecraft.world.item.ItemCooldowns}) — the same mechanism items like ender
 * pearls use, complete with the hotbar sweep indicator. Without it, holding right-click
 * paints once almost every tick (the client resends the interact packet continuously
 * while the button is held), which is far more block edits per second than a player
 * could ever generate manually placing blocks — a real lag risk on a large brush.</p>
 */
@Mod.EventBusSubscriber(modid = PainterMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PainterInteractions {

    /** Minimum ticks between paints per player, ~ the pace of manual block placement. */
    private static final int PAINT_COOLDOWN_TICKS = 4;

    private PainterInteractions() {
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        ItemStack stack = event.getItemStack();
        if (!stack.is(ModItems.PAINTBRUSH.get())) return;

        Player player = event.getEntity();
        Level world = event.getLevel();

        // Sneak = open the config GUI (never paint, never subject to the cooldown).
        if (player.isShiftKeyDown()) {
            if (world.isClientSide()) {
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> () -> com.painter.client.PaintbrushClient.openScreen());
            }
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }

        // Paint (server side only does the work).
        if (world.isClientSide()) return;

        if (player instanceof ServerPlayer serverPlayer
                && serverPlayer.getCooldowns().isOnCooldown(stack.getItem())) {
            // Still cooling down from the last paint; swallow the click so vanilla
            // brushing/interaction doesn't fire, but don't touch the world.
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.PASS);
            return;
        }

        int size = BrushData.getSize(stack, 1);
        PainterMod.BrushMode mode = BrushData.getMode(stack, PainterMod.BrushMode.RANDOMIZE);
        PaletteData palette = BrushData.hasPalette(stack) ? BrushData.getPalette(stack) : new PaletteData(Map.of());
        boolean hasPalette = palette != null && !palette.weights().isEmpty();
        boolean hasGrid = mode == PainterMod.BrushMode.CUSTOM && BrushData.hasGridCells(stack, size);
        if (!hasPalette && !hasGrid) return;

        BlockHitResult hit = event.getHitVec();
        boolean success = PainterLogic.tryPaint(world, player, stack, hit.getBlockPos(), hit.getDirection(),
                palette == null ? new PaletteData(Map.of()) : palette);

        if (success) {
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.getCooldowns().addCooldown(stack.getItem(), PAINT_COOLDOWN_TICKS);
            }
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }
}
