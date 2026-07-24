package com.painter;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Mod.EventBusSubscriber(modid = PainterMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class PainterCommand {

    private static final SuggestionProvider<CommandSourceStack> SUGGEST_PROFILES = (context, builder) ->
            SharedSuggestionProvider.suggest(ProfileManager.getProfileNames(), builder);

    private static final SuggestionProvider<CommandSourceStack> SUGGEST_BLOCKS = (context, builder) -> {
        String remaining = builder.getRemaining();
        int lastDelim = -1;
        for (int i = remaining.length() - 1; i >= 0; i--) {
            char c = remaining.charAt(i);
            if (c == ' ' || c == ',' || c == ';' || c == '=') {
                lastDelim = i;
                break;
            }
        }
        String currentWord = remaining.substring(lastDelim + 1);
        if (currentWord.matches("\\d+.*")) return builder.buildFuture();

        List<String> suggestions = new ArrayList<>();
        for (ResourceLocation id : BuiltInRegistries.BLOCK.keySet()) {
            if (id.getNamespace().equals("minecraft")) suggestions.add(id.getPath());
            else suggestions.add(id.toString());
        }
        return SharedSuggestionProvider.suggest(suggestions, builder.createOffset(builder.getStart() + lastDelim + 1));
    };

    private static final SuggestionProvider<CommandSourceStack> SUGGEST_SINGLE_BLOCK = (context, builder) -> {
        List<String> suggestions = new ArrayList<>();
        for (ResourceLocation id : BuiltInRegistries.BLOCK.keySet()) {
            suggestions.add(id.getNamespace().equals("minecraft") ? id.getPath() : id.toString());
        }
        return SharedSuggestionProvider.suggest(suggestions, builder);
    };

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    /**
     * Returns the player's main-hand stack if it is a Paintbrush, otherwise sends a
     * hint and returns null. All configuration commands act on the held Paintbrush.
     */
    private static ItemStack requireBrush(ServerPlayer player) {
        ItemStack stack = player.getMainHandItem();
        if (!stack.is(ModItems.PAINTBRUSH.get())) {
            player.displayClientMessage(Component.literal("§cHold a Paintbrush in your main hand to use this command."), false);
            return null;
        }
        return stack;
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("paintbrush")
                // --- HELP COMMAND ---
                .executes(context -> {
                    sendHelpMessage(context.getSource());
                    return 1;
                })
                .then(Commands.literal("help").executes(context -> {
                    sendHelpMessage(context.getSource());
                    return 1;
                }))
                // --- PROFILE COMMANDS ---
                .then(Commands.literal("save")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayer();
                                    if (player == null) return 0;
                                    ItemStack stack = requireBrush(player);
                                    if (stack == null) return 0;

                                    if (!BrushData.hasPalette(stack)) {
                                        player.displayClientMessage(Component.literal("§cYour brush has no palette to save!"), false);
                                        return 0;
                                    }

                                    String name = StringArgumentType.getString(context, "name");
                                    PaletteData palette = BrushData.getPalette(stack);
                                    int size = BrushData.getSize(stack, 1);
                                    PainterMod.BrushShape shape = BrushData.getShape(stack, PainterMod.BrushShape.SQUARE);

                                    ProfileManager.saveProfile(name, palette, size, shape);
                                    BrushData.setProfile(stack, name);

                                    player.displayClientMessage(Component.literal("§aProfile '§f" + name + "§a' saved successfully!"), false);
                                    return 1;
                                })
                        )
                )
                .then(Commands.literal("load")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(SUGGEST_PROFILES)
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayer();
                                    if (player == null) return 0;
                                    ItemStack stack = requireBrush(player);
                                    if (stack == null) return 0;

                                    String name = StringArgumentType.getString(context, "name");
                                    PaletteProfile profile = ProfileManager.getProfile(name);

                                    if (profile == null) {
                                        player.displayClientMessage(Component.literal("§cProfile '§f" + name + "§c' not found."), false);
                                        return 0;
                                    }

                                    Map<Block, Integer> weights = new HashMap<>();
                                    profile.weights().forEach((idStr, weight) -> {
                                        ResourceLocation id = ResourceLocation.tryParse(idStr);
                                        if (id == null) return;
                                        Block block = BuiltInRegistries.BLOCK.get(id);
                                        if (block != Blocks.AIR) weights.put(block, weight);
                                    });

                                    BrushData.setPalette(stack, new PaletteData(weights));
                                    BrushData.setSize(stack, profile.size());
                                    BrushData.setShape(stack, PainterMod.BrushShape.valueOf(profile.shape()));
                                    BrushData.setProfile(stack, name);

                                    player.displayClientMessage(Component.literal("§bProfile '§f" + name + "§b' loaded onto brush."), true);
                                    return 1;
                                })
                        )
                )
                // --- SETTINGS COMMANDS ---
                .then(Commands.literal("size")
                        .then(Commands.argument("value", IntegerArgumentType.integer(1, 5))
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayer();
                                    if (player == null) return 0;
                                    ItemStack stack = requireBrush(player);
                                    if (stack == null) return 0;

                                    int size = IntegerArgumentType.getInteger(context, "value");
                                    BrushData.setSize(stack, size);
                                    player.displayClientMessage(Component.literal("§bBrush size: " + size + "x" + size), true);
                                    return 1;
                                })
                        )
                )
                .then(Commands.literal("shape")
                        .then(Commands.literal("square").executes(context -> setShape(context.getSource().getPlayer(), PainterMod.BrushShape.SQUARE)))
                        .then(Commands.literal("circle").executes(context -> setShape(context.getSource().getPlayer(), PainterMod.BrushShape.CIRCLE)))
                        .then(Commands.literal("diamond").executes(context -> setShape(context.getSource().getPlayer(), PainterMod.BrushShape.DIAMOND)))
                )
                .then(Commands.literal("pattern")
                        .then(Commands.literal("random").executes(context -> setPattern(context.getSource().getPlayer(), PainterMod.PatternMode.RANDOM)))
                        .then(Commands.literal("checkerboard").executes(context -> setPattern(context.getSource().getPlayer(), PainterMod.PatternMode.CHECKERBOARD)))
                        .then(Commands.literal("stripes").executes(context -> setPattern(context.getSource().getPlayer(), PainterMod.PatternMode.STRIPES)))
                )
                // --- GRID TEMPLATE COMMANDS ---
                .then(Commands.literal("grid")
                        .then(Commands.literal("set")
                                .then(Commands.argument("row", IntegerArgumentType.integer(0, 4))
                                        .then(Commands.argument("col", IntegerArgumentType.integer(0, 4))
                                                .then(Commands.argument("block", StringArgumentType.word())
                                                        .suggests(SUGGEST_SINGLE_BLOCK)
                                                        .executes(context -> {
                                                            ServerPlayer player = context.getSource().getPlayer();
                                                            if (player == null) return 0;
                                                            ItemStack stack = requireBrush(player);
                                                            if (stack == null) return 0;

                                                            int size = BrushData.getSize(stack, 1);
                                                            int row = IntegerArgumentType.getInteger(context, "row");
                                                            int col = IntegerArgumentType.getInteger(context, "col");
                                                            if (row >= size || col >= size) {
                                                                player.displayClientMessage(Component.literal("§cCell out of range for size " + size + "x" + size + " (use 0-" + (size - 1) + ")."), false);
                                                                return 0;
                                                            }
                                                            Block block = parseBlock(StringArgumentType.getString(context, "block"));
                                                            if (block == null) {
                                                                player.displayClientMessage(Component.literal("§cUnknown block."), false);
                                                                return 0;
                                                            }
                                                            BrushData.setCell(stack, size, row, col, block);
                                                            player.displayClientMessage(Component.literal("§aCell (" + row + "," + col + ") = §f" + block.getName().getString()), true);
                                                            return 1;
                                                        })
                                                )
                                        )
                                )
                        )
                        .then(Commands.literal("random")
                                .then(Commands.argument("row", IntegerArgumentType.integer(0, 4))
                                        .then(Commands.argument("col", IntegerArgumentType.integer(0, 4))
                                                .executes(context -> {
                                                    ServerPlayer player = context.getSource().getPlayer();
                                                    if (player == null) return 0;
                                                    ItemStack stack = requireBrush(player);
                                                    if (stack == null) return 0;

                                                    int size = BrushData.getSize(stack, 1);
                                                    int row = IntegerArgumentType.getInteger(context, "row");
                                                    int col = IntegerArgumentType.getInteger(context, "col");
                                                    if (row >= size || col >= size) {
                                                        player.displayClientMessage(Component.literal("§cCell out of range for size " + size + "x" + size + " (use 0-" + (size - 1) + ")."), false);
                                                        return 0;
                                                    }
                                                    BrushData.setCell(stack, size, row, col, null);
                                                    player.displayClientMessage(Component.literal("§eCell (" + row + "," + col + ") = RANDOM"), true);
                                                    return 1;
                                                })
                                        )
                                )
                        )
                        .then(Commands.literal("fill")
                                .then(Commands.argument("block", StringArgumentType.word())
                                        .suggests(SUGGEST_SINGLE_BLOCK)
                                        .executes(context -> {
                                            ServerPlayer player = context.getSource().getPlayer();
                                            if (player == null) return 0;
                                            ItemStack stack = requireBrush(player);
                                            if (stack == null) return 0;

                                            int size = BrushData.getSize(stack, 1);
                                            Block block = parseBlock(StringArgumentType.getString(context, "block"));
                                            if (block == null) {
                                                player.displayClientMessage(Component.literal("§cUnknown block."), false);
                                                return 0;
                                            }
                                            BrushData.fillGrid(stack, size, block);
                                            player.displayClientMessage(Component.literal("§aGrid filled with §f" + block.getName().getString()), true);
                                            return 1;
                                        })
                                )
                        )
                        .then(Commands.literal("clear")
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayer();
                                    if (player == null) return 0;
                                    ItemStack stack = requireBrush(player);
                                    if (stack == null) return 0;
                                    BrushData.clearGrid(stack);
                                    player.displayClientMessage(Component.literal("§eGrid cleared (all cells RANDOM)."), true);
                                    return 1;
                                })
                        )
                )
                .then(Commands.literal("set")
                        .then(Commands.argument("pattern", StringArgumentType.greedyString())
                                .suggests(SUGGEST_BLOCKS)
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayer();
                                    if (player == null) return 0;
                                    ItemStack stack = requireBrush(player);
                                    if (stack == null) return 0;

                                    String pattern = StringArgumentType.getString(context, "pattern");
                                    Map<Block, Integer> weights = parsePattern(pattern);
                                    if (weights.isEmpty()) return 0;

                                    BrushData.setPalette(stack, new PaletteData(weights));
                                    BrushData.removeProfile(stack);

                                    player.displayClientMessage(Component.literal("§aBrush palette updated!"), true);
                                    return 1;
                                })
                        )
                )
                .then(Commands.literal("mask")
                        .then(Commands.argument("blocks", StringArgumentType.greedyString())
                                .suggests(SUGGEST_BLOCKS)
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayer();
                                    if (player == null) return 0;
                                    ItemStack stack = requireBrush(player);
                                    if (stack == null) return 0;

                                    String pattern = StringArgumentType.getString(context, "blocks");
                                    Map<Block, Integer> blocks = parsePattern(pattern);
                                    if (blocks.isEmpty()) return 0;

                                    BrushData.setMask(stack, new PaletteData(blocks));
                                    player.displayClientMessage(Component.literal("§aBrush mask updated!"), true);
                                    return 1;
                                })
                        )
                        .then(Commands.literal("clear")
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayer();
                                    if (player == null) return 0;
                                    ItemStack stack = requireBrush(player);
                                    if (stack == null) return 0;

                                    BrushData.removeMask(stack);
                                    player.displayClientMessage(Component.literal("§eBrush mask cleared."), true);
                                    return 1;
                                })
                        )
                )
                .then(Commands.literal("undo")
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayer();
                            if (player == null) return 0;
                            int reverted = UndoManager.undo(player);
                            if (reverted > 0) {
                                player.displayClientMessage(Component.literal("§aUndid §f" + reverted + "§a block(s)."), true);
                            } else {
                                player.displayClientMessage(Component.literal("§eNothing to undo."), true);
                            }
                            return 1;
                        })
                )
                .then(Commands.literal("clear")
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayer();
                            if (player == null) return 0;
                            ItemStack stack = requireBrush(player);
                            if (stack == null) return 0;

                            BrushData.removePalette(stack);
                            BrushData.removeProfile(stack);
                            BrushData.removeMask(stack);
                            player.displayClientMessage(Component.literal("§eBrush palette and mask cleared."), true);
                            return 1;
                        })
                )
        );
    }

    private static void sendHelpMessage(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("§6§l=== Painter Mod Help ==="), false);
        source.sendSuccess(() -> Component.literal("§7Craft a §fPaintbrush §7(brush + white dye) and hold it to configure."), false);
        source.sendSuccess(() -> Component.literal("§e/paintbrush set <pattern> §7- Define blocks (e.g. 50 stone, 50 grass)"), false);
        source.sendSuccess(() -> Component.literal("§e/paintbrush mask <blocks> §7- Set blocks to target (e.g. stone,dirt)"), false);
        source.sendSuccess(() -> Component.literal("§e/paintbrush size <1-5> §7- Adjust brush radius"), false);
        source.sendSuccess(() -> Component.literal("§e/paintbrush shape <type> §7- Square, Circle, or Diamond"), false);
        source.sendSuccess(() -> Component.literal("§e/paintbrush pattern <type> §7- Random, Checkerboard, or Stripes"), false);
        source.sendSuccess(() -> Component.literal("§e/paintbrush grid ... §7- Per-cell template: set/random <row> <col>, fill, clear"), false);
        source.sendSuccess(() -> Component.literal("§e/paintbrush save <name> §7- Save current settings to a profile"), false);
        source.sendSuccess(() -> Component.literal("§e/paintbrush load <name> §7- Load a saved profile"), false);
        source.sendSuccess(() -> Component.literal("§e/paintbrush undo §7- Revert your last paint"), false);
        source.sendSuccess(() -> Component.literal("§e/paintbrush clear §7- Wipe current brush settings"), false);
        source.sendSuccess(() -> Component.literal("§6§l========================"), false);
    }

    private static int setShape(ServerPlayer player, PainterMod.BrushShape shape) {
        if (player == null) return 0;
        ItemStack stack = requireBrush(player);
        if (stack == null) return 0;
        BrushData.setShape(stack, shape);
        player.displayClientMessage(Component.literal("§bBrush shape: §f" + shape.name()), true);
        return 1;
    }

    private static int setPattern(ServerPlayer player, PainterMod.PatternMode pattern) {
        if (player == null) return 0;
        ItemStack stack = requireBrush(player);
        if (stack == null) return 0;
        BrushData.setPattern(stack, pattern);
        player.displayClientMessage(Component.literal("§bBrush pattern: §f" + pattern.name()), true);
        return 1;
    }

    /** Parses a single block id ("stone" or "modid:block"), returning null if unknown. */
    private static Block parseBlock(String s) {
        ResourceLocation id = s.contains(":") ? ResourceLocation.tryParse(s) : new ResourceLocation("minecraft", s);
        if (id == null) return null;
        Block block = BuiltInRegistries.BLOCK.get(id);
        return block == Blocks.AIR ? null : block;
    }

    private static Map<Block, Integer> parsePattern(String pattern) {
        Map<Block, Integer> weights = new HashMap<>();
        for (String part : pattern.split("[,;]")) {
            part = part.trim();
            if (part.isEmpty()) continue;
            String[] subParts = part.split("\\s+");
            int weight = 100;
            String blockId;
            if (subParts[0].matches("\\d+")) {
                weight = Integer.parseInt(subParts[0]);
                blockId = subParts.length > 1 ? subParts[1] : "";
            } else {
                blockId = subParts[0];
            }
            if (blockId.isEmpty()) continue;
            ResourceLocation id = blockId.contains(":") ? ResourceLocation.tryParse(blockId) : new ResourceLocation("minecraft", blockId);
            if (id == null) continue;
            Block block = BuiltInRegistries.BLOCK.get(id);
            if (block != Blocks.AIR) weights.put(block, weight);
        }
        return weights;
    }
}
