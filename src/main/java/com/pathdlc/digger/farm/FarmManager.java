package com.pathdlc.digger.farm;

import com.pathdlc.digger.baritone.BaritoneBridge;
import net.minecraft.client.MinecraftClient;

public class FarmManager {
   private final AutoAppleFarm apple;

   public FarmManager(BaritoneBridge baritone) {
      this.apple = new AutoAppleFarm(baritone);
   }

   public AutoAppleFarm apple() {
      return this.apple;
   }

   public void tick(MinecraftClient mc) {
      this.apple.tick(mc);
   }

   public void stopAll() {
      if (this.apple.isRunning()) {
         this.apple.stop();
      }
   }

   public String status() {
      return this.apple.status();
   }
}
