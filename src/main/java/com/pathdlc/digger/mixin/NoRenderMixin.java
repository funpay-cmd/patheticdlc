package com.pathdlc.digger.mixin;

import com.pathdlc.digger.gui.Module;
import com.pathdlc.digger.gui.ModuleManager;
import com.pathdlc.digger.gui.ModuleSetting;
import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Optional cancellations for various HUD overlays controlled by the
 * NoRender module's individual toggles (Scoreboard / BossBar /
 * Overlays).  Method names are matched with require=0 so a Yarn
 * mapping rename across a Minecraft minor will simply make the
 * cancel a no-op instead of a hard crash.
 */
@Mixin(InGameHud.class)
public abstract class NoRenderMixin {
   @Inject(method = "renderScoreboardSidebar*", at = @At("HEAD"), cancellable = true, require = 0)
   private void waredisuals$noScoreboard(CallbackInfo ci) {
      if (toggle("Scoreboard")) {
         ci.cancel();
      }
   }

   @Inject(method = "renderMiscOverlays*", at = @At("HEAD"), cancellable = true, require = 0)
   private void waredisuals$noMiscOverlays(CallbackInfo ci) {
      if (toggle("Overlays")) {
         ci.cancel();
      }
   }

   @Inject(method = "renderPortalOverlay*", at = @At("HEAD"), cancellable = true, require = 0)
   private void waredisuals$noPortal(CallbackInfo ci) {
      if (toggle("Portal")) {
         ci.cancel();
      }
   }

   @Inject(method = "renderNauseaOverlay*", at = @At("HEAD"), cancellable = true, require = 0)
   private void waredisuals$noNausea(CallbackInfo ci) {
      if (toggle("Nausea")) {
         ci.cancel();
      }
   }

   private static boolean toggle(String name) {
      Module m = ModuleManager.get("NoRender");
      if (m == null || !m.isEnabled()) {
         return false;
      }
      ModuleSetting s = m.getSetting(name);
      return s != null && s.getBool();
   }
}
