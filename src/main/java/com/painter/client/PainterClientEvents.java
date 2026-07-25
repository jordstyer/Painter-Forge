package com.painter.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.painter.BrushData;
import com.painter.ModItems;
import com.painter.PaletteData;
import com.painter.PainterMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHighlightEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Client-only presentation: the brush tooltip (was Fabric's ItemTooltipCallback)
 * and the painted-area outline preview (was the WorldRenderer mixin). Both are
 * implemented with standard Forge client events.
 */
@Mod.EventBusSubscriber(modid = PainterMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class PainterClientEvents {

    private PainterClientEvents() {
    }

    // ---------------------------------------------------------------- Tooltip

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        int size = BrushData.getSize(stack, 1);
        if (!BrushData.hasPalette(stack) && !BrushData.hasMask(stack) && !BrushData.hasGridCells(stack, size)) return;

        List<Component> lines = event.getToolTip();

        // 1. Display active profile if it exists
        if (BrushData.hasProfile(stack)) {
            lines.add(Component.literal("§6📁 Profile: §f" + BrushData.getProfile(stack)));
        }

        // 2. Display brush settings
        PainterMod.BrushShape shape = BrushData.getShape(stack, PainterMod.BrushShape.SQUARE);
        PainterMod.BrushMode mode = BrushData.getMode(stack, PainterMod.BrushMode.RANDOMIZE);
        lines.add(Component.literal("§b📐 Size: " + size + "x" + size + " §7(" + shape.name() + ")"));
        lines.add(Component.literal("§b🎲 Mode: §7" + mode.name()));

        // 2b. Grid template preview (▪ = fixed block, · = random)
        if (BrushData.hasGridCells(stack, size)) {
            lines.add(Component.literal("§9🔲 Grid template:"));
            for (int row = 0; row < size; row++) {
                StringBuilder sb = new StringBuilder("  §7");
                for (int col = 0; col < size; col++) {
                    sb.append(BrushData.getCell(stack, size, row, col) != null ? "§b▪" : "§8·").append(' ');
                }
                lines.add(Component.literal(sb.toString()));
            }
        }

        // 3. Display Mask if it exists
        if (BrushData.hasMask(stack)) {
            PaletteData maskData = BrushData.getMask(stack);
            if (maskData != null && !maskData.weights().isEmpty()) {
                PainterMod.MaskMode maskMode = BrushData.getMaskMode(stack, PainterMod.MaskMode.INCLUDE);
                lines.add(Component.literal("§d🎯 Mask §7(" + maskMode.name() + "):"));
                String blockNames = maskData.weights().keySet().stream()
                        .map(block -> block.getName().getString())
                        .collect(Collectors.joining(", "));
                lines.add(Component.literal("  §7- " + blockNames));
            }
        }

        // 4. Display Palette weights
        PaletteData paletteData = BrushData.getPalette(stack);
        if (paletteData != null && !paletteData.weights().isEmpty()) {
            lines.add(Component.literal("§e🎨 Palette:"));
            Player player = event.getEntity();
            paletteData.weights().forEach((block, weight) -> {
                String check = "";
                if (player != null) {
                    boolean hasBlock = countItem(player.getInventory(), block.asItem()) > 0;
                    check = hasBlock ? "§a✓ " : "§c✗ ";
                }
                lines.add(Component.literal("  " + check + "§7- " + block.getName().getString() + ": §f" + weight + "%"));
            });
        }
    }

    private static int countItem(Inventory inv, net.minecraft.world.item.Item item) {
        int count = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).is(item)) count += inv.getItem(i).getCount();
        }
        return count;
    }

    // ---------------------------------------------------------------- Outline

    @SubscribeEvent
    public static void onRenderHighlight(RenderHighlightEvent.Block event) {
        Minecraft client = Minecraft.getInstance();
        Player player = client.player;
        Level world = client.level;
        if (player == null || world == null) return;

        // Only activate when holding a configured Painter brush
        ItemStack stack = player.getMainHandItem();
        if (!isBrush(stack)) {
            stack = player.getOffhandItem();
            if (!isBrush(stack)) return;
        }

        BlockHitResult hit = event.getTarget();
        BlockPos centerPos = hit.getBlockPos();
        Direction side = hit.getDirection();

        int size = BrushData.getSize(stack, 1);
        PainterMod.BrushShape shape = BrushData.getShape(stack, PainterMod.BrushShape.SQUARE);

        Vec3 cam = event.getCamera().getPosition();
        PoseStack matrices = event.getPoseStack();
        MultiBufferSource bufferSource = event.getMultiBufferSource();
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.lines());

        int radius = (size - 1) / 2;
        int min = -radius;
        int max = (size % 2 == 0) ? radius + 1 : radius;

        for (int a = min; a <= max; a++) {
            for (int b = min; b <= max; b++) {
                if (!isInShape(a, b, size, shape)) continue;

                BlockPos targetPos = getRelativePos(centerPos, side, a, b);
                BlockState targetState = world.getBlockState(targetPos);
                if (targetState.isAir()) continue;

                VoxelShape voxelShape = targetState.getShape(world, targetPos);
                if (voxelShape.isEmpty()) continue;

                renderShapeOutline(
                        matrices,
                        consumer,
                        voxelShape,
                        targetPos.getX() - cam.x,
                        targetPos.getY() - cam.y,
                        targetPos.getZ() - cam.z,
                        0.0f, 0.0f, 0.0f, 0.4f // black ~40% opacity
                );
            }
        }
    }

    /**
     * Draws the edges of a VoxelShape as lines. This mirrors the logic of
     * {@code LevelRenderer.renderShape}, which is private in 1.20.1.
     */
    private static void renderShapeOutline(PoseStack poseStack, VertexConsumer consumer, VoxelShape shape,
                                           double x, double y, double z,
                                           float red, float green, float blue, float alpha) {
        PoseStack.Pose pose = poseStack.last();
        shape.forAllEdges((x1, y1, z1, x2, y2, z2) -> {
            float dx = (float) (x2 - x1);
            float dy = (float) (y2 - y1);
            float dz = (float) (z2 - z1);
            float len = Mth.sqrt(dx * dx + dy * dy + dz * dz);
            dx /= len;
            dy /= len;
            dz /= len;
            consumer.vertex(pose.pose(), (float) (x1 + x), (float) (y1 + y), (float) (z1 + z))
                    .color(red, green, blue, alpha).normal(pose.normal(), dx, dy, dz).endVertex();
            consumer.vertex(pose.pose(), (float) (x2 + x), (float) (y2 + y), (float) (z2 + z))
                    .color(red, green, blue, alpha).normal(pose.normal(), dx, dy, dz).endVertex();
        });
    }

    private static boolean isBrush(ItemStack stack) {
        return !stack.isEmpty()
                && stack.is(ModItems.PAINTBRUSH.get())
                && (BrushData.hasSize(stack) || BrushData.hasPalette(stack));
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

    // Must stay identical to PainterLogic.getRelativePos, or the outline preview will
    // show a different footprint than what actually gets painted.
    private static BlockPos getRelativePos(BlockPos pos, Direction side, int a, int b) {
        return switch (side.getAxis()) {
            case X -> pos.offset(0, a, b);
            case Y -> pos.offset(a, 0, b);
            case Z -> pos.offset(b, a, 0);
        };
    }
}
