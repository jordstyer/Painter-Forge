package com.painter;

import net.minecraftforge.fml.common.Mod;

/**
 * Forge 1.20.1 port of the Painter mod (originally Fabric 1.21).
 *
 * <p>The Fabric version stored brush configuration through the data-component
 * system that only exists in 1.20.5+. Since this targets 1.20.1, all of that
 * state now lives in ItemStack NBT via {@link BrushData}. Fabric API hooks
 * (command registration, item-use interception, tooltips, world outline
 * rendering) are replaced by Forge events; no mixins are required.</p>
 */
@Mod(PainterMod.MOD_ID)
public class PainterMod {
    public static final String MOD_ID = "painter";

    public enum BrushShape {
        SQUARE, CIRCLE, DIAMOND
    }

    public PainterMod() {
        // Load saved brush profiles from the config directory at startup.
        ProfileManager.loadFromDisk();
        // Command/interaction/tooltip/render handlers register themselves through
        // @Mod.EventBusSubscriber on the Forge event bus.
    }
}
