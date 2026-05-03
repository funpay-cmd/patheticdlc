package com.pathdlc.digger.render;

import com.pathdlc.digger.gui.Module;
import com.pathdlc.digger.gui.ModuleManager;
import com.pathdlc.digger.gui.ModuleSetting;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;

/**
 * Sets the OS window title to a configurable format string while the
 * ClientName module is enabled.  Restores the original title when the
 * module is disabled.  Supported placeholders:
 *   %fps%, %ping%, %time%, %ver%, %name%
 */
public final class ClientNameHandler {
   private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

   private static String originalTitle;
   private static int slowTickCounter;

   private ClientNameHandler() {
   }

   public static void tick() {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc == null) {
         return;
      }
      Module m = ModuleManager.get("ClientName");
      if (m == null || !m.isEnabled()) {
         restoreIfNeeded(mc);
         return;
      }
      // Throttle to ~5 Hz — GLFW.glfwSetWindowTitle isn't free.
      if (++slowTickCounter % 4 != 0) {
         return;
      }
      if (originalTitle == null) {
         originalTitle = currentTitle(mc);
      }
      ModuleSetting fmtSetting = m.getSetting("Format");
      String fmt = fmtSetting != null ? fmtSetting.getChoiceValue() : "WareVisuals %fps% FPS";
      String resolved = expand(fmt, mc);
      mc.getWindow().setTitle(resolved);
   }

   private static void restoreIfNeeded(MinecraftClient mc) {
      if (originalTitle != null) {
         mc.getWindow().setTitle(originalTitle);
         originalTitle = null;
      }
   }

   private static String currentTitle(MinecraftClient mc) {
      // Window doesn't expose a getter, but we can rebuild Mojang's default
      // "Minecraft <ver>" string from SharedConstants.
      return "Minecraft " + net.minecraft.SharedConstants.getGameVersion().getName();
   }

   private static String expand(String fmt, MinecraftClient mc) {
      String fps = "" + mc.getCurrentFps();
      String ver = net.minecraft.SharedConstants.getGameVersion().getName();
      String time = LocalTime.now().format(TIME);
      String name = mc.player != null ? mc.player.getName().getString() : "Player";
      String ping = "0";
      ClientPlayNetworkHandler nh = mc.getNetworkHandler();
      if (nh != null && mc.player != null) {
         PlayerListEntry e = nh.getPlayerListEntry(mc.player.getUuid());
         if (e != null) ping = "" + Math.max(0, e.getLatency());
      }
      return fmt
            .replace("%fps%", fps)
            .replace("%ping%", ping)
            .replace("%time%", time)
            .replace("%ver%", ver)
            .replace("%name%", name);
   }
}
