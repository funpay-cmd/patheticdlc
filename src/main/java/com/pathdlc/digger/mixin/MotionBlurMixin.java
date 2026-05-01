package com.pathdlc.digger.mixin;

import com.pathdlc.digger.render.MotionBlurRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({GameRenderer.class})
public abstract class MotionBlurMixin {
   @Inject(
      method = {"render"},
      at = {@At("TAIL")}
   )
   private void onRenderEnd(RenderTickCounter tickCounter, boolean tick, CallbackInfo ci) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.getWindow() != null) {
         int w = mc.getWindow().getFramebufferWidth();
         int h = mc.getWindow().getFramebufferHeight();
         if (w > 0 && h > 0) {
            MotionBlurRenderer.onFrameEnd(w, h);
         }
      }
   }
}
