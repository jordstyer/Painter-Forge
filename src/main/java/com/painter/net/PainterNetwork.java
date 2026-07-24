package com.painter.net;

import com.painter.PainterMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Networking for Painter. The brush's data lives in server-authoritative ItemStack
 * NBT, so edits made in the client GUI are sent to the server via this channel.
 */
public final class PainterNetwork {

    private static final String PROTOCOL = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(PainterMod.MOD_ID, "main"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals
    );

    private PainterNetwork() {
    }

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++, BrushConfigPacket.class,
                BrushConfigPacket::encode, BrushConfigPacket::decode, BrushConfigPacket::handle);
    }
}
