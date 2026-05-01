package com.pathdlc.digger.funtime;

import com.pathdlc.digger.baritone.BaritoneBridge;
import com.pathdlc.digger.gui.Module;
import com.pathdlc.digger.gui.ModuleManager;
import com.pathdlc.digger.gui.ModuleSetting;
import com.pathdlc.digger.util.Chat;
import net.minecraft.client.MinecraftClient;

public class AutoMineBot {
   private final BaritoneBridge baritone;
   private boolean running;
   private String lastOreSet = "";

   public AutoMineBot(BaritoneBridge baritone) {
      this.baritone = baritone;
   }

   public void start() {
      if (!this.baritone.isAvailable()) {
         Chat.info("Baritone not available");
      } else {
         this.running = true;
         this.lastOreSet = "";
         Chat.info("AutoMine started");
      }
   }

   public void stop() {
      this.running = false;
      this.baritone.cancel();
      Chat.info("AutoMine stopped");
   }

   public void tick(MinecraftClient client) {
      if (this.running) {
         if (!ModuleManager.isEnabled("AutoMine")) {
            this.stop();
         } else if (client.player != null) {
            Module mod = ModuleManager.get("AutoMine");
            if (mod != null) {
               ModuleSetting oreSetting = mod.getSetting("Ore");
               int oreIndex = oreSetting != null ? oreSetting.getChoiceIndex() : 0;
               String oreCommand = this.getOreCommand(oreIndex);
               if (!oreCommand.equals(this.lastOreSet)) {
                  this.baritone.execute(oreCommand);
                  this.lastOreSet = oreCommand;
               }
            }
         }
      }
   }

   private String getOreCommand(int index) {
      return switch (index) {
         case 0 -> "mine diamond_ore deepslate_diamond_ore";
         case 1 -> "mine emerald_ore deepslate_emerald_ore";
         case 2 -> "mine gold_ore deepslate_gold_ore nether_gold_ore";
         case 3 -> "mine iron_ore deepslate_iron_ore";
         case 4 -> "mine ancient_debris";
         case 5 -> "mine diamond_ore deepslate_diamond_ore emerald_ore deepslate_emerald_ore gold_ore deepslate_gold_ore";
         default -> "mine diamond_ore deepslate_diamond_ore";
      };
   }

   public boolean isRunning() {
      return this.running;
   }
}
