package com.painter;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class PainterLogic {

    /** Tag containing every ore block. On Forge the convention is "forge:ores". */
    private static final TagKey<Block> ORES =
            TagKey.create(Registries.BLOCK, new ResourceLocation("forge", "ores"));

    public static boolean tryPaint(Level world, Player player, ItemStack brush,
                                   BlockPos centerPos, Direction side, PaletteData palette) {
        if (world.isClientSide()) return true;
        if (player == null) return false;

        int size = BrushData.getSize(brush, 1);
        PainterMod.BrushShape shape = BrushData.getShape(brush, PainterMod.BrushShape.SQUARE);

        Map<Item, Integer> returnedItems = new HashMap<>();
        Set<Block> missingBlocks = new HashSet<>();
        int changedCount = 0;
        BlockState lastState = null;

        int radius = (size - 1) / 2;
        int min = -radius;
        int max = (size % 2 == 0) ? radius + 1 : radius;

        for (int a = min; a <= max; a++) {
            for (int b = min; b <= max; b++) {
                if (!isInShape(a, b, size, shape)) continue;

                BlockPos targetPos = getRelativePos(centerPos, side, a, b);
                Item item = paintSingle(world, targetPos, player, palette, brush, missingBlocks);

                if (item != null) {
                    changedCount++;
                    lastState = world.getBlockState(targetPos);
                    // If the item is AIR, it was destroyed by the anti-cheat and isn't returned
                    if (item != Items.AIR) {
                        returnedItems.put(item, returnedItems.getOrDefault(item, 0) + 1);
                    }
                }
            }
        }

        if (!missingBlocks.isEmpty() && player instanceof ServerPlayer) {
            String missingBlockNames = missingBlocks.stream()
                    .map(block -> block.getName().getString())
                    .collect(Collectors.joining(", "));
            player.displayClientMessage(Component.literal("§cOut of stock: §f" + missingBlockNames), true);
        }

        if (changedCount > 0 && lastState != null) {
            world.playSound(null, centerPos, lastState.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.0f, 1.0f);
            if (world instanceof ServerLevel sw) {
                sw.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, lastState),
                        centerPos.getX() + 0.5, centerPos.getY() + 0.5, centerPos.getZ() + 0.5,
                        10 + (size * 2), 0.5, 0.5, 0.5, 0.1);
            }

            if (!player.isCreative()) {
                returnedItems.forEach((item, count) -> {
                    ItemStack stack = new ItemStack(item, count);
                    if (!player.getInventory().add(stack)) player.drop(stack, false);
                });
            }
            return true;
        }
        return false;
    }

    private static boolean isInShape(int a, int b, int size, PainterMod.BrushShape shape) {
        double offset = (size % 2 == 0) ? 0.5 : 0.0;
        double x = (double) a - offset;
        double y = (double) b - offset;
        double r = (double) size / 2.0;

        return switch (shape) {
            case SQUARE -> true;
            case CIRCLE -> (x * x + y * y) < (r * r);
            case DIAMOND -> (Math.abs(x) + Math.abs(y)) < r;
        };
    }

    private static Item paintSingle(Level world, BlockPos pos, Player player, PaletteData palette,
                                    ItemStack brush, Set<Block> missingBlocks) {
        BlockState oldState = world.getBlockState(pos);

        // 1. MASK GUARD: If a mask is set, only replace blocks in the mask.
        if (BrushData.hasMask(brush)) {
            PaletteData mask = BrushData.getMask(brush);
            if (mask != null && !mask.weights().containsKey(oldState.getBlock())) {
                return null;
            }
        }

        // 2. AIR GUARD: Prevent painting in mid-air.
        if (oldState.isAir()) return null;

        // 3. UNBREAKABLE GUARD: Prevent painting Bedrock, End Portals, etc.
        if (oldState.getDestroySpeed(world, pos) < 0.0F) return null;

        Block target = pickRandom(palette.weights(), world.random);
        if (target == null || oldState.is(target) || !isCompatible(oldState, target)) return null;

        if (!player.isCreative() && !consumeItem(player, target.asItem())) {
            missingBlocks.add(target);
            return null;
        }

        BlockState newState = target.defaultBlockState();
        for (Property<?> prop : oldState.getProperties()) {
            if (newState.hasProperty(prop)) newState = copyProp(oldState, newState, prop);
        }
        world.setBlock(pos, newState, 2);

        // Return the item evaluated by our anti-cheat logic
        return getReturnedItem(oldState);
    }

    private static Item getReturnedItem(BlockState state) {
        Block block = state.getBlock();
        String blockId = BuiltInRegistries.BLOCK.getKey(block).getPath();

        // Anti-Cheat: Prevent ore duplication.
        // ORES: Destroy completely. Drops nothing (Items.AIR).
        if (state.is(ORES) || blockId.endsWith("_ore") || blockId.equals("ancient_debris")) {
            return Items.AIR;
        }

        // For all other blocks, return the exact item that was replaced.
        return block.asItem();
    }

    private static BlockPos getRelativePos(BlockPos pos, Direction side, int a, int b) {
        return switch (side.getAxis()) {
            case X -> pos.offset(0, a, b);
            case Y -> pos.offset(a, 0, b);
            case Z -> pos.offset(a, b, 0);
        };
    }

    private static <T extends Comparable<T>> BlockState copyProp(BlockState s1, BlockState s2, Property<T> p) {
        return s2.setValue(p, s1.getValue(p));
    }

    private static Block pickRandom(Map<Block, Integer> weights, RandomSource random) {
        int total = weights.values().stream().mapToInt(i -> i).sum();
        if (total <= 0) return null;
        int roll = random.nextInt(total);
        for (var entry : weights.entrySet()) {
            if ((roll -= entry.getValue()) < 0) return entry.getKey();
        }
        return null;
    }

    private static boolean isCompatible(BlockState oldState, Block target) {
        Block b1 = oldState.getBlock();

        if (b1.getClass().equals(target.getClass())) return true;
        if (b1 instanceof RotatedPillarBlock && target instanceof RotatedPillarBlock) return true;

        // Ores are NO LONGER blocked here! You can freely paint over them.

        boolean b1IsFragile = b1 instanceof BushBlock || b1 instanceof LiquidBlock ||
                b1 instanceof EntityBlock || b1 instanceof DoorBlock ||
                b1 instanceof TrapDoorBlock || b1 instanceof BedBlock ||
                b1 instanceof CarpetBlock;

        if (b1IsFragile) return false;

        boolean b1IsStructural = b1 instanceof StairBlock || b1 instanceof SlabBlock || b1 instanceof WallBlock || b1 instanceof FenceBlock || b1 instanceof IronBarsBlock;
        boolean targetIsStructural = target instanceof StairBlock || target instanceof SlabBlock || target instanceof WallBlock || target instanceof FenceBlock || target instanceof IronBarsBlock;

        return !b1IsStructural && !targetIsStructural;
    }

    private static boolean consumeItem(Player player, Item item) {
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).is(item)) {
                inv.getItem(i).shrink(1);
                return true;
            }
        }
        return false;
    }
}
