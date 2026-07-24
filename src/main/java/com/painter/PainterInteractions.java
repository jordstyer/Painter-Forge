package com.painter;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Replaces the Fabric {@code BrushItemMixin}. Instead of injecting into
 * {@code BrushItem#useOn}, we listen for the right-click event and, if the held
 * brush carries a Painter palette, take over the interaction to paint.
 *
 * <p>We run this server-side only and cancel the event <em>only</em> when a paint
 * actually happened. That keeps ordinary interactions intact — e.g. right-clicking
 * a chest or door while holding a configured brush still opens it (those blocks are
 * never paintable, so no paint occurs and the event falls through to vanilla).</p>
 */
@Mod.EventBusSubscriber(modid = PainterMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PainterInteractions {

    private PainterInteractions() {
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Level world = event.getLevel();
        if (world.isClientSide()) return; // server-authoritative

        ItemStack stack = event.getItemStack();
        if (!stack.is(ModItems.PAINTBRUSH.get())) return;

        // Only override vanilla behavior if the brush has our custom palette assigned.
        if (!BrushData.hasPalette(stack)) return;
        PaletteData data = BrushData.getPalette(stack);
        if (data == null || data.weights().isEmpty()) return;

        Player player = event.getEntity();
        BlockHitResult hit = event.getHitVec();

        boolean success = PainterLogic.tryPaint(world, player, stack, hit.getBlockPos(), hit.getDirection(), data);

        // Only consume the interaction if we actually painted; otherwise let vanilla proceed.
        if (success) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }
}
