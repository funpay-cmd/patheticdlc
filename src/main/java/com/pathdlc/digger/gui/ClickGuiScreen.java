package com.pathdlc.digger.gui;

import com.pathdlc.digger.render.LiquidGlassRenderer;
import com.pathdlc.digger.render.PerformanceSettings;
import com.pathdlc.digger.render.RoundedRectRenderer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;

public class ClickGuiScreen extends Screen {
   private static final int COL_WIDTH = 130;
   private static final int COL_GAP = 6;
   private static final int HEADER_H = 26;
   private static final int MODULE_H = 18;
   private static final int SETTING_H = 16;
   private static final int PAD = 6;
   private static final int CORNER_R = 8;
   private static final int SCROLL_SPEED = 10;
   private static final Identifier CUSTOM_FONT = Identifier.of("pathdlc_digger", "clickgui");
   private static final Identifier LOGO = Identifier.of("pathdlc_digger", "textures/gui/logo.png");
   private final List<Category> categories = new ArrayList<>();
   private float openProgress = 0.0F;
   private final Map<String, Integer> scrollOffsets = new HashMap<>();
   private final Map<String, Float> colDragY = new HashMap<>();
   private final Map<String, Boolean> compactExpanded = new HashMap<>();
   private ModuleSetting draggingSlider;
   private float draggingSliderX;
   private float draggingSliderW;
   private int dogenSelectedCategory = 0;
   private int tabsSelectedCategory = 0;
   private int cardsOpenCategory = -1;
   private static final int DOGEN_PANEL_W = 520;
   private static final int DOGEN_PANEL_H = 320;
   private static final int DOGEN_SIDEBAR_W = 90;
   private static final int DOGEN_HEADER_H = 30;
   private static final int DOGEN_GAP = 6;
   private static final int DOGEN_CAT_H = 30;
   private static final int DOGEN_MODULE_H = 22;
   private static final int TABS_PANEL_W = 540;
   private static final int TABS_PANEL_H = 320;
   private static final int TABS_BAR_H = 36;
   private static final int TABS_GAP = 6;
   private static final int TABS_MODULE_H = 22;
   private static final int COMPACT_PANEL_W = 200;
   private static final int COMPACT_HEADER_H = 30;
   private static final int COMPACT_CAT_H = 22;
   private static final int COMPACT_MODULE_H = 18;
   private static final int CARDS_W = 160;
   private static final int CARDS_H = 90;
   private static final int CARDS_GAP = 10;
   private static final int CARDS_COLS = 3;
   private static final int CARDS_OVERLAY_W = 340;
   private static final int CARDS_OVERLAY_H = 300;

   private enum Style {
      COLON,
      DOGEN,
      TABS,
      COMPACT,
      CARDS
   }

   public ClickGuiScreen() {
      super(Text.literal("ClickGUI"));
   }

   protected void init() {
      super.init();
      this.openProgress = 0.0F;
   }

   public void initCategories(
      Runnable appleOn,
      Runnable appleOff,
      Runnable digOn,
      Runnable digOff,
      Runnable clanOn,
      Runnable clanOff
   ) {
      if (this.categories.isEmpty()) {
         Module apple = new Module("Apple", appleOn, appleOff);
         apple.addSetting(ModuleSetting.toggle("Notify", true));
         Module dig = new Module("Dig", digOn, digOff);
         dig.addSetting(ModuleSetting.toggle("Use Baritone", true));
         Module clan = new Module("Clan", clanOn, clanOff);
         clan.addSetting(ModuleSetting.toggle("Notify", true));
         Module autoEvent = new Module("AutoEvent");
         autoEvent.addSetting(ModuleSetting.toggle("Auto Refresh", true));
         Category modules = new Category("Modules", 0.0F, 0.0F);
         modules.addModule(apple);
         modules.addModule(dig);
         modules.addModule(clan);
         modules.addModule(autoEvent);
         this.categories.add(modules);

         Module hudOptions = new Module("HUDOptions");
         hudOptions.addSetting(ModuleSetting.toggle("Watermark", true));
         hudOptions.addSetting(ModuleSetting.toggle("ArrayList", true));
         hudOptions.addSetting(ModuleSetting.toggle("FPS", true));
         hudOptions.addSetting(ModuleSetting.toggle("Ping", true));
         hudOptions.addSetting(ModuleSetting.toggle("Coords", true));
         hudOptions.addSetting(ModuleSetting.toggle("Time", false));
         hudOptions.addSetting(ModuleSetting.toggle("TargetHUD", true));
         hudOptions.addSetting(ModuleSetting.toggle("Music", true));
         hudOptions.setEnabled(true);
         Category hud = new Category("HUD", 0.0F, 0.0F);
         hud.addModule(hudOptions);
         this.categories.add(hud);

         Module menuStyle = new Module("MenuStyle");
         menuStyle.addSetting(ModuleSetting.choice("Style", new String[]{"Colon", "Dogen", "Tabs", "Compact", "Cards"}, 0));
         Category settings = new Category("Settings", 0.0F, 0.0F);
         settings.addModule(menuStyle);
         this.categories.add(settings);

         for (Category cat : this.categories) {
            for (ModuleButton btn : cat.getModules()) {
               ModuleManager.register(btn.getModule());
            }
         }
      }
   }

   private boolean isDogenLayout() {
      return this.getStyle() == Style.DOGEN;
   }

   private Style getStyle() {
      Module m = ModuleManager.get("MenuStyle");
      if (m == null) {
         return Style.COLON;
      }
      ModuleSetting s = m.getSetting("Style");
      if (s == null) {
         return Style.COLON;
      }
      String v = s.getChoiceValue();
      if (v == null) {
         return Style.COLON;
      }
      switch (v) {
         case "Dogen":
            return Style.DOGEN;
         case "Tabs":
            return Style.TABS;
         case "Compact":
            return Style.COMPACT;
         case "Cards":
            return Style.CARDS;
         default:
            return Style.COLON;
      }
   }

   private int totalWidth() {
      return this.categories.size() * 130 + (this.categories.size() - 1) * 6;
   }

   private int startX() {
      return (this.width - this.totalWidth()) / 2;
   }

   private int startY() {
      return 40;
   }

   public void render(DrawContext context, int mouseX, int mouseY, float delta) {
      this.openProgress = this.openProgress + (1.0F - this.openProgress) * 0.15F;
      if (this.openProgress > 0.99F) {
         this.openProgress = 1.0F;
      }

      if (!(this.openProgress < 0.01F)) {
         LiquidGlassRenderer.captureAndBlur();
         int dimAlpha = (int)(this.openProgress * 102.0F);
         context.fill(0, 0, this.width, this.height, dimAlpha << 24);
         GuiSettings.AccentColor accent = GuiSettings.getAccentColor();
         switch (this.getStyle()) {
            case DOGEN:
               this.renderDogenLayout(context, mouseX, mouseY, accent);
               break;
            case TABS:
               this.renderTabsLayout(context, mouseX, mouseY, accent);
               break;
            case COMPACT:
               this.renderCompactLayout(context, mouseX, mouseY, accent);
               break;
            case CARDS:
               this.renderCardsLayout(context, mouseX, mouseY, accent);
               break;
            default:
               this.renderColonLayout(context, mouseX, mouseY, accent);
               this.renderColonBrand(context, accent);
               break;
         }

         super.render(context, mouseX, mouseY, delta);
      }
   }

   private void renderColonBrand(DrawContext context, GuiSettings.AccentColor accent) {
      float alpha = this.openProgress;
      if (alpha < 0.05F) {
         return;
      }
      int logoSize = 22;
      String brand = "WareVisuals";
      int textW = this.textRenderer.getWidth(this.styledText(brand));
      int rowW = 14 + logoSize + 6 + textW + 14;
      int rowH = 30;
      int rowX = (this.width - rowW) / 2;
      int rowY = 8 + (int)((1.0F - alpha) * -10.0F);
      int alphaByte = (int)(alpha * 200.0F);
      RoundedRectRenderer.draw(context, rowX, rowY, rowW, rowH, 6, alphaByte << 24 | 0x080808);
      int logoX = rowX + 14;
      int logoY = rowY + rowH / 2 - logoSize / 2;
      context.drawTexture(
         RenderLayer::getGuiTextured,
         LOGO,
         logoX,
         logoY,
         0.0F,
         0.0F,
         logoSize,
         logoSize,
         logoSize,
         logoSize
      );
      int textX = logoX + logoSize + 6;
      int textY = rowY + rowH / 2 - this.textRenderer.fontHeight / 2;
      this.drawStyledText(context, brand, textX, textY, -1);
   }

   private void renderColonLayout(DrawContext context, int mouseX, int mouseY, GuiSettings.AccentColor accent) {
      int sx = this.startX();
      int sy = this.startY();

      for (int i = 0; i < this.categories.size(); i++) {
         Category cat = this.categories.get(i);
         int cx = sx + i * 136;
         int colH = this.getColumnHeight(cat);
         float slideOffset = (1.0F - this.openProgress) * (float)(30 + i * 12);
         int cy = sy + (int)slideOffset;
         float alpha = this.openProgress;
         this.renderColumn(context, cat, cx, cy, colH, mouseX, mouseY, accent, alpha);
      }
   }

   private int dogenStartX() {
      return (this.width - DOGEN_PANEL_W) / 2;
   }

   private int dogenStartY() {
      return (this.height - DOGEN_PANEL_H) / 2;
   }

   private int dogenSidebarX() {
      return this.dogenStartX();
   }

   private int dogenContentX() {
      return this.dogenStartX() + DOGEN_SIDEBAR_W + DOGEN_GAP;
   }

   private int dogenContentW() {
      return DOGEN_PANEL_W - DOGEN_SIDEBAR_W - DOGEN_GAP;
   }

   private int dogenContentY() {
      return this.dogenStartY() + DOGEN_HEADER_H + DOGEN_GAP;
   }

   private int dogenContentH() {
      return DOGEN_PANEL_H - DOGEN_HEADER_H - DOGEN_GAP;
   }

   private void renderDogenLayout(DrawContext context, int mouseX, int mouseY, GuiSettings.AccentColor accent) {
      if (this.dogenSelectedCategory < 0 || this.dogenSelectedCategory >= this.categories.size()) {
         this.dogenSelectedCategory = 0;
      }

      int sx = this.dogenStartX();
      int sy = this.dogenStartY();
      float alpha = this.openProgress;
      float slideOffset = (1.0F - this.openProgress) * 30.0F;
      int sidebarY = sy + (int)slideOffset;
      int sidebarH = DOGEN_PANEL_H;
      int sidebarX = sx;
      if (PerformanceSettings.useGlassEffect() && LiquidGlassRenderer.isReady()) {
         LiquidGlassRenderer.drawGlassPanel(context, (float)sidebarX, (float)sidebarY, (float)DOGEN_SIDEBAR_W, (float)sidebarH, 8.0F, 0.0F, 0.08F, accent.r, accent.g, accent.b);
      } else {
         RoundedRectRenderer.draw(context, sidebarX, sidebarY, DOGEN_SIDEBAR_W, sidebarH, 8, 0xCC1A0A0F);
      }

      int catH = Math.max(DOGEN_CAT_H, (sidebarH - 8) / Math.max(1, this.categories.size()));
      int cy = sidebarY + 4;

      for (int i = 0; i < this.categories.size(); i++) {
         Category cat = this.categories.get(i);
         boolean selected = i == this.dogenSelectedCategory;
         boolean hovered = mouseX >= sidebarX + 4
            && mouseX <= sidebarX + DOGEN_SIDEBAR_W - 4
            && mouseY >= cy
            && mouseY < cy + catH;
         if (selected) {
            RoundedRectRenderer.draw(context, sidebarX + 4, cy, DOGEN_SIDEBAR_W - 8, catH, 5, (int)(96.0F * alpha) << 24 | accent.textColor & 16777215);
         } else if (hovered) {
            int hAlpha = (int)(40.0F * alpha);
            RoundedRectRenderer.draw(context, sidebarX + 4, cy, DOGEN_SIDEBAR_W - 8, catH, 5, hAlpha << 24 | 16777215);
         }

         int textColor = selected ? -1 : -3355444;
         String label = cat.getName();
         int textW = this.textRenderer.getWidth(this.styledText(label));
         int tx = sidebarX + DOGEN_SIDEBAR_W / 2 - textW / 2;
         int ty = cy + catH / 2 - this.textRenderer.fontHeight / 2;
         this.drawStyledText(context, label, tx, ty, textColor);
         cy += catH;
      }

      int hx = sx + DOGEN_SIDEBAR_W + DOGEN_GAP;
      int hy = sy + (int)slideOffset;
      int hw = this.dogenContentW();
      if (PerformanceSettings.useGlassEffect() && LiquidGlassRenderer.isReady()) {
         LiquidGlassRenderer.drawGlassPanel(context, (float)hx, (float)hy, (float)hw, (float)DOGEN_HEADER_H, 8.0F, 0.0F, 0.08F, accent.r, accent.g, accent.b);
      } else {
         RoundedRectRenderer.draw(context, hx, hy, hw, DOGEN_HEADER_H, 8, 0xCC1A0A0F);
      }

      Category selectedCat = this.categories.get(this.dogenSelectedCategory);
      String header = selectedCat.getName();
      int headerY = hy + DOGEN_HEADER_H / 2 - this.textRenderer.fontHeight / 2;
      int logoSize = 18;
      int logoX = hx + 8;
      int logoY = hy + DOGEN_HEADER_H / 2 - logoSize / 2;
      context.drawTexture(
         RenderLayer::getGuiTextured,
         LOGO,
         logoX,
         logoY,
         0.0F,
         0.0F,
         logoSize,
         logoSize,
         logoSize,
         logoSize
      );
      int brandX = logoX + logoSize + 6;
      this.drawStyledText(context, "WareVisuals", brandX, headerY, accent.textColor);
      int brandW = this.textRenderer.getWidth(this.styledText("WareVisuals"));
      int sepX = brandX + brandW + 8;
      context.fill(sepX, hy + 8, sepX + 1, hy + DOGEN_HEADER_H - 8, 0x40FFFFFF);
      this.drawStyledText(context, header, sepX + 8, headerY, -1);
      int countLabelW = this.textRenderer.getWidth(this.styledText(selectedCat.getModules().size() + " modules"));
      this.drawStyledText(
         context,
         selectedCat.getModules().size() + " modules",
         hx + hw - countLabelW - 12,
         headerY,
         accent.textColor
      );
      int mainX = hx;
      int mainY = this.dogenContentY() + (int)slideOffset;
      int mainW = hw;
      int mainH = this.dogenContentH();
      if (PerformanceSettings.useGlassEffect() && LiquidGlassRenderer.isReady()) {
         LiquidGlassRenderer.drawGlassPanel(context, (float)mainX, (float)mainY, (float)mainW, (float)mainH, 8.0F, 0.0F, 0.08F, accent.r, accent.g, accent.b);
      } else {
         RoundedRectRenderer.draw(context, mainX, mainY, mainW, mainH, 8, 0xCC1A0A0F);
      }

      int scroll = this.scrollOffsets.getOrDefault("dogen:" + selectedCat.getName(), 0);
      context.enableScissor(mainX + 2, mainY + 2, mainX + mainW - 2, mainY + mainH - 2);
      int rowY = mainY + 8 - scroll;

      for (ModuleButton btn : selectedCat.getModules()) {
         Module mod = btn.getModule();
         int rowH = DOGEN_MODULE_H;
         boolean hovered = mouseX >= mainX + 6
            && mouseX <= mainX + mainW - 6
            && mouseY >= rowY
            && mouseY < rowY + rowH
            && mouseY >= mainY + 2
            && mouseY < mainY + mainH - 2;
         btn.updateHover(hovered);
         if (mod.isEnabled()) {
            RoundedRectRenderer.draw(context, mainX + 6, rowY, mainW - 12, rowH, 5, (int)(78.0F * alpha) << 24 | accent.textColor & 16777215);
         } else if (btn.hoverAmount > 0.01F) {
            int hAlpha = (int)(btn.hoverAmount * 36.0F * alpha);
            RoundedRectRenderer.draw(context, mainX + 6, rowY, mainW - 12, rowH, 5, hAlpha << 24 | 16777215);
         }

         int nameColor = mod.isEnabled() ? -1 : -3355444;
         int nameY = rowY + rowH / 2 - this.textRenderer.fontHeight / 2;
         this.drawStyledText(context, mod.getName(), mainX + 16, nameY, nameColor);
         int sw = 22;
         int sh = 12;
         int tx = mainX + mainW - 12 - sw;
         int ty = rowY + rowH / 2 - sh / 2;
         int bgColor = mod.isEnabled() ? -1157627904 | accent.textColor & 16777215 : 1145324612;
         RoundedRectRenderer.draw(context, tx, ty, sw, sh, sh / 2, bgColor);
         int knobD = sh - 2;
         int knobX = mod.isEnabled() ? tx + sw - knobD - 1 : tx + 1;
         RoundedRectRenderer.draw(context, knobX, ty + 1, knobD, knobD, knobD / 2, -1);
         if (mod.hasSettings()) {
            String arrow = mod.isSettingsExpanded() ? "v" : ">";
            int arrowColor = mod.isSettingsExpanded() ? accent.textColor : -8947849;
            this.drawStyledText(context, arrow, tx - 14, nameY, arrowColor);
         }

         rowY += rowH;
         if (mod.isSettingsExpanded() && mod.hasSettings()) {
            for (ModuleSetting setting : mod.getSettings()) {
               this.renderDogenSetting(context, setting, mainX, rowY, mainW, mouseX, mouseY, accent, alpha);
               rowY += 16;
            }

            rowY += 4;
         }
      }

      context.disableScissor();
   }

   private void renderDogenSetting(
      DrawContext context, ModuleSetting setting, int mainX, int y, int mainW, int mouseX, int mouseY, GuiSettings.AccentColor accent, float alpha
   ) {
      int left = mainX + 24;
      int right = mainX + mainW - 16;
      int w = right - left;
      RoundedRectRenderer.draw(context, left - 2, y, w + 4, 16, 3, (int)(28.0F * alpha) << 24);
      if (setting.getType() == ModuleSetting.Type.SLIDER) {
         this.drawStyledText(context, setting.getName(), left + 2, y + 1, -8947849);
         float sliderX = (float)(left + 2);
         float sliderW = (float)(w - 4);
         float sliderY = (float)(y + 16 - 5);
         float norm = setting.getNormalized();
         RoundedRectRenderer.draw(context, (int)sliderX, (int)sliderY, (int)sliderW, 3, 2, 872415231);
         int fillW = (int)(sliderW * norm);
         if (fillW > 0) {
            RoundedRectRenderer.draw(context, (int)sliderX, (int)sliderY, fillW, 3, 2, accent.textColor);
         }

         int knobCx = (int)(sliderX + sliderW * norm);
         RoundedRectRenderer.draw(context, knobCx - 3, (int)sliderY - 2, 6, 7, 3, -1);
         String val = setting.getDisplayValue();
         int valW = this.textRenderer.getWidth(this.styledText(val));
         this.drawStyledText(context, val, right - valW - 1, y + 1, -4473925);
      } else if (setting.getType() == ModuleSetting.Type.TOGGLE) {
         this.drawStyledText(context, setting.getName(), left + 2, y + 4, -8947849);
         int sw = 16;
         int sh = 8;
         int tx = right - sw - 2;
         int ty = y + 8 - sh / 2;
         int bgColor = setting.getBool() ? -1157627904 | accent.textColor & 16777215 : 1145324612;
         RoundedRectRenderer.draw(context, tx, ty, sw, sh, sh / 2, bgColor);
         int knobD = sh - 2;
         int knobX = setting.getBool() ? tx + sw - knobD - 1 : tx + 1;
         RoundedRectRenderer.draw(context, knobX, ty + 1, knobD, knobD, knobD / 2, -1);
      } else if (setting.getType() == ModuleSetting.Type.CHOICE) {
         this.drawStyledText(context, setting.getName(), left + 2, y + 4, -8947849);
         String val = setting.getChoiceValue();
         int valW = this.textRenderer.getWidth(this.styledText(val));
         int valX = right - valW - 4;
         RoundedRectRenderer.draw(context, valX - 3, y + 2, valW + 6, 12, 3, 587202559);
         this.drawStyledText(context, val, valX, y + 4, -3355444);
      }
   }

   private void renderColumn(DrawContext context, Category cat, int cx, int cy, int colH, int mouseX, int mouseY, GuiSettings.AccentColor accent, float alpha) {
      if (PerformanceSettings.useGlassEffect() && LiquidGlassRenderer.isReady()) {
         LiquidGlassRenderer.drawGlassPanel(context, (float)cx, (float)cy, 130.0F, (float)colH, 8.0F, 0.0F, 0.08F, accent.r, accent.g, accent.b);
      } else {
         RoundedRectRenderer.draw(context, cx, cy, 130, colH, 8, 0xCC1A0A0F);
      }

      RoundedRectRenderer.draw(context, cx, cy, 130, 26, 8, 8, 0, 0, (int)(85.0F * alpha) << 24 | accent.textColor & 16777215);
      String name = cat.getName();
      int textW = this.textRenderer.getWidth(this.styledText(name));
      int textX = cx + 65 - textW / 2;
      this.drawStyledText(context, name, textX, cy + 13 - 4, -1);
      int scroll = this.scrollOffsets.getOrDefault(cat.getName(), 0);
      int contentY = cy + 26 + 2;
      int maxContentH = this.height - contentY - 20;
      context.enableScissor(cx, contentY, cx + 130, contentY + maxContentH);
      int y = contentY - scroll;

      for (ModuleButton btn : cat.getModules()) {
         Module mod = btn.getModule();
         boolean hovered = mouseX >= cx + 2
            && mouseX <= cx + 130 - 2
            && mouseY >= y
            && mouseY < y + 18
            && mouseY >= contentY
            && mouseY < contentY + maxContentH;
         btn.updateHover(hovered);
         if (mod.isEnabled()) {
            RoundedRectRenderer.draw(context, cx + 3, y + 1, 124, 16, 4, (int)(68.0F * alpha) << 24 | accent.textColor & 16777215);
         } else if (btn.hoverAmount > 0.01F) {
            int hAlpha = (int)(btn.hoverAmount * 32.0F * alpha);
            RoundedRectRenderer.draw(context, cx + 3, y + 1, 124, 16, 4, hAlpha << 24 | 16777215);
         }

         int nameColor = mod.isEnabled() ? -1 : -5592406;
         this.drawStyledText(context, mod.getName(), cx + 6 + 2, y + 5, nameColor);
         if (mod.hasSettings()) {
            String arrow = mod.isSettingsExpanded() ? "v" : ">";
            int arrowColor = mod.isSettingsExpanded() ? accent.textColor : -11184811;
            this.drawStyledText(context, arrow, cx + 130 - 6 - 8, y + 5, arrowColor);
         }

         y += 18;
         if (mod.isSettingsExpanded() && mod.hasSettings()) {
            for (ModuleSetting setting : mod.getSettings()) {
               this.renderSetting(context, setting, cx, y, mouseX, mouseY, accent, alpha, contentY, maxContentH);
               y += 16;
            }

            y += 2;
         }
      }

      context.disableScissor();
   }

   private void renderSetting(
      DrawContext context,
      ModuleSetting setting,
      int cx,
      int y,
      int mouseX,
      int mouseY,
      GuiSettings.AccentColor accent,
      float alpha,
      int contentY,
      int maxContentH
   ) {
      int left = cx + 6 + 4;
      int right = cx + 130 - 6 - 2;
      int w = right - left;
      RoundedRectRenderer.draw(context, left - 1, y, w + 2, 16, 3, (int)(24.0F * alpha) << 24);
      if (setting.getType() == ModuleSetting.Type.SLIDER) {
         this.drawStyledText(context, setting.getName(), left + 2, y + 1, -8947849);
         float sliderX = (float)(left + 2);
         float sliderW = (float)(w - 4);
         float sliderY = (float)(y + 16 - 5);
         float norm = setting.getNormalized();
         RoundedRectRenderer.draw(context, (int)sliderX, (int)sliderY, (int)sliderW, 3, 2, 872415231);
         int fillW = (int)(sliderW * norm);
         if (fillW > 0) {
            RoundedRectRenderer.draw(context, (int)sliderX, (int)sliderY, fillW, 3, 2, accent.textColor);
         }

         int knobCx = (int)(sliderX + sliderW * norm);
         RoundedRectRenderer.draw(context, knobCx - 3, (int)sliderY - 2, 6, 7, 3, -1);
         String val = setting.getDisplayValue();
         int valW = this.textRenderer.getWidth(this.styledText(val));
         this.drawStyledText(context, val, right - valW - 1, y + 1, -4473925);
      } else if (setting.getType() == ModuleSetting.Type.TOGGLE) {
         this.drawStyledText(context, setting.getName(), left + 2, y + 4, -8947849);
         int sw = 16;
         int sh = 8;
         int tx = right - sw - 2;
         int ty = y + 8 - sh / 2;
         int bgColor = setting.getBool() ? -1157627904 | accent.textColor & 16777215 : 1145324612;
         RoundedRectRenderer.draw(context, tx, ty, sw, sh, sh / 2, bgColor);
         int knobD = sh - 2;
         int knobX = setting.getBool() ? tx + sw - knobD - 1 : tx + 1;
         RoundedRectRenderer.draw(context, knobX, ty + 1, knobD, knobD, knobD / 2, -1);
      } else if (setting.getType() == ModuleSetting.Type.CHOICE) {
         this.drawStyledText(context, setting.getName(), left + 2, y + 4, -8947849);
         String val = setting.getChoiceValue();
         int valW = this.textRenderer.getWidth(this.styledText(val));
         int valX = right - valW - 4;
         RoundedRectRenderer.draw(context, valX - 3, y + 2, valW + 6, 12, 3, 587202559);
         this.drawStyledText(context, val, valX, y + 4, -3355444);
      }
   }

   private int getColumnHeight(Category cat) {
      int h = 30;

      for (ModuleButton btn : cat.getModules()) {
         h += 18;
         Module mod = btn.getModule();
         if (mod.isSettingsExpanded() && mod.hasSettings()) {
            h += mod.getSettings().size() * 16 + 2;
         }
      }

      return h + 4;
   }

   private Text styledText(String text) {
      return com.pathdlc.digger.render.StyledTextCache.get(text, CUSTOM_FONT);
   }

   private void drawStyledText(DrawContext context, String text, int x, int y, int color) {
      context.drawText(this.textRenderer, this.styledText(text), x, y, color, true);
   }

   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      Style style = this.getStyle();
      switch (style) {
         case DOGEN:
            return this.mouseClickedDogen(mouseX, mouseY, button);
         case TABS:
            return this.mouseClickedTabs(mouseX, mouseY, button);
         case COMPACT:
            return this.mouseClickedCompact(mouseX, mouseY, button);
         case CARDS:
            return this.mouseClickedCards(mouseX, mouseY, button);
         default:
            break;
      }

      int sx = this.startX();
      int sy = this.startY();

      for (int i = 0; i < this.categories.size(); i++) {
         Category cat = this.categories.get(i);
         int cx = sx + i * 136;
         float slideOffset = (1.0F - this.openProgress) * (float)(30 + i * 12);
         int cy = sy + (int)slideOffset;
         int colH = this.getColumnHeight(cat);
         if (!(mouseX < (double)cx) && !(mouseX > (double)(cx + 130)) && !(mouseY < (double)cy) && !(mouseY > (double)(cy + colH))) {
            if (mouseY < (double)(cy + 26)) {
               return true;
            }

            int scroll = this.scrollOffsets.getOrDefault(cat.getName(), 0);
            int contentY = cy + 26 + 2;
            int y = contentY - scroll;

            for (ModuleButton btn : cat.getModules()) {
               Module mod = btn.getModule();
               if (mouseY >= (double)y && mouseY < (double)(y + 18) && mouseY >= (double)contentY) {
                  if (button == 0) {
                     if (mod.hasSettings() && mouseX >= (double)(cx + 130 - 6 - 14)) {
                        mod.toggleSettingsExpanded();
                     } else {
                        mod.toggle();
                     }
                  } else if (button == 1 && mod.hasSettings()) {
                     mod.toggleSettingsExpanded();
                  }

                  return true;
               }

               y += 18;
               if (mod.isSettingsExpanded() && mod.hasSettings()) {
                  for (ModuleSetting setting : mod.getSettings()) {
                     if (mouseY >= (double)y && mouseY < (double)(y + 16) && mouseY >= (double)contentY) {
                        this.handleSettingClick(setting, cx, y, mouseX);
                        return true;
                     }

                     y += 16;
                  }

                  y += 2;
               }
            }
         }
      }

      return super.mouseClicked(mouseX, mouseY, button);
   }

   private void handleSettingClick(ModuleSetting setting, int cx, int y, double mouseX) {
      if (setting.getType() == ModuleSetting.Type.TOGGLE) {
         setting.toggleBool();
      } else if (setting.getType() == ModuleSetting.Type.CHOICE) {
         setting.cycleChoice();
      } else if (setting.getType() == ModuleSetting.Type.SLIDER) {
         int left = cx + 6 + 4;
         int right = cx + 130 - 6 - 2;
         int w = right - left;
         float sliderX = (float)(left + 2);
         float sliderW = (float)(w - 4);
         float norm = (float)((mouseX - (double)sliderX) / (double)sliderW);
         norm = Math.max(0.0F, Math.min(1.0F, norm));
         setting.setFromNormalized(norm);
         this.draggingSlider = setting;
         this.draggingSliderX = sliderX;
         this.draggingSliderW = sliderW;
      }
   }

   public boolean mouseReleased(double mouseX, double mouseY, int button) {
      if (button == 0) {
         this.draggingSlider = null;
      }

      return super.mouseReleased(mouseX, mouseY, button);
   }

   public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
      if (button == 0 && this.draggingSlider != null) {
         float norm = (float)((mouseX - (double)this.draggingSliderX) / (double)this.draggingSliderW);
         norm = Math.max(0.0F, Math.min(1.0F, norm));
         this.draggingSlider.setFromNormalized(norm);
         return true;
      } else {
         return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
      }
   }

   private boolean mouseClickedDogen(double mouseX, double mouseY, int button) {
      int sx = this.dogenStartX();
      int sy = this.dogenStartY();
      int sidebarX = sx;
      int sidebarY = sy;
      int sidebarH = DOGEN_PANEL_H;
      int catH = Math.max(DOGEN_CAT_H, (sidebarH - 8) / Math.max(1, this.categories.size()));
      int cy = sidebarY + 4;
      if (mouseX >= (double)(sidebarX + 4) && mouseX <= (double)(sidebarX + DOGEN_SIDEBAR_W - 4)) {
         for (int i = 0; i < this.categories.size(); i++) {
            if (mouseY >= (double)cy && mouseY < (double)(cy + catH)) {
               this.dogenSelectedCategory = i;
               return true;
            }

            cy += catH;
         }
      }

      int mainX = sx + DOGEN_SIDEBAR_W + DOGEN_GAP;
      int mainY = this.dogenContentY();
      int mainW = this.dogenContentW();
      int mainH = this.dogenContentH();
      if (this.dogenSelectedCategory < 0 || this.dogenSelectedCategory >= this.categories.size()) {
         return super.mouseClicked(mouseX, mouseY, button);
      } else {
         Category selectedCat = this.categories.get(this.dogenSelectedCategory);
         int scroll = this.scrollOffsets.getOrDefault("dogen:" + selectedCat.getName(), 0);
         int rowY = mainY + 8 - scroll;
         if (!(mouseX < (double)(mainX + 6)) && !(mouseX > (double)(mainX + mainW - 6))) {
            if (mouseY >= (double)(mainY + 2) && mouseY < (double)(mainY + mainH - 2)) {
               for (ModuleButton btn : selectedCat.getModules()) {
                  Module mod = btn.getModule();
                  int rowH = DOGEN_MODULE_H;
                  if (mouseY >= (double)rowY && mouseY < (double)(rowY + rowH)) {
                     int sw = 22;
                     int tx = mainX + mainW - 12 - sw;
                     boolean clickedToggle = mouseX >= (double)tx && mouseX <= (double)(tx + sw);
                     if (button == 0) {
                        if (clickedToggle) {
                           mod.toggle();
                        } else if (mod.hasSettings()) {
                           mod.toggleSettingsExpanded();
                        } else {
                           mod.toggle();
                        }
                     } else if (button == 1 && mod.hasSettings()) {
                        mod.toggleSettingsExpanded();
                     }

                     return true;
                  }

                  rowY += rowH;
                  if (mod.isSettingsExpanded() && mod.hasSettings()) {
                     for (ModuleSetting setting : mod.getSettings()) {
                        if (mouseY >= (double)rowY && mouseY < (double)(rowY + 16)) {
                           this.handleDogenSettingClick(setting, mainX, mainW, rowY, mouseX);
                           return true;
                        }

                        rowY += 16;
                     }

                     rowY += 4;
                  }
               }

               return true;
            }

            return super.mouseClicked(mouseX, mouseY, button);
         } else {
            return super.mouseClicked(mouseX, mouseY, button);
         }
      }
   }

   private void handleDogenSettingClick(ModuleSetting setting, int mainX, int mainW, int y, double mouseX) {
      int left = mainX + 24;
      int right = mainX + mainW - 16;
      if (setting.getType() == ModuleSetting.Type.TOGGLE) {
         setting.toggleBool();
      } else if (setting.getType() == ModuleSetting.Type.CHOICE) {
         setting.cycleChoice();
      } else if (setting.getType() == ModuleSetting.Type.SLIDER) {
         int w = right - left;
         float sliderX = (float)(left + 2);
         float sliderW = (float)(w - 4);
         float norm = (float)((mouseX - (double)sliderX) / (double)sliderW);
         norm = Math.max(0.0F, Math.min(1.0F, norm));
         setting.setFromNormalized(norm);
         this.draggingSlider = setting;
         this.draggingSliderX = sliderX;
         this.draggingSliderW = sliderW;
      }
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
      Style style = this.getStyle();
      switch (style) {
         case DOGEN:
            return this.mouseScrolledDogen(mouseX, mouseY, horizontalAmount, verticalAmount);
         case TABS:
            return this.mouseScrolledTabs(mouseX, mouseY, horizontalAmount, verticalAmount);
         case COMPACT:
            return this.mouseScrolledCompact(mouseX, mouseY, horizontalAmount, verticalAmount);
         case CARDS:
            return this.mouseScrolledCards(mouseX, mouseY, horizontalAmount, verticalAmount);
         default:
            break;
      }

      int sx = this.startX();
      int sy = this.startY();

      for (int i = 0; i < this.categories.size(); i++) {
         Category cat = this.categories.get(i);
         int cx = sx + i * 136;
         float slideOffset = (1.0F - this.openProgress) * (float)(30 + i * 12);
         int cy = sy + (int)slideOffset;
         int colH = this.getColumnHeight(cat);
         if (mouseX >= (double)cx && mouseX <= (double)(cx + 130) && mouseY >= (double)cy && mouseY <= (double)(cy + colH)) {
            int scroll = this.scrollOffsets.getOrDefault(cat.getName(), 0);
            scroll -= (int)(verticalAmount * 10.0);
            int contentH = this.height - cy - 26 - 22;
            int totalH = colH - 26 - 4;
            int maxScroll = Math.max(0, totalH - contentH);
            scroll = Math.max(0, Math.min(maxScroll, scroll));
            this.scrollOffsets.put(cat.getName(), scroll);
            return true;
         }
      }

      return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
   }

   private boolean mouseScrolledDogen(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
      int sx = this.dogenStartX();
      int mainX = sx + DOGEN_SIDEBAR_W + DOGEN_GAP;
      int mainY = this.dogenContentY();
      int mainW = this.dogenContentW();
      int mainH = this.dogenContentH();
      if (mouseX >= (double)mainX
         && mouseX <= (double)(mainX + mainW)
         && mouseY >= (double)mainY
         && mouseY <= (double)(mainY + mainH)
         && this.dogenSelectedCategory >= 0
         && this.dogenSelectedCategory < this.categories.size()) {
         Category cat = this.categories.get(this.dogenSelectedCategory);
         String key = "dogen:" + cat.getName();
         int scroll = this.scrollOffsets.getOrDefault(key, 0);
         scroll -= (int)(verticalAmount * 10.0);
         int totalH = 8;

         for (ModuleButton btn : cat.getModules()) {
            totalH += DOGEN_MODULE_H;
            Module mod = btn.getModule();
            if (mod.isSettingsExpanded() && mod.hasSettings()) {
               totalH += mod.getSettings().size() * 16 + 4;
            }
         }

         int maxScroll = Math.max(0, totalH - mainH);
         scroll = Math.max(0, Math.min(maxScroll, scroll));
         this.scrollOffsets.put(key, scroll);
         return true;
      } else {
         return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
      }
   }

   private void drawPanelBg(DrawContext context, int x, int y, int w, int h, GuiSettings.AccentColor accent) {
      if (PerformanceSettings.useGlassEffect() && LiquidGlassRenderer.isReady()) {
         LiquidGlassRenderer.drawGlassPanel(context, (float)x, (float)y, (float)w, (float)h, 8.0F, 0.0F, 0.08F, accent.r, accent.g, accent.b);
      } else {
         RoundedRectRenderer.draw(context, x, y, w, h, 8, 0xCC1A0A0F);
      }
   }

   private void drawBrandRow(DrawContext context, int x, int y, int h, GuiSettings.AccentColor accent) {
      int logoSize = Math.min(20, h - 8);
      int logoY = y + h / 2 - logoSize / 2;
      context.drawTexture(
         RenderLayer::getGuiTextured,
         LOGO,
         x + 8,
         logoY,
         0.0F,
         0.0F,
         logoSize,
         logoSize,
         logoSize,
         logoSize
      );
      int textY = y + h / 2 - this.textRenderer.fontHeight / 2;
      this.drawStyledText(context, "WareVisuals", x + 8 + logoSize + 6, textY, accent.textColor);
   }

   private int brandRowWidth(int logoSize) {
      int textW = this.textRenderer.getWidth(this.styledText("WareVisuals"));
      return 8 + logoSize + 6 + textW + 8;
   }

   private int tabsStartX() {
      return (this.width - TABS_PANEL_W) / 2;
   }

   private int tabsStartY() {
      return (this.height - TABS_PANEL_H) / 2;
   }

   private int tabsContentY() {
      return this.tabsStartY() + TABS_BAR_H + TABS_GAP;
   }

   private int tabsContentH() {
      return TABS_PANEL_H - TABS_BAR_H - TABS_GAP;
   }

   private void renderTabsLayout(DrawContext context, int mouseX, int mouseY, GuiSettings.AccentColor accent) {
      if (this.tabsSelectedCategory < 0 || this.tabsSelectedCategory >= this.categories.size()) {
         this.tabsSelectedCategory = 0;
      }
      float alpha = this.openProgress;
      float slideOffset = (1.0F - alpha) * 24.0F;
      int sx = this.tabsStartX();
      int sy = this.tabsStartY() + (int)slideOffset;

      this.drawPanelBg(context, sx, sy, TABS_PANEL_W, TABS_BAR_H, accent);
      this.drawBrandRow(context, sx, sy, TABS_BAR_H, accent);
      int brandW = this.brandRowWidth(20);
      int sepX = sx + brandW;
      context.fill(sepX, sy + 8, sepX + 1, sy + TABS_BAR_H - 8, 0x40FFFFFF);

      int tabsX = sepX + 8;
      int tabsRight = sx + TABS_PANEL_W - 8;
      int availW = tabsRight - tabsX;
      int gap = 4;
      int tabH = TABS_BAR_H - 12;
      int totalTextW = 0;
      for (Category cat : this.categories) {
         totalTextW += this.textRenderer.getWidth(this.styledText(cat.getName())) + 16;
      }
      totalTextW += gap * (this.categories.size() - 1);
      int extra = Math.max(0, (availW - totalTextW) / Math.max(1, this.categories.size()));
      int tx = tabsX;
      int ty = sy + 6;
      for (int i = 0; i < this.categories.size(); i++) {
         Category cat = this.categories.get(i);
         int tw = this.textRenderer.getWidth(this.styledText(cat.getName())) + 16 + extra;
         boolean selected = i == this.tabsSelectedCategory;
         boolean hovered = mouseX >= tx && mouseX <= tx + tw && mouseY >= ty && mouseY < ty + tabH;
         if (selected) {
            RoundedRectRenderer.draw(context, tx, ty, tw, tabH, 5, (int)(110.0F * alpha) << 24 | accent.textColor & 0xFFFFFF);
         } else if (hovered) {
            int hAlpha = (int)(40.0F * alpha);
            RoundedRectRenderer.draw(context, tx, ty, tw, tabH, 5, hAlpha << 24 | 0xFFFFFF);
         }
         int textColor = selected ? -1 : -3355444;
         int labelW = this.textRenderer.getWidth(this.styledText(cat.getName()));
         this.drawStyledText(context, cat.getName(), tx + tw / 2 - labelW / 2, ty + tabH / 2 - this.textRenderer.fontHeight / 2, textColor);
         tx += tw + gap;
      }

      int contentX = sx;
      int contentY = sy + TABS_BAR_H + TABS_GAP;
      int contentW = TABS_PANEL_W;
      int contentH = TABS_PANEL_H - TABS_BAR_H - TABS_GAP;
      this.drawPanelBg(context, contentX, contentY, contentW, contentH, accent);

      Category selectedCat = this.categories.get(this.tabsSelectedCategory);
      String key = "tabs:" + selectedCat.getName();
      int scroll = this.scrollOffsets.getOrDefault(key, 0);
      context.enableScissor(contentX + 2, contentY + 2, contentX + contentW - 2, contentY + contentH - 2);
      int rowY = contentY + 8 - scroll;
      for (ModuleButton btn : selectedCat.getModules()) {
         Module mod = btn.getModule();
         int rowH = TABS_MODULE_H;
         boolean hovered = mouseX >= contentX + 8 && mouseX <= contentX + contentW - 8
            && mouseY >= rowY && mouseY < rowY + rowH
            && mouseY >= contentY + 2 && mouseY < contentY + contentH - 2;
         btn.updateHover(hovered);
         if (mod.isEnabled()) {
            RoundedRectRenderer.draw(context, contentX + 8, rowY, contentW - 16, rowH, 5, (int)(78.0F * alpha) << 24 | accent.textColor & 0xFFFFFF);
         } else if (btn.hoverAmount > 0.01F) {
            int hAlpha = (int)(btn.hoverAmount * 36.0F * alpha);
            RoundedRectRenderer.draw(context, contentX + 8, rowY, contentW - 16, rowH, 5, hAlpha << 24 | 0xFFFFFF);
         }
         int nameColor = mod.isEnabled() ? -1 : -3355444;
         this.drawStyledText(context, mod.getName(), contentX + 16, rowY + rowH / 2 - this.textRenderer.fontHeight / 2, nameColor);
         int sw = 22;
         int sh = 12;
         int toggleX = contentX + contentW - 16 - sw;
         int toggleY = rowY + rowH / 2 - sh / 2;
         int bgColor = mod.isEnabled() ? 0xC0000000 | (accent.textColor & 0xFFFFFF) : 0x80444444;
         RoundedRectRenderer.draw(context, toggleX, toggleY, sw, sh, sh / 2, bgColor);
         int knobD = sh - 2;
         int knobX = mod.isEnabled() ? toggleX + sw - knobD - 1 : toggleX + 1;
         RoundedRectRenderer.draw(context, knobX, toggleY + 1, knobD, knobD, knobD / 2, -1);
         if (mod.hasSettings()) {
            String arrow = mod.isSettingsExpanded() ? "v" : ">";
            this.drawStyledText(context, arrow, toggleX - 14, rowY + rowH / 2 - this.textRenderer.fontHeight / 2, mod.isSettingsExpanded() ? accent.textColor : -8947849);
         }
         rowY += rowH;
         if (mod.isSettingsExpanded() && mod.hasSettings()) {
            for (ModuleSetting setting : mod.getSettings()) {
               this.renderDogenSetting(context, setting, contentX, rowY, contentW, mouseX, mouseY, accent, alpha);
               rowY += 16;
            }
            rowY += 4;
         }
      }
      context.disableScissor();
   }

   private boolean mouseClickedTabs(double mouseX, double mouseY, int button) {
      int sx = this.tabsStartX();
      int sy = this.tabsStartY();
      int brandW = this.brandRowWidth(20);
      int tabsX = sx + brandW + 8;
      int tabsRight = sx + TABS_PANEL_W - 8;
      int availW = tabsRight - tabsX;
      int gap = 4;
      int tabH = TABS_BAR_H - 12;
      int totalTextW = 0;
      for (Category cat : this.categories) {
         totalTextW += this.textRenderer.getWidth(this.styledText(cat.getName())) + 16;
      }
      totalTextW += gap * (this.categories.size() - 1);
      int extra = Math.max(0, (availW - totalTextW) / Math.max(1, this.categories.size()));
      int tx = tabsX;
      int ty = sy + 6;
      for (int i = 0; i < this.categories.size(); i++) {
         Category cat = this.categories.get(i);
         int tw = this.textRenderer.getWidth(this.styledText(cat.getName())) + 16 + extra;
         if (mouseX >= tx && mouseX <= tx + tw && mouseY >= ty && mouseY < ty + tabH) {
            this.tabsSelectedCategory = i;
            return true;
         }
         tx += tw + gap;
      }
      if (this.tabsSelectedCategory < 0 || this.tabsSelectedCategory >= this.categories.size()) {
         return super.mouseClicked(mouseX, mouseY, button);
      }
      Category selectedCat = this.categories.get(this.tabsSelectedCategory);
      int contentX = sx;
      int contentY = sy + TABS_BAR_H + TABS_GAP;
      int contentW = TABS_PANEL_W;
      int contentH = TABS_PANEL_H - TABS_BAR_H - TABS_GAP;
      if (mouseX < contentX + 8 || mouseX > contentX + contentW - 8 || mouseY < contentY + 2 || mouseY > contentY + contentH - 2) {
         return super.mouseClicked(mouseX, mouseY, button);
      }
      String key = "tabs:" + selectedCat.getName();
      int scroll = this.scrollOffsets.getOrDefault(key, 0);
      int rowY = contentY + 8 - scroll;
      for (ModuleButton btn : selectedCat.getModules()) {
         Module mod = btn.getModule();
         int rowH = TABS_MODULE_H;
         if (mouseY >= rowY && mouseY < rowY + rowH) {
            int sw = 22;
            int toggleX = contentX + contentW - 16 - sw;
            boolean clickedToggle = mouseX >= toggleX && mouseX <= toggleX + sw;
            if (button == 0) {
               if (clickedToggle) {
                  mod.toggle();
               } else if (mod.hasSettings()) {
                  mod.toggleSettingsExpanded();
               } else {
                  mod.toggle();
               }
            } else if (button == 1 && mod.hasSettings()) {
               mod.toggleSettingsExpanded();
            }
            return true;
         }
         rowY += rowH;
         if (mod.isSettingsExpanded() && mod.hasSettings()) {
            for (ModuleSetting setting : mod.getSettings()) {
               if (mouseY >= rowY && mouseY < rowY + 16) {
                  this.handleDogenSettingClick(setting, contentX, contentW, rowY, mouseX);
                  return true;
               }
               rowY += 16;
            }
            rowY += 4;
         }
      }
      return true;
   }

   private boolean mouseScrolledTabs(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
      int sx = this.tabsStartX();
      int sy = this.tabsStartY();
      int contentX = sx;
      int contentY = sy + TABS_BAR_H + TABS_GAP;
      int contentW = TABS_PANEL_W;
      int contentH = TABS_PANEL_H - TABS_BAR_H - TABS_GAP;
      if (mouseX < contentX || mouseX > contentX + contentW || mouseY < contentY || mouseY > contentY + contentH
         || this.tabsSelectedCategory < 0 || this.tabsSelectedCategory >= this.categories.size()) {
         return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
      }
      Category cat = this.categories.get(this.tabsSelectedCategory);
      String key = "tabs:" + cat.getName();
      int scroll = this.scrollOffsets.getOrDefault(key, 0);
      scroll -= (int)(verticalAmount * 10.0);
      int totalH = 8;
      for (ModuleButton btn : cat.getModules()) {
         totalH += TABS_MODULE_H;
         Module mod = btn.getModule();
         if (mod.isSettingsExpanded() && mod.hasSettings()) {
            totalH += mod.getSettings().size() * 16 + 4;
         }
      }
      int maxScroll = Math.max(0, totalH - contentH);
      scroll = Math.max(0, Math.min(maxScroll, scroll));
      this.scrollOffsets.put(key, scroll);
      return true;
   }

   private int compactStartX() {
      return 16;
   }

   private int compactStartY() {
      return 20;
   }

   private int compactPanelH() {
      return Math.min(this.height - 40, 24 + COMPACT_HEADER_H + this.compactTotalContentH() + 8);
   }

   private int compactTotalContentH() {
      int total = 0;
      for (Category cat : this.categories) {
         total += COMPACT_CAT_H;
         if (Boolean.TRUE.equals(this.compactExpanded.get(cat.getName()))) {
            for (ModuleButton btn : cat.getModules()) {
               total += COMPACT_MODULE_H;
               Module mod = btn.getModule();
               if (mod.isSettingsExpanded() && mod.hasSettings()) {
                  total += mod.getSettings().size() * 16 + 4;
               }
            }
         }
      }
      return total;
   }

   private void renderCompactLayout(DrawContext context, int mouseX, int mouseY, GuiSettings.AccentColor accent) {
      float alpha = this.openProgress;
      int slide = (int)((1.0F - alpha) * -30.0F);
      int sx = this.compactStartX() + slide;
      int sy = this.compactStartY();
      int w = COMPACT_PANEL_W;
      int h = this.compactPanelH();
      this.drawPanelBg(context, sx, sy, w, h, accent);
      this.drawBrandRow(context, sx, sy, COMPACT_HEADER_H, accent);
      RoundedRectRenderer.draw(context, sx + 6, sy + COMPACT_HEADER_H, w - 12, 1, 0, 0x30FFFFFF);

      int contentTop = sy + COMPACT_HEADER_H + 4;
      int contentBottom = sy + h - 4;
      int viewportH = contentBottom - contentTop;
      int totalH = this.compactTotalContentH();
      int maxScroll = Math.max(0, totalH - viewportH);
      int scroll = Math.max(0, Math.min(maxScroll, this.scrollOffsets.getOrDefault("compact:scroll", 0)));
      this.scrollOffsets.put("compact:scroll", scroll);
      context.enableScissor(sx + 2, contentTop, sx + w - 2, contentBottom);
      int y = contentTop - scroll;
      for (int i = 0; i < this.categories.size(); i++) {
         Category cat = this.categories.get(i);
         boolean expanded = Boolean.TRUE.equals(this.compactExpanded.get(cat.getName()));
         boolean hovered = mouseX >= sx + 6 && mouseX <= sx + w - 6 && mouseY >= y && mouseY < y + COMPACT_CAT_H;
         if (hovered) {
            int hAlpha = (int)(36.0F * alpha);
            RoundedRectRenderer.draw(context, sx + 6, y, w - 12, COMPACT_CAT_H, 4, hAlpha << 24 | 0xFFFFFF);
         }
         int enabledCount = 0;
         for (ModuleButton btn : cat.getModules()) {
            if (btn.getModule().isEnabled()) {
               enabledCount++;
            }
         }
         this.drawStyledText(context, cat.getName(), sx + 12, y + COMPACT_CAT_H / 2 - this.textRenderer.fontHeight / 2, expanded ? accent.textColor : -1);
         String count = enabledCount + "/" + cat.getModules().size();
         int countW = this.textRenderer.getWidth(this.styledText(count));
         this.drawStyledText(context, count, sx + w - 18 - countW, y + COMPACT_CAT_H / 2 - this.textRenderer.fontHeight / 2, -7829368);
         String arrow = expanded ? "v" : ">";
         this.drawStyledText(context, arrow, sx + w - 14, y + COMPACT_CAT_H / 2 - this.textRenderer.fontHeight / 2, accent.textColor);
         y += COMPACT_CAT_H;
         if (expanded) {
            for (ModuleButton btn : cat.getModules()) {
               Module mod = btn.getModule();
               int rowH = COMPACT_MODULE_H;
               boolean rowHover = mouseX >= sx + 14 && mouseX <= sx + w - 6 && mouseY >= y && mouseY < y + rowH;
               btn.updateHover(rowHover);
               if (mod.isEnabled()) {
                  RoundedRectRenderer.draw(context, sx + 14, y, w - 20, rowH, 4, (int)(70.0F * alpha) << 24 | accent.textColor & 0xFFFFFF);
               } else if (btn.hoverAmount > 0.01F) {
                  int hAlpha = (int)(btn.hoverAmount * 30.0F * alpha);
                  RoundedRectRenderer.draw(context, sx + 14, y, w - 20, rowH, 4, hAlpha << 24 | 0xFFFFFF);
               }
               int nameColor = mod.isEnabled() ? -1 : -5592406;
               this.drawStyledText(context, mod.getName(), sx + 18, y + rowH / 2 - this.textRenderer.fontHeight / 2, nameColor);
               if (mod.hasSettings()) {
                  String marrow = mod.isSettingsExpanded() ? "v" : ">";
                  this.drawStyledText(context, marrow, sx + w - 16, y + rowH / 2 - this.textRenderer.fontHeight / 2, mod.isSettingsExpanded() ? accent.textColor : -8947849);
               }
               y += rowH;
               if (mod.isSettingsExpanded() && mod.hasSettings()) {
                  for (ModuleSetting setting : mod.getSettings()) {
                     this.renderDogenSetting(context, setting, sx, y, w, mouseX, mouseY, accent, alpha);
                     y += 16;
                  }
                  y += 4;
               }
            }
         }
      }
      context.disableScissor();
   }

   private boolean mouseClickedCompact(double mouseX, double mouseY, int button) {
      int sx = this.compactStartX();
      int sy = this.compactStartY();
      int w = COMPACT_PANEL_W;
      int h = this.compactPanelH();
      int contentTop = sy + COMPACT_HEADER_H + 4;
      int contentBottom = sy + h - 4;
      if (mouseY < contentTop || mouseY > contentBottom || mouseX < sx + 6 || mouseX > sx + w - 6) {
         return super.mouseClicked(mouseX, mouseY, button);
      }
      int scroll = this.scrollOffsets.getOrDefault("compact:scroll", 0);
      int y = contentTop - scroll;
      for (int i = 0; i < this.categories.size(); i++) {
         Category cat = this.categories.get(i);
         boolean expanded = Boolean.TRUE.equals(this.compactExpanded.get(cat.getName()));
         if (mouseY >= y && mouseY < y + COMPACT_CAT_H) {
            this.compactExpanded.put(cat.getName(), !expanded);
            return true;
         }
         y += COMPACT_CAT_H;
         if (expanded) {
            for (ModuleButton btn : cat.getModules()) {
               Module mod = btn.getModule();
               int rowH = COMPACT_MODULE_H;
               if (mouseY >= y && mouseY < y + rowH) {
                  if (button == 0) {
                     if (mod.hasSettings() && mouseX >= sx + w - 22) {
                        mod.toggleSettingsExpanded();
                     } else {
                        mod.toggle();
                     }
                  } else if (button == 1 && mod.hasSettings()) {
                     mod.toggleSettingsExpanded();
                  }
                  return true;
               }
               y += rowH;
               if (mod.isSettingsExpanded() && mod.hasSettings()) {
                  for (ModuleSetting setting : mod.getSettings()) {
                     if (mouseY >= y && mouseY < y + 16) {
                        this.handleDogenSettingClick(setting, sx, w, y, mouseX);
                        return true;
                     }
                     y += 16;
                  }
                  y += 4;
               }
            }
         }
      }
      return super.mouseClicked(mouseX, mouseY, button);
   }

   private boolean mouseScrolledCompact(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
      int sx = this.compactStartX();
      int sy = this.compactStartY();
      int w = COMPACT_PANEL_W;
      int h = this.compactPanelH();
      if (mouseX < sx || mouseX > sx + w || mouseY < sy || mouseY > sy + h) {
         return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
      }
      int viewportH = h - COMPACT_HEADER_H - 8;
      int totalH = this.compactTotalContentH();
      int maxScroll = Math.max(0, totalH - viewportH);
      int scroll = this.scrollOffsets.getOrDefault("compact:scroll", 0);
      scroll -= (int)(verticalAmount * 12.0);
      scroll = Math.max(0, Math.min(maxScroll, scroll));
      this.scrollOffsets.put("compact:scroll", scroll);
      return true;
   }

   private int cardsRows() {
      return (this.categories.size() + CARDS_COLS - 1) / CARDS_COLS;
   }

   private int cardsGridW() {
      return CARDS_COLS * CARDS_W + (CARDS_COLS - 1) * CARDS_GAP;
   }

   private int cardsGridH() {
      return this.cardsRows() * CARDS_H + (this.cardsRows() - 1) * CARDS_GAP;
   }

   private int cardsGridX() {
      return (this.width - this.cardsGridW()) / 2;
   }

   private int cardsGridY() {
      return Math.max(80, (this.height - this.cardsGridH()) / 2);
   }

   private void renderCardsLayout(DrawContext context, int mouseX, int mouseY, GuiSettings.AccentColor accent) {
      float alpha = this.openProgress;
      int brandW = this.brandRowWidth(22);
      int brandH = 32;
      int brandX = (this.width - brandW) / 2;
      int brandY = this.cardsGridY() - brandH - 12 + (int)((1.0F - alpha) * -10.0F);
      this.drawPanelBg(context, brandX, brandY, brandW, brandH, accent);
      this.drawBrandRow(context, brandX, brandY, brandH, accent);

      int gx = this.cardsGridX();
      int gy = this.cardsGridY();
      for (int i = 0; i < this.categories.size(); i++) {
         Category cat = this.categories.get(i);
         int row = i / CARDS_COLS;
         int col = i % CARDS_COLS;
         int cardSlide = (int)((1.0F - alpha) * (float)(20 + (row + col) * 8));
         int x = gx + col * (CARDS_W + CARDS_GAP);
         int y = gy + row * (CARDS_H + CARDS_GAP) + cardSlide;
         boolean hovered = mouseX >= x && mouseX <= x + CARDS_W && mouseY >= y && mouseY < y + CARDS_H;
         this.drawPanelBg(context, x, y, CARDS_W, CARDS_H, accent);
         if (hovered) {
            RoundedRectRenderer.draw(context, x, y, CARDS_W, CARDS_H, 8, 0x18FFFFFF);
         }
         int enabledCount = 0;
         for (ModuleButton btn : cat.getModules()) {
            if (btn.getModule().isEnabled()) {
               enabledCount++;
            }
         }
         int nameW = this.textRenderer.getWidth(this.styledText(cat.getName()));
         this.drawStyledText(context, cat.getName(), x + CARDS_W / 2 - nameW / 2, y + 18, -1);
         String sub = enabledCount + " / " + cat.getModules().size() + " enabled";
         int subW = this.textRenderer.getWidth(this.styledText(sub));
         this.drawStyledText(context, sub, x + CARDS_W / 2 - subW / 2, y + CARDS_H - 18, accent.textColor);

      }

      if (this.cardsOpenCategory >= 0 && this.cardsOpenCategory < this.categories.size()) {
         context.fill(0, 0, this.width, this.height, 0xA0000000);
         int ow = CARDS_OVERLAY_W;
         int oh = CARDS_OVERLAY_H;
         int ox = (this.width - ow) / 2;
         int oy = (this.height - oh) / 2;
         this.drawPanelBg(context, ox, oy, ow, oh, accent);
         Category openCat = this.categories.get(this.cardsOpenCategory);
         this.drawBrandRow(context, ox, oy, 30, accent);
         int brandRowW = this.brandRowWidth(20);
         int sepX = ox + brandRowW;
         context.fill(sepX, oy + 8, sepX + 1, oy + 30 - 8, 0x40FFFFFF);
         this.drawStyledText(context, openCat.getName(), sepX + 8, oy + 30 / 2 - this.textRenderer.fontHeight / 2, -1);
         String closeStr = "x";
         int closeW = this.textRenderer.getWidth(this.styledText(closeStr));
         this.drawStyledText(context, closeStr, ox + ow - 12 - closeW, oy + 30 / 2 - this.textRenderer.fontHeight / 2, accent.textColor);

         int listTop = oy + 30 + 4;
         int listBottom = oy + oh - 6;
         String key = "cards:" + openCat.getName();
         int scroll = this.scrollOffsets.getOrDefault(key, 0);
         context.enableScissor(ox + 4, listTop, ox + ow - 4, listBottom);
         int rowY = listTop + 2 - scroll;
         for (ModuleButton btn : openCat.getModules()) {
            Module mod = btn.getModule();
            int rowH = 22;
            boolean rowHover = mouseX >= ox + 8 && mouseX <= ox + ow - 8 && mouseY >= rowY && mouseY < rowY + rowH
               && mouseY >= listTop && mouseY < listBottom;
            btn.updateHover(rowHover);
            if (mod.isEnabled()) {
               RoundedRectRenderer.draw(context, ox + 8, rowY, ow - 16, rowH, 5, (int)(76.0F * alpha) << 24 | accent.textColor & 0xFFFFFF);
            } else if (btn.hoverAmount > 0.01F) {
               int hAlpha = (int)(btn.hoverAmount * 32.0F * alpha);
               RoundedRectRenderer.draw(context, ox + 8, rowY, ow - 16, rowH, 5, hAlpha << 24 | 0xFFFFFF);
            }
            int nameColor = mod.isEnabled() ? -1 : -3355444;
            this.drawStyledText(context, mod.getName(), ox + 16, rowY + rowH / 2 - this.textRenderer.fontHeight / 2, nameColor);
            int sw = 22;
            int sh = 12;
            int toggleX = ox + ow - 16 - sw;
            int toggleY = rowY + rowH / 2 - sh / 2;
            int bgColor = mod.isEnabled() ? 0xC0000000 | (accent.textColor & 0xFFFFFF) : 0x80444444;
            RoundedRectRenderer.draw(context, toggleX, toggleY, sw, sh, sh / 2, bgColor);
            int knobD = sh - 2;
            int knobX = mod.isEnabled() ? toggleX + sw - knobD - 1 : toggleX + 1;
            RoundedRectRenderer.draw(context, knobX, toggleY + 1, knobD, knobD, knobD / 2, -1);
            if (mod.hasSettings()) {
               String marrow = mod.isSettingsExpanded() ? "v" : ">";
               this.drawStyledText(context, marrow, toggleX - 14, rowY + rowH / 2 - this.textRenderer.fontHeight / 2, mod.isSettingsExpanded() ? accent.textColor : -8947849);
            }
            rowY += rowH;
            if (mod.isSettingsExpanded() && mod.hasSettings()) {
               for (ModuleSetting setting : mod.getSettings()) {
                  this.renderDogenSetting(context, setting, ox, rowY, ow, mouseX, mouseY, accent, alpha);
                  rowY += 16;
               }
               rowY += 4;
            }
         }
         context.disableScissor();
      }
   }

   private boolean mouseClickedCards(double mouseX, double mouseY, int button) {
      if (this.cardsOpenCategory >= 0 && this.cardsOpenCategory < this.categories.size()) {
         int ow = CARDS_OVERLAY_W;
         int oh = CARDS_OVERLAY_H;
         int ox = (this.width - ow) / 2;
         int oy = (this.height - oh) / 2;
         if (mouseX < ox || mouseX > ox + ow || mouseY < oy || mouseY > oy + oh) {
            this.cardsOpenCategory = -1;
            return true;
         }
         int closeW = this.textRenderer.getWidth(this.styledText("x"));
         if (mouseX >= ox + ow - 14 - closeW && mouseX <= ox + ow - 4 && mouseY >= oy + 4 && mouseY < oy + 30 - 4) {
            this.cardsOpenCategory = -1;
            return true;
         }
         Category openCat = this.categories.get(this.cardsOpenCategory);
         int listTop = oy + 30 + 4;
         int listBottom = oy + oh - 6;
         if (mouseY < listTop || mouseY > listBottom) {
            return true;
         }
         String key = "cards:" + openCat.getName();
         int scroll = this.scrollOffsets.getOrDefault(key, 0);
         int rowY = listTop + 2 - scroll;
         for (ModuleButton btn : openCat.getModules()) {
            Module mod = btn.getModule();
            int rowH = 22;
            if (mouseY >= rowY && mouseY < rowY + rowH) {
               int sw = 22;
               int toggleX = ox + ow - 16 - sw;
               boolean clickedToggle = mouseX >= toggleX && mouseX <= toggleX + sw;
               if (button == 0) {
                  if (clickedToggle) {
                     mod.toggle();
                  } else if (mod.hasSettings()) {
                     mod.toggleSettingsExpanded();
                  } else {
                     mod.toggle();
                  }
               } else if (button == 1 && mod.hasSettings()) {
                  mod.toggleSettingsExpanded();
               }
               return true;
            }
            rowY += rowH;
            if (mod.isSettingsExpanded() && mod.hasSettings()) {
               for (ModuleSetting setting : mod.getSettings()) {
                  if (mouseY >= rowY && mouseY < rowY + 16) {
                     this.handleDogenSettingClick(setting, ox, ow, rowY, mouseX);
                     return true;
                  }
                  rowY += 16;
               }
               rowY += 4;
            }
         }
         return true;
      }
      int gx = this.cardsGridX();
      int gy = this.cardsGridY();
      for (int i = 0; i < this.categories.size(); i++) {
         int row = i / CARDS_COLS;
         int col = i % CARDS_COLS;
         int x = gx + col * (CARDS_W + CARDS_GAP);
         int y = gy + row * (CARDS_H + CARDS_GAP);
         if (mouseX >= x && mouseX <= x + CARDS_W && mouseY >= y && mouseY < y + CARDS_H) {
            this.cardsOpenCategory = i;
            return true;
         }
      }
      return super.mouseClicked(mouseX, mouseY, button);
   }

   private boolean mouseScrolledCards(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
      if (this.cardsOpenCategory < 0 || this.cardsOpenCategory >= this.categories.size()) {
         return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
      }
      int ow = CARDS_OVERLAY_W;
      int oh = CARDS_OVERLAY_H;
      int ox = (this.width - ow) / 2;
      int oy = (this.height - oh) / 2;
      if (mouseX < ox || mouseX > ox + ow || mouseY < oy || mouseY > oy + oh) {
         return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
      }
      Category cat = this.categories.get(this.cardsOpenCategory);
      String key = "cards:" + cat.getName();
      int scroll = this.scrollOffsets.getOrDefault(key, 0);
      scroll -= (int)(verticalAmount * 10.0);
      int totalH = 4;
      for (ModuleButton btn : cat.getModules()) {
         totalH += 22;
         Module mod = btn.getModule();
         if (mod.isSettingsExpanded() && mod.hasSettings()) {
            totalH += mod.getSettings().size() * 16 + 4;
         }
      }
      int viewH = oh - 30 - 10;
      int maxScroll = Math.max(0, totalH - viewH);
      scroll = Math.max(0, Math.min(maxScroll, scroll));
      this.scrollOffsets.put(key, scroll);
      return true;
   }

   public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
      if (this.getStyle() == Style.CARDS && this.cardsOpenCategory >= 0 && keyCode == 256) {
         this.cardsOpenCategory = -1;
         return true;
      }
      return super.keyPressed(keyCode, scanCode, modifiers);
   }

   public boolean shouldPause() {
      return false;
   }
}
