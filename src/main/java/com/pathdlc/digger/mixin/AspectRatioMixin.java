package com.pathdlc.digger.mixin;

import com.pathdlc.digger.gui.Module;
import com.pathdlc.digger.gui.ModuleManager;
import com.pathdlc.digger.gui.ModuleSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({GameRenderer.class})
public abstract class AspectRatioMixin {
   @Shadow
   @Final
   private MinecraftClient client;

   @Inject(
      method = {"getFov"},
      at = {@At("RETURN")},
      cancellable = true
   )
   private void onGetFov(Camera camera, float tickDelta, boolean changingFov, CallbackInfoReturnable<Float> cir) {
      if (ModuleManager.isEnabled("AspectRatio")) {
         int w = this.client.getWindow().getWidth();
         int h = this.client.getWindow().getHeight();
         if (w > 0 && h > 0) {
            float currentAspect = (float)w / (float)h;
            float targetAspect = 1.3333334F;
            Module mod = ModuleManager.get("AspectRatio");
            if (mod != null) {
               ModuleSetting fovSetting = mod.getSetting("FOV Scale");
               if (fovSetting != null) {
                  targetAspect = fovSetting.getFloat();
               }
            }

            if (currentAspect > targetAspect) {
               float scale = targetAspect / currentAspect;
               cir.setReturnValue((Float)cir.getReturnValue() * scale);
            }
         }
      }
   }
}
