package com.painter;

import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

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

    /**
     * How the brush decides what to place.
     * <ul>
     *   <li>{@code RANDOMIZE} — every painted position draws a fresh weighted-random block
     *       from the palette on each right-click; the grid template is ignored.</li>
     *   <li>{@code CUSTOM} — use the grid template: each cell places its assigned block, and
     *       any unset (RANDOM) cell draws from the palette.</li>
     * </ul>
     */
    public enum BrushMode {
        RANDOMIZE, CUSTOM
    }

    public PainterMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // Register the Paintbrush item (and any future items).
        ModItems.ITEMS.register(modEventBus);

        // Set up the client<->server channel used by the configuration GUI.
        com.painter.net.PainterNetwork.register();

        // Load saved brush profiles from the config directory at startup.
        ProfileManager.loadFromDisk();
        // Command/interaction/tooltip/render handlers register themselves through
        // @Mod.EventBusSubscriber on the Forge event bus.
    }
}
