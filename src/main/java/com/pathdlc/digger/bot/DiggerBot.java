package com.pathdlc.digger.bot;

import com.pathdlc.digger.baritone.BaritoneBridge;
import com.pathdlc.digger.selection.SelectionManager;
import com.pathdlc.digger.util.Chat;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.Queue;
import net.minecraft.util.Hand;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;


public class DiggerBot {
   private static final double MINE_REACH = 4.65;
   private static final double PLACE_REACH = 4.65;
   private static final int PLACE_BURST_PER_TICK = 4;
   private static final int PATH_COOLDOWN_TICKS = 34;
   private static final int MAX_PLACE_RETRIES = 10;
   private final SelectionManager selection;
   private final BaritoneBridge baritone;
   private final Queue<BlockTask> tasks = new ArrayDeque<>();
   private BlockTask currentTask;
   private boolean running;
   private boolean bulldozer2;
   private int taskTicks;
   private int completedTasks;
   private int failedTasks;
   private int pathCooldown;

   public DiggerBot(SelectionManager selection, BaritoneBridge baritone) {
      this.selection = selection;
      this.baritone = baritone;
   }

   public void startDigAndFill() {
      this.tasks.clear();
      this.tasks.addAll(TaskPlanner.planDig(this.selection, this.bulldozer2));
      this.tasks.addAll(TaskPlanner.planFillShell(this.selection, this.bulldozer2));
      this.start("Fast dig + fast shell repair planned. Tasks: " + this.tasks.size());
   }

   public void startDigOnly() {
      this.tasks.clear();
      this.tasks.addAll(TaskPlanner.planDig(this.selection, this.bulldozer2));
      this.start("Dig planned. Tasks: " + this.tasks.size());
   }

   public void startFillOnly() {
      this.tasks.clear();
      this.tasks.addAll(TaskPlanner.planFillShell(this.selection, this.bulldozer2));
      this.start("Fast shell repair planned. Tasks: " + this.tasks.size());
   }

   public void stop() {
      this.running = false;
      this.tasks.clear();
      this.currentTask = null;
      this.taskTicks = 0;
      this.pathCooldown = 0;
   }

   public void tick(MinecraftClient mc) {
      Chat.flush();
      if (this.running) {
         if (mc.player != null && mc.world != null && mc.interactionManager != null) {
            if (this.pathCooldown > 0) {
               this.pathCooldown--;
            }

            int placed = this.placeReachableBurst(mc);
            if (placed > 0 && this.currentTask != null && this.currentTask.type == BlockTask.Type.PLACE) {
               this.currentTask = null;
            }

            if (this.currentTask == null) {
               this.currentTask = this.pollNextSmartTask(mc);
               this.taskTicks = 0;
               if (this.currentTask == null) {
                  this.running = false;
                  this.baritone.cancel();
                  Chat.info("Done. Completed: " + this.completedTasks + ", failed/skipped: " + this.failedTasks);
                  return;
               }
            }

            this.taskTicks++;
            if (this.taskTicks > 520) {
               this.failedTasks++;
               this.requeueOrDrop(this.currentTask);
               this.currentTask = null;
               this.baritone.cancel();
            } else {
               if (this.currentTask.type == BlockTask.Type.PLACE) {
                  this.tickPlaceTask(mc, this.currentTask);
               } else {
                  this.tickMineTask(mc, this.currentTask);
               }
            }
         }
      }
   }

   private void start(String message) {
      this.currentTask = null;
      this.running = true;
      this.completedTasks = 0;
      this.failedTasks = 0;
      this.taskTicks = 0;
      this.pathCooldown = 0;
      if (this.tasks.isEmpty()) {
         this.running = false;
         Chat.warn("No tasks generated. Check selection.");
      } else {
         Chat.info(message);
         Chat.info("Bulldozer 2 logic: " + (this.bulldozer2 ? "ON" : "OFF"));
         Chat.info("Baritone API: " + (this.baritone.isAvailable() ? "found" : "not found"));
      }
   }

   private BlockTask pollNextSmartTask(MinecraftClient mc) {
      this.removeAlreadyDoneTasks(mc);
      BlockTask reachablePlace = this.removeReachablePlaceTask(mc);
      if (reachablePlace != null) {
         return reachablePlace;
      } else {
         BlockTask reachableMine = this.removeReachableMineTask(mc);
         return reachableMine != null ? reachableMine : this.removeNearestTask(mc);
      }
   }

   private void removeAlreadyDoneTasks(MinecraftClient mc) {
      Iterator<BlockTask> iterator = this.tasks.iterator();

      while (iterator.hasNext()) {
         BlockTask task = iterator.next();
         if (this.isTaskAlreadyDone(mc, task)) {
            this.failedTasks++;
            iterator.remove();
         }
      }
   }

   private boolean isTaskAlreadyDone(MinecraftClient mc, BlockTask task) {
      BlockState state = mc.world.getBlockState(task.pos);
      return task.type == BlockTask.Type.MINE
         ? state.isAir() || state.getBlock() == Blocks.BEDROCK
         : !state.isAir() && state.getFluidState().isEmpty();
   }

   private BlockTask removeReachablePlaceTask(MinecraftClient mc) {
      Iterator<BlockTask> iterator = this.tasks.iterator();

      while (iterator.hasNext()) {
         BlockTask task = iterator.next();
         if (task.type == BlockTask.Type.PLACE && this.isInReach(mc, task.pos, 4.65) && this.findPlacementFace(mc, task.pos) != null) {
            iterator.remove();
            return task;
         }
      }

      return null;
   }

   private BlockTask removeReachableMineTask(MinecraftClient mc) {
      Iterator<BlockTask> iterator = this.tasks.iterator();

      while (iterator.hasNext()) {
         BlockTask task = iterator.next();
         if (task.type == BlockTask.Type.MINE && this.isInReach(mc, task.pos, 4.65)) {
            iterator.remove();
            return task;
         }
      }

      return null;
   }

   private BlockTask removeNearestTask(MinecraftClient mc) {
      Iterator<BlockTask> iterator = this.tasks.iterator();
      BlockTask best = null;
      double bestDistance = Double.MAX_VALUE;

      while (iterator.hasNext()) {
         BlockTask task = iterator.next();
         double distance = mc.player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(task.pos));
         if (distance < bestDistance) {
            bestDistance = distance;
            best = task;
         }
      }

      if (best == null) {
         return null;
      } else {
         this.tasks.remove(best);
         return best;
      }
   }

   private int placeReachableBurst(MinecraftClient mc) {
      int placed = 0;

      for (int i = 0; i < 4; i++) {
         BlockTask task = this.removeReachablePlaceTask(mc);
         if (task == null) {
            break;
         }

         if (this.tryPlace(mc, task)) {
            placed++;
         } else {
            this.requeueOrDrop(task);
         }
      }

      return placed;
   }

   private void tickMineTask(MinecraftClient mc, BlockTask task) {
      BlockState state = mc.world.getBlockState(task.pos);
      if (state.isAir() || state.getBlock() == Blocks.BEDROCK) {
         this.completedTasks++;
         this.currentTask = null;
      } else if (!this.isInReach(mc, task.pos, 4.65)) {
         this.requestPathTo(task.pos);
      } else {
         this.baritone.cancel();
         this.lookAt(mc, Vec3d.ofCenter(task.pos));
         Direction side = this.bestSide(mc, task.pos);
         mc.interactionManager.updateBlockBreakingProgress(task.pos, side);
         mc.player.swingHand(Hand.MAIN_HAND);
      }
   }

   private void tickPlaceTask(MinecraftClient mc, BlockTask task) {
      if (this.isTaskAlreadyDone(mc, task)) {
         this.completedTasks++;
         this.currentTask = null;
      } else if (!this.selectFillBlock(mc)) {
         Chat.error("No cobblestone/stone in hotbar. Put blocks in hotbar and run .fill again.");
         this.stop();
      } else if (!this.isInReach(mc, task.pos, 4.65)) {
         this.requestPathTo(this.findNearbyGoal(task.pos));
      } else if (this.tryPlace(mc, task)) {
         this.currentTask = null;
      } else {
         this.requeueOrDrop(task);
         this.currentTask = null;
      }
   }

   private boolean tryPlace(MinecraftClient mc, BlockTask task) {
      if (!this.selectFillBlock(mc)) {
         return false;
      } else if (this.isTaskAlreadyDone(mc, task)) {
         this.completedTasks++;
         return true;
      } else {
         DiggerBot.PlacementFace face = this.findPlacementFace(mc, task.pos);
         if (face == null) {
            return false;
         } else {
            Vec3d hitVec = Vec3d.ofCenter(face.neighbor).add(Vec3d.of(face.side.getVector()).multiply(0.5));
            this.lookAt(mc, hitVec);
            BlockHitResult hit = new BlockHitResult(hitVec, face.side, face.neighbor, false);
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
            mc.player.swingHand(Hand.MAIN_HAND);
            this.completedTasks++;
            return true;
         }
      }
   }

   private void requestPathTo(BlockPos pos) {
      if (this.pathCooldown <= 0 && pos != null) {
         this.baritone.gotoBlock(pos);
         this.pathCooldown = 34;
      }
   }

   private void requeueOrDrop(BlockTask task) {
      if (task != null) {
         task.retries++;
         if (task.type == BlockTask.Type.PLACE && task.retries <= 10) {
            this.tasks.add(task);
         } else {
            this.failedTasks++;
         }
      }
   }

   private BlockPos findNearbyGoal(BlockPos pos) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.world == null) {
         return pos;
      } else {
         BlockPos best = null;
         double bestDist = Double.MAX_VALUE;

         for (Direction direction : Direction.Type.HORIZONTAL) {
            BlockPos candidate = pos.offset(direction);
            BlockPos head = candidate.up();
            if (mc.world.getBlockState(candidate).isAir() && mc.world.getBlockState(head).isAir()) {
               double dist = mc.player == null ? 0.0 : mc.player.getBlockPos().getSquaredDistance(candidate);
               if (dist < bestDist) {
                  bestDist = dist;
                  best = candidate;
               }
            }
         }

         if (best != null) {
            return best;
         } else {
            BlockPos above = pos.up();
            return mc.world.getBlockState(above).isAir() ? above : pos;
         }
      }
   }

   private boolean isInReach(MinecraftClient mc, BlockPos pos, double reach) {
      return mc.player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(pos)) <= reach * reach;
   }

   private boolean selectFillBlock(MinecraftClient mc) {
      int slot = this.findHotbarBlock(mc, Items.COBBLESTONE);
      if (slot < 0) {
         slot = this.findHotbarBlock(mc, Items.STONE);
      }

      if (slot < 0) {
         return false;
      } else {
         mc.player.getInventory().selectedSlot = slot;
         return true;
      }
   }

   private int findHotbarBlock(MinecraftClient mc, Item item) {
      for (int slot = 0; slot < 9; slot++) {
         ItemStack stack = mc.player.getInventory().getStack(slot);
         if (!stack.isEmpty() && stack.isOf(item)) {
            return slot;
         }
      }

      return -1;
   }

   private DiggerBot.PlacementFace findPlacementFace(MinecraftClient mc, BlockPos target) {
      for (Direction direction : Direction.values()) {
         BlockPos neighbor = target.offset(direction);
         BlockState neighborState = mc.world.getBlockState(neighbor);
         if (!neighborState.isAir() && neighborState.getFluidState().isEmpty()) {
            return new DiggerBot.PlacementFace(neighbor, direction.getOpposite());
         }
      }

      return null;
   }

   private Direction bestSide(MinecraftClient mc, BlockPos pos) {
      Vec3d eye = mc.player.getEyePos();
      Vec3d center = Vec3d.ofCenter(pos);
      Vec3d diff = center.subtract(eye);
      return Direction.getFacing(diff.x, diff.y, diff.z).getOpposite();
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

   public boolean isBulldozer2() {
      return this.bulldozer2;
   }

   public void setBulldozer2(boolean bulldozer2) {
      this.bulldozer2 = bulldozer2;
   }

   public String statusLine() {
      return "running="
         + this.running
         + ", queued="
         + this.tasks.size()
         + ", completed="
         + this.completedTasks
         + ", failed/skipped="
         + this.failedTasks
         + ", bulldozer2="
         + this.bulldozer2
         + ", current="
         + (this.currentTask == null ? "none" : this.currentTask.type + " " + this.currentTask.pos.toShortString() + " retries=" + this.currentTask.retries);
   }

   private static record PlacementFace(BlockPos neighbor, Direction side) {
   }
}
