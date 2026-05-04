package com.pathdlc.digger.mixin;

import com.pathdlc.digger.combat.RotationHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Movement correction mixin for KillAura non-silent mode.
 *
 * When KillAura rotates the player's camera to aim at a target,
 * WASD movement direction changes because it's relative to the yaw.
 * This mixin transforms the movement input in travel() so pressing W
 * still moves the player in their original intended direction.
 *
 * This is equivalent to LiquidBounce's MovementCorrection: Silent.
 */
@Mixin(LivingEntity.class)
public abstract class MovementFixMixin {

    @ModifyVariable(method = "travel", at = @At("HEAD"), argsOnly = true)
    private Vec3d fixMovementInput(Vec3d input) {
        if (!((Object) this instanceof ClientPlayerEntity)) return input;
        if (!RotationHandler.isMovementCorrectionActive()) return input;

        float diff = RotationHandler.getMovementCorrectionDiff();
        if (Math.abs(diff) < 0.01F) return input;

        float rad = diff * ((float) Math.PI / 180.0F);
        float cos = MathHelper.cos(rad);
        float sin = MathHelper.sin(rad);

        double origStrafe = input.x;
        double origForward = input.z;

        return new Vec3d(
                origStrafe * cos + origForward * sin,
                input.y,
                -origStrafe * sin + origForward * cos
        );
    }
}
