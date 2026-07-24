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
     * How a block is chosen for each painted position.
     * <ul>
     *   <li>{@code RANDOM} — weighted random draw from the palette (the original behavior).</li>
     *   <li>{@code CHECKERBOARD} — deterministic by {@code (x+y+z)} across the palette, so it tiles.</li>
     *   <li>{@code STRIPES} — deterministic horizontal bands by {@code y} across the palette.</li>
     * </ul>
     * Non-random modes ignore the palette weights and just cycle through the palette blocks
     * (ordered by registry id), so placement is seamless regardless of where you click.
     */
    public enum PatternMode {
        RANDOM, CHECKERBOARD, STRIPES
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
