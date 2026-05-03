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
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/**
 * Pill-shaped billboard marker rendered at every cached AutoEvent's world
 * coordinates. Always faces the camera, ignores depth (visible through walls),
 * scales with distance so it stays roughly the same on-screen size from up
 * close to several thousand blocks away.
 *
 * <p>Layout matches the user-supplied reference:
 *
 * <pre>
 * +--------+----------------+
 * |        | Аир-дроп       |
 * | [icon] |                |
 * |        | 1229 метров    |
 * +--------+----------------+
 * </pre>
 */
public final class EventBeaconRenderer {
   private static final Identifier MONO_FONT = Identifier.of("pathdlc_digger", "mono");
   private static final Identifier TITLE_FONT = Identifier.of("pathdlc_digger", "title");

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
      MatrixStack matrices = context.matrixStack();
      VertexConsumerProvider consumers = context.consumers();
      if (camera == null || matrices == null || consumers == null) {
         return;
      }
      Vec3d camPos = camera.getPos();

      GuiSettings.AccentColor accent = GuiSettings.getAccentColor();
      int rgb = accent.textColor & 0xFFFFFF;
      float r = ((rgb >> 16) & 0xFF) / 255.0F;
      float g = ((rgb >> 8) & 0xFF) / 255.0F;
      float b = (rgb & 0xFF) / 255.0F;
      int accentARGB = 0xFF000000 | rgb;

      for (AutoEventBot.EventEntry event : events) {
         drawPill(mc, matrices, consumers, event, camera, camPos, r, g, b, accentARGB);
      }
   }

   private static void drawPill(
         MinecraftClient mc,
         MatrixStack matrices,
         VertexConsumerProvider consumers,
         AutoEventBot.EventEntry event,
         Camera camera,
         Vec3d camPos,
         float r, float g, float b,
         int accentARGB) {
      double tx = event.x + 0.5 - camPos.x;
      double tz = event.z + 0.5 - camPos.z;
      double labelY = clampLabelY(camera, event);
      double ty = labelY - camPos.y;

      double dist = Math.sqrt(tx * tx + tz * tz + ty * ty);
      double scale = Math.max(0.022, dist / 90.0);
      if (scale > 1.6) {
         scale = 1.6;
      }

      matrices.push();
      matrices.translate(tx, ty, tz);
      matrices.multiply(camera.getRotation());
      matrices.scale(-(float) scale, -(float) scale, (float) scale);

      Matrix4f matrix = matrices.peek().getPositionMatrix();
      TextRenderer tr = mc.textRenderer;

      String name = event.name == null ? "Event" : event.name;
      double horizDist = Math.sqrt(tx * tx + tz * tz);
      String distLabel = (int) Math.round(horizDist) + " метров";

      Text nameText = styled(name, TITLE_FONT);
      Text distText = styled(distLabel, MONO_FONT);

      int nameW = tr.getWidth(nameText);
      int distW = tr.getWidth(distText);
      int textColumnW = Math.max(nameW, distW);

      int iconSize = 22;
      int iconPad = 4;
      int rowH = iconSize + iconPad * 2;
      int textGap = 8;
      int rightPad = 10;
      int boxW = iconPad + iconSize + textGap + textColumnW + rightPad;
      int boxH = rowH;

      // Centered horizontally on the event coords; baseline of pill sits a bit above the world Y of the label.
      float left = -boxW / 2.0F;
      float right = boxW / 2.0F;
      float top = -boxH;
      float bottom = 0;

      int bgARGB = 0xCC0F0710;

      // Rounded background. Since we don't have a real RoundedRectRenderer
      // with depth-disabled vertex consumers, simulate rounding via an
      // inset main rectangle plus two narrower "cap" rectangles.
      float radius = 6.0F;
      drawQuad(matrix, consumers, left + radius, top, right - radius, bottom, bgARGB);
      drawQuad(matrix, consumers, left, top + radius, left + radius, bottom - radius, bgARGB);
      drawQuad(matrix, consumers, right - radius, top + radius, right, bottom - radius, bgARGB);
      // Anti-alias-y soft caps at corners (slightly transparent to mimic curve)
      int softARGB = 0x991A0F1A;
      drawQuad(matrix, consumers, left, top, left + radius, top + radius, softARGB);
      drawQuad(matrix, consumers, right - radius, top, right, top + radius, softARGB);
      drawQuad(matrix, consumers, left, bottom - radius, left + radius, bottom, softARGB);
      drawQuad(matrix, consumers, right - radius, bottom - radius, right, bottom, softARGB);

      // Icon (rounded red squircle on the left side of the pill).
      float iconX0 = left + iconPad;
      float iconY0 = top + iconPad;
      float iconX1 = iconX0 + iconSize;
      float iconY1 = iconY0 + iconSize;
      drawIcon(matrix, consumers, iconX0, iconY0, iconX1, iconY1, accentARGB);

      // Text column (top: name, bottom: distance).
      float textX = iconX1 + textGap;
      float textTopY = top + iconPad;
      tr.draw(nameText, textX, textTopY, 0xFFFFFFFF, false, matrix, consumers,
            TextRenderer.TextLayerType.SEE_THROUGH, 0, LightmapTextureManager.MAX_LIGHT_COORDINATE);
      float distY = textTopY + tr.fontHeight + 3;
      tr.draw(distText, textX, distY, 0xFFB0B0B0, false, matrix, consumers,
            TextRenderer.TextLayerType.SEE_THROUGH, 0, LightmapTextureManager.MAX_LIGHT_COORDINATE);

      matrices.pop();
   }

   /**
    * Draws a 22×22 chest/parcel-style icon: red rounded square with a thin white
    * waist band and a small white latch above the band — close enough to the
    * reference image without bundling extra textures.
    */
   private static void drawIcon(Matrix4f matrix, VertexConsumerProvider consumers,
                                float x0, float y0, float x1, float y1, int accentARGB) {
      float w = x1 - x0;
      float h = y1 - y0;
      float radius = Math.min(w, h) * 0.22F;

      // Squircle background built from the same 9-quad trick as the main pill.
      drawQuad(matrix, consumers, x0 + radius, y0,         x1 - radius, y1,         accentARGB);
      drawQuad(matrix, consumers, x0,          y0 + radius, x0 + radius, y1 - radius, accentARGB);
      drawQuad(matrix, consumers, x1 - radius, y0 + radius, x1,          y1 - radius, accentARGB);

      // Lighter "soft" corners to fake the curve.
      int dim = (accentARGB & 0xFFFFFF) | 0x99000000;
      drawQuad(matrix, consumers, x0,          y0,          x0 + radius, y0 + radius, dim);
      drawQuad(matrix, consumers, x1 - radius, y0,          x1,          y0 + radius, dim);
      drawQuad(matrix, consumers, x0,          y1 - radius, x0 + radius, y1,          dim);
      drawQuad(matrix, consumers, x1 - radius, y1 - radius, x1,          y1,          dim);

      // White chest "lid" line — horizontal band 2px tall just above center.
      float band0 = y0 + h * 0.45F;
      float band1 = band0 + Math.max(1.5F, h * 0.07F);
      drawQuad(matrix, consumers, x0 + w * 0.18F, band0, x1 - w * 0.18F, band1, 0xE6FFFFFF);

      // Latch — small white square in the centre on top of the band.
      float latchW = w * 0.14F;
      float latchH = h * 0.18F;
      float latchX0 = x0 + (w - latchW) / 2.0F;
      float latchX1 = latchX0 + latchW;
      float latchY0 = band0 - latchH * 0.6F;
      float latchY1 = latchY0 + latchH;
      drawQuad(matrix, consumers, latchX0, latchY0, latchX1, latchY1, 0xE6FFFFFF);

      // Hinges — two tiny dots near top.
      float hingeW = w * 0.06F;
      float hingeY0 = y0 + h * 0.18F;
      float hingeY1 = hingeY0 + h * 0.07F;
      drawQuad(matrix, consumers, x0 + w * 0.18F, hingeY0, x0 + w * 0.18F + hingeW, hingeY1, 0xCCFFFFFF);
      drawQuad(matrix, consumers, x1 - w * 0.18F - hingeW, hingeY0, x1 - w * 0.18F, hingeY1, 0xCCFFFFFF);
   }

   private static double clampLabelY(Camera camera, AutoEventBot.EventEntry event) {
      double eventY = event.y + 2.5;
      double camY = camera.getPos().y;
      if (eventY < camY - 60.0) {
         eventY = camY - 8.0;
      } else if (eventY > camY + 80.0) {
         eventY = camY + 14.0;
      }
      return eventY;
   }

   private static Text styled(String s, Identifier font) {
      if (!GuiSettings.isCustomFontEnabled()) {
         return Text.literal(s);
      }
      return Text.literal(s).styled(style -> style.withFont(font));
   }

   private static void drawQuad(Matrix4f matrix, VertexConsumerProvider consumers,
                                float x0, float y0, float x1, float y1, int argb) {
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
