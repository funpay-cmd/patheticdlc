package com.pathdlc.digger.combat;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.MathHelper;

/**
 * Manages server-side vs client-side rotations for Silent Aim.
 * Applies GCD spoofing to all outgoing rotation deltas.
 */
public final class RotationHandler {

    private static boolean active;

    // Server-side rotation (what the server sees)
    private static float serverYaw;
    private static float serverPitch;
    // Stored visual rotation for restore after packet send
    private static float visualYaw;
    private static float visualPitch;
    private static float visualHeadYaw;
    private static float visualBodyYaw;

    // GCD state
    private static float gcdValue = 0.0F;
    private static float fakeSensitivity = 0.5F;

    // Previous server rotation for delta computation
    private static float prevServerYaw;
    private static float prevServerPitch;
    private static boolean initialized;

    public static void setActive(boolean state) {
        active = state;
        if (!state) {
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
     * Compute GCD from a fake sensitivity value.
     * f = sensitivity * 0.6 + 0.2
     * gcd = f^3 * 1.2
     */
    public static void updateGCD(float sensitivity) {
        fakeSensitivity = sensitivity;
        float f = sensitivity * 0.6F + 0.2F;
        gcdValue = f * f * f * 1.2F;
    }

    public static void clearGCD() {
        gcdValue = 0.0F;
    }

    public static float getGCD() {
        return gcdValue;
    }

    /**
     * Apply GCD rounding to a rotation delta so it appears
     * as if it came from real mouse input with the fake sensitivity.
     */
    public static float applyGCD(float delta) {
        if (gcdValue <= 0.0F) return delta;
        return Math.round(delta / gcdValue) * gcdValue;
    }

    /**
     * Apply GCD to the server yaw/pitch deltas relative to previous values.
     * Must be called after setServerRotation() each tick.
     */
    public static void applyGCDToServerRotation() {
        if (gcdValue <= 0.0F) return;

        float deltaYaw = MathHelper.wrapDegrees(serverYaw - prevServerYaw);
        float deltaPitch = serverPitch - prevServerPitch;

        float gcdYaw = applyGCD(deltaYaw);
        float gcdPitch = applyGCD(deltaPitch);

        serverYaw = prevServerYaw + gcdYaw;
        serverPitch = MathHelper.clamp(prevServerPitch + gcdPitch, -90.0F, 90.0F);
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
     * Restores visual rotation.
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
        player.setYaw(serverYaw);
        player.setPitch(serverPitch);
    }

    private RotationHandler() {
    }
}
