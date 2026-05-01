package com.pathdlc.digger.selection;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

public class SelectionManager {
   private BlockPos pos1;
   private BlockPos pos2;

   public BlockPos getPos1() {
      return this.pos1;
   }

   public BlockPos getPos2() {
      return this.pos2;
   }

   public void setPos1(BlockPos pos1) {
      this.pos1 = pos1 == null ? null : pos1.toImmutable();
   }

   public void setPos2(BlockPos pos2) {
      this.pos2 = pos2 == null ? null : pos2.toImmutable();
   }

   public void clear() {
      this.pos1 = null;
      this.pos2 = null;
   }

   public boolean isComplete() {
      return this.pos1 != null && this.pos2 != null;
   }

   public BlockPos min() {
      return !this.isComplete()
         ? null
         : new BlockPos(
            Math.min(this.pos1.getX(), this.pos2.getX()),
            Math.min(this.pos1.getY(), this.pos2.getY()),
            Math.min(this.pos1.getZ(), this.pos2.getZ())
         );
   }

   public BlockPos max() {
      return !this.isComplete()
         ? null
         : new BlockPos(
            Math.max(this.pos1.getX(), this.pos2.getX()),
            Math.max(this.pos1.getY(), this.pos2.getY()),
            Math.max(this.pos1.getZ(), this.pos2.getZ())
         );
   }

   public int volume() {
      if (!this.isComplete()) {
         return 0;
      } else {
         BlockPos min = this.min();
         BlockPos max = this.max();
         return (max.getX() - min.getX() + 1) * (max.getY() - min.getY() + 1) * (max.getZ() - min.getZ() + 1);
      }
   }

   public Box outlineBox() {
      if (!this.isComplete()) {
         return null;
      } else {
         BlockPos min = this.min();
         BlockPos max = this.max();
         return new Box(
            (double)min.getX(),
            (double)min.getY(),
            (double)min.getZ(),
            (double)max.getX() + 1.0,
            (double)max.getY() + 1.0,
            (double)max.getZ() + 1.0
         );
      }
   }
}
