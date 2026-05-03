package com.pathdlc.digger.render;

import com.pathdlc.digger.gui.GuiSettings;
import com.pathdlc.digger.gui.Module;
import com.pathdlc.digger.gui.ModuleManager;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.TntEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;

/**
 * Floats a countdown timer above each primed-TNT entity in the world
 * (in seconds).  Renders through translucent geometry so the value is
 * always visible.  Driven from the existing AFTER_ENTITIES world-render
 * callback so it costs nothing while the module is disabled or no TNT
 * is in range.
 */
public final class TntTimerRenderer {
   private static final Identifier MONO = Identifier.of("pathdlc_digger", "mono");
   /** Don't draw past this far — at 80m the digits are sub-pixel anyway. */
   private static final double MAX_DISTANCE = 80.0D;

   private TntTimerRenderer() {
   }

   public static void render(WorldRenderContext ctx) {
      Module m = ModuleManager.get("TNTTimer");
      if (m == null || !m.isEnabled()) {
         return;
      }
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc == null || mc.world == null || mc.player == null) {
         return;
      }
      Camera camera = ctx.camera();
      if (camera == null) {
         return;
      }
      MatrixStack matrices = ctx.matrixStack();
      VertexConsumerProvider.Immediate consumers =
            (VertexConsumerProvider.Immediate) mc.getBufferBuilders().getEntityVertexConsumers();
      TextRenderer fr = mc.textRenderer;
      double cx = camera.getPos().x;
      double cy = camera.getPos().y;
      double cz = camera.getPos().z;
      int accent = 0xFF000000 | (GuiSettings.getAccentColor().textColor & 0xFFFFFF);

      for (var entity : mc.world.getEntities()) {
         if (!(entity instanceof TntEntity tnt)) {
            continue;
         }
         double dx = tnt.getX() - cx;
         double dy = tnt.getY() - cy;
         double dz = tnt.getZ() - cz;
         double dist2 = dx * dx + dy * dy + dz * dz;
         if (dist2 > MAX_DISTANCE * MAX_DISTANCE) {
            continue;
         }
         int fuse = tnt.getFuse();
         if (fuse <= 0) {
            continue;
         }
         float seconds = fuse / 20.0F;
         String label = String.format("%.1fs", seconds);

         matrices.push();
         matrices.translate(dx, dy + 1.2D, dz);
         matrices.multiply(camera.getRotation());
         matrices.scale(-0.025F, -0.025F, 0.025F);
         Matrix4f m4 = matrices.peek().getPositionMatrix();
         int half = fr.getWidth(label) / 2;
         // Halo background pill so the digits are readable against bright fire.
         fr.draw(Text.literal(label).styled(s -> s.withFont(MONO)),
               -half, 0,
               accent, false, m4, consumers,
               TextRenderer.TextLayerType.SEE_THROUGH,
               0x60000000, 0xF000F0);
         fr.draw(Text.literal(label).styled(s -> s.withFont(MONO)),
               -half, 0,
               accent, false, m4, consumers,
               TextRenderer.TextLayerType.NORMAL,
               0, 0xF000F0);
         matrices.pop();
      }
      consumers.draw();
   }
}
