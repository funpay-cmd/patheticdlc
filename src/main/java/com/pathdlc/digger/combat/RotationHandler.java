package com.pathdlc.digger.combat;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.MathHelper;

/**
 * Manages server-side vs client-side rotations for Silent Aim.
 * Applies GCD spoofing to all outgoing rotation deltas.
 *
 * The mixin in SilentRotationMixin swaps player rotation before sendMovementPackets()
 * so the movement packet contains serverYaw/serverPitch, then restores the visual rotation after.
 *
 * GCD: Minecraft server computes mouse sensitivity as:
 *   f = sensitivity * 0.6 + 0.2
 *   gcd = f^3 * 1.2
 * All rotation deltas must be multiples of this GCD value.
 */
public final class RotationHandler {

    private static boolean active;

    // Server-side rotation (what the server sees via movement packets)
    private static float serverYaw;
    private static float serverPitch;

    // Stored visual rotation for restore after packet send
    private static float visualYaw;
    private static float visualPitch;
    private static float visualHeadYaw;
    private static float visualBodyYaw;

    // Movement correction state (for non-silent mode)
    private static float movementCorrectionDiff = 0.0F;
    private static boolean movementCorrectionActive = false;

    // GCD state
    private static float gcdValue = 0.0F;

    // Previous server rotation for delta computation
    private static float prevServerYaw;
    private static float prevServerPitch;
    private static boolean initialized;

    public static void setActive(boolean state) {
        if (state && !active) {
            // Transitioning to active: seed server rotation from player's current
            // rotation so the mixin never sends stale values on early-return ticks
            ClientPlayerEntity player = MinecraftClient.getInstance().player;
            if (player != null) {
                serverYaw = player.getYaw();
                serverPitch = player.getPitch();
                prevServerYaw = serverYaw;
                prevServerPitch = serverPitch;
            }
        }
        active = state;
        if (!state) {
            // Seed server rotation from player so getServerYaw()/getServerPitch()
            // return the player's current facing instead of stale 0.0F.
            // This prevents the non-silent rotation speed limit from computing
            // deltaYaw = wrapDegrees(newYaw - 0) on the first tick.
            ClientPlayerEntity p = MinecraftClient.getInstance().player;
            if (p != null) {
                serverYaw = p.getYaw();
                serverPitch = p.getPitch();
                prevServerYaw = serverYaw;
                prevServerPitch = serverPitch;
            }
            initialized = false;
        }
    }

    public static boolean isActive() {
        return active;
    }

    public static void setServerRotation(float yaw, float pitch) {
        if (!initialized) {
            ClientPlayerEntity player = MinecraftClient.getInstance().player;
            if (player != null) {
                prevServerYaw = player.getYaw();
                prevServerPitch = player.getPitch();
            }
            initialized = true;
        }
        serverYaw = yaw;
        serverPitch = MathHelper.clamp(pitch, -90.0F, 90.0F);
    }

    public static float getServerYaw() {
        return serverYaw;
    }

    public static float getServerPitch() {
        return serverPitch;
    }

    /**
     * Compute GCD factor from a fake sensitivity value.
     * Uses the exact Minecraft mouse sensitivity formula (same as Augustus):
     *   f1 = sensitivity * 0.6 + 0.2
     *   f2 = f1^3 * 8.0
     * The factor f2 is used to convert between angle deltas and mouse deltas.
     */
    public static void updateGCD(float sensitivity) {
        float f = sensitivity * 0.6F + 0.2F;
        gcdValue = f * f * f * 8.0F;
    }

    public static void clearGCD() {
        gcdValue = 0.0F;
    }

    public static float getGCD() {
        return gcdValue;
    }

    /**
     * Apply mouse sensitivity GCD to a rotation, converting angle delta to
     * mouse delta (int) and back. Exact Minecraft formula (same as Augustus):
     *   deltaX = (int)((6.667 * yaw - 6.667 * lastYaw) / f2)
     *   yaw = lastYaw + deltaX * f2 * 0.15
     */
    public static void applyGCDToServerRotation() {
        if (gcdValue <= 0.0F) return;

        // Yaw: convert to mouse delta and back
        int deltaX = (int) ((6.667 * serverYaw - 6.667 * prevServerYaw) / gcdValue);
        float f5 = (float) deltaX * gcdValue;
        serverYaw = (float) ((double) prevServerYaw + (double) f5 * 0.15);

        // Pitch: convert to mouse delta and back (inverted Y axis)
        int deltaY = (int) ((6.667 * serverPitch - 6.667 * prevServerPitch) / gcdValue) * -1;
        float f3 = (float) deltaY * gcdValue;
        serverPitch = MathHelper.clamp((float) ((double) prevServerPitch - (double) f3 * 0.15), -90.0F, 90.0F);
    }

    /**
     * Commit the current server rotation as the "previous" for next tick's delta.
     */
    public static void commitServerRotation() {
        prevServerYaw = serverYaw;
        prevServerPitch = serverPitch;
    }

    // --- Silent Aim packet interception ---

    /**
     * Called by mixin BEFORE sendMovementPackets().
     * Saves visual rotation and swaps player rotation to server values.
     * This ensures the movement packet sent to the server contains our aim rotation.
     */
    public static void onPreSendMovementPackets() {
        if (!active) return;
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) return;

        visualYaw = player.getYaw();
        visualPitch = player.getPitch();
        visualHeadYaw = player.headYaw;
        visualBodyYaw = player.bodyYaw;

        player.setYaw(serverYaw);
        player.setPitch(serverPitch);
        player.headYaw = serverYaw;
        player.bodyYaw = serverYaw;
    }

    /**
     * Called by mixin AFTER sendMovementPackets().
     * Restores visual rotation so the player's camera doesn't jump.
     */
    public static void onPostSendMovementPackets() {
        if (!active) return;
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) return;

        player.setYaw(visualYaw);
        player.setPitch(visualPitch);
        player.headYaw = visualHeadYaw;
        player.bodyYaw = visualBodyYaw;
    }

    /**
     * For non-silent mode: apply server rotation directly to the player
     * so the camera actually follows the aim.
     */
    public static void applyToPlayer(ClientPlayerEntity player) {
        player.prevYaw = player.getYaw();
        player.prevPitch = player.getPitch();
        player.setYaw(serverYaw);
        player.setPitch(serverPitch);
    }

    // --- Movement correction ---

    /**
     * Set movement correction: the yaw difference between aim direction and
     * the player's original look direction. The MovementFixMixin uses this
     * to transform travel() input so WASD still moves in the intended direction.
     */
    public static void setMovementCorrection(float originalYaw, float aimYaw) {
        movementCorrectionDiff = MathHelper.wrapDegrees(aimYaw - originalYaw);
        movementCorrectionActive = Math.abs(movementCorrectionDiff) > 0.01F;
    }

    public static void clearMovementCorrection() {
        movementCorrectionActive = false;
        movementCorrectionDiff = 0.0F;
    }

    public static boolean isMovementCorrectionActive() {
        return movementCorrectionActive;
    }

    public static float getMovementCorrectionDiff() {
        return movementCorrectionDiff;
    }

    private RotationHandler() {
    }
}
