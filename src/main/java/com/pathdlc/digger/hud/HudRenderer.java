package com.pathdlc.digger.hud;

import com.pathdlc.digger.gui.GuiSettings;
import com.pathdlc.digger.gui.Module;
import com.pathdlc.digger.gui.ModuleManager;
import com.pathdlc.digger.gui.ModuleSetting;
import com.pathdlc.digger.render.RoundedRectRenderer;
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
   private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

   private static final int MARGIN = 6;
   private static final int LINE_GAP = 2;
   private static final int PAD_X = 8;
   private static final int PAD_Y = 5;
   private static final int LOGO_SIZE = 14;
   private static final int LOGO_TEXT_GAP = 6;
   private static final int STRIPE_W = 2;

   private static Field musicCurrentField;
   private static boolean musicReflectionFailed = false;

   private HudRenderer() {
   }

   public static void render(DrawContext context) {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client == null
         || client.player == null
         || client.world == null
         || client.options.hudHidden
         || client.currentScreen != null) {
         return;
      }

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
            topLeftY = renderInfoRow(context, tr, accent, MARGIN, topLeftY, "\u266A " + track);
         }
      }

      if (widgetEnabled("ArrayList", true)) {
         renderArrayList(context, tr, accent, sw);
      }

      if (widgetEnabled("FPS", true)) {
         String label = client.getCurrentFps() + " FPS";
         bottomRightY = renderInfoRowRight(context, tr, accent, sw - MARGIN, bottomRightY, label);
      }

      if (widgetEnabled("Ping", true)) {
         int ping = currentPing(client);
         String label = (ping >= 0 ? ping : 0) + " ms";
         bottomRightY = renderInfoRowRight(context, tr, accent, sw - MARGIN, bottomRightY, label);
      }

      if (widgetEnabled("Time", false)) {
         String label = LocalTime.now().format(TIME_FMT);
         bottomLeftY = renderInfoRow(context, tr, accent, MARGIN, bottomLeftY, label);
      }

      if (widgetEnabled("Coords", true)) {
         double x = client.player.getX();
         double y = client.player.getY();
         double z = client.player.getZ();
         String label = String.format("XYZ %.0f %.0f %.0f", x, y, z);
         bottomLeftY = renderInfoRow(context, tr, accent, MARGIN, bottomLeftY, label);
      }

      if (widgetEnabled("TargetHUD", true)) {
         renderTargetHud(context, tr, accent, sw, sh, client);
      }
   }

   private static boolean widgetEnabled(String name, boolean defaultValue) {
      Module m = ModuleManager.get("HUDOptions");
      if (m == null) {
         return defaultValue;
      }
      ModuleSetting s = m.getSetting(name);
      return s != null ? s.getBool() : defaultValue;
   }

   private static int renderWatermark(DrawContext context, TextRenderer tr, GuiSettings.AccentColor accent, int x, int y) {
      Text label = styledText("WareVisuals");
      int textW = tr.getWidth(label);
      int rowH = Math.max(LOGO_SIZE, tr.fontHeight) + PAD_Y * 2;
      int rowW = PAD_X * 2 + LOGO_SIZE + LOGO_TEXT_GAP + textW;

      RoundedRectRenderer.draw(context, x, y, rowW, rowH, 4, 0xC0080808);
      RoundedRectRenderer.draw(context, x, y, STRIPE_W, rowH, 1, 0xFF000000 | (accent.textColor & 0xFFFFFF));

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
      Text label = styledText(text);
      int textW = tr.getWidth(label);
      int rowH = tr.fontHeight + PAD_Y * 2;
      int rowW = textW + PAD_X * 2;

      RoundedRectRenderer.draw(context, x, y, rowW, rowH, 3, 0xC0080808);
      RoundedRectRenderer.draw(context, x, y, STRIPE_W, rowH, 1, 0xFF000000 | (accent.textColor & 0xFFFFFF));

      int textX = x + PAD_X;
      int textY = y + rowH / 2 - tr.fontHeight / 2;
      context.drawText(tr, label, textX, textY, 0xFFFFFFFF, true);
      return y + rowH + LINE_GAP;
   }

   private static int renderInfoRowRight(DrawContext context, TextRenderer tr, GuiSettings.AccentColor accent, int rightX, int y, String text) {
      Text label = styledText(text);
      int textW = tr.getWidth(label);
      int rowH = tr.fontHeight + PAD_Y * 2;
      int rowW = textW + PAD_X * 2;
      int x = rightX - rowW;

      RoundedRectRenderer.draw(context, x, y, rowW, rowH, 3, 0xC0080808);
      RoundedRectRenderer.draw(context, x + rowW - STRIPE_W, y, STRIPE_W, rowH, 1, 0xFF000000 | (accent.textColor & 0xFFFFFF));

      int textX = x + PAD_X;
      int textY = y + rowH / 2 - tr.fontHeight / 2;
      context.drawText(tr, label, textX, textY, 0xFFFFFFFF, true);
      return y + rowH + LINE_GAP;
   }

   private static final java.util.Set<String> ARRAYLIST_HIDDEN = java.util.Set.of("HUDOptions", "MenuStyle");

   private static void renderArrayList(DrawContext context, TextRenderer tr, GuiSettings.AccentColor accent, int screenW) {
      List<Module> enabled = collectEnabled();
      enabled.removeIf(m -> ARRAYLIST_HIDDEN.contains(m.getName()));
      if (enabled.isEmpty()) {
         return;
      }

      enabled.sort(Comparator.comparingInt((Module m) -> tr.getWidth(styledText(m.getName()))).reversed());

      int rowH = tr.fontHeight + PAD_Y * 2;
      int y = MARGIN;

      for (Module m : enabled) {
         Text label = styledText(m.getName());
         int textW = tr.getWidth(label);
         int rowW = textW + PAD_X * 2 + STRIPE_W;
         int x = screenW - rowW - MARGIN;

         RoundedRectRenderer.draw(context, x, y, rowW, rowH, 3, 0xC0080808);
         int stripeColor = 0xFF000000 | (accent.textColor & 0xFFFFFF);
         RoundedRectRenderer.draw(context, x + rowW - STRIPE_W, y, STRIPE_W, rowH, 1, stripeColor);

         int textX = x + PAD_X;
         int textY = y + rowH / 2 - tr.fontHeight / 2;
         context.drawText(tr, label, textX, textY, 0xFFFFFFFF, true);

         y += rowH + 1;
      }
   }

   private static void renderTargetHud(DrawContext context, TextRenderer tr, GuiSettings.AccentColor accent, int sw, int sh, MinecraftClient client) {
      LivingEntity target = currentTarget(client);
      if (target == null || !target.isAlive()) {
         return;
      }

      String name = target.getName().getString();
      float health = target.getHealth();
      float maxHealth = Math.max(1.0F, target.getMaxHealth());
      float distance = client.player.distanceTo(target);

      int logoSize = LOGO_SIZE;
      Text nameLabel = styledText(name);
      Text statsLabel = styledText(String.format("%.1f / %.1f HP   %.1fm", health, maxHealth, distance));
      int nameW = tr.getWidth(nameLabel);
      int statsW = tr.getWidth(statsLabel);
      int textBlockW = Math.max(nameW, statsW);
      int rowW = PAD_X * 2 + logoSize + LOGO_TEXT_GAP + textBlockW;
      int rowH = PAD_Y * 2 + tr.fontHeight * 2 + 4 + 6;
      int x = sw / 2 - rowW / 2;
      int y = sh - rowH - 60;

      RoundedRectRenderer.draw(context, x, y, rowW, rowH, 5, 0xCC080808);
      RoundedRectRenderer.draw(context, x, y, STRIPE_W, rowH, 1, 0xFF000000 | (accent.textColor & 0xFFFFFF));

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
      context.drawText(tr, nameLabel, textX, textY, 0xFFFFFFFF, true);
      context.drawText(tr, statsLabel, textX, textY + tr.fontHeight + 2, 0xFFAAAAAA, true);

      int barX = textX;
      int barY = y + rowH - PAD_Y - 4;
      int barW = textBlockW;
      int barH = 4;
      RoundedRectRenderer.draw(context, barX, barY, barW, barH, 2, 0x80222222);
      int fillW = (int)(barW * (health / maxHealth));
      int healthColor = healthBarColor(health / maxHealth, accent);
      if (fillW > 0) {
         RoundedRectRenderer.draw(context, barX, barY, fillW, barH, 2, healthColor);
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
      ClientPlayNetworkHandler net = client.getNetworkHandler();
      if (net == null || client.player == null) {
         return -1;
      }
      PlayerListEntry entry = net.getPlayerListEntry(client.player.getUuid());
      return entry != null ? entry.getLatency() : -1;
   }

   private static String currentMusicLabel(MinecraftClient client) {
      if (musicReflectionFailed) {
         return null;
      }
      try {
         MusicTracker tracker = client.getMusicTracker();
         if (tracker == null) {
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
               return null;
            }
         }
         Object current = musicCurrentField.get(tracker);
         if (current == null) {
            return null;
         }
         SoundInstance instance = (SoundInstance)current;
         Identifier id = instance.getId();
         if (id == null) {
            return null;
         }
         String path = id.getPath();
         return prettifyTrack(path);
      } catch (Throwable t) {
         musicReflectionFailed = true;
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

   private static Text styledText(String s) {
      return GuiSettings.isCustomFontEnabled()
         ? Text.literal(s).styled(style -> style.withFont(CUSTOM_FONT))
         : Text.literal(s);
   }
}
