package com.pathdlc.digger.bot;

import com.pathdlc.digger.selection.SelectionManager;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import net.minecraft.util.math.BlockPos;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;

public final class TaskPlanner {
   private static final int MAX_TASKS = 120000;

   public static Queue<BlockTask> planDig(SelectionManager selection, boolean bulldozer2) {
      Queue<BlockTask> tasks = new ArrayDeque<>();
      if (!selection.isComplete()) {
         return tasks;
      } else {
         BlockPos min = selection.min();
         BlockPos max = selection.max();
         if (bulldozer2) {
            planBulldozerDig(tasks, min, max);
         } else {
            planNormalDig(tasks, min, max);
         }

         return tasks;
      }
   }

   public static Queue<BlockTask> planFillShell(SelectionManager selection, boolean bulldozer2) {
      Queue<BlockTask> tasks = new ArrayDeque<>();
      if (!selection.isComplete()) {
         return tasks;
      } else {
         MinecraftClient mc = MinecraftClient.getInstance();
         if (mc.world == null) {
            return tasks;
         } else {
            BlockPos min = selection.min();
            BlockPos max = selection.max();
            Set<BlockPos> unique = new HashSet<>();

            for (int y = min.getY(); y <= max.getY(); y++) {
               for (int z = min.getZ(); z <= max.getZ(); z++) {
                  addPlaceIfNeedsRepair(tasks, unique, new BlockPos(min.getX() - 1, y, z));
                  addPlaceIfNeedsRepair(tasks, unique, new BlockPos(max.getX() + 1, y, z));
               }

               for (int x = min.getX(); x <= max.getX(); x++) {
                  addPlaceIfNeedsRepair(tasks, unique, new BlockPos(x, y, min.getZ() - 1));
                  addPlaceIfNeedsRepair(tasks, unique, new BlockPos(x, y, max.getZ() + 1));
               }
            }

            int bottomY = min.getY() - 1;

            for (int x = min.getX(); x <= max.getX(); x++) {
               for (int z = min.getZ(); z <= max.getZ(); z++) {
                  addPlaceIfNeedsRepair(tasks, unique, new BlockPos(x, bottomY, z));
               }
            }

            if (bulldozer2) {
               int topY = max.getY() + 1;

               for (int x = min.getX(); x <= max.getX(); x++) {
                  addPlaceIfNeedsRepair(tasks, unique, new BlockPos(x, topY, min.getZ() - 1));
                  addPlaceIfNeedsRepair(tasks, unique, new BlockPos(x, topY, max.getZ() + 1));
               }

               for (int z = min.getZ(); z <= max.getZ(); z++) {
                  addPlaceIfNeedsRepair(tasks, unique, new BlockPos(min.getX() - 1, topY, z));
                  addPlaceIfNeedsRepair(tasks, unique, new BlockPos(max.getX() + 1, topY, z));
               }
            }

            return tasks;
         }
      }
   }

   private static void planNormalDig(Queue<BlockTask> tasks, BlockPos min, BlockPos max) {
      for (int y = max.getY(); y >= min.getY(); y--) {
         for (int x = min.getX(); x <= max.getX(); x++) {
            for (int z = min.getZ(); z <= max.getZ(); z++) {
               addMine(tasks, new BlockPos(x, y, z));
            }
         }
      }
   }

   private static void planBulldozerDig(Queue<BlockTask> tasks, BlockPos min, BlockPos max) {
      int startX = min.getX();
      int endX = max.getX();
      int startZ = min.getZ();
      int endZ = max.getZ();
      if (endX - startX >= 2) {
         startX = min.getX() + 1;
         endX = max.getX() - 1;
      }

      if (endZ - startZ >= 2) {
         startZ = min.getZ() + 1;
         endZ = max.getZ() - 1;
      }

      for (int y = max.getY(); y >= min.getY(); y--) {
         for (int x = startX; x <= endX; x += 3) {
            for (int z = startZ; z <= endZ; z += 3) {
               addMine(tasks, new BlockPos(x, y, z));
            }
         }
      }
   }

   private static void addMine(Queue<BlockTask> tasks, BlockPos pos) {
      if (tasks.size() < 120000) {
         tasks.add(new BlockTask(BlockTask.Type.MINE, pos));
      }
   }

   private static void addPlaceIfNeedsRepair(Queue<BlockTask> tasks, Set<BlockPos> unique, BlockPos pos) {
      if (tasks.size() < 120000 && !unique.contains(pos)) {
         MinecraftClient mc = MinecraftClient.getInstance();
         if (mc.world != null) {
            BlockState state = mc.world.getBlockState(pos);
            if (state.isAir() || !state.getFluidState().isEmpty()) {
               unique.add(pos.toImmutable());
               tasks.add(new BlockTask(BlockTask.Type.PLACE, pos));
            }
         }
      }
   }

   private TaskPlanner() {
   }
}
