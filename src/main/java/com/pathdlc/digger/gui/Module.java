package com.pathdlc.digger.gui;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.Identifier;

public class Module {
   private final String name;
   private final Identifier icon;
   private boolean enabled;
   private Runnable onEnable;
   private Runnable onDisable;
   private final List<ModuleSetting> settings = new ArrayList<>();
   private boolean settingsExpanded;

   public Module(String name) {
      this.name = name;
      this.icon = Identifier.of("pathdlc_digger", "textures/gui/icons/" + name.toLowerCase() + ".png");
   }

   public Module(String name, Runnable onEnable, Runnable onDisable) {
      this.name = name;
      this.icon = Identifier.of("pathdlc_digger", "textures/gui/icons/" + name.toLowerCase() + ".png");
      this.onEnable = onEnable;
      this.onDisable = onDisable;
   }

   public String getName() {
      return this.name;
   }

   public Identifier getIcon() {
      return this.icon;
   }

   public boolean isEnabled() {
      return this.enabled;
   }

   public void setEnabled(boolean enabled) {
      this.enabled = enabled;
   }

   public void toggle() {
      this.enabled = !this.enabled;
      if (this.enabled && this.onEnable != null) {
         this.onEnable.run();
      }

      if (!this.enabled && this.onDisable != null) {
         this.onDisable.run();
      }
   }

   public Module addSetting(ModuleSetting setting) {
      this.settings.add(setting);
      return this;
   }

   public List<ModuleSetting> getSettings() {
      return this.settings;
   }

   public boolean hasSettings() {
      return !this.settings.isEmpty();
   }

   public boolean isSettingsExpanded() {
      return this.settingsExpanded;
   }

   public void toggleSettingsExpanded() {
      this.settingsExpanded = !this.settingsExpanded;
   }

   public ModuleSetting getSetting(String name) {
      for (ModuleSetting s : this.settings) {
         if (s.getName().equals(name)) {
            return s;
         }
      }

      return null;
   }
}
