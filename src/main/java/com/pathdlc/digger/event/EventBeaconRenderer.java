package com.pathdlc.digger.event;

import com.pathdlc.digger.gui.GuiSettings;
import com.pathdlc.digger.gui.ModuleManager;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/**
 * Draws a translucent beacon column + floating label at the world coordinates
 * of every cached AutoEvent entry. Uses RenderLayer.getDebugFilledBox so the
 * marker ignores depth and is visible through terrain at any distance.
 */
public final class EventBeaconRenderer {
   private static final Identifier MONO_FONT = Identifier.of("pathdlc_digger", "mono");
   private static final Identifier TITLE_FONT = Identifier.of("pathdlc_digger", "title");
   private static final float BEAM_HALF_WIDTH = 0.18F;
   private static final float BEAM_TOP = 320.0F;
   private static final float BEAM_BOTTOM = -64.0F;

   private EventBeaconRenderer() {
   }

   public static void render(WorldRenderContext context) {
      if (!ModuleManager.isEnabled("AutoEvent")) {
         return;
      }
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc == null || mc.player == null || mc.world == null) {
         return;
      }
      List<AutoEventBot.EventEntry> events = AutoEventBot.snapshot();
      if (events.isEmpty()) {
         return;
      }

      Camera camera = context.camera();
      if (camera == null) {
         return;
      }
      Vec3d camPos = camera.getPos();
      MatrixStack matrices = context.matrixStack();
      VertexConsumerProvider consumers = context.consumers();
      if (matrices == null || consumers == null) {
         return;
      }

      GuiSettings.AccentColor accent = GuiSettings.getAccentColor();
      int rgb = accent.textColor & 0xFFFFFF;
      float r = ((rgb >> 16) & 0xFF) / 255.0F;
      float g = ((rgb >> 8) & 0xFF) / 255.0F;
      float b = (rgb & 0xFF) / 255.0F;

      VertexConsumer beamBuffer = consumers.getBuffer(RenderLayer.getDebugFilledBox());

      for (AutoEventBot.EventEntry event : events) {
         drawBeam(matrices, beamBuffer, camPos, event, r, g, b);
         drawLabel(mc, matrices, consumers, event, camera, camPos, r, g, b);
      }
   }

   private static void drawBeam(
         MatrixStack matrices,
         VertexConsumer buffer,
         Vec3d camPos,
         AutoEventBot.EventEntry event,
         float r, float g, float b) {
      double cx = event.x + 0.5;
      double cz = event.z + 0.5;

      matrices.push();
      matrices.translate(-camPos.x, -camPos.y, -camPos.z);

      double w = BEAM_HALF_WIDTH;
      double x1 = cx - w;
      double x2 = cx + w;
      double z1 = cz - w;
      double z2 = cz + w;

      // bottom column (full alpha)
      VertexRendering.drawFilledBox(
            matrices, buffer,
            x1, BEAM_BOTTOM, z1,
            x2, event.y + 1.5, z2,
            r, g, b, 0.55F);
      // mid column (medium alpha)
      VertexRendering.drawFilledBox(
            matrices, buffer,
            x1, event.y + 1.5, z1,
            x2, event.y + 80.0, z2,
            r, g, b, 0.35F);
      // top column (faded)
      VertexRendering.drawFilledBox(
            matrices, buffer,
            x1, event.y + 80.0, z1,
            x2, BEAM_TOP, z2,
            r, g, b, 0.18F);

      matrices.pop();
   }

   private static void drawLabel(
         MinecraftClient mc,
         MatrixStack matrices,
         VertexConsumerProvider consumers,
         AutoEventBot.EventEntry event,
         Camera camera,
         Vec3d camPos,
         float r, float g, float b) {
      double labelY = clampLabelY(camera, event);

      double tx = event.x + 0.5 - camPos.x;
      double ty = labelY - camPos.y;
      double tz = event.z + 0.5 - camPos.z;

      double horizontalDistSq = tx * tx + tz * tz;
      double dist = Math.sqrt(horizontalDistSq + ty * ty);

      double straight = Math.sqrt(tx * tx + ty * ty + tz * tz);
      double scale = Math.max(0.025, straight / 80.0);
      if (scale > 1.5) {
         scale = 1.5;
      }

      matrices.push();
      matrices.translate(tx, ty, tz);
      matrices.multiply(camera.getRotation());
      matrices.scale(-(float) scale, -(float) scale, (float) scale);

      Matrix4f matrix = matrices.peek().getPositionMatrix();
      TextRenderer tr = mc.textRenderer;

      String name = event.name == null ? "Event" : event.name;
      String distLabel = (int) Math.round(dist) + "m";
      String countdown = AutoEventBot.formatCountdown(event);

      Text nameText = styled(name, TITLE_FONT);
      Text distText = styled(distLabel, MONO_FONT);
      Text cdText = styled(countdown, MONO_FONT);

      int nameW = tr.getWidth(nameText);
      int distW = tr.getWidth(distText);
      int cdW = tr.getWidth(cdText);
      int rowH = tr.fontHeight + 1;
      int totalH = rowH * 3 + 6;
      int boxW = Math.max(Math.max(nameW, distW), cdW) + 14;

      int bgARGB = 0xCC1A0A0F;
      int accentARGB = 0xFF000000 | ((int) (r * 255) << 16) | ((int) (g * 255) << 8) | (int) (b * 255);

      drawQuad(matrix, consumers, -boxW / 2.0F, -totalH - 2.0F, boxW / 2.0F, -2.0F, bgARGB);
      drawQuad(matrix, consumers, -boxW / 2.0F + 4, -totalH + rowH, boxW / 2.0F - 4, -totalH + rowH + 1, accentARGB);

      int textY = -totalH + 2;
      tr.draw(nameText, -nameW / 2.0F, textY, 0xFFFFFFFF, false, matrix, consumers,
            TextRenderer.TextLayerType.SEE_THROUGH, 0, LightmapTextureManager.MAX_LIGHT_COORDINATE);
      textY += rowH + 2;
      tr.draw(distText, -distW / 2.0F, textY, 0xFFCCCCCC, false, matrix, consumers,
            TextRenderer.TextLayerType.SEE_THROUGH, 0, LightmapTextureManager.MAX_LIGHT_COORDINATE);
      textY += rowH;
      tr.draw(cdText, -cdW / 2.0F, textY, accentARGB, false, matrix, consumers,
            TextRenderer.TextLayerType.SEE_THROUGH, 0, LightmapTextureManager.MAX_LIGHT_COORDINATE);

      matrices.pop();
   }

   private static double clampLabelY(Camera camera, AutoEventBot.EventEntry event) {
      double eventY = event.y + 2.5;
      double camY = camera.getPos().y;
      if (eventY < camY - 30.0) {
         eventY = camY - 6.0;
      } else if (eventY > camY + 60.0) {
         eventY = camY + 12.0;
      }
      return eventY;
   }

   private static Text styled(String s, Identifier font) {
      if (!GuiSettings.isCustomFontEnabled()) {
         return Text.literal(s);
      }
      return Text.literal(s).styled(style -> style.withFont(font));
   }

   private static void drawQuad(Matrix4f matrix, VertexConsumerProvider consumers, float x0, float y0, float x1, float y1, int argb) {
      int a = (argb >> 24) & 0xFF;
      int rr = (argb >> 16) & 0xFF;
      int gg = (argb >> 8) & 0xFF;
      int bb = argb & 0xFF;
      VertexConsumer vc = consumers.getBuffer(RenderLayer.getTextBackgroundSeeThrough());
      vc.vertex(matrix, x0, y1, 0).color(rr, gg, bb, a).light(LightmapTextureManager.MAX_LIGHT_COORDINATE);
      vc.vertex(matrix, x1, y1, 0).color(rr, gg, bb, a).light(LightmapTextureManager.MAX_LIGHT_COORDINATE);
      vc.vertex(matrix, x1, y0, 0).color(rr, gg, bb, a).light(LightmapTextureManager.MAX_LIGHT_COORDINATE);
      vc.vertex(matrix, x0, y0, 0).color(rr, gg, bb, a).light(LightmapTextureManager.MAX_LIGHT_COORDINATE);
   }
}
