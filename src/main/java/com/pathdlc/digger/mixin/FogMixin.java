package com.pathdlc.digger.mixin;

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

@Mixin(BackgroundRenderer.class)
public abstract class FogMixin {
   private static final float[][] CUSTOM_FOG_COLORS = new float[][]{
         {0.92F, 0.20F, 0.25F},
         {1.00F, 0.55F, 0.20F},
         {1.00F, 0.85F, 0.25F},
         {0.30F, 0.85F, 0.40F},
         {0.30F, 0.85F, 0.95F},
         {0.30F, 0.55F, 0.95F},
         {0.65F, 0.35F, 0.95F},
         {0.95F, 0.50F, 0.80F},
         {0.95F, 0.95F, 0.95F}
   };

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
      if (ModuleManager.isEnabled("CustomFog")) {
         Module mod = ModuleManager.get("CustomFog");
         int colorIndex = 0;
         float density = 0.5F;
         if (mod != null) {
            ModuleSetting cs = mod.getSetting("Color");
            if (cs != null) {
               colorIndex = Math.min(cs.getChoiceIndex(), CUSTOM_FOG_COLORS.length - 1);
            }
            ModuleSetting ds = mod.getSetting("Density");
            if (ds != null) {
               density = ds.getFloat();
            }
         }
         float[] rgb = CUSTOM_FOG_COLORS[colorIndex];
         float start = viewDistance * (1.0F - density) * 0.8F;
         float end = viewDistance * (1.0F - density * 0.7F);
         cir.setReturnValue(new Fog(start, end, FogShape.SPHERE, rgb[0], rgb[1], rgb[2], 1.0F));
      }
   }
}
