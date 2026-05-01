package com.pathdlc.digger.funtime;

import net.minecraft.client.MinecraftClient;

/**
 * Stub. BaseFinder is exposed in the ClickGUI but performs no scanning.
 * All entry points are no-ops.
 */
public class BaseFinderBot {
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
