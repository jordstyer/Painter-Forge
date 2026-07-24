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

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
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
                                    ItemStack stack = player.getMainHandItem();

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

                                    String name = StringArgumentType.getString(context, "name");
                                    PaletteProfile profile = ProfileManager.getProfile(name);

                                    if (profile == null) {
                                        player.displayClientMessage(Component.literal("§cProfile '§f" + name + "§c' not found."), false);
                                        return 0;
                                    }

                                    ItemStack stack = player.getMainHandItem();
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
                                    if (player != null) {
                                        int size = IntegerArgumentType.getInteger(context, "value");
                                        BrushData.setSize(player.getMainHandItem(), size);
                                        player.displayClientMessage(Component.literal("§bBrush size: " + size + "x" + size), true);
                                    }
                                    return 1;
                                })
                        )
                )
                .then(Commands.literal("shape")
                        .then(Commands.literal("square").executes(context -> setShape(context.getSource().getPlayer(), PainterMod.BrushShape.SQUARE)))
                        .then(Commands.literal("circle").executes(context -> setShape(context.getSource().getPlayer(), PainterMod.BrushShape.CIRCLE)))
                        .then(Commands.literal("diamond").executes(context -> setShape(context.getSource().getPlayer(), PainterMod.BrushShape.DIAMOND)))
                )
                .then(Commands.literal("set")
                        .then(Commands.argument("pattern", StringArgumentType.greedyString())
                                .suggests(SUGGEST_BLOCKS)
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayer();
                                    if (player != null) {
                                        String pattern = StringArgumentType.getString(context, "pattern");
                                        Map<Block, Integer> weights = parsePattern(pattern);
                                        if (weights.isEmpty()) return 0;

                                        ItemStack stack = player.getMainHandItem();
                                        BrushData.setPalette(stack, new PaletteData(weights));
                                        BrushData.removeProfile(stack);

                                        player.displayClientMessage(Component.literal("§aBrush palette updated!"), true);
                                    }
                                    return 1;
                                })
                        )
                )
                .then(Commands.literal("mask")
                        .then(Commands.argument("blocks", StringArgumentType.greedyString())
                                .suggests(SUGGEST_BLOCKS)
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayer();
                                    if (player != null) {
                                        String pattern = StringArgumentType.getString(context, "blocks");
                                        Map<Block, Integer> blocks = parsePattern(pattern);
                                        if (blocks.isEmpty()) return 0;

                                        BrushData.setMask(player.getMainHandItem(), new PaletteData(blocks));
                                        player.displayClientMessage(Component.literal("§aBrush mask updated!"), true);
                                    }
                                    return 1;
                                })
                        )
                        .then(Commands.literal("clear")
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayer();
                                    if (player != null) {
                                        BrushData.removeMask(player.getMainHandItem());
                                        player.displayClientMessage(Component.literal("§eBrush mask cleared."), true);
                                    }
                                    return 1;
                                })
                        )
                )
                .then(Commands.literal("clear")
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayer();
                            if (player != null) {
                                ItemStack stack = player.getMainHandItem();
                                BrushData.removePalette(stack);
                                BrushData.removeProfile(stack);
                                BrushData.removeMask(stack);
                                player.displayClientMessage(Component.literal("§eBrush palette and mask cleared."), true);
                            }
                            return 1;
                        })
                )
        );
    }

    private static void sendHelpMessage(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("§6§l=== Painter Mod Help ==="), false);
        source.sendSuccess(() -> Component.literal("§e/paintbrush set <pattern> §7- Define blocks (e.g. 50 stone, 50 grass)"), false);
        source.sendSuccess(() -> Component.literal("§e/paintbrush mask <blocks> §7- Set blocks to target (e.g. stone,dirt)"), false);
        source.sendSuccess(() -> Component.literal("§e/paintbrush size <1-5> §7- Adjust brush radius"), false);
        source.sendSuccess(() -> Component.literal("§e/paintbrush shape <type> §7- Square, Circle, or Diamond"), false);
        source.sendSuccess(() -> Component.literal("§e/paintbrush save <name> §7- Save current settings to a profile"), false);
        source.sendSuccess(() -> Component.literal("§e/paintbrush load <name> §7- Load a saved profile"), false);
        source.sendSuccess(() -> Component.literal("§e/paintbrush clear §7- Wipe current brush settings"), false);
        source.sendSuccess(() -> Component.literal("§6§l========================"), false);
    }

    private static int setShape(ServerPlayer player, PainterMod.BrushShape shape) {
        if (player != null) {
            BrushData.setShape(player.getMainHandItem(), shape);
            player.displayClientMessage(Component.literal("§bBrush shape: §f" + shape.name()), true);
        }
        return 1;
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
