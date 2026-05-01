package com.pathdlc.digger.funtime;

import com.pathdlc.digger.gui.Module;
import com.pathdlc.digger.gui.ModuleManager;
import com.pathdlc.digger.gui.ModuleSetting;
import com.pathdlc.digger.util.Chat;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

public class AutoSellBot {
   private boolean running;
   private int tickCounter;
   private int sellCooldown;
   private static final int SELL_INTERVAL = 600;

   public void start() {
      this.running = true;
      this.tickCounter = 0;
      this.sellCooldown = 20;
      Chat.info("AutoSell started - selling via /buyer");
   }

   public void stop() {
      this.running = false;
      Chat.info("AutoSell stopped");
   }

   public void tick(MinecraftClient client) {
      if (this.running) {
         if (!ModuleManager.isEnabled("AutoSell")) {
            this.stop();
         } else if (client.player != null) {
            this.tickCounter++;
            Module mod = ModuleManager.get("AutoSell");
            if (mod != null) {
               ModuleSetting modeSetting = mod.getSetting("Mode");
               int mode = modeSetting != null ? modeSetting.getChoiceIndex() : 0;
               ModuleSetting intervalSetting = mod.getSetting("Interval");
               int interval = intervalSetting != null ? (int)(intervalSetting.getFloat() * 20.0F) : 600;
               if (this.tickCounter >= interval) {
                  this.tickCounter = 0;
                  ClientPlayerEntity player = client.player;
                  switch (mode) {
                     case 0:
                        this.sellViaBuyer(player);
                        break;
                     case 1:
                        this.sellJunk(player);
                        break;
                     case 2:
                        this.sellAll(player);
                  }
               }
            }
         }
      }
   }

   private void sellViaBuyer(ClientPlayerEntity player) {
      player.networkHandler.sendChatMessage("/buyer");
      Chat.info("Opening /buyer...");
   }

   private void sellJunk(ClientPlayerEntity player) {
      boolean hasJunk = false;

      for (int i = 0; i < player.getInventory().size(); i++) {
         Item item = player.getInventory().getStack(i).getItem();
         if (this.isJunk(item)) {
            hasJunk = true;
            break;
         }
      }

      if (hasJunk) {
         player.networkHandler.sendChatMessage("/buyer");
         Chat.info("Selling junk items...");
      }
   }

   private void sellAll(ClientPlayerEntity player) {
      player.networkHandler.sendChatMessage("/buyer");
      Chat.info("Selling all via /buyer...");
   }

   private boolean isJunk(Item item) {
      return item == Items.COBBLESTONE
         || item == Items.COBBLED_DEEPSLATE
         || item == Items.DIRT
         || item == Items.GRAVEL
         || item == Items.SAND
         || item == Items.ANDESITE
         || item == Items.DIORITE
         || item == Items.GRANITE
         || item == Items.TUFF
         || item == Items.NETHERRACK
         || item == Items.ROTTEN_FLESH
         || item == Items.SPIDER_EYE
         || item == Items.STRING
         || item == Items.BONE
         || item == Items.GUNPOWDER
         || item == Items.FEATHER
         || item == Items.FLINT;
   }

   public boolean isRunning() {
      return this.running;
   }
}
