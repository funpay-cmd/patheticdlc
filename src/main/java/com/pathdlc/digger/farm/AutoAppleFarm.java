package com.pathdlc.digger.farm;

import net.minecraft.util.hit.HitResult;
import com.pathdlc.digger.baritone.BaritoneBridge;
import com.pathdlc.digger.util.Chat;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.item.AxeItem;
import net.minecraft.item.HoeItem;
import net.minecraft.item.Items;
import net.minecraft.block.Blocks;
import net.minecraft.block.Block;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;


public class AutoAppleFarm extends AbstractFarmBot {
   private static final double TREE_REACH = 4.55;
   private static final int TREE_SCAN_HORIZONTAL = 4;
   private static final int TREE_SCAN_UP = 9;
   private boolean bonemeal = true;
   private BlockPos farmGround;
   private BlockPos currentBreakTarget;
   private final Set<BlockPos> skippedThisCycle = new HashSet<>();

   public AutoAppleFarm(BaritoneBridge baritone) {
      super(baritone);
   }

   @Override
   public String name() {
      return "AutoApple";
   }

   @Override
   public void start() {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.player != null && this.farmGround == null) {
         this.farmGround = mc.player.getBlockPos().down().toImmutable();
         Chat.info("AutoApple spot auto-set to block under you: " + this.shortPos(this.farmGround));
      }

      if (!this.validateRequiredItems(mc)) {
         this.running = false;
      } else if (!this.validateFarmSpot(mc)) {
         this.running = false;
      } else {
         this.resetTreeState();
         super.start();
      }
   }

   @Override
   public void tick(MinecraftClient mc) {
      if (this.running && this.worldReady(mc)) {
         this.cooldownTick();
         if (this.waitTicks <= 0) {
            if (!this.validateRequiredItems(mc)) {
               this.stop();
            } else if (!this.validateFarmSpot(mc)) {
               this.stop();
            } else {
               BlockPos saplingPos = this.farmGround.up();
               if (!this.breakReachableTreeBlocks(mc)) {
                  BlockState plantState = mc.world.getBlockState(saplingPos);
                  if (plantState.isOf(Blocks.OAK_SAPLING)) {
                     if (this.bonemeal) {
                        this.growSapling(mc, saplingPos);
                     } else {
                        this.waitTicks = 20;
                     }
                  } else if (plantState.isAir()) {
                     this.resetTreeState();
                     this.plantSapling(mc);
                  } else if (!this.isOakLog(plantState) && !this.isOakLeaf(plantState)) {
                     Chat.warn("AutoApple spot is blocked at " + this.shortPos(saplingPos) + ". Clear it or set another spot with .apple set.");
                     this.waitTicks = 60;
                  } else {
                     if (!FarmInteraction.inReach(mc, saplingPos, 4.55)) {
                        this.gotoIfNeeded(this.farmGround);
                     }

                     this.waitTicks = 8;
                  }
               }
            }
         }
      }
   }

   public void setSpotAtPlayer() {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.player == null) {
         Chat.error("Join a world first.");
      } else {
         this.farmGround = mc.player.getBlockPos().down().toImmutable();
         this.resetTreeState();
         Chat.info("AutoApple spot = " + this.shortPos(this.farmGround) + " / plant at " + this.shortPos(this.farmGround.up()));
      }
   }

   public void setSpotAtLook() {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.player != null && mc.crosshairTarget != null) {
         if (mc.crosshairTarget instanceof BlockHitResult hit && mc.crosshairTarget.getType() == HitResult.Type.BLOCK) {
            this.farmGround = hit.getBlockPos().toImmutable();
            this.resetTreeState();
            Chat.info("AutoApple spot = " + this.shortPos(this.farmGround) + " / plant at " + this.shortPos(this.farmGround.up()));
            return;
         }

         Chat.warn("Look at a block first.");
      } else {
         Chat.error("Join a world first.");
      }
   }

   public void clearSpot() {
      this.farmGround = null;
      this.resetTreeState();
      Chat.info("AutoApple spot cleared.");
   }

   public BlockPos getFarmGround() {
      return this.farmGround;
   }

   private boolean breakReachableTreeBlocks(MinecraftClient mc) {
      if (this.currentBreakTarget == null
         || mc.world.getBlockState(this.currentBreakTarget).isAir()
         || this.skippedThisCycle.contains(this.currentBreakTarget)
         || !FarmInteraction.inReach(mc, this.currentBreakTarget, 4.55)) {
         this.currentBreakTarget = this.findReachableTreeTarget(mc);
      }

      if (this.currentBreakTarget == null) {
         return false;
      } else {
         BlockState targetState = mc.world.getBlockState(this.currentBreakTarget);
         boolean leaf = this.isOakLeaf(targetState);
         boolean log = this.isOakLog(targetState);
         if (!leaf && !log) {
            this.markSkipped(this.currentBreakTarget);
            this.currentBreakTarget = null;
            return true;
         } else if (!FarmInteraction.inReach(mc, this.currentBreakTarget, 4.55)) {
            this.markSkipped(this.currentBreakTarget);
            this.currentBreakTarget = null;
            return true;
         } else {
            this.baritone.cancel();
            if (log) {
               if (!InventoryUtil.selectMatching(mc, stack -> stack.getItem() instanceof AxeItem, 0)) {
                  Chat.error("AutoApple needs an axe in inventory.");
                  this.stop();
                  return true;
               }
            } else if (!InventoryUtil.selectMatching(mc, stack -> stack.getItem() instanceof HoeItem, 1)) {
               Chat.error("AutoApple needs a hoe in inventory.");
               this.stop();
               return true;
            }

            boolean done = FarmInteraction.breakBlock(mc, this.currentBreakTarget);
            if (done || mc.world.getBlockState(this.currentBreakTarget).isAir()) {
               this.actions++;
               this.currentBreakTarget = null;
            }

            return true;
         }
      }
   }

   private void growSapling(MinecraftClient mc, BlockPos saplingPos) {
      if (!InventoryUtil.selectItem(mc, Items.BONE_MEAL, 3)) {
         Chat.error("AutoApple needs bone meal in inventory.");
         this.stop();
      } else if (!FarmInteraction.inReach(mc, saplingPos, 4.6)) {
         this.gotoIfNeeded(saplingPos);
      } else {
         this.baritone.cancel();

         for (int i = 0; i < 4; i++) {
            FarmInteraction.interactBlock(mc, saplingPos, Direction.UP, Items.BONE_MEAL);
         }

         this.actions++;
         this.waitTicks = 5;
      }
   }

   private void plantSapling(MinecraftClient mc) {
      if (!InventoryUtil.selectItem(mc, Items.OAK_SAPLING, 2)) {
         Chat.error("AutoApple needs oak saplings in inventory.");
         this.stop();
      } else if (!FarmInteraction.inReach(mc, this.farmGround, 4.6)) {
         this.gotoIfNeeded(this.farmGround);
      } else {
         this.baritone.cancel();
         FarmInteraction.interactBlock(mc, this.farmGround, Direction.UP, Items.OAK_SAPLING);
         this.actions++;
         this.waitTicks = 8;
      }
   }

   private BlockPos findReachableTreeTarget(MinecraftClient mc) {
      if (this.farmGround != null && mc.world != null && mc.player != null) {
         BlockPos plant = this.farmGround.up();
         BlockPos log = this.scanReachableAroundSpot(mc, plant, true);
         return log != null ? log : this.scanReachableAroundSpot(mc, plant, false);
      } else {
         return null;
      }
   }

   private BlockPos scanReachableAroundSpot(MinecraftClient mc, BlockPos plant, boolean logsOnly) {
      BlockPos best = null;
      double bestScore = Double.MAX_VALUE;

      for (int y = 0; y <= 9; y++) {
         for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
               BlockPos pos = plant.add(x, y, z).toImmutable();
               if (!this.skippedThisCycle.contains(pos) && FarmInteraction.inReach(mc, pos, 4.55)) {
                  BlockState state = mc.world.getBlockState(pos);
                  boolean match = logsOnly ? this.isOakLog(state) : this.isOakLeaf(state);
                  if (match) {
                     double heightPenalty = (double)y * 3.0;
                     double distance = mc.player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(pos));
                     double score = distance + heightPenalty;
                     if (score < bestScore) {
                        bestScore = score;
                        best = pos;
                     }
                  }
               }
            }
         }
      }

      return best;
   }

   private void markSkipped(BlockPos pos) {
      if (pos != null) {
         this.skippedThisCycle.add(pos.toImmutable());
         this.failed++;
      }
   }

   private boolean validateRequiredItems(MinecraftClient mc) {
      if (!this.worldReady(mc)) {
         return false;
      } else if (!InventoryUtil.hasMatching(mc, stack -> stack.getItem() instanceof AxeItem)) {
         Chat.error("AutoApple will not start: axe missing in inventory.");
         return false;
      } else if (!InventoryUtil.hasMatching(mc, stack -> stack.getItem() instanceof HoeItem)) {
         Chat.error("AutoApple will not start: hoe missing in inventory.");
         return false;
      } else if (!InventoryUtil.hasItem(mc, Items.OAK_SAPLING)) {
         Chat.error("AutoApple will not start: oak sapling missing in inventory.");
         return false;
      } else if (this.bonemeal && !InventoryUtil.hasItem(mc, Items.BONE_MEAL)) {
         Chat.error("AutoApple will not start: bone meal missing in inventory.");
         return false;
      } else {
         return true;
      }
   }

   private boolean validateFarmSpot(MinecraftClient mc) {
      if (this.farmGround == null) {
         Chat.error("AutoApple spot is not set. Use .apple set or stand on the spot and run .apple start.");
         return false;
      } else if (mc.world == null) {
         return false;
      } else {
         BlockState ground = mc.world.getBlockState(this.farmGround);
         if (!this.isValidSaplingGround(ground)) {
            Chat.error("AutoApple spot ground is invalid: " + this.shortPos(this.farmGround) + ". Use dirt/grass/podzol/coarse dirt.");
            return false;
         } else {
            return true;
         }
      }
   }

   private boolean isValidSaplingGround(BlockState state) {
      Block block = state.getBlock();
      return block == Blocks.GRASS_BLOCK
         || block == Blocks.DIRT
         || block == Blocks.COARSE_DIRT
         || block == Blocks.PODZOL
         || block == Blocks.ROOTED_DIRT
         || block == Blocks.MOSS_BLOCK;
   }

   private boolean isOakLog(BlockState state) {
      Block block = state.getBlock();
      return block == Blocks.OAK_LOG || block == Blocks.OAK_WOOD || block == Blocks.STRIPPED_OAK_LOG || block == Blocks.STRIPPED_OAK_WOOD;
   }

   private boolean isOakLeaf(BlockState state) {
      return state.isOf(Blocks.OAK_LEAVES);
   }

   private void resetTreeState() {
      this.currentBreakTarget = null;
      this.skippedThisCycle.clear();
   }

   public boolean isBonemeal() {
      return this.bonemeal;
   }

   public void setBonemeal(boolean bonemeal) {
      this.bonemeal = bonemeal;
   }

   @Override
   public String status() {
      return super.status()
         + ", bonemeal="
         + this.bonemeal
         + ", spot="
         + (this.farmGround == null ? "not set" : this.shortPos(this.farmGround))
         + ", skipped="
         + this.skippedThisCycle.size()
         + ", saplings="
         + InventoryUtil.countItem(MinecraftClient.getInstance(), Items.OAK_SAPLING)
         + ", bonemealCount="
         + InventoryUtil.countItem(MinecraftClient.getInstance(), Items.BONE_MEAL);
   }

   private String shortPos(BlockPos pos) {
      return pos.getX() + " " + pos.getY() + " " + pos.getZ();
   }
}
