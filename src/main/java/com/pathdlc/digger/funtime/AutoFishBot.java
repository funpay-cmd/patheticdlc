package com.pathdlc.digger.funtime;

import com.pathdlc.digger.gui.ModuleManager;
import com.pathdlc.digger.util.Chat;
import net.minecraft.util.Hand;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.item.Items;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

public class AutoFishBot {
   private boolean running;
   private int castDelay;
   private int reelDelay;
   private boolean waitingForBite;

   public void start() {
      this.running = true;
      this.castDelay = 20;
      this.reelDelay = 0;
      this.waitingForBite = false;
      Chat.info("AutoFish started - hold a fishing rod");
   }

   public void stop() {
      this.running = false;
      Chat.info("AutoFish stopped");
   }

   public void tick(MinecraftClient client) {
      if (this.running) {
         if (!ModuleManager.isEnabled("AutoFish")) {
            this.stop();
         } else if (client.player != null && client.world != null) {
            if (client.interactionManager != null) {
               ClientPlayerEntity player = client.player;
               boolean hasRod = player.getMainHandStack().getItem() == Items.FISHING_ROD;
               if (hasRod) {
                  FishingBobberEntity bobber = player.fishHook;
                  if (bobber == null) {
                     if (this.castDelay > 0) {
                        this.castDelay--;
                     } else {
                        client.interactionManager.interactItem(player, Hand.MAIN_HAND);
                        this.waitingForBite = true;
                        this.castDelay = 30;
                     }
                  } else {
                     if (this.waitingForBite) {
                        boolean caught = bobber.getVelocity().y < -0.04 && bobber.getVelocity().y > -0.5 && !bobber.isOnGround();
                        if (caught) {
                           if (this.reelDelay > 0) {
                              this.reelDelay--;
                              return;
                           }

                           client.interactionManager.interactItem(player, Hand.MAIN_HAND);
                           this.waitingForBite = false;
                           this.castDelay = 20 + (int)(Math.random() * 20.0);
                           this.reelDelay = 3;
                        }
                     }
                  }
               }
            }
         }
      }
   }

   public boolean isRunning() {
      return this.running;
   }
}
