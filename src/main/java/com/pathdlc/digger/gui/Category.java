package com.pathdlc.digger.gui;

import java.util.ArrayList;
import java.util.List;

public class Category {
   private final String name;
   private final List<ModuleButton> modules = new ArrayList<>();
   public float x;
   public float y;
   public float hoverAmount;
   private boolean collapsed;
   private float expandProgress = 1.0F;

   public Category(String name, float x, float y) {
      this.name = name;
      this.x = x;
      this.y = y;
   }

   public void addModule(Module module) {
      this.modules.add(new ModuleButton(module));
   }

   public String getName() {
      return this.name;
   }

   public List<ModuleButton> getModules() {
      return this.modules;
   }

   public boolean isCollapsed() {
      return this.collapsed;
   }

   public void toggleCollapsed() {
      this.collapsed = !this.collapsed;
   }

   public void updateExpandProgress() {
      float target = this.collapsed ? 0.0F : 1.0F;
      this.expandProgress = this.expandProgress + (target - this.expandProgress) * 0.18F;
      if (Math.abs(this.expandProgress - target) < 0.01F) {
         this.expandProgress = target;
      }
   }

   public float getExpandProgress() {
      return this.expandProgress;
   }

   public float getHeight() {
      float fullH = (float)(23 + this.modules.size() * 20);
      return 22.0F + (fullH - 22.0F) * this.expandProgress;
   }
}
