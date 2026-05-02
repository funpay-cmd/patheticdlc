package com.pathdlc.digger.mixin;

import com.pathdlc.digger.gui.ModuleManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Cancels the vanilla crosshair render so our {@link
 * com.pathdlc.digger.render.CrosshairRenderer} can replace it 1:1
 * (otherwise both would draw on top of each other).
 */
@Mixin(InGameHud.class)
public abstract class CrosshairMixin {
   @Inject(method = "renderCrosshair*", at = @At("HEAD"), cancellable = true, require = 0)
   private void waredisuals$cancelVanillaCrosshair(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
      if (ModuleManager.isEnabled("Crosshair")) {
         ci.cancel();
      }
   }
}
