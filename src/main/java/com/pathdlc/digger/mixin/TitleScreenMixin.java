package com.pathdlc.digger.mixin;

import com.pathdlc.digger.gui.CustomTitleScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({TitleScreen.class})
public abstract class TitleScreenMixin {
   @Inject(
      method = {"init"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onInit(CallbackInfo ci) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (!((Object) this instanceof CustomTitleScreen)) {
         mc.setScreen(new CustomTitleScreen());
         ci.cancel();
      }
   }
}
