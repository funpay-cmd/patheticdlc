package com.pathdlc.digger.funtime;

import com.pathdlc.digger.baritone.BaritoneBridge;
import net.minecraft.client.MinecraftClient;

/**
 * Stub. AutoMine is exposed in the ClickGUI but performs no mining
 * automation. Calls to start/stop/tick are no-ops.
 */
public class AutoMineBot {
   private final BaritoneBridge baritone;
   private boolean running;

   public AutoMineBot(BaritoneBridge baritone) {
      this.baritone = baritone;
   }

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
