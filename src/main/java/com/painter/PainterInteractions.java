package com.painter;

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
 */
@Mod.EventBusSubscriber(modid = PainterMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PainterInteractions {

    private PainterInteractions() {
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        ItemStack stack = event.getItemStack();
        if (!stack.is(ModItems.PAINTBRUSH.get())) return;

        Player player = event.getEntity();
        Level world = event.getLevel();

        // Sneak = open the config GUI (never paint).
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
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }
}
