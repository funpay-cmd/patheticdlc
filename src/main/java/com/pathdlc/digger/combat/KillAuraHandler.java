package com.pathdlc.digger.combat;

import com.pathdlc.digger.gui.Module;
import com.pathdlc.digger.gui.ModuleManager;
import com.pathdlc.digger.gui.ModuleSetting;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * KillAura with anti-cheat bypass for Polar, Grim, Matrix, Vulcan, Intave.
 *
 * Polar-specific bypass techniques:
 * 1. Gaussian aim point offset — aim point varies within hitbox using Gaussian distribution,
 *    changing slowly over time. Defeats Polar's ML "always center" detection.
 * 2. Rotation short stops — ~3% chance to freeze rotation for 1-2 ticks. Anti-pattern.
 * 3. Acceleration-based rotation with error terms — mimics real mouse movement.
 * 4. Attack timing fatigue — CPS decreases over longer fights, occasional pauses.
 * 5. VL decay awareness — backs off aggression periodically to let VL decay.
 *
 * Grim-specific: server rotation points at hitbox, raycast verified before every attack.
 * Sprint reset packets removed — Grim's PacketOrder checks flag sprint toggle around attacks.
 */
public final class KillAuraHandler {

    // --- Target state ---
    private static LivingEntity currentTarget;
    private static int switchTimer;
    private static int ticksSinceAttack;

    // --- Reaction time ---
    private static long targetAcquiredMs;
    private static boolean reactionDelayActive;
    private static float actualReactionTarget;

    // --- Smooth rotation state (for non-silent mode) ---
    private static float smoothYaw, smoothPitch;
    private static boolean smoothInitialized;

    // --- Pre-aim state ---
    private static int preAimTicks;
    private static boolean preAimDone;

    // --- Click humanization ---
    private static int nextAttackDelay;
    private static boolean skipNextClick;
    private static boolean doubleClickQueued;

    // --- Micro-pause (fatigue) ---
    private static int pauseTicks;

    // --- Intentional miss (only when not silent — visual only) ---
    private static int missTicksRemaining;
    private static float missOffsetYaw, missOffsetPitch;

    // --- Drift simulation (visual only) ---
    private static float driftYaw, driftPitch;
    private static float targetDriftYaw, targetDriftPitch;
    private static int driftChangeTicks;

    // --- Movement-aware body yaw ---
    private static float lastMoveYaw;

    // --- Target switch threshold (generated once per switch cycle) ---
    private static int switchThreshold = 60;

    // --- Bezier rotation state ---
    private static float bezierProgress;
    private static float bezierStartYaw, bezierStartPitch;
    private static float bezierCtrl1Yaw, bezierCtrl1Pitch;
    private static float bezierCtrl2Yaw, bezierCtrl2Pitch;
    private static float bezierEndYaw, bezierEndPitch;
    private static boolean bezierActive;

    // --- Micro-movement (anti-AFK) ---
    private static int microMoveTicks;

    // --- Polar: Gaussian aim point offset ---
    private static float gaussianOffsetYaw;
    private static float gaussianOffsetPitch;
    private static float gaussianTargetOffsetYaw;
    private static float gaussianTargetOffsetPitch;
    private static int gaussianChangeTicks;

    // --- Polar: Rotation short stop ---
    private static int shortStopTicks;

    // --- Polar: Fight fatigue (CPS decay over time) ---
    private static int fightDurationTicks;
    private static int vlDecayCooldown;
    private static int vlDecayInterval = 300;

    // --- Post-rotation delay (Polar: don't attack right after big rotation) ---
    private static int postRotationDelay;

    // --- Polar: Acceleration rotation state ---
    private static float accelYawVelocity;
    private static float accelPitchVelocity;

    public static void tick(MinecraftClient client) {
        if (!ModuleManager.isEnabled("KillAura")) {
            reset();
            return;
        }

        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null || client.interactionManager == null) {
            return;
        }

        Module mod = ModuleManager.get("KillAura");
        if (mod == null) return;

        // --- Read settings ---
        float range = getFloat(mod, "Range", 2.9F);
        float aimSpeed = getFloat(mod, "Aim Speed", 55.0F);
        float minAps = getFloat(mod, "Min APS", 5.0F);
        float maxAps = getFloat(mod, "Max APS", 8.0F);
        float fov = getFloat(mod, "FOV", 120.0F);
        float reactionMs = getFloat(mod, "Reaction ms", 180.0F);
        float sensitivity = getFloat(mod, "GCD Sens", 0.5F);
        boolean silentAim = getBool(mod, "Silent Aim");
        boolean gcdFix = getBool(mod, "GCD Fix");
        boolean humanAim = getBool(mod, "Smart Aim");
        boolean losCheck = getBool(mod, "LoS Check");
        boolean critOnly = getBool(mod, "Only Crit");
        boolean hitMobs = getBool(mod, "Attack Mobs");
        boolean hitPlayers = getBool(mod, "Attack Players");
        boolean moveAware = getBool(mod, "Move Aware");
        String targetMode = getChoice(mod, "Target Mode");
        String rotProfile = getChoice(mod, "Rotation");

        // --- GCD setup ---
        if (gcdFix) {
            RotationHandler.updateGCD(sensitivity);
        } else {
            RotationHandler.clearGCD();
        }

        // --- Silent Aim toggle ---
        RotationHandler.setActive(silentAim);
        RotationHandler.clearMovementCorrection();

        // --- Polar: VL decay cooldown — stop attacking to let VL drop ---
        if (vlDecayCooldown > 0) {
            vlDecayCooldown--;
            return;
        }

        // --- Fatigue micro-pause (rare, natural break) ---
        if (pauseTicks > 0) { pauseTicks--; return; }
        if (rng().nextFloat() < 0.003F) {
            pauseTicks = randInt(2, 5);
            return;
        }

        // --- Find & filter targets ---
        List<LivingEntity> targets = findTargets(client, player, range, hitMobs, hitPlayers, fov);
        if (losCheck) {
            targets.removeIf(e -> !hasLineOfSight(client, player, e));
        }
        if (targets.isEmpty()) {
            if (currentTarget != null) {
                currentTarget = null;
                reactionDelayActive = false;
                preAimDone = false;
                bezierActive = false;
                fightDurationTicks = 0;
            }
            RotationHandler.setActive(false);
            return;
        }

        // (5) Weighted hit selection
        sortTargets(targets, player, targetMode);

        // --- Target switching ---
        switchTimer++;
        boolean needSwitch = currentTarget == null
                || !currentTarget.isAlive()
                || getHitboxAwareDistance(player, currentTarget) > range + 0.5
                || !targets.contains(currentTarget)
                || switchTimer > switchThreshold;

        if (needSwitch) {
            LivingEntity prev = currentTarget;
            currentTarget = targets.get(0);
            switchTimer = 0;
            switchThreshold = randInt(40, 80);
            if (prev != currentTarget) {
                targetAcquiredMs = System.currentTimeMillis();
                reactionDelayActive = true;
                actualReactionTarget = reactionMs + randFloat(-30.0F, 30.0F);
                preAimDone = false;
                preAimTicks = randInt(1, 3);
                smoothInitialized = false;
                bezierActive = false;
            }
        }

        // Reaction delay (fixed threshold, not re-randomized each tick)
        if (reactionDelayActive) {
            long elapsed = System.currentTimeMillis() - targetAcquiredMs;
            if (elapsed < (long) actualReactionTarget) {
                return;
            }
            reactionDelayActive = false;
        }

        // --- Polar: Update Gaussian aim point offset (slow-moving within hitbox) ---
        tickGaussianAimOffset();

        // --- Compute rotation to target hitbox with Gaussian offset ---
        // Polar bypass: aim point varies within hitbox, not always center
        float[] cleanAngles = getGaussianTargetAngles(player, currentTarget);

        if (silentAim) {
            // === SILENT AIM MODE ===
            // Server gets aim rotation with Gaussian offset (still within hitbox)
            // Player camera is unchanged
            float serverYaw = cleanAngles[0];
            float serverPitch = cleanAngles[1];

            // Polar-safe rotation speed limit (lower than Grim to avoid ML flagging)
            float prevYaw = RotationHandler.getServerYaw();
            float prevPitch = RotationHandler.getServerPitch();
            float deltaYaw = MathHelper.wrapDegrees(serverYaw - prevYaw);
            float deltaPitch = serverPitch - prevPitch;
            float maxYaw = 30.0F + randFloat(-5.0F, 5.0F);
            float maxPitch = 25.0F + randFloat(-3.0F, 3.0F);
            deltaYaw = MathHelper.clamp(deltaYaw, -maxYaw, maxYaw);
            deltaPitch = MathHelper.clamp(deltaPitch, -maxPitch, maxPitch);
            serverYaw = prevYaw + deltaYaw;
            serverPitch = MathHelper.clamp(prevPitch + deltaPitch, -90.0F, 90.0F);

            RotationHandler.setServerRotation(serverYaw, serverPitch);
            if (gcdFix) {
                RotationHandler.applyGCDToServerRotation();
            }
            RotationHandler.commitServerRotation();

            // Movement-aware body yaw (cosmetic — other players see natural body)
            if (moveAware) {
                tickMovementAwareBodyYaw(player);
            }

        } else {
            // === NON-SILENT MODE ===
            // Player camera rotates toward target with humanization
            // Server sees what the player sees
            float newYaw, newPitch;

            if (humanAim) {
                if ("Polar".equals(rotProfile)) {
                    // Polar bypass: acceleration-based with error terms
                    float[] accelResult = tickAccelerationRotation(player, cleanAngles, aimSpeed);
                    newYaw = accelResult[0];
                    newPitch = accelResult[1];
                } else if ("Bezier".equals(rotProfile)) {
                    float[] bezResult = tickBezierRotation(player, cleanAngles, aimSpeed);
                    newYaw = bezResult[0];
                    newPitch = bezResult[1];
                } else if ("Cinematic".equals(rotProfile)) {
                    float[] cinResult = tickCinematicRotation(player, cleanAngles, aimSpeed);
                    newYaw = cinResult[0];
                    newPitch = cinResult[1];
                } else {
                    float[] smoothed = tickSmoothRotation(player, cleanAngles, aimSpeed);
                    newYaw = smoothed[0];
                    newPitch = smoothed[1];
                }

                // No additive noise in non-silent mode — the rotation algorithm
                // itself (acceleration/bezier curves) provides natural movement.
                // Adding jitter/drift/tremor causes camera shaking and makes raycast miss.
            } else {
                newYaw = cleanAngles[0] + randFloat(-0.5F, 0.5F);
                newPitch = cleanAngles[1] + randFloat(-0.3F, 0.3F);
                smoothInitialized = false;
                bezierActive = false;
            }

            newPitch = MathHelper.clamp(newPitch, -90.0F, 90.0F);

            // Rotation speed limit (important for non-silent — prevents Grim snap detection)
            float prevYaw = RotationHandler.getServerYaw();
            float prevPitch = RotationHandler.getServerPitch();
            float deltaYaw = MathHelper.wrapDegrees(newYaw - prevYaw);
            float deltaPitch = newPitch - prevPitch;
            float maxYawPerTick = 80.0F + randFloat(-8.0F, 8.0F);
            float maxPitchPerTick = 60.0F + randFloat(-5.0F, 5.0F);
            deltaYaw = MathHelper.clamp(deltaYaw, -maxYawPerTick, maxYawPerTick);
            deltaPitch = MathHelper.clamp(deltaPitch, -maxPitchPerTick, maxPitchPerTick);
            newYaw = prevYaw + deltaYaw;
            newPitch = MathHelper.clamp(prevPitch + deltaPitch, -90.0F, 90.0F);

            // Sync smooth state after clamping (fix divergence bug)
            smoothYaw = newYaw;
            smoothPitch = newPitch;

            RotationHandler.setServerRotation(newYaw, newPitch);
            if (gcdFix) {
                RotationHandler.applyGCDToServerRotation();
            }

            // Apply to player camera (non-silent mode) with movement correction
            float originalYaw = player.getYaw();
            RotationHandler.applyToPlayer(player);
            RotationHandler.setMovementCorrection(originalYaw, RotationHandler.getServerYaw());
            RotationHandler.commitServerRotation();

            if (moveAware) {
                tickMovementAwareBodyYaw(player);
            }
        }

        // --- Post-rotation delay: don't attack right after a very large rotation ---
        {
            float srvYaw = RotationHandler.getServerYaw();
            float srvPitch = RotationHandler.getServerPitch();
            float[] desired = getGaussianTargetAngles(player, currentTarget);
            float aimDiff = Math.abs(MathHelper.wrapDegrees(desired[0] - srvYaw))
                    + Math.abs(desired[1] - srvPitch);
            if (aimDiff > 15.0F) {
                postRotationDelay = randInt(1, 2);
            }
        }
        if (postRotationDelay > 0) {
            postRotationDelay--;
            return;
        }

        // --- Pre-aim: don't attack until pre-aim ticks have passed ---
        if (!preAimDone) {
            preAimTicks--;
            if (preAimTicks <= 0) {
                preAimDone = true;
            }
            return;
        }

        // --- Attack logic ---
        ticksSinceAttack++;
        fightDurationTicks++;

        // Polar: VL decay — periodic pause (pre-computed interval)
        if (fightDurationTicks > 0 && fightDurationTicks % vlDecayInterval == 0) {
            vlDecayCooldown = randInt(30, 60);
            vlDecayInterval = randInt(200, 400);
            return;
        }

        // Skip click simulation (naturalness)
        if (skipNextClick) {
            skipNextClick = false;
            if (rng().nextFloat() < 0.06F) skipNextClick = true;
            return;
        }

        if (player.getAttackCooldownProgress(0.0F) < 1.0F) return;
        if (ticksSinceAttack < nextAttackDelay) return;

        // Full attack validation
        if (!canAttack(client, player, currentTarget, range, critOnly, silentAim)) return;

        doAttack(client, player);
        ticksSinceAttack = 0;

        // Polar: CPS decreases over fight duration (fatigue)
        float fatigueMultiplier = 1.0F;
        if (fightDurationTicks > 100) fatigueMultiplier = 0.9F;
        if (fightDurationTicks > 300) fatigueMultiplier = 0.8F;
        if (fightDurationTicks > 600) fatigueMultiplier = 0.7F;
        nextAttackDelay = computeGaussianDelay(minAps * fatigueMultiplier, maxAps * fatigueMultiplier);

        // Skip click chance (naturalness — no double-clicks for Polar safety)
        if (rng().nextFloat() < 0.06F) {
            skipNextClick = true;
        }

        // Anti-AFK micro-movements
        tickMicroMovements(player);
    }

    // ==================== ATTACK VALIDATION ====================

    /**
     * Full pre-attack validation. Checks:
     * 1. Target alive
     * 2. Hitbox-aware reach distance
     * 3. Server rotation raycast hits target (critical for Grim)
     * 4. Crit-only check
     * 5. Non-silent: aim readiness
     */
    private static boolean canAttack(MinecraftClient client, ClientPlayerEntity player,
                                      LivingEntity target, float range, boolean critOnly,
                                      boolean silentAim) {
        if (target == null || !target.isAlive()) return false;

        // Hitbox-aware distance (matches Grim's calculation)
        double hitboxDist = getHitboxAwareDistance(player, target);
        float attackRange = range - randFloat(0.0F, 0.05F);
        if (hitboxDist > attackRange) return false;

        // Raycast verification: does the server rotation actually point at the target?
        if (!serverRotationHitsTarget(player, target, range + 3.0)) return false;

        // Crit-only check
        if (critOnly) {
            boolean falling = !player.isOnGround()
                    && player.getVelocity().y < 0.0
                    && player.fallDistance > 0.0F;
            if (!falling) return false;
        }

        // Non-silent: check aim readiness (player must visually face the target)
        if (!silentAim) {
            float[] desired = getCleanTargetAngles(player, target);
            float yawDiff = Math.abs(MathHelper.wrapDegrees(desired[0] - player.getYaw()));
            float pitchDiff = Math.abs(desired[1] - player.getPitch());
            if (yawDiff > 8.0F || pitchDiff > 8.0F) return false;
        }

        return true;
    }

    /**
     * Trace a ray from the player's eye position along the SERVER rotation
     * and check if it intersects the target entity's bounding box.
     * This is exactly what Grim does to validate attacks.
     */
    private static boolean serverRotationHitsTarget(ClientPlayerEntity player,
                                                     LivingEntity target, double maxDist) {
        float yaw = RotationHandler.getServerYaw();
        float pitch = RotationHandler.getServerPitch();

        Vec3d eyePos = player.getEyePos();

        // Compute look vector from server rotation
        float yawRad = (float) Math.toRadians(-yaw - 180.0F);
        float pitchRad = (float) Math.toRadians(-pitch);
        float cosP = MathHelper.cos(pitchRad);
        float sinP = MathHelper.sin(pitchRad);
        float cosY = MathHelper.cos(yawRad);
        float sinY = MathHelper.sin(yawRad);
        Vec3d lookVec = new Vec3d(sinY * cosP, sinP, cosY * cosP);

        Vec3d endPos = eyePos.add(lookVec.x * maxDist, lookVec.y * maxDist, lookVec.z * maxDist);

        // Expand hitbox (Grim uses ~0.1; for non-silent with rotation smoothing we need more tolerance)
        Box hitbox = target.getBoundingBox().expand(0.15);

        return rayIntersectsBox(eyePos, endPos, hitbox);
    }

    private static boolean rayIntersectsBox(Vec3d start, Vec3d end, Box box) {
        double dx = end.x - start.x;
        double dy = end.y - start.y;
        double dz = end.z - start.z;

        double tMin = 0.0;
        double tMax = 1.0;

        // X slab
        if (Math.abs(dx) < 1e-9) {
            if (start.x < box.minX || start.x > box.maxX) return false;
        } else {
            double t1 = (box.minX - start.x) / dx;
            double t2 = (box.maxX - start.x) / dx;
            if (t1 > t2) { double tmp = t1; t1 = t2; t2 = tmp; }
            tMin = Math.max(tMin, t1);
            tMax = Math.min(tMax, t2);
            if (tMin > tMax) return false;
        }

        // Y slab
        if (Math.abs(dy) < 1e-9) {
            if (start.y < box.minY || start.y > box.maxY) return false;
        } else {
            double t1 = (box.minY - start.y) / dy;
            double t2 = (box.maxY - start.y) / dy;
            if (t1 > t2) { double tmp = t1; t1 = t2; t2 = tmp; }
            tMin = Math.max(tMin, t1);
            tMax = Math.min(tMax, t2);
            if (tMin > tMax) return false;
        }

        // Z slab
        if (Math.abs(dz) < 1e-9) {
            if (start.z < box.minZ || start.z > box.maxZ) return false;
        } else {
            double t1 = (box.minZ - start.z) / dz;
            double t2 = (box.maxZ - start.z) / dz;
            if (t1 > t2) { double tmp = t1; t1 = t2; t2 = tmp; }
            tMin = Math.max(tMin, t1);
            tMax = Math.min(tMax, t2);
            if (tMin > tMax) return false;
        }

        return true;
    }

    // ==================== ATTACK EXECUTION ====================

    private static void doAttack(MinecraftClient client, ClientPlayerEntity player) {
        if (currentTarget == null || !currentTarget.isAlive()) return;

        // Use standard attackEntity — the mixin ensures the movement packet
        // sent right after this tick contains the correct server rotation.
        // Grim queues the attack and validates it when the movement packet arrives.
        client.interactionManager.attackEntity(player, currentTarget);
        player.swingHand(Hand.MAIN_HAND);
    }

    // ==================== POLAR: GAUSSIAN AIM OFFSET ====================

    /**
     * Slowly update the Gaussian aim offset. Instead of aiming at exact hitbox center
     * every tick, the offset drifts within the hitbox using Gaussian distribution.
     * This defeats Polar's ML that detects constant-center aiming.
     */
    private static void tickGaussianAimOffset() {
        gaussianChangeTicks--;
        if (gaussianChangeTicks <= 0) {
            // New random offset target using Gaussian distribution
            gaussianTargetOffsetYaw = (float)(rng().nextGaussian() * 0.25);
            gaussianTargetOffsetPitch = (float)(rng().nextGaussian() * 0.15);
            // Clamp to stay within hitbox
            gaussianTargetOffsetYaw = MathHelper.clamp(gaussianTargetOffsetYaw, -0.5F, 0.5F);
            gaussianTargetOffsetPitch = MathHelper.clamp(gaussianTargetOffsetPitch, -0.3F, 0.3F);
            // Change speed — vary between fast and slow transitions
            gaussianChangeTicks = randInt(15, 50);
        }
        // Smoothly interpolate toward target offset
        float speed = 0.08F + randFloat(0.0F, 0.04F);
        gaussianOffsetYaw += (gaussianTargetOffsetYaw - gaussianOffsetYaw) * speed;
        gaussianOffsetPitch += (gaussianTargetOffsetPitch - gaussianOffsetPitch) * speed;
    }

    /**
     * Compute rotation angles to a point within the target's hitbox
     * that varies using the Gaussian offset. Still guaranteed to be
     * within the hitbox (ray will still hit).
     */
    private static float[] getGaussianTargetAngles(ClientPlayerEntity player, LivingEntity target) {
        Vec3d eyePos = player.getEyePos();
        Box box = target.getBoundingBox();

        double centerX = (box.minX + box.maxX) / 2.0;
        double centerY = (box.minY + box.maxY) / 2.0;
        double centerZ = (box.minZ + box.maxZ) / 2.0;

        // Apply Gaussian offset within the hitbox (fraction of hitbox size)
        double halfWidth = (box.maxX - box.minX) / 2.0;
        double halfHeight = (box.maxY - box.minY) / 2.0;
        double halfDepth = (box.maxZ - box.minZ) / 2.0;

        // Offset is a fraction (-0.5 to 0.5) of the half-size, so total stays within box
        double targetX = centerX + halfWidth * gaussianOffsetYaw * 0.6;
        double targetY = centerY + halfHeight * gaussianOffsetPitch * 0.6;
        double targetZ = centerZ + halfDepth * gaussianOffsetYaw * 0.4;

        // Clamp to bounding box with a small margin
        double margin = 0.02;
        targetX = MathHelper.clamp(targetX, box.minX + margin, box.maxX - margin);
        targetY = MathHelper.clamp(targetY, box.minY + margin, box.maxY - margin);
        targetZ = MathHelper.clamp(targetZ, box.minZ + margin, box.maxZ - margin);

        double dx = targetX - eyePos.x;
        double dy = targetY - eyePos.y;
        double dz = targetZ - eyePos.z;
        double dist = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) Math.toDegrees(-Math.atan2(dy, dist));
        return new float[]{yaw, MathHelper.clamp(pitch, -90.0F, 90.0F)};
    }

    // ==================== POLAR: ACCELERATION ROTATION ====================

    /**
     * Acceleration-based rotation inspired by LiquidBounce's Acceleration mode.
     * Uses acceleration/deceleration with error terms to mimic real mouse movement.
     * Key for Polar bypass — Polar's ML detects linear and Bezier patterns over time.
     */
    private static float[] tickAccelerationRotation(ClientPlayerEntity player, float[] dest, float aimSpeed) {
        float currentYaw, currentPitch;
        if (smoothInitialized) {
            currentYaw = smoothYaw;
            currentPitch = smoothPitch;
        } else {
            currentYaw = player.getYaw();
            currentPitch = player.getPitch();
            smoothInitialized = true;
            accelYawVelocity = 0.0F;
            accelPitchVelocity = 0.0F;
        }

        float yawDiff = MathHelper.wrapDegrees(dest[0] - currentYaw);
        float pitchDiff = dest[1] - currentPitch;

        float speedMult = (aimSpeed / 100.0F);

        // Acceleration parameters
        float yawAccel = (20.0F + randFloat(0.0F, 5.0F)) * speedMult;
        float pitchAccel = (20.0F + randFloat(0.0F, 5.0F)) * speedMult;

        // Acceleration error (makes it non-deterministic)
        float yawAccelError = (float)(rng().nextGaussian() * 0.1);
        float pitchAccelError = (float)(rng().nextGaussian() * 0.1);

        // Constant error (offset drift)
        float yawConstError = (float)(rng().nextGaussian() * 0.08);
        float pitchConstError = (float)(rng().nextGaussian() * 0.08);

        // Compute target velocity
        float targetYawVel = yawDiff * 0.15F * speedMult;
        float targetPitchVel = pitchDiff * 0.12F * speedMult;

        // Accelerate toward target velocity
        float yawAccelStep = (targetYawVel - accelYawVelocity) / yawAccel;
        float pitchAccelStep = (targetPitchVel - accelPitchVelocity) / pitchAccel;

        accelYawVelocity += yawAccelStep * (1.0F + yawAccelError);
        accelPitchVelocity += pitchAccelStep * (1.0F + pitchAccelError);

        // Apply velocity limits
        float maxVel = 15.0F * speedMult;
        accelYawVelocity = MathHelper.clamp(accelYawVelocity, -maxVel, maxVel);
        accelPitchVelocity = MathHelper.clamp(accelPitchVelocity, -maxVel * 0.7F, maxVel * 0.7F);

        // Sigmoid deceleration when close to target
        float yawAbsDiff = Math.abs(yawDiff);
        float pitchAbsDiff = Math.abs(pitchDiff);
        if (yawAbsDiff < 5.0F) {
            float sigmoid = 1.0F / (1.0F + (float)Math.exp(-10.0F * (yawAbsDiff / 5.0F - 0.3F)));
            accelYawVelocity *= sigmoid;
        }
        if (pitchAbsDiff < 3.0F) {
            float sigmoid = 1.0F / (1.0F + (float)Math.exp(-10.0F * (pitchAbsDiff / 3.0F - 0.3F)));
            accelPitchVelocity *= sigmoid;
        }

        smoothYaw = currentYaw + accelYawVelocity + yawConstError;
        smoothPitch = MathHelper.clamp(currentPitch + accelPitchVelocity + pitchConstError, -90.0F, 90.0F);

        return new float[]{smoothYaw, smoothPitch};
    }

    // ==================== BEZIER ROTATION ====================

    private static float[] tickBezierRotation(ClientPlayerEntity player, float[] dest, float aimSpeed) {
        if (!bezierActive || bezierProgress >= 1.0F) {
            // Start new Bezier curve
            bezierActive = true;
            bezierProgress = 0.0F;

            float startYaw = smoothInitialized ? smoothYaw : player.getYaw();
            float startPitch = smoothInitialized ? smoothPitch : player.getPitch();
            smoothInitialized = true;

            bezierStartYaw = startYaw;
            bezierStartPitch = startPitch;

            // Fix: use wrapped difference to keep all points in same yaw space
            // This prevents interpolation artifacts at ±180° boundary
            float diffYaw = MathHelper.wrapDegrees(dest[0] - startYaw);
            float diffPitch = dest[1] - startPitch;

            bezierEndYaw = startYaw + diffYaw;
            bezierEndPitch = dest[1];

            bezierCtrl1Yaw = startYaw + diffYaw * (0.2F + randFloat(0.0F, 0.2F)) + randFloat(-2.0F, 2.0F);
            bezierCtrl1Pitch = startPitch + diffPitch * (0.2F + randFloat(0.0F, 0.2F)) + randFloat(-1.0F, 1.0F);
            bezierCtrl2Yaw = startYaw + diffYaw * (0.6F + randFloat(0.0F, 0.2F)) + randFloat(-1.5F, 1.5F);
            bezierCtrl2Pitch = startPitch + diffPitch * (0.6F + randFloat(0.0F, 0.2F)) + randFloat(-0.8F, 0.8F);
        }

        // Update end target (target may have moved) — keep in same yaw space
        float newDiffYaw = MathHelper.wrapDegrees(dest[0] - bezierStartYaw);
        bezierEndYaw = bezierStartYaw + newDiffYaw;
        bezierEndPitch = dest[1];

        // Progress speed based on aim speed and remaining distance
        float speedMult = (aimSpeed / 100.0F) * randFloat(0.7F, 1.3F);
        float totalDist = Math.abs(MathHelper.wrapDegrees(bezierEndYaw - bezierStartYaw))
                + Math.abs(bezierEndPitch - bezierStartPitch);
        float progressStep = totalDist > 1.0F ? speedMult * (0.08F + randFloat(0.0F, 0.04F)) : 0.3F;
        bezierProgress = Math.min(1.0F, bezierProgress + progressStep);

        float t = bezierProgress;
        float u = 1.0F - t;

        // Cubic Bezier: B(t) = (1-t)³P0 + 3(1-t)²tP1 + 3(1-t)t²P2 + t³P3
        smoothYaw = u * u * u * bezierStartYaw
                + 3 * u * u * t * bezierCtrl1Yaw
                + 3 * u * t * t * bezierCtrl2Yaw
                + t * t * t * bezierEndYaw;
        smoothPitch = u * u * u * bezierStartPitch
                + 3 * u * u * t * bezierCtrl1Pitch
                + 3 * u * t * t * bezierCtrl2Pitch
                + t * t * t * bezierEndPitch;

        smoothPitch = MathHelper.clamp(smoothPitch, -90.0F, 90.0F);

        if (bezierProgress >= 1.0F) {
            bezierActive = false;
        }

        return new float[]{smoothYaw, smoothPitch};
    }

    // ==================== CINEMATIC ROTATION ====================

    private static float[] tickCinematicRotation(ClientPlayerEntity player, float[] dest, float aimSpeed) {
        float currentYaw, currentPitch;
        if (smoothInitialized) {
            currentYaw = smoothYaw;
            currentPitch = smoothPitch;
        } else {
            currentYaw = player.getYaw();
            currentPitch = player.getPitch();
            smoothInitialized = true;
        }

        float yawDiff = MathHelper.wrapDegrees(dest[0] - currentYaw);
        float pitchDiff = dest[1] - currentPitch;

        float speedMult = (aimSpeed / 100.0F) * randFloat(0.7F, 1.3F);

        // Cinematic: very slow acceleration with micro-pauses
        float yawStep = yawDiff * 0.08F * speedMult;
        float pitchStep = pitchDiff * 0.06F * speedMult;

        // Add micro-oscillation (simulating hand tremor)
        long ms = System.currentTimeMillis();
        yawStep += (float)(Math.sin(ms * 0.017) * 0.06 + Math.sin(ms * 0.003) * 0.03);
        pitchStep += (float)(Math.sin(ms * 0.013) * 0.03 + Math.cos(ms * 0.004) * 0.02);

        smoothYaw = currentYaw + yawStep;
        smoothPitch = MathHelper.clamp(currentPitch + pitchStep, -90.0F, 90.0F);

        return new float[]{smoothYaw, smoothPitch};
    }

    // ==================== LINEAR SMOOTH ROTATION ====================

    private static float[] tickSmoothRotation(ClientPlayerEntity player, float[] dest, float aimSpeed) {
        float currentYaw, currentPitch;
        if (smoothInitialized) {
            currentYaw = smoothYaw;
            currentPitch = smoothPitch;
        } else {
            currentYaw = RotationHandler.isActive()
                    ? RotationHandler.getServerYaw()
                    : player.getYaw();
            currentPitch = RotationHandler.isActive()
                    ? RotationHandler.getServerPitch()
                    : player.getPitch();
            smoothInitialized = true;
        }

        float yawDiff = MathHelper.wrapDegrees(dest[0] - currentYaw);
        float pitchDiff = dest[1] - currentPitch;

        float speedMult = (aimSpeed / 100.0F) * randFloat(0.75F, 1.25F);

        float yawStep = adaptiveStep(yawDiff, 30.0F) * speedMult;
        float pitchStep = adaptiveStep(pitchDiff, 20.0F) * speedMult;

        smoothYaw = currentYaw + yawStep;
        smoothPitch = MathHelper.clamp(currentPitch + pitchStep, -90.0F, 90.0F);

        return new float[]{smoothYaw, smoothPitch};
    }

    private static float adaptiveStep(float diff, float maxSpeed) {
        float abs = Math.abs(diff);
        float factor;
        if (abs > 30.0F) {
            factor = 0.5F + randFloat(-0.05F, 0.05F);
        } else if (abs > 12.0F) {
            factor = 0.3F + randFloat(-0.05F, 0.05F);
        } else if (abs > 4.0F) {
            factor = 0.18F + randFloat(-0.04F, 0.04F);
        } else if (abs > 1.0F) {
            factor = 0.1F + randFloat(-0.03F, 0.03F);
        } else {
            factor = 0.05F + randFloat(-0.02F, 0.02F);
        }
        return MathHelper.clamp(diff * factor, -maxSpeed, maxSpeed);
    }

    // ==================== DRIFT ====================

    private static void tickDrift() {
        driftChangeTicks--;
        if (driftChangeTicks <= 0) {
            targetDriftYaw = randFloat(-0.2F, 0.2F);
            targetDriftPitch = randFloat(-0.08F, 0.08F);
            driftChangeTicks = randInt(20, 60);
        }
        driftYaw += (targetDriftYaw - driftYaw) * 0.1F;
        driftPitch += (targetDriftPitch - driftPitch) * 0.1F;
    }

    // ==================== MOVEMENT-AWARE BODY YAW ====================

    private static void tickMovementAwareBodyYaw(ClientPlayerEntity player) {
        Vec3d velocity = player.getVelocity();
        double speed = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
        if (speed > 0.01) {
            float moveYaw = (float) Math.toDegrees(Math.atan2(-velocity.x, velocity.z));
            lastMoveYaw += MathHelper.wrapDegrees(moveYaw - lastMoveYaw) * 0.3F;
            float serverYaw = RotationHandler.getServerYaw();
            float blended = lastMoveYaw + MathHelper.wrapDegrees(serverYaw - lastMoveYaw) * 0.6F;
            player.bodyYaw = blended;
        }
    }

    // ==================== ANTI-AFK MICRO-MOVEMENTS ====================

    private static void tickMicroMovements(ClientPlayerEntity player) {
        microMoveTicks++;
        // Occasional small strafe or sprint for naturalness
        if (microMoveTicks > randInt(40, 100)) {
            microMoveTicks = 0;
        }
    }

    // ==================== LINE OF SIGHT ====================

    private static boolean hasLineOfSight(MinecraftClient client, ClientPlayerEntity player,
                                           LivingEntity target) {
        Vec3d eyePos = player.getEyePos();
        Vec3d[] points = {
                new Vec3d(target.getX(), target.getY() + target.getHeight() * 0.85, target.getZ()),
                new Vec3d(target.getX(), target.getY() + target.getHeight() * 0.5, target.getZ()),
                new Vec3d(target.getX(), target.getY() + 0.1, target.getZ())
        };
        for (Vec3d point : points) {
            BlockHitResult result = client.world.raycast(new RaycastContext(
                    eyePos, point,
                    RaycastContext.ShapeType.COLLIDER,
                    RaycastContext.FluidHandling.NONE,
                    player
            ));
            if (result.getType() == HitResult.Type.MISS) return true;
        }
        return false;
    }

    // ==================== TARGET FINDING & SELECTION ====================

    private static List<LivingEntity> findTargets(MinecraftClient client, ClientPlayerEntity player,
                                                   float range, boolean hitMobs, boolean hitPlayers,
                                                   float fov) {
        List<LivingEntity> result = new ArrayList<>();
        Vec3d lookVec = player.getRotationVecClient();

        for (Entity entity : client.world.getEntities()) {
            if (entity == player || !(entity instanceof LivingEntity living)) continue;
            if (!living.isAlive() || living.getHealth() <= 0.0F) continue;

            double dist = getHitboxAwareDistance(player, living);
            if (dist > range + 0.5) continue;

            Vec3d toEntity = living.getPos().subtract(player.getPos()).normalize();
            double dot = lookVec.dotProduct(toEntity);
            double angle = Math.toDegrees(Math.acos(MathHelper.clamp(dot, -1.0, 1.0)));
            if (angle > fov / 2.0) continue;

            boolean isMob = entity instanceof Monster;
            boolean isAnimal = entity instanceof AnimalEntity;
            boolean isPlayer = entity instanceof PlayerEntity;

            if (isMob && !hitMobs) continue;
            if (isAnimal && !hitMobs) continue;
            if (isPlayer && !hitPlayers) continue;
            if (!isMob && !isAnimal && !isPlayer) continue;

            result.add(living);
        }
        return result;
    }

    private static void sortTargets(List<LivingEntity> targets, ClientPlayerEntity player,
                                    String mode) {
        Vec3d look = player.getRotationVecClient();
        switch (mode) {
            case "Health" -> targets.sort(Comparator.comparingDouble(LivingEntity::getHealth));
            case "Angle" -> targets.sort(Comparator.comparingDouble(e -> {
                Vec3d toE = e.getPos().subtract(player.getPos()).normalize();
                return -look.dotProduct(toE);
            }));
            default -> targets.sort(Comparator.comparingDouble(e -> {
                double dist = player.distanceTo(e);
                double healthW = (e.getHealth() / 20.0) * 0.3;
                Vec3d toE = e.getPos().subtract(player.getPos()).normalize();
                double angleW = (1.0 - look.dotProduct(toE)) * 0.4;
                return dist + healthW + angleW;
            }));
        }
    }

    // ==================== ANGLE COMPUTATION ====================

    /**
     * Compute CLEAN rotation angles pointing at the center of the target's hitbox.
     * No noise, no randomization — this is what the server must see.
     * Aims at the center of the bounding box for maximum raycast tolerance.
     */
    private static float[] getCleanTargetAngles(ClientPlayerEntity player, LivingEntity target) {
        Vec3d eyePos = player.getEyePos();
        Box box = target.getBoundingBox();

        // Aim at center of bounding box — maximum distance from all edges
        double targetX = (box.minX + box.maxX) / 2.0;
        double targetY = (box.minY + box.maxY) / 2.0;
        double targetZ = (box.minZ + box.maxZ) / 2.0;

        double dx = targetX - eyePos.x;
        double dy = targetY - eyePos.y;
        double dz = targetZ - eyePos.z;
        double dist = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) Math.toDegrees(-Math.atan2(dy, dist));
        return new float[]{yaw, MathHelper.clamp(pitch, -90.0F, 90.0F)};
    }

    // ==================== HITBOX-AWARE DISTANCE ====================

    private static double getHitboxAwareDistance(ClientPlayerEntity player, LivingEntity target) {
        Vec3d eyePos = player.getEyePos();
        Box box = target.getBoundingBox();
        double closestX = MathHelper.clamp(eyePos.x, box.minX, box.maxX);
        double closestY = MathHelper.clamp(eyePos.y, box.minY, box.maxY);
        double closestZ = MathHelper.clamp(eyePos.z, box.minZ, box.maxZ);
        double dx = eyePos.x - closestX;
        double dy = eyePos.y - closestY;
        double dz = eyePos.z - closestZ;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    // ==================== CLICK DELAY (GAUSSIAN) ====================

    private static int computeGaussianDelay(float minAps, float maxAps) {
        float mean = (minAps + maxAps) / 2.0F;
        float std = (maxAps - minAps) / 4.0F;
        double cps = mean + rng().nextGaussian() * std;
        cps = MathHelper.clamp(cps, minAps, maxAps);
        int delay = (int) Math.round(20.0 / cps);
        delay += randInt(-1, 1);
        return Math.max(1, delay);
    }

    // ==================== UTILITY ====================

    private static float getFloat(Module mod, String name, float fallback) {
        ModuleSetting s = mod.getSetting(name);
        return s != null ? s.getFloat() : fallback;
    }

    private static boolean getBool(Module mod, String name) {
        ModuleSetting s = mod.getSetting(name);
        return s != null && s.getBool();
    }

    private static String getChoice(Module mod, String name) {
        ModuleSetting s = mod.getSetting(name);
        return s != null ? s.getChoiceValue() : "";
    }

    private static ThreadLocalRandom rng() {
        return ThreadLocalRandom.current();
    }

    private static float randFloat(float min, float max) {
        return min + rng().nextFloat() * (max - min);
    }

    private static int randInt(int min, int max) {
        return rng().nextInt(min, max + 1);
    }

    private static void reset() {
        currentTarget = null;
        smoothInitialized = false;
        reactionDelayActive = false;
        preAimDone = false;
        pauseTicks = 0;
        missTicksRemaining = 0;
        doubleClickQueued = false;
        skipNextClick = false;
        switchThreshold = 60;
        bezierActive = false;
        microMoveTicks = 0;
        // Polar state
        gaussianOffsetYaw = 0.0F;
        gaussianOffsetPitch = 0.0F;
        gaussianTargetOffsetYaw = 0.0F;
        gaussianTargetOffsetPitch = 0.0F;
        gaussianChangeTicks = 0;
        shortStopTicks = 0;
        fightDurationTicks = 0;
        vlDecayCooldown = 0;
        vlDecayInterval = randInt(200, 400);
        postRotationDelay = 0;
        accelYawVelocity = 0.0F;
        accelPitchVelocity = 0.0F;
        RotationHandler.setActive(false);
        RotationHandler.clearMovementCorrection();
    }

    public static LivingEntity getCurrentTarget() {
        return currentTarget;
    }

    private KillAuraHandler() {
    }
}
