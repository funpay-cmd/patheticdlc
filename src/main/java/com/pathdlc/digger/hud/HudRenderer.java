package com.pathdlc.digger.hud;

import com.pathdlc.digger.event.AutoEventBot;
import com.pathdlc.digger.gui.ClickGuiScreen;
import com.pathdlc.digger.gui.GuiSettings;
import com.pathdlc.digger.gui.Module;
import com.pathdlc.digger.gui.ModuleManager;
import com.pathdlc.digger.gui.ModuleSetting;
import com.pathdlc.digger.render.Animations;
import com.pathdlc.digger.render.PerformanceSettings;
import com.pathdlc.digger.render.RoundedRectRenderer;
import com.pathdlc.digger.render.StyledTextCache;
import java.lang.reflect.Field;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.sound.MusicTracker;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;

/**
 * WareVisuals HUD with widget-based layout: watermark, ArrayList, FPS, ping,
 * coords, time, TargetHUD, music. Each widget is gated by a toggle setting on
 * the HUDOptions module so the user can mix-and-match through the ClickGUI.
 */
public final class HudRenderer {
   private static final Identifier LOGO = Identifier.of("pathdlc_digger", "textures/gui/logo.png");
   private static final Identifier CUSTOM_FONT = Identifier.of("pathdlc_digger", "clickgui");
   private static final Identifier MONO_FONT = Identifier.of("pathdlc_digger", "mono");
   private static final Identifier TITLE_FONT = Identifier.of("pathdlc_digger", "title");
   private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

   private static final int MARGIN = 6;
   private static final int LINE_GAP = 2;
   private static final int PAD_X = 8;
   private static final int PAD_Y = 5;
   private static final int LOGO_SIZE = 14;
   private static final int LOGO_TEXT_GAP = 6;
   private static final int STRIPE_W = 2;

   private static final int MUSIC_REFRESH_FRAMES = 20;
   private static final int PING_REFRESH_FRAMES = 20;
   private static final int TARGET_AVATAR_SIZE = 24;

   private static Field musicCurrentField;
   private static boolean musicReflectionFailed = false;
   private static String cachedMusicLabel;
   private static long lastMusicLookup = -1L;
   private static int cachedPing = -1;
   private static long lastPingLookup = -1L;

   private HudRenderer() {
   }

   public static void render(DrawContext context) {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client == null
         || client.player == null
         || client.world == null
         || client.options.hudHidden
         || client.currentScreen instanceof ClickGuiScreen) {
         return;
      }

      Animations.beginFrame();
      tickModuleVisibility();

      GuiSettings.AccentColor accent = GuiSettings.getAccentColor();
      TextRenderer tr = client.textRenderer;
      int sw = client.getWindow().getScaledWidth();
      int sh = client.getWindow().getScaledHeight();

      int topLeftY = MARGIN;
      int bottomLeftY = sh - MARGIN - 60;
      int bottomRightY = sh - MARGIN - 60;

      if (widgetEnabled("Watermark", true)) {
         topLeftY = renderWatermark(context, tr, accent, MARGIN, topLeftY);
      }

      if (widgetEnabled("Music", true)) {
         String track = currentMusicLabel(client);
         if (track != null) {
            topLeftY = renderMusicCard(context, tr, accent, MARGIN, topLeftY, track);
         }
      }

      if (widgetEnabled("ArrayList", true)) {
         renderArrayList(context, tr, accent, sw);
      }

      if (widgetEnabled("FPS", true)) {
         String label = client.getCurrentFps() + " FPS";
         bottomRightY = renderInfoRowRight(context, tr, accent, sw - MARGIN, bottomRightY, label, true);
      }

      if (widgetEnabled("Ping", true)) {
         int ping = currentPing(client);
         String label = (ping >= 0 ? ping : 0) + " ms";
         bottomRightY = renderInfoRowRight(context, tr, accent, sw - MARGIN, bottomRightY, label, true);
      }

      if (widgetEnabled("Time", false)) {
         String label = LocalTime.now().format(TIME_FMT);
         bottomLeftY = renderInfoRow(context, tr, accent, MARGIN, bottomLeftY, label, true);
      }

      if (widgetEnabled("Coords", true)) {
         double x = client.player.getX();
         double y = client.player.getY();
         double z = client.player.getZ();
         String label = String.format("XYZ %.0f %.0f %.0f", x, y, z);
         bottomLeftY = renderInfoRow(context, tr, accent, MARGIN, bottomLeftY, label, true);
      }

      if (widgetEnabled("TargetHUD", true)) {
         renderTargetHud(context, tr, accent, sw, sh, client);
      }

      if (widgetEnabled("Keystrokes", false)) {
         renderKeystrokes(context, tr, accent, sw, sh, client);
      }

      if (ModuleManager.isEnabled("AutoEvent")) {
         renderAutoEvent(context, tr, accent, sw);
      }
   }

   private static void renderKeystrokes(DrawContext context, TextRenderer tr, GuiSettings.AccentColor accent, int sw, int sh, MinecraftClient client) {
      // Bottom-center 3-row stack:
      //   . W .
      //   A S D
      //   LMB RMB
      int keySize = 18;
      int gap = 2;
      int gridW = keySize * 3 + gap * 2;
      int wideW = (gridW - gap) / 2;
      int gridH = keySize * 2 + gap;
      int totalH = gridH + gap + keySize;

      int x0 = (sw - gridW) / 2;
      int y0 = sh - totalH - MARGIN - 28;

      boolean fwd = client.options.forwardKey.isPressed();
      boolean back = client.options.backKey.isPressed();
      boolean left = client.options.leftKey.isPressed();
      boolean right = client.options.rightKey.isPressed();
      boolean attack = client.options.attackKey.isPressed();
      boolean use = client.options.useKey.isPressed();
      boolean jump = client.options.jumpKey.isPressed();

      drawKey(context, tr, accent, x0 + keySize + gap, y0, keySize, "W", fwd);
      drawKey(context, tr, accent, x0, y0 + keySize + gap, keySize, "A", left);
      drawKey(context, tr, accent, x0 + keySize + gap, y0 + keySize + gap, keySize, "S", back);
      drawKey(context, tr, accent, x0 + (keySize + gap) * 2, y0 + keySize + gap, keySize, "D", right);

      int rowY = y0 + (keySize + gap) * 2;
      drawKey(context, tr, accent, x0, rowY, wideW, keySize, "LMB", attack);
      drawKey(context, tr, accent, x0 + wideW + gap, rowY, wideW, keySize, "RMB", use);

      if (jump) {
         // Subtle accent underline when jumping (space bar) — keeps grid compact.
         int barW = gridW;
         int barH = 2;
         int barY = rowY + keySize + gap;
         int barColor = 0xFF000000 | (accent.textColor & 0xFFFFFF);
         RoundedRectRenderer.draw(context, x0, barY, barW, barH, 1, barColor);
      }
   }

   private static void drawKey(DrawContext context, TextRenderer tr, GuiSettings.AccentColor accent, int x, int y, int size, String label, boolean held) {
      drawKey(context, tr, accent, x, y, size, size, label, held);
   }

   private static void drawKey(DrawContext context, TextRenderer tr, GuiSettings.AccentColor accent, int x, int y, int w, int h, String label, boolean held) {
      int bg = held ? (0xFF000000 | (accent.textColor & 0xFFFFFF)) : 0xC01A0A0F;
      int fg = held ? 0xFF1A0A0F : 0xFFFFFFFF;
      RoundedRectRenderer.draw(context, x, y, w, h, 3, bg);
      int textW = tr.getWidth(label);
      int textX = x + (w - textW) / 2;
      int textY = y + (h - tr.fontHeight) / 2 + 1;
      context.drawText(tr, label, textX, textY, fg, false);
   }

   private static float autoEventAnim = 0.0F;

   private static void renderAutoEvent(DrawContext context, TextRenderer tr, GuiSettings.AccentColor accent, int sw) {
      List<AutoEventBot.EventEntry> events = AutoEventBot.snapshot();
      float target = events.isEmpty() ? 0.0F : 1.0F;
      autoEventAnim = Animations.ease(autoEventAnim, target, 0.07F);
      if (events.isEmpty() && autoEventAnim < 0.005F) {
         return;
      }
      events.sort(Comparator.comparingLong(e -> e.startEpochMs <= 0L ? Long.MAX_VALUE : e.startEpochMs));

      float anim = Animations.smoothstep(autoEventAnim);
      // Compact layout: one row per event, no header/divider, tight padding.
      int compactPadX = 6;
      int compactPadY = 3;
      int rowH = tr.fontHeight + compactPadY * 2;

      int maxRowW = 0;
      String[][] cells = new String[Math.max(events.size(), 1)][3];
      for (int i = 0; i < events.size(); i++) {
         AutoEventBot.EventEntry e = events.get(i);
         double dist = AutoEventBot.distance(e);
         cells[i][0] = e.name;
         cells[i][1] = dist >= 0.0 ? String.format("%dm", (int) Math.round(dist)) : "--";
         cells[i][2] = AutoEventBot.formatCountdown(e);
         int w = tr.getWidth(plainText(cells[i][0]))
               + tr.getWidth(monoText(cells[i][1]))
               + tr.getWidth(monoText(cells[i][2]))
               + 24;
         if (w > maxRowW) {
            maxRowW = w;
         }
      }
      int rowW = maxRowW + compactPadX * 2;
      int x = sw / 2 - rowW / 2;
      int slideY = (int) ((1.0F - anim) * -8.0F);
      int y = MARGIN + slideY;

      int rowY = y;
      int bgAlpha = (int) (204.0F * anim);
      for (int i = 0; i < events.size(); i++) {
         RoundedRectRenderer.draw(context, x, rowY, rowW, rowH, 4, (bgAlpha << 24) | 0x1A0A0F);

         Text name = plainText(cells[i][0]);
         Text dist = monoText(cells[i][1]);
         Text countdown = monoText(cells[i][2]);
         int distW = tr.getWidth(dist);
         int cdW = tr.getWidth(countdown);
         int rowTextY = rowY + rowH / 2 - tr.fontHeight / 2;
         int textA = (int) (255.0F * anim);
         int dimA = (int) (170.0F * anim);
         int accentA = ((int) (((accent.textColor >>> 24) & 0xFF) * anim)) << 24
               | (accent.textColor & 0xFFFFFF);
         context.drawText(tr, name, x + compactPadX, rowTextY, (textA << 24) | 0xFFFFFF, true);
         int countdownX = x + rowW - compactPadX - cdW;
         int distX = countdownX - 8 - distW;
         context.drawText(tr, dist, distX, rowTextY, (dimA << 24) | 0xAAAAAA, true);
         context.drawText(tr, countdown, countdownX, rowTextY, accentA, true);
         rowY += rowH + 1;
      }
   }

   private static boolean widgetEnabled(String name, boolean defaultValue) {
      Module m = ModuleManager.get("HUDOptions");
      if (m == null) {
         return defaultValue;
      }
      if (!m.isEnabled()) {
         return false;
      }
      ModuleSetting s = m.getSetting(name);
      return s != null ? s.getBool() : defaultValue;
   }

   private static int renderWatermark(DrawContext context, TextRenderer tr, GuiSettings.AccentColor accent, int x, int y) {
      Text label = titleText("WareVisuals");
      int textW = tr.getWidth(label);
      int rowH = Math.max(LOGO_SIZE, tr.fontHeight) + PAD_Y * 2;
      int rowW = PAD_X * 2 + LOGO_SIZE + LOGO_TEXT_GAP + textW;

      RoundedRectRenderer.draw(context, x, y, rowW, rowH, 4, 0xC01A0A0F);

      int logoY = y + rowH / 2 - LOGO_SIZE / 2;
      int logoX = x + PAD_X;
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
      return y + rowH + LINE_GAP;
   }

   private static int renderInfoRow(DrawContext context, TextRenderer tr, GuiSettings.AccentColor accent, int x, int y, String text) {
      return renderInfoRow(context, tr, accent, x, y, text, false);
   }

   private static int renderInfoRow(DrawContext context, TextRenderer tr, GuiSettings.AccentColor accent, int x, int y, String text, boolean mono) {
      Text label = mono ? monoText(text) : plainText(text);
      int textW = tr.getWidth(label);
      int rowH = tr.fontHeight + PAD_Y * 2;
      int rowW = textW + PAD_X * 2;

      RoundedRectRenderer.draw(context, x, y, rowW, rowH, 3, 0xC01A0A0F);

      int textX = x + PAD_X;
      int textY = y + rowH / 2 - tr.fontHeight / 2;
      context.drawText(tr, label, textX, textY, 0xFFFFFFFF, true);
      return y + rowH + LINE_GAP;
   }

   private static int renderInfoRowRight(DrawContext context, TextRenderer tr, GuiSettings.AccentColor accent, int rightX, int y, String text) {
      return renderInfoRowRight(context, tr, accent, rightX, y, text, false);
   }

   private static int renderInfoRowRight(DrawContext context, TextRenderer tr, GuiSettings.AccentColor accent, int rightX, int y, String text, boolean mono) {
      Text label = mono ? monoText(text) : plainText(text);
      int textW = tr.getWidth(label);
      int rowH = tr.fontHeight + PAD_Y * 2;
      int rowW = textW + PAD_X * 2;
      int x = rightX - rowW;

      RoundedRectRenderer.draw(context, x, y, rowW, rowH, 3, 0xC01A0A0F);

      int textX = x + PAD_X;
      int textY = y + rowH / 2 - tr.fontHeight / 2;
      context.drawText(tr, label, textX, textY, 0xFFFFFFFF, true);
      return y + rowH + LINE_GAP;
   }

   /**
    * Spotify-style music card: dark rounded panel with an album-art tile on
    * the left, title above artist on the right, decorative heart in the
    * upper-right and a thin progress bar at the bottom.  Position/duration
    * is not actually queried (SystemMediaTracker only exposes the title and
    * artist text reliably across platforms), so the progress bar is purely
    * a visual flourish that scrubs slowly as a heartbeat for the widget.
    */
   private static int renderMusicCard(DrawContext context, TextRenderer tr, GuiSettings.AccentColor accent, int x, int y, String track) {
      int cardW = 184;
      int cardH = 56;
      int padX = 6;
      int padY = 6;
      int tile = cardH - padY * 2;
      int gap = 8;

      String artist;
      String title;
      int sep = track.indexOf(" - ");
      if (sep > 0 && sep + 3 < track.length()) {
         artist = track.substring(0, sep).trim();
         title = track.substring(sep + 3).trim();
      } else {
         artist = "";
         title = track.trim();
      }

      int textX = x + padX + tile + gap;
      int rightInner = x + cardW - padX;
      int textWidthBudget = rightInner - textX - 14;

      Text titleLabel = titleText(truncateForWidth(tr, title, textWidthBudget, true));
      Text artistLabel = plainText(truncateForWidth(tr, artist.isEmpty() ? "Now Playing" : artist, textWidthBudget, false));

      RoundedRectRenderer.draw(context, x, y, cardW, cardH, 6, 0xE6120A0E);
      int tileX = x + padX;
      int tileY = y + padY;
      RoundedRectRenderer.draw(context, tileX, tileY, tile, tile, 4, 0xFF2A1015);
      RoundedRectRenderer.draw(context, tileX, tileY, tile, tile / 2, 4, 0xFF38121A);

      int accentARGB = 0xFF000000 | (accent.textColor & 0xFFFFFF);
      Text noteGlyph = plainText("\u266A");
      int glyphW = tr.getWidth(noteGlyph);
      int glyphX = tileX + tile / 2 - glyphW / 2;
      int glyphY = tileY + tile / 2 - tr.fontHeight / 2;
      context.drawText(tr, noteGlyph, glyphX, glyphY, accentARGB, true);

      context.drawText(tr, titleLabel, textX, y + padY + 2, 0xFFFFFFFF, true);
      context.drawText(tr, artistLabel, textX, y + padY + 2 + tr.fontHeight + 2, 0xFFA0A0A0, true);

      Text heart = plainText("\u2665");
      int heartW = tr.getWidth(heart);
      int heartX = x + cardW - padX - heartW;
      int heartY = y + padY;
      context.drawText(tr, heart, heartX, heartY, accentARGB, true);

      int barX = textX;
      int barY = y + cardH - padY - 3;
      int barW = rightInner - barX;
      context.fill(barX, barY, barX + barW, barY + 1, 0xFF2A1015);
      long now = System.currentTimeMillis();
      float progress = ((now / 80L) % 100) / 100.0F;
      int filled = Math.max(2, (int) (barW * progress));
      context.fill(barX, barY, barX + filled, barY + 1, accentARGB);

      return y + cardH + LINE_GAP;
   }

   private static String truncateForWidth(TextRenderer tr, String text, int maxWidth, boolean useTitleFont) {
      if (text == null || text.isEmpty()) {
         return "";
      }
      Text probe = useTitleFont ? titleText(text) : plainText(text);
      if (tr.getWidth(probe) <= maxWidth) {
         return text;
      }
      String suffix = "\u2026";
      String candidate = text;
      while (candidate.length() > 1) {
         candidate = candidate.substring(0, candidate.length() - 1);
         Text trimmed = useTitleFont ? titleText(candidate + suffix) : plainText(candidate + suffix);
         if (tr.getWidth(trimmed) <= maxWidth) {
            return candidate + suffix;
         }
      }
      return suffix;
   }

   private static final java.util.Set<String> ARRAYLIST_HIDDEN = java.util.Set.of("HUDOptions", "MenuStyle");

   private static void renderArrayList(DrawContext context, TextRenderer tr, GuiSettings.AccentColor accent, int screenW) {
      List<Module> visible = collectVisible();
      if (visible.isEmpty()) {
         return;
      }

      visible.sort(Comparator.comparingInt((Module m) -> measureWidth(tr, m.getName())).reversed());

      int rowH = tr.fontHeight + PAD_Y * 2;
      float yF = (float) MARGIN;
      int rightEdge = screenW - MARGIN;

      for (Module m : visible) {
         float anim = Animations.smoothstep(m.visibilityAnim);
         if (anim < 0.005F) {
            continue;
         }
         String labelText = m.getName();
         Text label = styledText(labelText);
         int textW = measureWidth(tr, labelText);
         int rowW = textW + PAD_X * 2 + 4;
         int slideX = (int) ((1.0F - anim) * (rowW + 8));
         int x = rightEdge - rowW + slideX;
         int y = Math.round(yF);

         int bgAlpha = (int) (192.0F * anim);
         int bgColor = (bgAlpha << 24) | 0x1A0A0F;
         RoundedRectRenderer.draw(context, x, y, rowW, rowH, 3, bgColor);

         int textX = x + PAD_X;
         int textY = y + rowH / 2 - tr.fontHeight / 2;
         int textAlpha = (int) (255.0F * anim);
         int textColor = (textAlpha << 24) | 0xFFFFFF;
         context.drawText(tr, label, textX, textY, textColor, true);

         yF += (rowH + 1) * anim;
      }
   }

   private static int measureWidth(TextRenderer tr, String labelText) {
      int styled = tr.getWidth(styledText(labelText));
      int plain = tr.getWidth(Text.literal(labelText));
      // Inter-rendered widths from getWidth can occasionally underreport vs the
      // actual rasterised glyphs due to TTF oversample. Pick the larger so the
      // background panel always contains the text.
      return Math.max(styled, plain);
   }

   private static float targetHudAnim = 0.0F;

   private static void renderTargetHud(DrawContext context, TextRenderer tr, GuiSettings.AccentColor accent, int sw, int sh, MinecraftClient client) {
      LivingEntity target = currentTarget(client);
      boolean alive = target != null && target.isAlive();
      float targetVal = alive ? 1.0F : 0.0F;
      targetHudAnim = Animations.ease(targetHudAnim, targetVal, 0.06F);
      if (!alive && targetHudAnim < 0.005F) {
         return;
      }
      if (target == null) {
         return;
      }

      String name = target.getName().getString();
      float health = target.getHealth();
      float maxHealth = Math.max(1.0F, target.getMaxHealth());
      float distance = client.player.distanceTo(target);

      int logoSize = TARGET_AVATAR_SIZE;
      Text nameLabel = plainText(name);
      Text statsLabel = plainText(String.format("%.1f / %.1f HP   %.1fm", health, maxHealth, distance));
      int nameW = tr.getWidth(nameLabel);
      int statsW = tr.getWidth(statsLabel);
      int textBlockW = Math.max(nameW, statsW);
      int rowW = PAD_X * 2 + logoSize + LOGO_TEXT_GAP + textBlockW;
      int rowH = Math.max(logoSize + PAD_Y * 2 + 6, PAD_Y * 2 + tr.fontHeight * 2 + 4 + 6);
      float anim = Animations.smoothstep(targetHudAnim);
      int x = sw / 2 - rowW / 2;
      int y = sh - rowH - 60 + (int) ((1.0F - anim) * 6.0F);

      int bgA = (int) (204.0F * anim);
      RoundedRectRenderer.draw(context, x, y, rowW, rowH, 5, (bgA << 24) | 0x1A0A0F);

      int avatarSize = logoSize;
      int avatarX = x + PAD_X;
      int avatarY = y + PAD_Y;
      Identifier skin = playerSkin(target);
      if (skin != null) {
         context.drawTexture(
            RenderLayer::getGuiTextured,
            skin,
            avatarX,
            avatarY,
            8.0F,
            8.0F,
            avatarSize,
            avatarSize,
            64,
            64
         );
      } else {
         context.drawTexture(
            RenderLayer::getGuiTextured,
            LOGO,
            avatarX,
            avatarY,
            0.0F,
            0.0F,
            avatarSize,
            avatarSize,
            avatarSize,
            avatarSize
         );
      }

      int textX = avatarX + avatarSize + LOGO_TEXT_GAP;
      int textY = y + PAD_Y;
      int nameA = (int) (255.0F * anim);
      int statsA = (int) (170.0F * anim);
      context.drawText(tr, nameLabel, textX, textY, (nameA << 24) | 0xFFFFFF, true);
      context.drawText(tr, statsLabel, textX, textY + tr.fontHeight + 2, (statsA << 24) | 0xAAAAAA, true);

      int barX = textX;
      int barY = y + rowH - PAD_Y - 4;
      int barW = textBlockW;
      int barH = 4;
      int trackA = (int) (128.0F * anim);
      RoundedRectRenderer.draw(context, barX, barY, barW, barH, 2, (trackA << 24) | 0x222222);
      int fillW = (int)(barW * (health / maxHealth));
      int healthColor = healthBarColor(health / maxHealth, accent);
      int healthA = (int) (((healthColor >>> 24) & 0xFF) * anim);
      int healthRGB = healthColor & 0xFFFFFF;
      if (fillW > 0) {
         RoundedRectRenderer.draw(context, barX, barY, fillW, barH, 2, (healthA << 24) | healthRGB);
      }
   }

   private static int healthBarColor(float ratio, GuiSettings.AccentColor accent) {
      if (ratio > 0.66F) {
         return 0xFF000000 | (accent.textColor & 0xFFFFFF);
      } else if (ratio > 0.33F) {
         return 0xFFE6A23C;
      } else {
         return 0xFFEF4444;
      }
   }

   private static LivingEntity currentTarget(MinecraftClient client) {
      HitResult target = client.crosshairTarget;
      if (target instanceof EntityHitResult && target.getType() == HitResult.Type.ENTITY) {
         EntityHitResult eh = (EntityHitResult)target;
         if (eh.getEntity() instanceof LivingEntity le && le != client.player) {
            return le;
         }
      }
      return null;
   }

   private static Identifier playerSkin(LivingEntity entity) {
      if (entity instanceof PlayerEntity) {
         try {
            PlayerEntity p = (PlayerEntity)entity;
            ClientPlayNetworkHandler net = MinecraftClient.getInstance().getNetworkHandler();
            if (net == null) {
               return null;
            }
            UUID uuid = p.getUuid();
            PlayerListEntry entry = net.getPlayerListEntry(uuid);
            if (entry == null) {
               return null;
            }
            return entry.getSkinTextures().texture();
         } catch (Throwable t) {
            return null;
         }
      }
      return null;
   }

   private static int currentPing(MinecraftClient client) {
      long frame = PerformanceSettings.getFrameCounter();
      if (lastPingLookup >= 0L && frame - lastPingLookup < PING_REFRESH_FRAMES) {
         return cachedPing;
      }
      lastPingLookup = frame;
      ClientPlayNetworkHandler net = client.getNetworkHandler();
      if (net == null || client.player == null) {
         cachedPing = -1;
         return -1;
      }
      PlayerListEntry entry = net.getPlayerListEntry(client.player.getUuid());
      cachedPing = entry != null ? entry.getLatency() : -1;
      return cachedPing;
   }

   private static String currentMusicLabel(MinecraftClient client) {
      long frame = PerformanceSettings.getFrameCounter();
      if (lastMusicLookup >= 0L && frame - lastMusicLookup < MUSIC_REFRESH_FRAMES) {
         return cachedMusicLabel;
      }
      lastMusicLookup = frame;

      // Prefer system-wide media (Spotify, browser-tab YouTube, VLC, etc.).
      String system = SystemMediaTracker.currentTrack();
      if (system != null && !system.isEmpty()) {
         cachedMusicLabel = system;
         return cachedMusicLabel;
      }

      // Fallback: vanilla Minecraft music via reflection on MusicTracker.
      if (musicReflectionFailed) {
         cachedMusicLabel = null;
         return null;
      }
      try {
         MusicTracker tracker = client.getMusicTracker();
         if (tracker == null) {
            cachedMusicLabel = null;
            return null;
         }
         if (musicCurrentField == null) {
            for (Field f : MusicTracker.class.getDeclaredFields()) {
               if (SoundInstance.class.isAssignableFrom(f.getType())) {
                  f.setAccessible(true);
                  musicCurrentField = f;
                  break;
               }
            }
            if (musicCurrentField == null) {
               musicReflectionFailed = true;
               cachedMusicLabel = null;
               return null;
            }
         }
         Object current = musicCurrentField.get(tracker);
         if (current == null) {
            cachedMusicLabel = null;
            return null;
         }
         SoundInstance instance = (SoundInstance)current;
         Identifier id = instance.getId();
         if (id == null) {
            cachedMusicLabel = null;
            return null;
         }
         cachedMusicLabel = prettifyTrack(id.getPath());
         return cachedMusicLabel;
      } catch (Throwable t) {
         musicReflectionFailed = true;
         cachedMusicLabel = null;
         return null;
      }
   }

   private static String prettifyTrack(String path) {
      if (path == null) {
         return null;
      }
      String name = path;
      int dot = name.lastIndexOf('.');
      if (dot >= 0 && dot + 1 < name.length()) {
         name = name.substring(dot + 1);
      }
      name = name.replace('_', ' ').trim();
      if (name.isEmpty()) {
         return null;
      }
      return Character.toUpperCase(name.charAt(0)) + name.substring(1);
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

   private static List<Module> collectVisible() {
      List<Module> out = new ArrayList<>();
      for (Module m : ModuleManager.values()) {
         if (ARRAYLIST_HIDDEN.contains(m.getName())) {
            continue;
         }
         if (m.isEnabled() || m.visibilityAnim > 0.005F) {
            out.add(m);
         }
      }
      return out;
   }

   private static void tickModuleVisibility() {
      for (Module m : ModuleManager.values()) {
         float target = m.isEnabled() ? 1.0F : 0.0F;
         m.visibilityAnim = Animations.ease(m.visibilityAnim, target, 0.05F);
      }
   }

   private static Text styledText(String s) {
      return StyledTextCache.get(s, CUSTOM_FONT);
   }

   private static Text plainText(String s) {
      String safe = s == null ? "" : s;
      if (!GuiSettings.isCustomFontEnabled()) {
         return Text.literal(safe);
      }
      return Text.literal(safe).styled(style -> style.withFont(CUSTOM_FONT));
   }

   private static Text monoText(String s) {
      String safe = s == null ? "" : s;
      if (!GuiSettings.isCustomFontEnabled()) {
         return Text.literal(safe);
      }
      return Text.literal(safe).styled(style -> style.withFont(MONO_FONT));
   }

   private static Text titleText(String s) {
      String safe = s == null ? "" : s;
      if (!GuiSettings.isCustomFontEnabled()) {
         return Text.literal(safe);
      }
      return Text.literal(safe).styled(style -> style.withFont(TITLE_FONT));
   }
}
