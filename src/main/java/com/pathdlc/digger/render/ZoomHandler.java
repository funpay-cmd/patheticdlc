package com.pathdlc.digger.render;

import com.pathdlc.digger.gui.Module;
import com.pathdlc.digger.gui.ModuleManager;
import com.pathdlc.digger.gui.ModuleSetting;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import org.lwjgl.glfw.GLFW;

/**
 * OptiFine-style scroll-zoom.  When the {@code Zoom} module is on AND
 * the configured key is held, the FOV multiplier returned by
 * {@link #getFovMultiplier()} drops below 1.0.  A {@link FovMixin}
 * (added to the existing mixin block) reads this value and multiplies
 * the live FOV before vanilla applies dynamic FOV / sprint widening.
 */
public final class ZoomHandler {
   private static final KeyBinding KEY = KeyBindingHelper.registerKeyBinding(
         new net.minecraft.client.option.KeyBinding(
               "key.pathdlc.zoom", GLFW.GLFW_KEY_C, "category.pathdlc"));

   /** Smoothed multiplier (1.0 = no zoom).  Eased toward target each frame
    *  so toggling the key does not snap. */
   private static volatile float current = 1.0F;
   private static volatile float wheelScale = 0.0F;
   private static volatile float lastDelta = 0.0F;

   private ZoomHandler() {
   }

   public static boolean isActive() {
      Module m = ModuleManager.get("Zoom");
      return m != null && m.isEnabled() && KEY.isPressed();
   }

   public static float getFovMultiplier() {
      return current;
   }

   public static void onScroll(double horizontal, double vertical) {
      if (!isActive()) {
         return;
      }
      lastDelta = (float) vertical;
   }

   public static void tick() {
      Module m = ModuleManager.get("Zoom");
      float target = 1.0F;
      if (m != null && m.isEnabled() && KEY.isPressed()) {
         float zoomFactor = 0.30F;
         ModuleSetting f = m.getSetting("Zoom");
         if (f != null) {
            zoomFactor = Math.max(0.05F, Math.min(0.9F, f.getFloat()));
         }
         // Allow scroll-wheel fine-tune while zoomed (compresses range).
         if (lastDelta != 0.0F) {
            wheelScale = Math.max(-0.2F, Math.min(0.4F, wheelScale - lastDelta * 0.05F));
            lastDelta = 0.0F;
         }
         target = Math.max(0.05F, Math.min(1.0F, zoomFactor + wheelScale));
      } else {
         wheelScale = 0.0F;
      }
      // Frame-rate-independent ease (~80ms half-life).
      MinecraftClient mc = MinecraftClient.getInstance();
      float dt = mc != null && mc.getRenderTickCounter() != null
            ? mc.getRenderTickCounter().getLastFrameDuration() * 0.05F
            : 0.016F;
      float k = 1.0F - (float) Math.exp(-dt * 8.0F);
      current += (target - current) * k;
   }
}
