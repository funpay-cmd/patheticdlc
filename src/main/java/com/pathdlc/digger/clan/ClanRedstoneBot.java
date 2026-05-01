package com.pathdlc.digger.clan;

import com.pathdlc.digger.farm.FarmInteraction;
import com.pathdlc.digger.farm.InventoryUtil;
import com.pathdlc.digger.util.Chat;
import net.minecraft.item.Items;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.client.MinecraftClient;

public class ClanRedstoneBot {
   private boolean running;
   private int actions;
   private int failed;
   private BlockPos lastWirePos;

   public void start() {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (this.hasRequiredRedstone(mc)) {
         this.running = true;
         this.actions = 0;
         this.failed = 0;
         this.lastWirePos = null;
         Chat.info("Clan redstone started. No artificial delay.");
      }
   }

   public void stop() {
      this.running = false;
      this.lastWirePos = null;
      Chat.info("Clan redstone stopped.");
   }

   public void tick(MinecraftClient mc) {
      if (this.running) {
         if (mc.player != null && mc.world != null && mc.interactionManager != null) {
            if (!this.hasRequiredRedstone(mc)) {
               this.stop();
            } else {
               BlockPos wirePos = this.lastWirePos != null ? this.lastWirePos : mc.player.getBlockPos();
               if (mc.world.getBlockState(wirePos).isOf(Blocks.REDSTONE_WIRE)) {
                  FarmInteraction.breakBlock(mc, wirePos);
                  this.actions++;
                  this.lastWirePos = null;
               } else {
                  BlockPos playerFeet = mc.player.getBlockPos();
                  BlockPos support = playerFeet.down();
                  if (!mc.world.getBlockState(playerFeet).isAir()) {
                     this.failed++;
                  } else if (mc.world.getBlockState(support).isAir() || !mc.world.getBlockState(support).getFluidState().isEmpty()) {
                     this.failed++;
                  } else if (!InventoryUtil.selectItem(mc, Items.REDSTONE, 4)) {
                     Chat.error("Clan redstone needs redstone dust in inventory.");
                     this.stop();
                  } else {
                     FarmInteraction.interactBlock(mc, support, Direction.UP, Items.REDSTONE);
                     this.lastWirePos = playerFeet.toImmutable();
                     this.actions++;
                  }
               }
            }
         }
      }
   }

   public boolean isRunning() {
      return this.running;
   }

   public String status() {
      return "clanRedstone running=" + this.running + ", actions=" + this.actions + ", failed=" + this.failed + ", mode=fast-no-delay";
   }

   private boolean hasRequiredRedstone(MinecraftClient mc) {
      if (mc == null || mc.player == null) {
         return false;
      } else if (!InventoryUtil.hasItem(mc, Items.REDSTONE)) {
         Chat.error("Clan redstone needs redstone dust in inventory.");
         return false;
      } else {
         return true;
      }
   }
}
