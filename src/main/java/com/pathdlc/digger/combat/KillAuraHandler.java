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
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * Advanced KillAura with 8 anti-cheat bypass techniques:
 *
 * 1. Silent Aim — server-side only rotation via packet interception
 * 2. GCD Spoofing — deltas rounded to sensitivity-based GCD
 * 3. Smooth / Interpolated rotations with overshoot
 * 4. Humanization — jitter, intentional misses, reaction time
 * 5. Weighted hit selection (dist / FOV / HP / time)
 * 6. Click humanization — Gaussian CPS, double-clicks, skips
 * 7. Pre-aim — rotate toward target 1-3 ticks before attack
 * 8. Movement-aware — body yaw synced with movement direction
 */
public final class KillAuraHandler {

    // --- Target state ---
    private static LivingEntity currentTarget;
    private static int switchTimer;
    private static int ticksSinceAttack;
    private static boolean isAiming;

    // --- Reaction time ---
    private static long targetAcquiredMs;
    private static boolean reactionDelayActive;

    // --- Smooth rotation state ---
    private static float smoothYaw, smoothPitch;
    private static boolean smoothInitialized;

    // --- Overshoot state ---
    private static int overshootPhase; // 0=none, 1=overshoot, 2=correct
    private static int overshootTicks;
    private static float overshootDeltaYaw, overshootDeltaPitch;

    // --- Pre-aim state ---
    private static int preAimTicks;
    private static boolean preAimDone;

    // --- Click humanization ---
    private static int nextAttackDelay;
    private static boolean skipNextClick;
    private static boolean doubleClickQueued;

    // --- Micro-pause (fatigue) ---
    private static int pauseTicks;

    // --- Intentional miss ---
    private static int missTicksRemaining;
    private static float missOffsetYaw, missOffsetPitch;

    // --- Drift simulation ---
    private static float driftYaw, driftPitch;
    private static float targetDriftYaw, targetDriftPitch;
    private static int driftChangeTicks;

    // --- Movement-aware body yaw ---
    private static float lastMoveYaw;

    // --- Target switch threshold (generated once per switch cycle) ---
    private static int switchThreshold = 60;

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
        float range = getFloat(mod, "Range", 3.2F);
        float aimSpeed = getFloat(mod, "Aim Speed", 55.0F);
        float minAps = getFloat(mod, "Min APS", 8.0F);
        float maxAps = getFloat(mod, "Max APS", 12.0F);
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

        // --- GCD setup ---
        if (gcdFix) {
            RotationHandler.updateGCD(sensitivity);
        } else {
            RotationHandler.clearGCD();
        }

        // --- Silent Aim toggle ---
        RotationHandler.setActive(silentAim);

        // --- Fatigue micro-pause ---
        if (pauseTicks > 0) { pauseTicks--; return; }
        if (rng().nextFloat() < 0.003F) {
            pauseTicks = randInt(2, 6);
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
            }
            isAiming = false;
            RotationHandler.setActive(false);
            return;
        }

        // (5) Weighted hit selection
        sortTargets(targets, player, targetMode);

        // --- Target switching ---
        switchTimer++;
        boolean needSwitch = currentTarget == null
                || !currentTarget.isAlive()
                || player.distanceTo(currentTarget) > range + 0.5F
                || !targets.contains(currentTarget)
                || switchTimer > switchThreshold;

        if (needSwitch) {
            LivingEntity prev = currentTarget;
            currentTarget = targets.get(0);
            switchTimer = 0;
            switchThreshold = randInt(40, 80);
            if (prev != currentTarget) {
                // (4) Reaction time on new target
                targetAcquiredMs = System.currentTimeMillis();
                reactionDelayActive = true;
                preAimDone = false;
                preAimTicks = randInt(1, 3);
                smoothInitialized = false;
                overshootPhase = 0;
            }
        }

        // (4) Reaction delay — wait before starting to aim
        if (reactionDelayActive) {
            float actualReaction = reactionMs + randFloat(-30.0F, 30.0F);
            long elapsed = System.currentTimeMillis() - targetAcquiredMs;
            if (elapsed < (long) actualReaction) {
                return;
            }
            reactionDelayActive = false;
        }

        // --- Compute desired server rotation ---
        float[] desired = getTargetAngles(player, currentTarget);

        // (4) Intentional miss simulation
        if (missTicksRemaining > 0) {
            desired[0] += missOffsetYaw;
            desired[1] += missOffsetPitch;
            missTicksRemaining--;
        } else if (rng().nextFloat() < 0.008F) {
            missTicksRemaining = randInt(3, 8);
            missOffsetYaw = randFloat(-3.0F, 3.0F);
            missOffsetPitch = randFloat(-2.0F, 2.0F);
        }

        float newYaw, newPitch;
        if (humanAim) {
            // (3) Smooth rotation with adaptive speed
            float[] smoothed = tickSmoothRotation(player, desired, aimSpeed);
            newYaw = smoothed[0];
            newPitch = smoothed[1];

            // (1) Overshoot simulation
            float[] overshootDeltas = tickOvershoot();
            newYaw += overshootDeltas[0];
            newPitch += overshootDeltas[1];

            // (4) Micro-jitter
            newYaw += randFloat(-0.4F, 0.4F);
            newPitch += randFloat(-0.2F, 0.2F);

            // (4) Drift
            tickDrift();
            newYaw += driftYaw;
            newPitch += driftPitch;

            // (4) Micro-tremor (sinusoidal)
            long ms = System.currentTimeMillis();
            newYaw += (float)(Math.sin(ms * 0.013) * 0.2 + Math.sin(ms * 0.0037) * 0.12);
            newPitch += (float)(Math.sin(ms * 0.011) * 0.1 + Math.cos(ms * 0.0029) * 0.06);
        } else {
            // Direct aim (rage mode) — snap to target with minimal jitter
            newYaw = desired[0] + randFloat(-0.5F, 0.5F);
            newPitch = desired[1] + randFloat(-0.3F, 0.3F);
            smoothInitialized = false;
        }

        newPitch = MathHelper.clamp(newPitch, -90.0F, 90.0F);

        // (2) GCD spoofing
        RotationHandler.setServerRotation(newYaw, newPitch);
        if (gcdFix) {
            RotationHandler.applyGCDToServerRotation();
        }

        // (8) Movement-aware body yaw
        if (moveAware) {
            tickMovementAwareBodyYaw(player, silentAim);
        }

        // Apply rotation to player or keep silent
        if (!silentAim) {
            RotationHandler.applyToPlayer(player);
        }

        RotationHandler.commitServerRotation();

        // --- Aim readiness check ---
        float yawDiff = MathHelper.wrapDegrees(desired[0] - RotationHandler.getServerYaw());
        float pitchDiff = desired[1] - RotationHandler.getServerPitch();
        float totalDiff = MathHelper.sqrt(yawDiff * yawDiff + pitchDiff * pitchDiff);
        isAiming = totalDiff < randFloat(5.0F, 9.0F);

        // (7) Pre-aim — don't attack until pre-aim ticks have passed
        if (!preAimDone) {
            preAimTicks--;
            if (preAimTicks <= 0) {
                preAimDone = true;
            }
            return;
        }

        // --- (6) Click humanization + attack logic ---
        ticksSinceAttack++;

        // (6) Double-click: fires on the tick after main attack with reduced cooldown
        if (doubleClickQueued && player.getAttackCooldownProgress(0.0F) >= 0.5F) {
            doubleClickQueued = false;
            doAttack(client, player, silentAim);
            return;
        }

        // Skip click simulation
        if (skipNextClick) {
            skipNextClick = false;
            if (rng().nextFloat() < 0.08F) {
                skipNextClick = true; // chain skip (rare)
            }
            return;
        }

        if (player.getAttackCooldownProgress(0.0F) < 1.0F) return;
        if (ticksSinceAttack < nextAttackDelay) return;
        if (!isAiming) return;

        // (4) Only Crit check
        if (critOnly) {
            boolean falling = !player.isOnGround()
                    && player.getVelocity().y < 0.0
                    && player.fallDistance > 0.0F;
            if (!falling) return;
        }

        // Final distance re-check
        float attackRange = range - randFloat(0.0F, 0.15F);
        if (player.distanceTo(currentTarget) > attackRange) return;

        // --- ATTACK ---
        doAttack(client, player, silentAim);
        ticksSinceAttack = 0;

        // (6) Gaussian-distributed next delay
        nextAttackDelay = computeGaussianDelay(minAps, maxAps);

        // (6) Double-click chance — will fire on next tick
        if (rng().nextFloat() < 0.1F) {
            doubleClickQueued = true;
        }

        // (6) Skip click chance
        if (rng().nextFloat() < 0.05F) {
            skipNextClick = true;
        }

        // (1) Post-attack overshoot
        if (rng().nextFloat() < 0.2F) {
            overshootPhase = 1;
            overshootTicks = randInt(2, 5);
            overshootDeltaYaw = randFloat(-2.0F, 2.0F);
            overshootDeltaPitch = randFloat(-1.0F, 1.0F);
        }
    }

    private static void doAttack(MinecraftClient client, ClientPlayerEntity player, boolean silentAim) {
        if (currentTarget == null || !currentTarget.isAlive()) return;
        client.interactionManager.attackEntity(player, currentTarget);
        player.swingHand(Hand.MAIN_HAND);
    }

    // ==================== (3) SMOOTH ROTATION ====================

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

        // Adaptive speed: faster when far, slower when close (exponential)
        float yawStep = adaptiveStep(yawDiff, 40.0F) * speedMult;
        float pitchStep = adaptiveStep(pitchDiff, 30.0F) * speedMult;

        smoothYaw = currentYaw + yawStep;
        smoothPitch = MathHelper.clamp(currentPitch + pitchStep, -90.0F, 90.0F);

        return new float[]{smoothYaw, smoothPitch};
    }

    private static float adaptiveStep(float diff, float maxSpeed) {
        float abs = Math.abs(diff);
        float factor;
        if (abs > 30.0F) {
            factor = 0.55F + randFloat(-0.05F, 0.05F);
        } else if (abs > 12.0F) {
            factor = 0.35F + randFloat(-0.05F, 0.05F);
        } else if (abs > 4.0F) {
            factor = 0.2F + randFloat(-0.04F, 0.04F);
        } else if (abs > 1.0F) {
            factor = 0.12F + randFloat(-0.03F, 0.03F);
        } else {
            factor = 0.05F + randFloat(-0.02F, 0.02F);
        }
        return MathHelper.clamp(diff * factor, -maxSpeed, maxSpeed);
    }

    // ==================== (1) OVERSHOOT ====================

    private static float[] tickOvershoot() {
        if (overshootPhase == 0) return new float[]{0.0F, 0.0F};
        int ticks = Math.max(overshootTicks, 1);
        float yaw, pitch;
        if (overshootPhase == 1) {
            yaw = overshootDeltaYaw / ticks;
            pitch = overshootDeltaPitch / ticks;
            overshootTicks--;
            if (overshootTicks <= 0) {
                overshootPhase = 2;
                overshootTicks = randInt(3, 7);
            }
        } else {
            yaw = -overshootDeltaYaw * 0.65F / ticks;
            pitch = -overshootDeltaPitch * 0.65F / ticks;
            overshootTicks--;
            if (overshootTicks <= 0) {
                overshootPhase = 0;
            }
        }
        return new float[]{yaw, pitch};
    }

    // ==================== (4) DRIFT ====================

    private static void tickDrift() {
        driftChangeTicks--;
        if (driftChangeTicks <= 0) {
            targetDriftYaw = randFloat(-0.3F, 0.3F);
            targetDriftPitch = randFloat(-0.12F, 0.12F);
            driftChangeTicks = randInt(20, 60);
        }
        driftYaw += (targetDriftYaw - driftYaw) * 0.1F;
        driftPitch += (targetDriftPitch - driftPitch) * 0.1F;
    }

    // ==================== (8) MOVEMENT-AWARE BODY YAW ====================

    private static void tickMovementAwareBodyYaw(ClientPlayerEntity player, boolean silent) {
        Vec3d velocity = player.getVelocity();
        double speed = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
        if (speed > 0.01) {
            float moveYaw = (float) Math.toDegrees(Math.atan2(-velocity.x, velocity.z));
            lastMoveYaw += MathHelper.wrapDegrees(moveYaw - lastMoveYaw) * 0.3F;

            // Blend body yaw between movement direction and aim direction
            float serverYaw = RotationHandler.getServerYaw();
            float blended = lastMoveYaw + MathHelper.wrapDegrees(serverYaw - lastMoveYaw) * 0.6F;

            if (silent) {
                // Update body yaw to look natural from other players' perspective
                player.bodyYaw = blended;
                player.headYaw = player.getYaw();
            } else {
                player.bodyYaw = blended;
            }
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
            BlockPos hitPos = result.getBlockPos();
            BlockPos targetPos = new BlockPos(
                    MathHelper.floor(target.getX()),
                    MathHelper.floor(target.getY()),
                    MathHelper.floor(target.getZ()));
            if (hitPos.equals(targetPos)) return true;
        }
        return false;
    }

    // ==================== (5) TARGET FINDING & SELECTION ====================

    private static List<LivingEntity> findTargets(MinecraftClient client, ClientPlayerEntity player,
                                                   float range, boolean hitMobs, boolean hitPlayers,
                                                   float fov) {
        List<LivingEntity> result = new ArrayList<>();
        Vec3d lookVec = player.getRotationVecClient();

        for (Entity entity : client.world.getEntities()) {
            if (entity == player || !(entity instanceof LivingEntity living)) continue;
            if (!living.isAlive() || living.getHealth() <= 0.0F) continue;

            double dist = player.distanceTo(living);
            if (dist > range) continue;

            // FOV check
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

    private static float[] getTargetAngles(ClientPlayerEntity player, LivingEntity target) {
        Vec3d eyePos = player.getEyePos();
        double aimH = target.getHeight() * randFloat(0.35F, 0.75F);
        Vec3d targetPos = new Vec3d(
                target.getX() + randFloat(-0.04F, 0.04F),
                target.getY() + aimH,
                target.getZ() + randFloat(-0.04F, 0.04F)
        );
        double dx = targetPos.x - eyePos.x;
        double dy = targetPos.y - eyePos.y;
        double dz = targetPos.z - eyePos.z;
        double dist = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) Math.toDegrees(-Math.atan2(dy, dist));
        return new float[]{yaw, MathHelper.clamp(pitch, -90.0F, 90.0F)};
    }

    // ==================== (6) CLICK DELAY (GAUSSIAN) ====================

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
        isAiming = false;
        smoothInitialized = false;
        overshootPhase = 0;
        reactionDelayActive = false;
        preAimDone = false;
        pauseTicks = 0;
        missTicksRemaining = 0;
        doubleClickQueued = false;
        skipNextClick = false;
        switchThreshold = 60;
        RotationHandler.setActive(false);
    }

    public static LivingEntity getCurrentTarget() {
        return currentTarget;
    }

    private KillAuraHandler() {
    }
}
