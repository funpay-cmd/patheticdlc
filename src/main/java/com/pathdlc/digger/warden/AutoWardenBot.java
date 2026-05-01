package com.pathdlc.digger.warden;

import com.pathdlc.digger.baritone.BaritoneBridge;
import com.pathdlc.digger.util.Chat;
import net.minecraft.client.MinecraftClient;

/**
 * Stub. AutoWarden is exposed in the ClickGUI and via the .warden
 * command for backwards compatibility, but performs no chest scanning,
 * pathing or looting. start/stop/tick are no-ops; configuration getters
 * and setters are kept so the command handler still echoes values back.
 */
public class AutoWardenBot {
   private final BaritoneBridge baritone;
   private boolean running;
   private int radius = 48;
   private int verticalRadius = 16;

   public AutoWardenBot(BaritoneBridge baritone) {
      this.baritone = baritone;
   }

   public void start() {
      this.running = false;
      Chat.info("AutoWarden is disabled in this build.");
   }

   public void stop() {
      this.running = false;
   }

   public void tick(MinecraftClient mc) {
   }

   public boolean isRunning() {
      return this.running;
   }

   public int getRadius() {
      return this.radius;
   }

   public void setRadius(int radius) {
      this.radius = Math.max(8, Math.min(96, radius));
   }

   public int getVerticalRadius() {
      return this.verticalRadius;
   }

   public void setVerticalRadius(int verticalRadius) {
      this.verticalRadius = Math.max(4, Math.min(32, verticalRadius));
   }

   public String status() {
      return "AutoWarden: disabled in this build";
   }
}
