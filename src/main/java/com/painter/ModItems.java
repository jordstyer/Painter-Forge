package com.painter;

import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Registry for the mod's items. v2 introduces a dedicated Paintbrush item so the
 * mod no longer has to hijack the vanilla archaeology brush.
 */
public final class ModItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, PainterMod.MOD_ID);

    public static final RegistryObject<Item> PAINTBRUSH = ITEMS.register("paintbrush",
            () -> new PaintbrushItem(new Item.Properties().stacksTo(1)));

    private ModItems() {
    }
}
