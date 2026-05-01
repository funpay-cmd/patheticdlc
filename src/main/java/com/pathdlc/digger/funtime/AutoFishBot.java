package com.pathdlc.digger.funtime;

import net.minecraft.client.MinecraftClient;

/**
 * Stub. AutoFish is exposed in the ClickGUI but performs no fishing
 * automation. All entry points are no-ops.
 */
public class AutoFishBot {
   private boolean running;

   public void start() {
      this.running = false;
   }

   public void stop() {
      this.running = false;
   }

   public void tick(MinecraftClient client) {
   }

   public boolean isRunning() {
      return this.running;
   }
}
