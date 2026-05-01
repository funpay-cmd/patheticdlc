package com.pathdlc.digger.render;

import java.util.Set;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.block.Blocks;
import net.minecraft.block.Block;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexRendering;

public final class BlockESPRenderer {
   private static final int SCAN_RADIUS = 32;
   private static final int SCAN_VERTICAL = 16;
   private static final int SCAN_COOLDOWN = 10;
   private static final Set<Block> INTERESTING_BLOCKS = Set.of(
      Blocks.DIAMOND_ORE,
      Blocks.DEEPSLATE_DIAMOND_ORE,
      Blocks.EMERALD_ORE,
      Blocks.DEEPSLATE_EMERALD_ORE,
      Blocks.ANCIENT_DEBRIS,
      Blocks.CHEST,
      Blocks.TRAPPED_CHEST,
      Blocks.ENDER_CHEST,
      Blocks.SPAWNER,
      Blocks.GOLD_ORE,
      Blocks.DEEPSLATE_GOLD_ORE,
      Blocks.IRON_ORE,
      Blocks.DEEPSLATE_IRON_ORE,
      Blocks.LAPIS_ORE,
      Blocks.DEEPSLATE_LAPIS_ORE,
      Blocks.REDSTONE_ORE,
      Blocks.DEEPSLATE_REDSTONE_ORE
   );
   private static BlockPos[] cachedPositions = new BlockPos[0];
   private static int tickCounter;

   public static void render(WorldRenderContext context) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.player != null && mc.world != null) {
         tickCounter++;
         if (tickCounter >= 10) {
            tickCounter = 0;
            cachedPositions = scan(mc);
         }

         MatrixStack matrices = context.matrixStack();
         VertexConsumerProvider consumers = context.consumers();
         if (matrices != null && consumers != null && context.camera() != null) {
            Vec3d camera = context.camera().getPos();
            matrices.push();
            matrices.translate(-camera.x, -camera.y, -camera.z);

            for (BlockPos pos : cachedPositions) {
               BlockState state = mc.world.getBlockState(pos);
               float[] color = colorFor(state.getBlock());
               Box box = new Box(pos).expand(0.002);
               VertexRendering.drawBox(matrices, consumers.getBuffer(RenderLayer.getLines()), box, color[0], color[1], color[2], 0.7F);
            }

            matrices.pop();
         }
      }
   }

   private static BlockPos[] scan(MinecraftClient mc) {
      BlockPos origin = mc.player.getBlockPos();
      int minY = Math.max(mc.world.getBottomY(), origin.getY() - 16);
      int maxY = Math.min(mc.world.getTopYInclusive(), origin.getY() + 16);
      BlockPos[] buffer = new BlockPos[512];
      int count = 0;

      for (int y = minY; y <= maxY && count < buffer.length; y++) {
         for (int x = origin.getX() - 32; x <= origin.getX() + 32 && count < buffer.length; x++) {
            for (int z = origin.getZ() - 32; z <= origin.getZ() + 32 && count < buffer.length; z++) {
               BlockPos pos = new BlockPos(x, y, z);
               if (INTERESTING_BLOCKS.contains(mc.world.getBlockState(pos).getBlock())) {
                  buffer[count++] = pos.toImmutable();
               }
            }
         }
      }

      BlockPos[] result = new BlockPos[count];
      System.arraycopy(buffer, 0, result, 0, count);
      return result;
   }

   private static float[] colorFor(Block block) {
      if (block == Blocks.DIAMOND_ORE || block == Blocks.DEEPSLATE_DIAMOND_ORE) {
         return new float[]{0.2F, 0.9F, 0.95F};
      } else if (block == Blocks.EMERALD_ORE || block == Blocks.DEEPSLATE_EMERALD_ORE) {
         return new float[]{0.1F, 0.95F, 0.3F};
      } else if (block == Blocks.ANCIENT_DEBRIS) {
         return new float[]{0.6F, 0.3F, 0.15F};
      } else if (block == Blocks.CHEST || block == Blocks.TRAPPED_CHEST || block == Blocks.ENDER_CHEST) {
         return new float[]{1.0F, 0.85F, 0.2F};
      } else if (block == Blocks.SPAWNER) {
         return new float[]{0.9F, 0.2F, 0.2F};
      } else if (block == Blocks.GOLD_ORE || block == Blocks.DEEPSLATE_GOLD_ORE) {
         return new float[]{1.0F, 0.85F, 0.0F};
      } else if (block == Blocks.IRON_ORE || block == Blocks.DEEPSLATE_IRON_ORE) {
         return new float[]{0.85F, 0.7F, 0.6F};
      } else if (block == Blocks.LAPIS_ORE || block == Blocks.DEEPSLATE_LAPIS_ORE) {
         return new float[]{0.15F, 0.2F, 0.9F};
      } else {
         return block != Blocks.REDSTONE_ORE && block != Blocks.DEEPSLATE_REDSTONE_ORE ? new float[]{1.0F, 1.0F, 1.0F} : new float[]{0.9F, 0.1F, 0.1F};
      }
   }

   private BlockESPRenderer() {
   }
}
