package com.pathdlc.digger.hud;

import com.pathdlc.digger.gui.GuiSettings;
import com.pathdlc.digger.gui.Module;
import com.pathdlc.digger.gui.ModuleManager;
import com.pathdlc.digger.render.RoundedRectRenderer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * WareVisuals HUD: top-left watermark with logo, top-right enabled modules ArrayList.
 */
public final class HudRenderer {
   private static final Identifier LOGO = Identifier.of("pathdlc_digger", "textures/gui/logo.png");
   private static final Identifier CUSTOM_FONT = Identifier.of("pathdlc_digger", "clickgui");

   private static final int WATERMARK_X = 6;
   private static final int WATERMARK_Y = 6;
   private static final int WATERMARK_PAD_X = 8;
   private static final int WATERMARK_PAD_Y = 5;
   private static final int LOGO_SIZE = 14;
   private static final int LOGO_TEXT_GAP = 6;

   private static final int ARRAYLIST_PAD_X = 6;
   private static final int ARRAYLIST_PAD_Y = 2;
   private static final int ARRAYLIST_GAP = 1;
   private static final int ARRAYLIST_STRIPE_W = 2;

   private HudRenderer() {
   }

   public static void render(DrawContext context) {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client == null || client.player == null || client.options.hudHidden || client.currentScreen != null) {
         return;
      }

      GuiSettings.AccentColor accent = GuiSettings.getAccentColor();
      TextRenderer tr = client.textRenderer;

      renderWatermark(context, tr, accent);
      renderArrayList(context, tr, accent, client.getWindow().getScaledWidth());
   }

   private static void renderWatermark(DrawContext context, TextRenderer tr, GuiSettings.AccentColor accent) {
      Text label = styledText("WareVisuals");
      int textW = tr.getWidth(label);
      int rowH = Math.max(LOGO_SIZE, tr.fontHeight) + WATERMARK_PAD_Y * 2;
      int rowW = WATERMARK_PAD_X * 2 + LOGO_SIZE + LOGO_TEXT_GAP + textW;

      int x = WATERMARK_X;
      int y = WATERMARK_Y;

      RoundedRectRenderer.draw(context, x, y, rowW, rowH, 4, 0xC0080808);
      RoundedRectRenderer.draw(context, x, y, ARRAYLIST_STRIPE_W, rowH, 1, 0xFF000000 | (accent.textColor & 0xFFFFFF));

      int logoY = y + rowH / 2 - LOGO_SIZE / 2;
      int logoX = x + WATERMARK_PAD_X;
      context.drawTexture(
         RenderLayer::getGuiTextured,
         LOGO,
         logoX,
         logoY,
         0.0F,
         0.0F,
         LOGO_SIZE,
         LOGO_SIZE,
         LOGO_SIZE,
         LOGO_SIZE
      );

      int textX = logoX + LOGO_SIZE + LOGO_TEXT_GAP;
      int textY = y + rowH / 2 - tr.fontHeight / 2;
      context.drawText(tr, label, textX, textY, 0xFFFFFFFF, true);
   }

   private static void renderArrayList(DrawContext context, TextRenderer tr, GuiSettings.AccentColor accent, int screenW) {
      List<Module> enabled = collectEnabled();
      if (enabled.isEmpty()) {
         return;
      }

      enabled.sort(Comparator.comparingInt((Module m) -> tr.getWidth(styledText(m.getName()))).reversed());

      int rowH = tr.fontHeight + ARRAYLIST_PAD_Y * 2;
      int y = WATERMARK_Y;

      for (int i = 0; i < enabled.size(); i++) {
         Module m = enabled.get(i);
         Text label = styledText(m.getName());
         int textW = tr.getWidth(label);
         int rowW = textW + ARRAYLIST_PAD_X * 2 + ARRAYLIST_STRIPE_W;
         int x = screenW - rowW - WATERMARK_X;

         int alpha = 0xC0;
         int bg = (alpha << 24) | 0x080808;
         RoundedRectRenderer.draw(context, x, y, rowW, rowH, 3, bg);

         int stripeColor = 0xFF000000 | (accent.textColor & 0xFFFFFF);
         RoundedRectRenderer.draw(context, x + rowW - ARRAYLIST_STRIPE_W, y, ARRAYLIST_STRIPE_W, rowH, 1, stripeColor);

         int textX = x + ARRAYLIST_PAD_X;
         int textY = y + rowH / 2 - tr.fontHeight / 2;
         context.drawText(tr, label, textX, textY, 0xFFFFFFFF, true);

         y += rowH + ARRAYLIST_GAP;
      }
   }

   private static List<Module> collectEnabled() {
      List<Module> out = new ArrayList<>();
      for (Module m : ModuleManager.values()) {
         if (m.isEnabled()) {
            out.add(m);
         }
      }
      return out;
   }

   private static Text styledText(String s) {
      return GuiSettings.isCustomFontEnabled()
         ? Text.literal(s).styled(style -> style.withFont(CUSTOM_FONT))
         : Text.literal(s);
   }
}
