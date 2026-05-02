package com.pathdlc.digger.mixin;

import com.pathdlc.digger.gui.ModuleManager;
import com.pathdlc.digger.render.ZoomHandler;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Multiplies the live FOV by the smoothed Zoom multiplier when the
 * Zoom module is active, before vanilla applies dynamic FOV /
 * sprint widening.  Touched only when the module is on so this is
 * a no-op for users who do not care about scroll-zoom.
 */
@Mixin(GameRenderer.class)
public abstract class FovMixin {
   @Inject(method = "getFov*", at = @At("RETURN"), cancellable = true, require = 0)
   private void waredisuals$applyZoom(CallbackInfoReturnable<Float> cir) {
      if (!ModuleManager.isEnabled("Zoom")) {
         return;
      }
      float multiplier = ZoomHandler.getFovMultiplier();
      if (multiplier >= 0.999F) {
         return;
      }
      Float current = cir.getReturnValue();
      if (current == null) {
         return;
      }
      cir.setReturnValue(current * multiplier);
   }
}
