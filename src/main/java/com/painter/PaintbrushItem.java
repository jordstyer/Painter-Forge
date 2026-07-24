package com.painter;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

/**
 * The dedicated Painter brush. Painting on a block is handled by
 * {@link PainterInteractions}; right-clicking in the air opens the configuration
 * GUI (client-side only).
 */
public class PaintbrushItem extends Item {

    public PaintbrushItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        // The config GUI (and its network apply) operate on the main-hand brush.
        if (level.isClientSide && hand == InteractionHand.MAIN_HAND) {
            // Load and call the client-only screen opener strictly on the physical client.
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> com.painter.client.PaintbrushClient.openScreen());
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
