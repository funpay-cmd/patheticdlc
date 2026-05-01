package com.pathdlc.digger.command;

import com.pathdlc.digger.baritone.BaritoneBridge;
import com.pathdlc.digger.bot.DiggerBot;
import com.pathdlc.digger.selection.SelectionManager;
import com.pathdlc.digger.util.Chat;
import java.util.Locale;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.hit.HitResult;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;


public class DotCommandHandler {
   private final SelectionManager selection;
   private final DiggerBot digger;
   private final BaritoneBridge baritone;

   public DotCommandHandler(SelectionManager selection, DiggerBot digger, BaritoneBridge baritone) {
      this.selection = selection;
      this.digger = digger;
      this.baritone = baritone;
   }

   public boolean handle(String rawMessage) {
      String message = rawMessage.trim();
      if (!message.startsWith(".")) {
         return false;
      } else {
         String[] args = message.substring(1).trim().split("\\s+");
         if (args.length != 0 && !args[0].isBlank()) {
            String root = args[0].toLowerCase(Locale.ROOT);

            return switch (root) {
               case "pos" -> this.handlePos(args);
               case "fill" -> this.handleFill();
               case "dig" -> this.handleDig(args);
               default -> false;
            };
         } else {
            return false;
         }
      }
   }

   private boolean handlePos(String[] args) {
      if (args.length < 2) {
         Chat.warn("Use: .pos 1 / .pos 2 / .pos 1 look / .pos clear");
         return true;
      } else {
         String side = args[1].toLowerCase(Locale.ROOT);
         if (side.equals("clear")) {
            this.selection.clear();
            Chat.info("Selection cleared.");
            return true;
         } else {
            BlockPos pos = this.getCommandPos(args);
            if (pos == null) {
               Chat.error("Cannot set position. Join a world first.");
               return true;
            } else if (side.equals("1")) {
               this.selection.setPos1(pos);
               Chat.info("pos1 = " + this.format(pos));
               this.printSelectionInfo();
               return true;
            } else if (side.equals("2")) {
               this.selection.setPos2(pos);
               Chat.info("pos2 = " + this.format(pos));
               this.printSelectionInfo();
               return true;
            } else {
               Chat.warn("Use: .pos 1 / .pos 2 / .pos 1 look / .pos clear");
               return true;
            }
         }
      }
   }

   private boolean handleFill() {
      if (!this.selection.isComplete()) {
         Chat.error("Set both positions first: .pos 1 and .pos 2");
         return true;
      } else {
         this.digger.startFillOnly();
         return true;
      }
   }

   private boolean handleDig(String[] args) {
      if (args.length < 2 || !args[1].equalsIgnoreCase("baritone")) {
         Chat.warn("Only command left here is: .dig baritone");
         return true;
      } else if (!this.selection.isComplete()) {
         Chat.error("Set both positions first.");
         return true;
      } else {
         if (this.baritone.clearArea(this.selection.min(), this.selection.max())) {
            Chat.info("Started Baritone BuilderProcess.clearArea for selected area.");
         } else {
            Chat.error("Baritone API clearArea failed. Is Baritone installed and loaded?");
         }

         return true;
      }
   }

   private BlockPos getCommandPos(String[] args) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.player == null) {
         return null;
      } else {
         if (args.length >= 3 && args[2].equalsIgnoreCase("look")) {
            HitResult hit = mc.crosshairTarget;
            if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
               return blockHit.getBlockPos().toImmutable();
            }
         }

         return mc.player.getBlockPos().toImmutable();
      }
   }

   private void printSelectionInfo() {
      if (this.selection.isComplete()) {
         Chat.info("Selection ready. Volume: " + this.selection.volume() + " blocks.");
      }
   }

   private String format(BlockPos pos) {
      return pos.getX() + " " + pos.getY() + " " + pos.getZ();
   }
}
