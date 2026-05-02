package com.pathdlc.digger.mixin;

import com.pathdlc.digger.gui.ModuleManager;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Cancel the screen-shake the vanilla camera applies when the player takes
 * damage while the NoHurtCam module is active.
 */
@Mixin(GameRenderer.class)
public abstract class HurtCamMixin {
   @Inject(
         method = "tiltViewWhenHurt",
         at = @At("HEAD"),
         cancellable = true
   )
   private void waredisuals$noHurtCam(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
      if (ModuleManager.isEnabled("NoHurtCam")) {
         ci.cancel();
      }
   }
}
