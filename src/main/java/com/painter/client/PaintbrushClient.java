package com.painter.client;

import net.minecraft.client.Minecraft;

/**
 * Client-only entry point for opening the Paintbrush configuration screen.
 * Kept separate so the server never classloads any client rendering code.
 */
public final class PaintbrushClient {

    private PaintbrushClient() {
    }

    public static void openScreen() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.setScreen(new PaintbrushScreen());
    }
}
