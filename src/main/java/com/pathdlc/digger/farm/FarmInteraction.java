package com.pathdlc.digger.farm;

import net.minecraft.util.Hand;
import net.minecraft.item.Item;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;

public final class FarmInteraction {
   public static boolean inReach(MinecraftClient mc, BlockPos pos, double reach) {
      return mc.player != null && mc.player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(pos)) <= reach * reach;
   }

   public static boolean selectHotbar(MinecraftClient mc, Item item) {
      return InventoryUtil.selectItem(mc, item, 2);
   }

   public static boolean breakBlock(MinecraftClient mc, BlockPos pos) {
      if (mc.player != null && mc.world != null && mc.interactionManager != null) {
         BlockState state = mc.world.getBlockState(pos);
         if (state.isAir()) {
            return true;
         } else {
            lookAt(mc, Vec3d.ofCenter(pos));
            Direction side = bestSide(mc, pos);
            mc.interactionManager.updateBlockBreakingProgress(pos, side);
            mc.player.swingHand(Hand.MAIN_HAND);
            return false;
         }
      } else {
         return false;
      }
   }

   public static boolean interactBlock(MinecraftClient mc, BlockPos blockPos, Direction side, Item item) {
      if (mc.player == null || mc.world == null || mc.interactionManager == null) {
         return false;
      } else if (!selectHotbar(mc, item)) {
         return false;
      } else {
         Vec3d hitVec = Vec3d.ofCenter(blockPos).add(Vec3d.of(side.getVector()).multiply(0.5));
         lookAt(mc, hitVec);
         BlockHitResult hit = new BlockHitResult(hitVec, side, blockPos, false);
         mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
         mc.player.swingHand(Hand.MAIN_HAND);
         return true;
      }
   }

   public static Direction bestSide(MinecraftClient mc, BlockPos pos) {
      Vec3d eye = mc.player.getEyePos();
      Vec3d center = Vec3d.ofCenter(pos);
      Vec3d diff = center.subtract(eye);
      return Direction.getFacing(diff.x, diff.y, diff.z).getOpposite();
   }

   public static void lookAt(MinecraftClient mc, Vec3d target) {
      if (mc.player != null) {
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
   }

   private FarmInteraction() {
   }
}
