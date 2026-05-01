package com.pathdlc.digger.warden;

import com.pathdlc.digger.baritone.BaritoneBridge;
import com.pathdlc.digger.util.Chat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.util.Hand;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.item.ItemStack;
import net.minecraft.block.Blocks;
import net.minecraft.block.Block;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;

public class AutoWardenBot {
   private static final double OPEN_REACH = 4.65;
   private static final int DEFAULT_RADIUS = 48;
   private static final int DEFAULT_VERTICAL_RADIUS = 16;
   private static final int SCAN_EVERY_TICKS = 18;
   private static final int PATH_EVERY_TICKS = 45;
   private static final int CHEST_OPEN_WAIT_TICKS = 10;
   private static final int LOOT_BURST_PER_TICK = 18;
   private final BaritoneBridge baritone;
   private boolean running;
   private int radius = 48;
   private int verticalRadius = 16;
   private int scanCooldown;
   private int pathCooldown;
   private int openWaitTicks;
   private int lootedChests;
   private int failed;
   private WardenChestTarget target;

   public AutoWardenBot(BaritoneBridge baritone) {
      this.baritone = baritone;
   }

   public void start() {
      this.running = true;
      this.scanCooldown = 0;
      this.pathCooldown = 0;
      this.openWaitTicks = 0;
      this.lootedChests = 0;
      this.failed = 0;
      this.target = null;
      Chat.info("AutoWarden started. Radius=" + this.radius + ", vertical=" + this.verticalRadius);
   }

   public void stop() {
      this.running = false;
      this.target = null;
      this.baritone.cancel();
      Chat.info("AutoWarden stopped.");
   }

   public void tick(MinecraftClient mc) {
      if (this.running) {
         if (mc.player != null && mc.world != null && mc.interactionManager != null) {
            if (this.scanCooldown > 0) {
               this.scanCooldown--;
            }

            if (this.pathCooldown > 0) {
               this.pathCooldown--;
            }

            if (this.openWaitTicks > 0) {
               this.openWaitTicks--;
            } else if (this.isContainerOpen(mc)) {
               this.lootOpenContainer(mc);
            } else {
               if (this.target == null || this.scanCooldown <= 0 || !this.isChestStillValid(mc, this.target.pos())) {
                  this.target = this.scanBestChest(mc);
                  this.scanCooldown = 18;
               }

               if (this.target != null) {
                  if (!this.inReach(mc, this.target.pos(), 4.65)) {
                     this.requestPath(this.target.pos());
                  } else {
                     this.baritone.cancel();
                     if (!this.target.hasTimer() || !(this.target.timerSeconds() > 1.25)) {
                        this.openChest(mc, this.target.pos());
                     }
                  }
               }
            }
         }
      }
   }

   private WardenChestTarget scanBestChest(MinecraftClient mc) {
      BlockPos origin = mc.player.getBlockPos();
      List<WardenChestTarget> targets = new ArrayList<>();
      int minY = Math.max(mc.world.getBottomY(), origin.getY() - this.verticalRadius);
      int maxY = Math.min(mc.world.getTopYInclusive(), origin.getY() + this.verticalRadius);

      for (int y = minY; y <= maxY; y++) {
         for (int x = origin.getX() - this.radius; x <= origin.getX() + this.radius; x++) {
            for (int z = origin.getZ() - this.radius; z <= origin.getZ() + this.radius; z++) {
               BlockPos pos = new BlockPos(x, y, z);
               BlockState state = mc.world.getBlockState(pos);
               if (this.isLootChestBlock(state.getBlock())) {
                  double timer = this.readTimerNear(mc, pos);
                  boolean hasTimer = timer >= 0.0;
                  double distance = origin.getSquaredDistance(pos);
                  double score = (hasTimer ? timer : 9999.0) * 100000.0 + distance;
                  targets.add(new WardenChestTarget(pos.toImmutable(), timer, hasTimer, score));
               }
            }
         }
      }

      return targets.stream().min(Comparator.comparingDouble(WardenChestTarget::score)).orElse(null);
   }

   private double readTimerNear(MinecraftClient mc, BlockPos chestPos) {
      double best = -1.0;

      for (Entity entity : mc.world.getEntities()) {
         if ((entity instanceof ArmorStandEntity || entity.hasCustomName()) && !(entity.squaredDistanceTo(Vec3d.ofCenter(chestPos)) > 25.0)) {
            String text = entity.getDisplayName().getString();
            double parsed = WardenTimerParser.parseSeconds(text);
            if (!(parsed < 0.0) && (best < 0.0 || parsed < best)) {
               best = parsed;
            }
         }
      }

      return best;
   }

   private boolean isLootChestBlock(Block block) {
      return block == Blocks.CHEST || block == Blocks.TRAPPED_CHEST || block == Blocks.BARREL;
   }

   private boolean isChestStillValid(MinecraftClient mc, BlockPos pos) {
      return pos != null && this.isLootChestBlock(mc.world.getBlockState(pos).getBlock());
   }

   private void requestPath(BlockPos pos) {
      if (this.pathCooldown <= 0) {
         this.baritone.gotoBlock(pos);
         this.pathCooldown = 45;
      }
   }

   private void openChest(MinecraftClient mc, BlockPos pos) {
      Vec3d hitVec = Vec3d.ofCenter(pos);
      this.lookAt(mc, hitVec);
      BlockHitResult hit = new BlockHitResult(hitVec, Direction.UP, pos, false);
      mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
      mc.player.swingHand(Hand.MAIN_HAND);
      this.openWaitTicks = 10;
   }

   private boolean isContainerOpen(MinecraftClient mc) {
      return mc.player == null ? false : mc.player.currentScreenHandler != mc.player.playerScreenHandler;
   }

   private void lootOpenContainer(MinecraftClient mc) {
      ScreenHandler handler = mc.player.currentScreenHandler;
      int totalSlots = handler.slots.size();
      int containerSlots = Math.max(0, totalSlots - 36);
      int moved = 0;

      for (int slot = 0; slot < containerSlots && moved < 18; slot++) {
         ItemStack stack = handler.getSlot(slot).getStack();
         if (!stack.isEmpty()) {
            mc.interactionManager.clickSlot(handler.syncId, slot, 0, SlotActionType.QUICK_MOVE, mc.player);
            moved++;
         }
      }

      if (moved == 0) {
         mc.player.closeHandledScreen();
         this.lootedChests++;
         this.target = null;
         this.scanCooldown = 0;
      }
   }

   private boolean inReach(MinecraftClient mc, BlockPos pos, double reach) {
      return mc.player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(pos)) <= reach * reach;
   }

   private void lookAt(MinecraftClient mc, Vec3d target) {
      Vec3d eye = mc.player.getEyePos();
      double dx = target.x - eye.x;
      double dy = target.y - eye.y;
      double dz = target.z - eye.z;
      double horizontal = Math.sqrt(dx * dx + dz * dz);
      float yaw = (float)Math.toDegrees(Math.atan2(dz, dx)) - 90.0F;
      float pitch = (float)(-Math.toDegrees(Math.atan2(dy, horizontal)));
      mc.player.setYaw(yaw);
      mc.player.setPitch(pitch);
   }

   public boolean isRunning() {
      return this.running;
   }

   public int getRadius() {
      return this.radius;
   }

   public void setRadius(int radius) {
      this.radius = Math.max(8, Math.min(96, radius));
      Chat.info("AutoWarden radius set to " + this.radius);
   }

   public int getVerticalRadius() {
      return this.verticalRadius;
   }

   public void setVerticalRadius(int verticalRadius) {
      this.verticalRadius = Math.max(4, Math.min(32, verticalRadius));
      Chat.info("AutoWarden vertical radius set to " + this.verticalRadius);
   }

   public String status() {
      return "autoWarden running="
         + this.running
         + ", radius="
         + this.radius
         + ", vertical="
         + this.verticalRadius
         + ", target="
         + (this.target == null ? "none" : this.target.pos().toShortString() + " timer=" + (this.target.hasTimer() ? this.target.timerSeconds() : "unknown"))
         + ", looted="
         + this.lootedChests
         + ", failed="
         + this.failed;
   }
}
