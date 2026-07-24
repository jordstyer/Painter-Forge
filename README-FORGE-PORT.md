# Painter — Forge 1.20.1 port

This is a port of the **Painter** mod from **Fabric 1.21.x** to **Forge 1.20.1**.
Gameplay and commands are identical to the original; only the plumbing changed.

## What changed and why

The port was not a mechanical find-and-replace because two things moved at once:
the mod loader (Fabric → Forge) **and** the Minecraft version (1.21.x → 1.20.1).

| Concern | Fabric 1.21 (original) | Forge 1.20.1 (this port) |
|---|---|---|
| Brush storage | **Data components** (`ComponentType`, `stack.get/set/contains`) | **ItemStack NBT** under a `PainterData` tag — data components don't exist before MC 1.20.5. See `BrushData.java`. |
| Mappings | Yarn (`MinecraftClient`, `Text`, `Identifier`, `Registries.BLOCK`, …) | Official/Mojang (`Minecraft`, `Component`, `ResourceLocation`, `BuiltInRegistries.BLOCK`, …) |
| Command registration | `CommandRegistrationCallback` | `RegisterCommandsEvent` |
| Brush interception | `BrushItemMixin` on `BrushItem.useOnBlock` | `PlayerInteractEvent.RightClickBlock` (no mixin) |
| Tooltip | `ItemTooltipCallback` | `ItemTooltipEvent` |
| Area outline | `WorldRenderer` mixin (`drawBlockOutline`) | `RenderHighlightEvent.Block` |
| Config dir | `FabricLoader.getConfigDir()` | `FMLPaths.CONFIGDIR` |
| Ore tag | `c:ores` (Fabric convention) | `forge:ores` (Forge convention) |

**No mixins are used** — every hook is a normal Forge event, which is more robust
across Minecraft updates. The two no-op mixins from the original
(`BrushDurabilityMixin`, `ExampleClientMixin`) were dropped as they did nothing.

## Behavior notes

- Brush configuration now lives in NBT, so **existing brushes from the Fabric
  version won't carry over** — reconfigure them with `/paintbrush set …`.
- The `RightClickBlock` handler is server-authoritative and only cancels the
  interaction when a paint actually happens. Right-clicking a chest/door while
  holding a configured brush still opens it (those blocks are never paintable).

## Commands (unchanged)

```
/paintbrush set <pattern>     e.g. 50 stone, 50 grass
/paintbrush mask <blocks>     e.g. stone,dirt
/paintbrush size <1-5>
/paintbrush shape <square|circle|diamond>
/paintbrush save <name>
/paintbrush load <name>
/paintbrush clear
```

## Building

Requires a **JDK 17** to run Gradle (Gradle 8.1.1 does not run on Java 21).

```bash
./gradlew build
```

The built mod jar lands in `build/libs/`. Drop it into your server's `mods/`
folder alongside Forge 47.x for 1.20.1.
