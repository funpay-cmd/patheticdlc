package com.pathdlc.digger.render;

import com.pathdlc.digger.selection.SelectionManager;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexRendering;

public final class SelectionRenderer {
   public static void render(WorldRenderContext context, SelectionManager selection) {
      if (selection.isComplete()) {
         MatrixStack matrices = context.matrixStack();
         VertexConsumerProvider consumers = context.consumers();
         if (matrices != null && consumers != null && context.camera() != null) {
            Box box = selection.outlineBox();
            if (box != null) {
               Vec3d camera = context.camera().getPos();
               matrices.push();
               matrices.translate(-camera.x, -camera.y, -camera.z);
               VertexRendering.drawBox(matrices, consumers.getBuffer(RenderLayer.getLines()), box.expand(0.002), 0.2F, 0.72F, 1.0F, 1.0F);
               matrices.pop();
            }
         }
      }
   }

   private SelectionRenderer() {
   }
}
