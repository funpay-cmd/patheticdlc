package com.pathdlc.digger.mixin;

import com.pathdlc.digger.gui.ModuleManager;
import com.pathdlc.digger.render.HitEffectsRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({ClientPlayerInteractionManager.class})
public abstract class HitEffectsMixin {
   @Inject(
      method = {"attackEntity"},
      at = {@At("HEAD")}
   )
   private void onAttackEntity(PlayerEntity player, Entity target, CallbackInfo ci) {
      if (ModuleManager.isEnabled("HitEffects")) {
         MinecraftClient mc = MinecraftClient.getInstance();
         int screenW = mc.getWindow().getScaledWidth();
         int screenH = mc.getWindow().getScaledHeight();
         HitEffectsRenderer.spawnAt((double)screenW / 2.0, (double)screenH / 2.0);
      }
   }
}
