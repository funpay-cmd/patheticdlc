package com.pathdlc.digger.funtime;

import net.minecraft.client.MinecraftClient;

/**
 * Stub. AutoSell is exposed in the ClickGUI but does not interact with
 * any /sell or /buyer command. All entry points are no-ops.
 */
public class AutoSellBot {
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
