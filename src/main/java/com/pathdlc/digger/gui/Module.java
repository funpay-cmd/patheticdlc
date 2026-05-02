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
   /**
    * Animation progress for the on/off knob slide and the highlight panel
    * fade.  Lerps toward isEnabled() at ~0.18 per render tick so the toggle
    * feels alive without being slow.
    */
   public float enabledAnim;
   /**
    * Animation progress for the settings drawer expand/collapse.  Lerps
    * toward isSettingsExpanded() so the row pushes neighbours down with a
    * smooth ease.
    */
   public float expandedAnim;
   /**
    * Animation progress for module visibility in the ArrayList HUD widget
    * and other fade-in surfaces.  Driven externally; modules just expose
    * the float so the renderer can lerp it without re-allocating maps.
    */
   public float visibilityAnim;

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
      this.enabledAnim = enabled ? 1.0F : 0.0F;
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
