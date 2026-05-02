package com.pathdlc.digger.world;

import com.pathdlc.digger.gui.Module;
import com.pathdlc.digger.gui.ModuleManager;
import com.pathdlc.digger.gui.ModuleSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.option.ServerList;
import net.minecraft.client.world.ClientWorld;

/**
 * Drives client-side state for the World-category modules: gamma override
 * (FullBright), client-side day/night override (TimeChanger), suppression of
 * the rain/thunder gradients (NoWeather), and the convenience modules
 * AutoRespawn and AutoReconnect.
 *
 * Heavy hooks (fog, hurt-camera tilt, weather rendering) live in their own
 * mixins; this class only does what can be expressed as per-tick state
 * mutations from the client thread.
 */
public final class WorldStateController {
   private static Double originalGamma = null;
   private static long lastReconnectAttempt = 0L;
   private static ServerInfo lastServerInfo = null;

   private WorldStateController() {
   }

   public static void tick(MinecraftClient client) {
      if (client == null) {
         return;
      }
      tickFullBright(client);
      tickTimeChanger(client);
      tickNoWeather(client);
      tickAutoRespawn(client);
      tickAutoReconnect(client);
      rememberLastServer(client);
   }

   private static void tickFullBright(MinecraftClient client) {
      try {
         if (ModuleManager.isEnabled("FullBright")) {
            double current = client.options.getGamma().getValue();
            if (originalGamma == null) {
               originalGamma = current;
            }
            if (current < 16.0) {
               client.options.getGamma().setValue(16.0);
            }
         } else if (originalGamma != null) {
            client.options.getGamma().setValue(originalGamma);
            originalGamma = null;
         }
      } catch (Throwable ignored) {
      }
   }

   private static void tickTimeChanger(MinecraftClient client) {
      if (!ModuleManager.isEnabled("TimeChanger")) {
         return;
      }
      ClientWorld world = client.world;
      if (world == null) {
         return;
      }
      Module mod = ModuleManager.get("TimeChanger");
      if (mod == null) {
         return;
      }
      ModuleSetting setting = mod.getSetting("Time");
      if (setting == null) {
         return;
      }
      long target;
      switch (setting.getChoiceValue()) {
         case "Night":
            target = 13000L;
            break;
         case "Sunrise":
            target = 23000L;
            break;
         case "Sunset":
            target = 12000L;
            break;
         case "Day":
         default:
            target = 1000L;
            break;
      }
      try {
         world.setTime(world.getTime(), target, false);
      } catch (Throwable ignored) {
      }
   }

   private static void tickNoWeather(MinecraftClient client) {
      if (!ModuleManager.isEnabled("NoWeather")) {
         return;
      }
      ClientWorld world = client.world;
      if (world == null) {
         return;
      }
      try {
         world.setRainGradient(0.0F);
         world.setThunderGradient(0.0F);
      } catch (Throwable ignored) {
      }
   }

   private static void tickAutoRespawn(MinecraftClient client) {
      if (!ModuleManager.isEnabled("AutoRespawn")) {
         return;
      }
      if (!(client.currentScreen instanceof DeathScreen)) {
         return;
      }
      ClientPlayerEntity player = client.player;
      if (player == null) {
         return;
      }
      try {
         player.requestRespawn();
         client.setScreen(null);
      } catch (Throwable ignored) {
      }
   }

   private static void tickAutoReconnect(MinecraftClient client) {
      if (!ModuleManager.isEnabled("AutoReconnect")) {
         return;
      }
      if (!(client.currentScreen instanceof DisconnectedScreen)) {
         return;
      }
      Module mod = ModuleManager.get("AutoReconnect");
      int delaySeconds = 5;
      if (mod != null) {
         ModuleSetting s = mod.getSetting("Delay");
         if (s != null) {
            delaySeconds = Math.max(1, (int) s.getFloat());
         }
      }
      long now = System.currentTimeMillis();
      if (lastReconnectAttempt != 0L && now - lastReconnectAttempt < delaySeconds * 1000L) {
         return;
      }
      ServerInfo target = lastServerInfo;
      if (target == null) {
         try {
            ServerList list = new ServerList(client);
            list.loadFile();
            if (list.size() > 0) {
               target = list.get(0);
            }
         } catch (Throwable ignored) {
         }
      }
      if (target == null) {
         return;
      }
      lastReconnectAttempt = now;
      try {
         ConnectScreen.connect(
               new TitleScreen(),
               client,
               ServerAddress.parse(target.address),
               target,
               false,
               null
         );
      } catch (Throwable ignored) {
      }
   }

   private static void rememberLastServer(MinecraftClient client) {
      try {
         ServerInfo info = client.getCurrentServerEntry();
         if (info != null) {
            lastServerInfo = info;
         }
      } catch (Throwable ignored) {
      }
   }
}
