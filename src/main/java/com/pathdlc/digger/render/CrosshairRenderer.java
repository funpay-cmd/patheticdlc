package com.pathdlc.digger.render;

import com.pathdlc.digger.gui.GuiSettings;
import com.pathdlc.digger.gui.Module;
import com.pathdlc.digger.gui.ModuleManager;
import com.pathdlc.digger.gui.ModuleSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/**
 * Replaces the vanilla crosshair with a custom one when the
 * {@code Crosshair} module is enabled.  Driven from the HUD render
 * callback BEFORE vanilla draws its crosshair so we can simply paint
 * over the same screen-centre area.  Style and colour are read from
 * the module's settings.
 */
public final class CrosshairRenderer {
   private CrosshairRenderer() {
   }

   public static void render(DrawContext ctx) {
      Module m = ModuleManager.get("Crosshair");
      if (m == null || !m.isEnabled()) {
         return;
      }
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc == null || mc.player == null || mc.options.hudHidden) {
         return;
      }
      // Hide custom crosshair while a screen is open or we are spectating.
      if (mc.currentScreen != null) {
         return;
      }

      String style = "Cross";
      ModuleSetting s = m.getSetting("Style");
      if (s != null && s.getChoiceValue() != null) {
         style = s.getChoiceValue();
      }
      int size = 5;
      ModuleSetting sz = m.getSetting("Size");
      if (sz != null) {
         size = Math.max(2, (int) sz.getFloat());
      }
      int gap = 2;
      ModuleSetting gp = m.getSetting("Gap");
      if (gp != null) {
         gap = Math.max(0, (int) gp.getFloat());
      }
      boolean accent = false;
      ModuleSetting ac = m.getSetting("Accent");
      if (ac != null) {
         accent = ac.getBool();
      }

      int color = accent
            ? 0xFF000000 | (GuiSettings.getAccentColor().textColor & 0xFFFFFF)
            : 0xFFFFFFFF;
      int sw = mc.getWindow().getScaledWidth();
      int sh = mc.getWindow().getScaledHeight();
      int cx = sw / 2;
      int cy = sh / 2;

      switch (style) {
         case "Dot":
            ctx.fill(cx - 1, cy - 1, cx + 1, cy + 1, color);
            break;
         case "Plus":
            ctx.fill(cx - size, cy, cx + size, cy + 1, color);
            ctx.fill(cx, cy - size, cx + 1, cy + size, color);
            break;
         case "X":
            for (int i = -size; i <= size; i++) {
               ctx.fill(cx + i, cy + i, cx + i + 1, cy + i + 1, color);
               ctx.fill(cx + i, cy - i, cx + i + 1, cy - i + 1, color);
            }
            break;
         case "Circle":
            int r = size + 2;
            for (int dx = -r; dx <= r; dx++) {
               for (int dy = -r; dy <= r; dy++) {
                  int d2 = dx * dx + dy * dy;
                  if (d2 >= (r - 1) * (r - 1) && d2 <= r * r) {
                     ctx.fill(cx + dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
                  }
               }
            }
            break;
         case "Square":
            ctx.fill(cx - size, cy - size, cx + size, cy - size + 1, color);
            ctx.fill(cx - size, cy + size - 1, cx + size, cy + size, color);
            ctx.fill(cx - size, cy - size, cx - size + 1, cy + size, color);
            ctx.fill(cx + size - 1, cy - size, cx + size, cy + size, color);
            break;
         case "Cross":
         default:
            // Two horizontal segments with a gap, plus two vertical segments.
            ctx.fill(cx - size - gap, cy, cx - gap, cy + 1, color);
            ctx.fill(cx + gap + 1, cy, cx + size + gap + 1, cy + 1, color);
            ctx.fill(cx, cy - size - gap, cx + 1, cy - gap, color);
            ctx.fill(cx, cy + gap + 1, cx + 1, cy + size + gap + 1, color);
            break;
      }
   }
}
