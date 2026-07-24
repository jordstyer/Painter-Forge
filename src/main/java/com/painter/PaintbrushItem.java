package com.painter;

import net.minecraft.world.item.Item;

/**
 * The dedicated Painter brush. Behaves as a plain single-stack tool for now; the
 * painting itself is driven by {@link PainterInteractions} on right-click.
 *
 * <p>Future (v2): override {@code use(...)} here to open the configuration GUI
 * when the item is right-clicked in the air.</p>
 */
public class PaintbrushItem extends Item {

    public PaintbrushItem(Properties properties) {
        super(properties);
    }
}
