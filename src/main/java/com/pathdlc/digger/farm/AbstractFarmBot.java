package com.pathdlc.digger.farm;

import com.pathdlc.digger.baritone.BaritoneBridge;
import com.pathdlc.digger.util.Chat;
import net.minecraft.util.math.BlockPos;
import net.minecraft.client.MinecraftClient;

public abstract class AbstractFarmBot implements FarmBot {
   protected final BaritoneBridge baritone;
   protected boolean running;
   protected int radius = 16;
   protected int pathCooldown;
   protected int waitTicks;
   protected int actions;
   protected int failed;

   protected AbstractFarmBot(BaritoneBridge baritone) {
      this.baritone = baritone;
   }

   @Override
   public void start() {
      this.running = true;
      this.pathCooldown = 0;
      this.waitTicks = 0;
      this.actions = 0;
      this.failed = 0;
      Chat.info(this.name() + " started. Radius: " + this.radius);
   }

   @Override
   public void stop() {
      this.running = false;
      this.baritone.cancel();
      Chat.info(this.name() + " stopped.");
   }

   @Override
   public boolean isRunning() {
      return this.running;
   }

   public void setRadius(int radius) {
      this.radius = Math.max(4, Math.min(64, radius));
   }

   public int getRadius() {
      return this.radius;
   }

   protected void gotoIfNeeded(BlockPos pos) {
      if (pos != null && this.pathCooldown <= 0) {
         this.baritone.gotoBlock(pos);
         this.pathCooldown = 45;
      }
   }

   protected void cooldownTick() {
      if (this.pathCooldown > 0) {
         this.pathCooldown--;
      }

      if (this.waitTicks > 0) {
         this.waitTicks--;
      }
   }

   @Override
   public String status() {
      return this.name() + ": running=" + this.running + ", radius=" + this.radius + ", actions=" + this.actions + ", failed=" + this.failed;
   }

   protected boolean worldReady(MinecraftClient mc) {
      return mc.player != null && mc.world != null && mc.interactionManager != null;
   }
}
