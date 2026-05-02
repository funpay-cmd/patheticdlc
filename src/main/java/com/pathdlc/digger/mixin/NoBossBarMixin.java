package com.pathdlc.digger.mixin;

import com.pathdlc.digger.gui.Module;
import com.pathdlc.digger.gui.ModuleManager;
import com.pathdlc.digger.gui.ModuleSetting;
import net.minecraft.client.gui.hud.BossBarHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Cancels boss-bar rendering when NoRender → BossBar is on. */
@Mixin(BossBarHud.class)
public abstract class NoBossBarMixin {
   @Inject(method = "render", at = @At("HEAD"), cancellable = true, require = 0)
   private void waredisuals$noBossBar(CallbackInfo ci) {
      Module m = ModuleManager.get("NoRender");
      if (m == null || !m.isEnabled()) {
         return;
      }
      ModuleSetting s = m.getSetting("BossBar");
      if (s != null && s.getBool()) {
         ci.cancel();
      }
   }
}
