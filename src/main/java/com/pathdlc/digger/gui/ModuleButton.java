package com.pathdlc.digger.gui;

public class ModuleButton {
   private final Module module;
   public float hoverAmount;

   public ModuleButton(Module module) {
      this.module = module;
   }

   public Module getModule() {
      return this.module;
   }

   public void updateHover(boolean isHovered) {
      float target = isHovered ? 1.0F : 0.0F;
      this.hoverAmount = this.hoverAmount + (target - this.hoverAmount) * 0.15F;
   }
}
