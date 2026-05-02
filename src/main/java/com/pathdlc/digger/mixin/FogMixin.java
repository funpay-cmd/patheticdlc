package com.pathdlc.digger.mixin;

import com.pathdlc.digger.gui.FogSettings;
import com.pathdlc.digger.gui.Module;
import com.pathdlc.digger.gui.ModuleManager;
import com.pathdlc.digger.gui.ModuleSetting;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.FogShape;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.Fog;

import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({BackgroundRenderer.class})
public abstract class FogMixin {
   @Inject(
      method = {"applyFog"},
      at = {@At("RETURN")},
      cancellable = true
   )
   private static void onApplyFog(
      Camera camera, BackgroundRenderer.FogType fogType, Vector4f color, float viewDistance, boolean thickFog, float tickDelta, CallbackInfoReturnable<Fog> cir
   ) {
      if (ModuleManager.isEnabled("NoFog")) {
         float far = Math.max(viewDistance, 1024.0F) * 8.0F;
         cir.setReturnValue(new Fog(far * 0.95F, far, FogShape.SPHERE, color.x, color.y, color.z, color.w));
         return;
      }
      if (ModuleManager.isEnabled("Fog")) {
         Module mod = ModuleManager.get("Fog");
         int colorIndex = 0;
         float density = 0.5F;
         if (mod != null) {
            ModuleSetting colorSetting = mod.getSetting("Color");
            if (colorSetting != null) {
               colorIndex = colorSetting.getChoiceIndex();
            }

            ModuleSetting densitySetting = mod.getSetting("Density");
            if (densitySetting != null) {
               density = densitySetting.getFloat();
            }
         }

         FogSettings.FogColor[] colors = FogSettings.FogColor.values();
         FogSettings.FogColor fogColor = colors[Math.min(colorIndex, colors.length - 1)];
         float start = viewDistance * (1.0F - density) * 0.8F;
         float end = viewDistance * (1.0F - density * 0.7F);
         cir.setReturnValue(new Fog(start, end, FogShape.SPHERE, fogColor.r, fogColor.g, fogColor.b, 1.0F));
      }
   }
}
