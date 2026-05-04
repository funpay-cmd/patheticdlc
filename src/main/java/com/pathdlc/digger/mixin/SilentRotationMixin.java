package com.pathdlc.digger.mixin;

import com.pathdlc.digger.combat.RotationHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerEntity.class)
public abstract class SilentRotationMixin {

    @Inject(method = "sendMovementPackets", at = @At("HEAD"))
    private void onPreSendMovement(CallbackInfo ci) {
        RotationHandler.onPreSendMovementPackets();
    }

    @Inject(method = "sendMovementPackets", at = @At("RETURN"))
    private void onPostSendMovement(CallbackInfo ci) {
        RotationHandler.onPostSendMovementPackets();
    }
}
