package com.painter;

import java.util.Map;

/**
 * A container for a full brush configuration.
 * Using Strings for block IDs ensures easy JSON serialization.
 */
public record PaletteProfile(
        Map<String, Integer> weights,
        int size,
        String shape
) {}
