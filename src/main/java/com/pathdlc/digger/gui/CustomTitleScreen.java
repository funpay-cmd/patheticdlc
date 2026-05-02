package com.pathdlc.digger.gui;

import com.pathdlc.digger.render.LiquidGlassRenderer;
import com.pathdlc.digger.render.PerformanceSettings;
import com.pathdlc.digger.render.RoundedRectRenderer;
import com.pathdlc.digger.gui.GuiSettings;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;

public class CustomTitleScreen extends Screen {
   private static final Identifier BACKGROUND = Identifier.of("pathdlc_digger", "textures/gui/background.png");
   private static final Identifier BACKGROUND_FEVER = Identifier.of("pathdlc_digger", "fever_assets/image/mainmenu/background.png");
   private static final int BTN_WIDTH = 220;
   private static final int BTN_HEIGHT = 28;
   private static final int BTN_GAP = 6;
   private static final int BTN_RADIUS = 14;
   private static final String[] LABELS = new String[]{"Singleplayer", "Multiplayer", "Settings", "Quit Game"};
   private float openProgress;
   private final float[] btnHover = new float[4];
   private final float[] btnSlide = new float[4];
   private long startTime;
   private static final int PARTICLE_COUNT = 40;
   private final float[] px = new float[40];
   private final float[] py = new float[40];
   private final float[] pvx = new float[40];
   private final float[] pvy = new float[40];
   private final float[] psz = new float[40];
   private final float[] pa = new float[40];

   public CustomTitleScreen() {
      super(Text.literal("WareVisuals"));
   }

   protected void init() {
      super.init();
      this.openProgress = 0.0F;
      this.startTime = System.currentTimeMillis();

      for (int i = 0; i < 4; i++) {
         this.btnSlide[i] = 0.0F;
         this.btnHover[i] = 0.0F;
      }

      this.initParticles();
   }

   private void initParticles() {
      for (int i = 0; i < 40; i++) {
         this.resetParticle(i, true);
      }
   }

   private void resetParticle(int i, boolean randomY) {
      this.px[i] = (float)(Math.random() * 2000.0);
      this.py[i] = randomY ? (float)(Math.random() * 1200.0) : -10.0F - (float)(Math.random() * 50.0);
      this.pvx[i] = (float)(Math.random() * 0.25 - 0.125);
      this.pvy[i] = (float)(Math.random() * 0.3 + 0.08);
      this.psz[i] = (float)(Math.random() * 2.5 + 1.0);
      this.pa[i] = (float)(Math.random() * 0.25 + 0.05);
   }

   public void render(DrawContext context, int mouseX, int mouseY, float delta) {
      LiquidGlassRenderer.captureAndBlur();
      this.openProgress = this.openProgress + (1.0F - this.openProgress) * 0.06F;
      if (this.openProgress > 0.99F) {
         this.openProgress = 1.0F;
      }

      long elapsed = System.currentTimeMillis() - this.startTime;
      this.renderBackground(context);
      this.updateParticles();
      this.renderParticles(context);
      this.renderTitle(context, elapsed);
      this.renderButtons(context, mouseX, mouseY, elapsed);
      this.renderFooter(context);
   }

   private void renderBackground(DrawContext context) {
      context.fill(0, 0, this.width, this.height, -16382448);
      Identifier bg = pickBackground();
      context.drawTexture(RenderLayer::getGuiTextured, bg, 0, 0, 0.0F, 0.0F, this.width, this.height, this.width, this.height);
      context.fill(0, 0, this.width, this.height, -1442445808);
   }

   private static Identifier pickBackground() {
      com.pathdlc.digger.gui.Module m = com.pathdlc.digger.gui.ModuleManager.get("MenuStyle");
      if (m == null) {
         return BACKGROUND;
      }
      com.pathdlc.digger.gui.ModuleSetting s = m.getSetting("Background");
      if (s == null || s.getChoiceValue() == null) {
         return BACKGROUND;
      }
      return "Fever".equalsIgnoreCase(s.getChoiceValue()) ? BACKGROUND_FEVER : BACKGROUND;
   }

   private void updateParticles() {
      for (int i = 0; i < 40; i++) {
         this.px[i] = this.px[i] + this.pvx[i];
         this.py[i] = this.py[i] + this.pvy[i];
         if (this.py[i] > (float)(this.height + 20) || this.px[i] < -20.0F || this.px[i] > (float)(this.width + 20)) {
            this.resetParticle(i, false);
            this.px[i] = (float)(Math.random() * (double)this.width);
         }
      }
   }

   private void renderParticles(DrawContext context) {
      int maxParticles = Math.min(40, PerformanceSettings.getParticleCount());
      int rgb = GuiSettings.getAccentColor().textColor & 0xFFFFFF;

      for (int i = 0; i < maxParticles; i++) {
         int x = (int)this.px[i];
         int y = (int)this.py[i];
         int s = Math.max(1, (int)this.psz[i]);
         int a = (int)(this.pa[i] * 255.0F * this.openProgress);
         if (a >= 1) {
            RoundedRectRenderer.draw(context, x, y, s * 2, s * 2, s, a << 24 | rgb);
         }
      }
   }

   private void renderTitle(DrawContext context, long elapsed) {
      float titleProgress = Math.min(1.0F, (float)elapsed / 600.0F) * this.openProgress;
      if (!(titleProgress < 0.01F)) {
         String title = "WareVisuals";
         int titleW = this.textRenderer.getWidth(title) * 3;
         int titleX = this.width / 2 - titleW / 6;
         int titleY = this.height / 2 - LABELS.length * 34 / 2 - 50;
         int glassW = titleW / 3 + 40;
         int glassH = 30;
         int glassX = this.width / 2 - glassW / 2;
         int glassY = titleY - 6;
         GuiSettings.AccentColor accent = GuiSettings.getAccentColor();
         if (PerformanceSettings.useGlassEffect() && LiquidGlassRenderer.isReady()) {
            LiquidGlassRenderer.drawGlassPanel(context, (float)glassX, (float)glassY, (float)glassW, (float)glassH, 15.0F, 0.0F, 0.12F, accent.r, accent.g, accent.b);
         } else {
            RoundedRectRenderer.draw(context, glassX, glassY, glassW, glassH, 15, 0x88180A0F);
         }

         int titleA = (int)(titleProgress * 255.0F);
         int titleColor = titleA << 24 | 16777215;
         context.getMatrices().push();
         context.getMatrices().scale(3.0F, 3.0F, 1.0F);
         context.drawText(this.textRenderer, Text.literal(title), titleX / 3, titleY / 3, titleColor, true);
         context.getMatrices().pop();
         int subtitleA = (int)(titleProgress * 120.0F);
         String subtitle = "v1.21.4 · Fabric";
         int subW = this.textRenderer.getWidth(subtitle);
         context.drawText(
            this.textRenderer, Text.literal(subtitle), this.width / 2 - subW / 2, titleY + 22, subtitleA << 24 | 8952268, false
         );
      }
   }

   private void renderButtons(DrawContext context, int mouseX, int mouseY, long elapsed) {
      int startY = this.height / 2 - LABELS.length * 34 / 2 + 10;
      int cx = this.width / 2 - 110;

      for (int i = 0; i < LABELS.length; i++) {
         float delay = (float)i * 100.0F;
         float slideTarget = (float)elapsed > delay ? 1.0F : 0.0F;
         this.btnSlide[i] = this.btnSlide[i] + (slideTarget - this.btnSlide[i]) * 0.1F;
         float slide = this.btnSlide[i];
         if (!(slide < 0.01F)) {
            int btnY = startY + i * 34;
            int offsetX = (int)((1.0F - slide) * 80.0F);
            float alphaF = slide * this.openProgress;
            boolean hovered = slide > 0.5F && mouseX >= cx && mouseX <= cx + 220 && mouseY >= btnY && mouseY <= btnY + 28;
            float hTarget = hovered ? 1.0F : 0.0F;
            this.btnHover[i] = this.btnHover[i] + (hTarget - this.btnHover[i]) * 0.15F;
            int drawX = cx + offsetX;
            GuiSettings.AccentColor accent = GuiSettings.getAccentColor();
            int accentRgb = accent.textColor & 0xFFFFFF;
            if (PerformanceSettings.useGlassEffect() && LiquidGlassRenderer.isReady()) {
               LiquidGlassRenderer.drawGlassPanel(
                  context, (float)drawX, (float)btnY, 220.0F, 28.0F, 14.0F, this.btnHover[i], 0.05F + this.btnHover[i] * 0.1F, accent.r, accent.g, accent.b
               );
            } else {
               int bgA = (int)(alphaF * (160.0F + 40.0F * this.btnHover[i]));
               RoundedRectRenderer.draw(context, drawX, btnY, 220, 28, 14, bgA << 24 | 0x180A0F);
            }

            if (this.btnHover[i] > 0.01F) {
               int glowA = (int)(this.btnHover[i] * 25.0F * alphaF);
               RoundedRectRenderer.draw(context, drawX + 2, btnY + 2, 216, 24, 12, glowA << 24 | accentRgb);
            }

            int textA = (int)(alphaF * 255.0F);
            int textColor = hovered ? textA << 24 | 16777215 : textA << 24 | 13426158;
            int labelW = this.textRenderer.getWidth(LABELS[i]);
            context.drawText(this.textRenderer, Text.literal(LABELS[i]), drawX + 110 - labelW / 2, btnY + 14 - 4, textColor, true);
         }
      }
   }

   private void renderFooter(DrawContext context) {
      int a = (int)(this.openProgress * 80.0F);
      int color = a << 24 | 6715272;
      context.drawText(this.textRenderer, Text.literal("WareVisuals"), 6, this.height - 14, color, false);
      String right = "Minecraft 1.21.4";
      int rw = this.textRenderer.getWidth(right);
      context.drawText(this.textRenderer, Text.literal(right), this.width - rw - 6, this.height - 14, color, false);
   }

   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      if (button == 0) {
         int startY = this.height / 2 - LABELS.length * 34 / 2 + 10;
         int cx = this.width / 2 - 110;

         for (int i = 0; i < 4; i++) {
            if (!(this.btnSlide[i] < 0.5F)) {
               int btnY = startY + i * 34;
               if (mouseX >= (double)cx && mouseX <= (double)(cx + 220) && mouseY >= (double)btnY && mouseY <= (double)(btnY + 28)) {
                  this.handleButton(i);
                  return true;
               }
            }
         }
      }

      return super.mouseClicked(mouseX, mouseY, button);
   }

   private void handleButton(int index) {
      if (this.client != null) {
         switch (index) {
            case 0:
               this.client.setScreen(new SelectWorldScreen(this));
               break;
            case 1:
               this.client.setScreen(new MultiplayerScreen(this));
               break;
            case 2:
               this.client.setScreen(new OptionsScreen(this, this.client.options));
               break;
            case 3:
               this.client.scheduleStop();
         }
      }
   }

   public boolean shouldCloseOnEsc() {
      return false;
   }
}
