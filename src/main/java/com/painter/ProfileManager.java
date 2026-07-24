package com.painter;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class ProfileManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    // FabricLoader.getConfigDir() -> Forge's config directory.
    private static final Path CONFIG_PATH = FMLPaths.CONFIGDIR.get().resolve("painter/profiles.json");
    private static Map<String, PaletteProfile> profiles = new HashMap<>();

    public static void loadFromDisk() {
        File file = CONFIG_PATH.toFile();
        if (!file.exists()) {
            file.getParentFile().mkdirs();
            saveToDisk();
            return;
        }

        try (FileReader reader = new FileReader(file)) {
            profiles = GSON.fromJson(reader, new TypeToken<Map<String, PaletteProfile>>(){}.getType());
            if (profiles == null) profiles = new HashMap<>();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void saveToDisk() {
        try (FileWriter writer = new FileWriter(CONFIG_PATH.toFile())) {
            GSON.toJson(profiles, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void saveProfile(String name, PaletteData palette, int size, PainterMod.BrushShape shape) {
        Map<String, Integer> stringWeights = new HashMap<>();
        palette.weights().forEach((block, weight) ->
                stringWeights.put(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block).toString(), weight));

        profiles.put(name, new PaletteProfile(stringWeights, size, shape.name()));
        saveToDisk();
    }

    public static PaletteProfile getProfile(String name) {
        return profiles.get(name);
    }

    public static java.util.Collection<String> getProfileNames() {
        return profiles.keySet();
    }
}
