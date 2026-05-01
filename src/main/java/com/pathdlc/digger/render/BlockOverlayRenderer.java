package com.pathdlc.digger.render;

import net.minecraft.util.hit.HitResult;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.Identifier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexRendering;


public final class BlockOverlayRenderer {
   private static final Identifier[] OVERLAY_TEXTURES = new Identifier[]{
      Identifier.of("pathdlc_digger", "textures/gui/overlay_kitten.png"),
      Identifier.of("pathdlc_digger", "textures/gui/overlay_sky.png"),
      Identifier.of("pathdlc_digger", "textures/gui/overlay_devil.png")
   };
   private static int currentTexture;
   private static long lastSwitch;
   private static int tick;

   public static void cycleTexture() {
      currentTexture = (currentTexture + 1) % OVERLAY_TEXTURES.length;
   }

   public static String getTextureName() {
      return switch (currentTexture) {
         case 0 -> "Kitten";
         case 1 -> "Sky";
         case 2 -> "Devil";
         default -> "Unknown";
      };
   }

   public static void render(WorldRenderContext context) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.crosshairTarget != null && mc.crosshairTarget.getType() == HitResult.Type.BLOCK) {
         BlockHitResult hit = (BlockHitResult)mc.crosshairTarget;
         BlockPos pos = hit.getBlockPos();
         Direction face = hit.getSide();
         MatrixStack matrices = context.matrixStack();
         VertexConsumerProvider consumers = context.consumers();
         if (matrices != null && consumers != null && context.camera() != null) {
            Vec3d camera = context.camera().getPos();
            tick++;
            float pulse = 0.6F + 0.2F * (float)Math.sin((double)tick * 0.1);
            float r = 0.4F;
            float g = 0.7F + 0.15F * (float)Math.sin((double)tick * 0.06);
            float b = 1.0F;
            Box box = new Box(pos).expand(0.003);
            matrices.push();
            matrices.translate(-camera.x, -camera.y, -camera.z);
            VertexRendering.drawBox(matrices, consumers.getBuffer(RenderLayer.getLines()), box, r, g, b, pulse);
            VertexRendering.drawBox(
               matrices, consumers.getBuffer(RenderLayer.getLines()), box.expand(0.008), r * 0.5F, g * 0.5F, b * 0.5F, pulse * 0.3F
            );
            matrices.pop();
         }
      }
   }

   private BlockOverlayRenderer() {
   }
}
