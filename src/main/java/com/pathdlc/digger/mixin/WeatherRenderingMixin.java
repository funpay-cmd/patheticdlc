package com.pathdlc.digger.mixin;

import com.pathdlc.digger.gui.ModuleManager;
import net.minecraft.client.render.FrameGraphBuilder;
import net.minecraft.client.render.Fog;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Cancel the weather pass entirely while the NoWeather module is active so
 * the player gets a clean sky regardless of server weather state.
 */
@Mixin(WorldRenderer.class)
public abstract class WeatherRenderingMixin {
   @Inject(
         method = "renderWeather",
         at = @At("HEAD"),
         cancellable = true
   )
   private void waredisuals$noWeather(FrameGraphBuilder builder, Vec3d pos, float tickDelta, Fog fog, CallbackInfo ci) {
      if (ModuleManager.isEnabled("NoWeather")) {
         ci.cancel();
      }
   }
}
