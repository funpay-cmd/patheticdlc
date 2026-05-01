package com.pathdlc.digger.funtime;

import com.pathdlc.digger.gui.Module;
import com.pathdlc.digger.gui.ModuleManager;
import com.pathdlc.digger.gui.ModuleSetting;
import com.pathdlc.digger.util.Chat;
import net.minecraft.util.Hand;
import net.minecraft.item.Items;
import net.minecraft.block.Blocks;
import net.minecraft.block.Block;
import net.minecraft.block.CropBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.network.ClientPlayerEntity;

public class AutoFarmBot {
   private boolean running;
   private int tickCounter;

   public void start() {
      this.running = true;
      this.tickCounter = 0;
      Chat.info("AutoFarm started - harvesting/replanting crops nearby");
   }

   public void stop() {
      this.running = false;
      Chat.info("AutoFarm stopped");
   }

   public void tick(MinecraftClient client) {
      if (this.running) {
         if (!ModuleManager.isEnabled("AutoFarm")) {
            this.stop();
         } else if (client.player != null && client.world != null) {
            if (client.interactionManager != null) {
               this.tickCounter++;
               if (this.tickCounter >= 4) {
                  this.tickCounter = 0;
                  Module mod = ModuleManager.get("AutoFarm");
                  if (mod != null) {
                     ModuleSetting radiusSetting = mod.getSetting("Radius");
                     int radius = radiusSetting != null ? (int)radiusSetting.getFloat() : 4;
                     ClientPlayerEntity player = client.player;
                     ClientWorld world = client.world;
                     BlockPos playerPos = player.getBlockPos();

                     for (int x = -radius; x <= radius; x++) {
                        for (int z = -radius; z <= radius; z++) {
                           for (int y = -2; y <= 2; y++) {
                              BlockPos pos = playerPos.add(x, y, z);
                              BlockState state = world.getBlockState(pos);
                              Block block = state.getBlock();
                              if (block instanceof CropBlock crop && crop.isMature(state)) {
                                 client.interactionManager.attackBlock(pos, Direction.UP);
                                 return;
                              }

                              if (block == Blocks.SUGAR_CANE) {
                                 BlockPos below = pos.down();
                                 if (world.getBlockState(below).getBlock() == Blocks.SUGAR_CANE) {
                                    client.interactionManager.attackBlock(pos, Direction.UP);
                                    return;
                                 }
                              }

                              if (block == Blocks.MELON || block == Blocks.PUMPKIN) {
                                 client.interactionManager.attackBlock(pos, Direction.UP);
                                 return;
                              }

                              if (block == Blocks.FARMLAND || block == Blocks.SOUL_SAND) {
                                 BlockPos above = pos.up();
                                 if (world.getBlockState(above).isAir()) {
                                    boolean hasSeeds = player.getMainHandStack().getItem() == Items.WHEAT_SEEDS
                                       || player.getMainHandStack().getItem() == Items.POTATO
                                       || player.getMainHandStack().getItem() == Items.CARROT
                                       || player.getMainHandStack().getItem() == Items.BEETROOT_SEEDS
                                       || player.getMainHandStack().getItem() == Items.NETHER_WART;
                                    if (hasSeeds) {
                                       BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(above), Direction.UP, above, false);
                                       client.interactionManager.interactBlock(player, Hand.MAIN_HAND, hit);
                                       return;
                                    }
                                 }
                              }
                           }
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
