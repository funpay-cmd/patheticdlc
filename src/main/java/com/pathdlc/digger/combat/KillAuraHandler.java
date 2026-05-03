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

public final class KillAuraHandler {

    private static LivingEntity currentTarget;
    private static int switchTimer;
    private static int nextAttackDelay;
    private static int ticksSinceAttack;
    private static boolean isAiming;

    // Bezier curve rotation state
    private static float bezierProgress;
    private static float bezierYawStart, bezierYawEnd;
    private static float bezierPitchStart, bezierPitchEnd;
    private static float bezierCtrlYaw1, bezierCtrlPitch1;
    private static float bezierCtrlYaw2, bezierCtrlPitch2;
    private static float bezierSpeed;
    private static boolean bezierActive;

    // Human micro-correction state
    private static float overshootYaw, overshootPitch;
    private static int correctionTicks;
    private static int correctionPhase; // 0=idle, 1=overshoot, 2=correct-back

    // Fatigue / drift simulation
    private static float driftYaw, driftPitch;
    private static int driftChangeTicks;
    private static float targetDriftYaw, targetDriftPitch;

    // Pause simulation
    private static int pauseTicks;

    // Last attack tracking for dynamic delay
    private static long lastAttackTimeMs;

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

        float range = getSetting(mod, "Range", 3.2F);
        boolean critOnly = getToggle(mod, "Only Crit");
        boolean hitMobs = getToggle(mod, "Attack Mobs");
        boolean hitPlayers = getToggle(mod, "Attack Players");
        float aimSpeed = getSetting(mod, "Aim Speed", 55.0F);
        float minAps = getSetting(mod, "Min APS", 8.0F);
        float maxAps = getSetting(mod, "Max APS", 12.0F);
        float fov = getSetting(mod, "FOV", 120.0F);
        boolean losCheck = getToggle(mod, "LoS Check");
        String targetMode = getChoice(mod, "Target Mode");
        boolean smartAim = getToggle(mod, "Smart Aim");

        // Random micro-pause (human fatigue)
        if (pauseTicks > 0) {
            pauseTicks--;
            return;
        }
        if (ThreadLocalRandom.current().nextFloat() < 0.003F) {
            pauseTicks = ThreadLocalRandom.current().nextInt(2, 6);
            return;
        }

        List<LivingEntity> targets = findTargets(client, player, range, hitMobs, hitPlayers, fov);

        // Filter by line-of-sight
        if (losCheck) {
            targets.removeIf(e -> !hasLineOfSight(client, player, e));
        }

        if (targets.isEmpty()) {
            currentTarget = null;
            isAiming = false;
            bezierActive = false;
            return;
        }

        sortTargets(targets, player, targetMode);

        switchTimer++;
        boolean needSwitch = currentTarget == null
                || !currentTarget.isAlive()
                || player.distanceTo(currentTarget) > range + 0.5F
                || !targets.contains(currentTarget)
                || switchTimer > randomInt(40, 80);

        if (needSwitch) {
            LivingEntity newTarget = targets.get(0);
            if (newTarget != currentTarget) {
                currentTarget = newTarget;
                switchTimer = 0;
                initBezierCurve(player, currentTarget, aimSpeed);
            }
        }

        // Perform rotation
        if (smartAim) {
            tickHumanRotation(player, currentTarget, aimSpeed);
        } else {
            tickSimpleRotation(player, currentTarget, aimSpeed);
        }

        // Attack logic
        ticksSinceAttack++;
        if (player.getAttackCooldownProgress(0.0F) < 1.0F) return;
        if (ticksSinceAttack < nextAttackDelay) return;
        if (!isAiming) return;

        if (critOnly) {
            boolean falling = !player.isOnGround()
                    && player.getVelocity().y < 0.0
                    && player.fallDistance > 0.0F;
            if (!falling) return;
        }

        // Final distance re-check with slight randomization
        float attackRange = range - randomFloat(0.0F, 0.15F);
        if (player.distanceTo(currentTarget) > attackRange) return;

        client.interactionManager.attackEntity(player, currentTarget);
        player.swingHand(Hand.MAIN_HAND);
        ticksSinceAttack = 0;
        lastAttackTimeMs = System.currentTimeMillis();

        // Gaussian-distributed delay between attacks
        nextAttackDelay = computeNextDelay(minAps, maxAps);

        // Small chance of double-tap correction after attack
        if (ThreadLocalRandom.current().nextFloat() < 0.15F) {
            correctionPhase = 1;
            correctionTicks = randomInt(2, 5);
            overshootYaw = randomFloat(-1.5F, 1.5F);
            overshootPitch = randomFloat(-0.8F, 0.8F);
        }
    }

    // --- Human-like bezier curve rotation ---

    private static void initBezierCurve(ClientPlayerEntity player, LivingEntity target, float aimSpeed) {
        float[] dest = getTargetAngles(player, target);
        bezierYawStart = player.getYaw();
        bezierPitchStart = player.getPitch();
        bezierYawEnd = dest[0];
        bezierPitchEnd = dest[1];

        float yawDelta = MathHelper.wrapDegrees(bezierYawEnd - bezierYawStart);
        float pitchDelta = bezierPitchEnd - bezierPitchStart;

        // Control points with randomized overshoot
        float overshoot = randomFloat(0.05F, 0.2F);
        bezierCtrlYaw1 = bezierYawStart + yawDelta * randomFloat(0.3F, 0.5F)
                + randomFloat(-3.0F, 3.0F);
        bezierCtrlPitch1 = bezierPitchStart + pitchDelta * randomFloat(0.2F, 0.5F)
                + randomFloat(-1.5F, 1.5F);
        bezierCtrlYaw2 = bezierYawStart + yawDelta * (1.0F + overshoot * sign(yawDelta))
                + randomFloat(-1.5F, 1.5F);
        bezierCtrlPitch2 = bezierPitchStart + pitchDelta * (1.0F + overshoot * 0.5F)
                + randomFloat(-0.5F, 0.5F);

        float dist = MathHelper.sqrt(yawDelta * yawDelta + pitchDelta * pitchDelta);
        float normalizedSpeed = (aimSpeed / 100.0F) * randomFloat(0.85F, 1.15F);
        bezierSpeed = MathHelper.clamp(normalizedSpeed * 12.0F / Math.max(dist, 5.0F), 0.04F, 0.35F);
        bezierProgress = 0.0F;
        bezierActive = true;
    }

    private static void tickHumanRotation(ClientPlayerEntity player, LivingEntity target, float aimSpeed) {
        float[] dest = getTargetAngles(player, target);
        float yawDiff = MathHelper.wrapDegrees(dest[0] - player.getYaw());
        float pitchDiff = dest[1] - player.getPitch();
        float totalDiff = MathHelper.sqrt(yawDiff * yawDiff + pitchDiff * pitchDiff);

        // Update drift simulation
        tickDrift();

        if (bezierActive && bezierProgress < 1.0F) {
            // Follow bezier path for large angle changes
            bezierYawEnd = dest[0];
            bezierPitchEnd = dest[1];

            bezierProgress = Math.min(1.0F, bezierProgress + bezierSpeed * randomFloat(0.9F, 1.1F));
            float t = easeInOutCubic(bezierProgress);

            float newYaw = cubicBezier(t, bezierYawStart, bezierCtrlYaw1, bezierCtrlYaw2, bezierYawEnd);
            float newPitch = cubicBezier(t, bezierPitchStart, bezierCtrlPitch1, bezierCtrlPitch2, bezierPitchEnd);

            // Add micro-jitter
            newYaw += randomFloat(-0.3F, 0.3F) + driftYaw;
            newPitch += randomFloat(-0.15F, 0.15F) + driftPitch;

            player.setYaw(newYaw);
            player.setPitch(MathHelper.clamp(newPitch, -90.0F, 90.0F));

            if (bezierProgress >= 1.0F) {
                bezierActive = false;
            }
        } else {
            // Fine-tracking mode: small corrections
            tickMicroCorrections(player, dest, totalDiff, aimSpeed);
        }

        // Apply post-attack overshoot correction
        if (correctionPhase == 1 && correctionTicks > 0) {
            player.setYaw(player.getYaw() + overshootYaw / correctionTicks);
            player.setPitch(MathHelper.clamp(
                    player.getPitch() + overshootPitch / correctionTicks, -90.0F, 90.0F));
            correctionTicks--;
            if (correctionTicks <= 0) {
                correctionPhase = 2;
                correctionTicks = randomInt(3, 6);
            }
        } else if (correctionPhase == 2 && correctionTicks > 0) {
            player.setYaw(player.getYaw() - overshootYaw * 0.7F / correctionTicks);
            player.setPitch(MathHelper.clamp(
                    player.getPitch() - overshootPitch * 0.7F / correctionTicks, -90.0F, 90.0F));
            correctionTicks--;
            if (correctionTicks <= 0) {
                correctionPhase = 0;
            }
        }

        isAiming = totalDiff < randomFloat(5.0F, 9.0F);
    }

    private static void tickMicroCorrections(ClientPlayerEntity player, float[] dest,
                                              float totalDiff, float aimSpeed) {
        float yawDiff = MathHelper.wrapDegrees(dest[0] - player.getYaw());
        float pitchDiff = dest[1] - player.getPitch();

        float speedMult = (aimSpeed / 100.0F) * randomFloat(0.7F, 1.3F);

        // Non-linear speed: faster for large angles, slower for small
        float yawSpeed = computeAdaptiveSpeed(yawDiff, 40.0F) * speedMult;
        float pitchSpeed = computeAdaptiveSpeed(pitchDiff, 30.0F) * speedMult;

        // Add human micro-tremor
        float tremorYaw = (float) (Math.sin(System.currentTimeMillis() * 0.013) * 0.25
                + Math.sin(System.currentTimeMillis() * 0.0037) * 0.15);
        float tremorPitch = (float) (Math.sin(System.currentTimeMillis() * 0.011) * 0.12
                + Math.cos(System.currentTimeMillis() * 0.0029) * 0.08);

        float newYaw = player.getYaw() + yawSpeed + tremorYaw + driftYaw;
        float newPitch = player.getPitch() + pitchSpeed + tremorPitch + driftPitch;

        player.setYaw(newYaw);
        player.setPitch(MathHelper.clamp(newPitch, -90.0F, 90.0F));
    }

    private static void tickSimpleRotation(ClientPlayerEntity player, LivingEntity target, float aimSpeed) {
        float[] dest = getTargetAngles(player, target);
        float yawDiff = MathHelper.wrapDegrees(dest[0] - player.getYaw());
        float pitchDiff = dest[1] - player.getPitch();

        float speedMult = (aimSpeed / 100.0F) * randomFloat(0.8F, 1.2F);
        float maxYaw = 35.0F * speedMult;
        float maxPitch = 25.0F * speedMult;

        float jitterYaw = randomFloat(-2.0F, 2.0F);
        float jitterPitch = randomFloat(-1.0F, 1.0F);

        float yawStep = MathHelper.clamp(yawDiff + jitterYaw, -maxYaw, maxYaw);
        float pitchStep = MathHelper.clamp(pitchDiff + jitterPitch, -maxPitch, maxPitch);

        player.setYaw(player.getYaw() + yawStep);
        player.setPitch(MathHelper.clamp(player.getPitch() + pitchStep, -90.0F, 90.0F));

        isAiming = Math.abs(yawDiff) < 8.0F && Math.abs(pitchDiff) < 8.0F;
    }

    // --- Drift simulation (human hand fatigue) ---

    private static void tickDrift() {
        driftChangeTicks--;
        if (driftChangeTicks <= 0) {
            targetDriftYaw = randomFloat(-0.35F, 0.35F);
            targetDriftPitch = randomFloat(-0.15F, 0.15F);
            driftChangeTicks = randomInt(20, 60);
        }
        driftYaw += (targetDriftYaw - driftYaw) * 0.1F;
        driftPitch += (targetDriftPitch - driftPitch) * 0.1F;
    }

    // --- Line of sight ---

    private static boolean hasLineOfSight(MinecraftClient client, ClientPlayerEntity player,
                                           LivingEntity target) {
        Vec3d eyePos = player.getEyePos();

        // Check three points: head, center, feet
        Vec3d[] checkPoints = {
                new Vec3d(target.getX(), target.getY() + target.getHeight() * 0.85, target.getZ()),
                new Vec3d(target.getX(), target.getY() + target.getHeight() * 0.5, target.getZ()),
                new Vec3d(target.getX(), target.getY() + 0.1, target.getZ())
        };

        for (Vec3d point : checkPoints) {
            BlockHitResult result = client.world.raycast(new RaycastContext(
                    eyePos, point,
                    RaycastContext.ShapeType.COLLIDER,
                    RaycastContext.FluidHandling.NONE,
                    player
            ));
            if (result.getType() == HitResult.Type.MISS
                    || result.getBlockPos().equals(new BlockPos(
                    (int) Math.floor(target.getX()),
                    (int) Math.floor(target.getY()),
                    (int) Math.floor(target.getZ())))) {
                return true;
            }
        }
        return false;
    }

    // --- Target finding and sorting ---

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
            double angle = Math.toDegrees(Math.acos(MathHelper.clamp(
                    lookVec.dotProduct(toEntity), -1.0, 1.0)));
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
        switch (mode) {
            case "Health" -> targets.sort(Comparator.comparingDouble(LivingEntity::getHealth));
            case "Angle" -> {
                Vec3d look = player.getRotationVecClient();
                targets.sort(Comparator.comparingDouble(e -> {
                    Vec3d toE = e.getPos().subtract(player.getPos()).normalize();
                    return -look.dotProduct(toE); // smaller angle = higher dot product
                }));
            }
            default -> // "Distance" or fallback
                    targets.sort(Comparator.comparingDouble(e -> {
                        double d = player.distanceTo(e);
                        double healthPenalty = (e.getHealth() / 20.0) * 0.5;
                        return d + healthPenalty;
                    }));
        }
    }

    // --- Angle computation ---

    private static float[] getTargetAngles(ClientPlayerEntity player, LivingEntity target) {
        Vec3d playerEyes = player.getEyePos();

        // Randomized aim point on target body
        double aimHeight = target.getHeight() * randomFloat(0.35F, 0.75F);
        double targetY = target.getY() + aimHeight;
        Vec3d targetPos = new Vec3d(
                target.getX() + randomFloat(-0.05F, 0.05F),
                targetY,
                target.getZ() + randomFloat(-0.05F, 0.05F)
        );

        double dx = targetPos.x - playerEyes.x;
        double dy = targetPos.y - playerEyes.y;
        double dz = targetPos.z - playerEyes.z;
        double dist = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) Math.toDegrees(-Math.atan2(dy, dist));
        return new float[]{yaw, MathHelper.clamp(pitch, -90.0F, 90.0F)};
    }

    // --- Attack delay computation (Gaussian) ---

    private static int computeNextDelay(float minAps, float maxAps) {
        float meanAps = (minAps + maxAps) / 2.0F;
        float stdDev = (maxAps - minAps) / 4.0F;

        // Gaussian CPS with clamping
        double gaussianAps = meanAps + ThreadLocalRandom.current().nextGaussian() * stdDev;
        gaussianAps = MathHelper.clamp(gaussianAps, minAps, maxAps);

        // Convert APS to tick delay (20 ticks/sec)
        int delay = (int) Math.round(20.0 / gaussianAps);

        // Small random variance on top
        delay += randomInt(-1, 1);
        return Math.max(1, delay);
    }

    // --- Bezier math ---

    private static float cubicBezier(float t, float p0, float p1, float p2, float p3) {
        float u = 1.0F - t;
        return u * u * u * p0
                + 3.0F * u * u * t * p1
                + 3.0F * u * t * t * p2
                + t * t * t * p3;
    }

    private static float easeInOutCubic(float t) {
        return t < 0.5F
                ? 4.0F * t * t * t
                : 1.0F - (float) Math.pow(-2.0F * t + 2.0F, 3) / 2.0F;
    }

    // --- Adaptive speed (non-linear) ---

    private static float computeAdaptiveSpeed(float diff, float maxSpeed) {
        float absDiff = Math.abs(diff);
        // Quadratic curve: fast when far, slow when close
        float factor;
        if (absDiff > 15.0F) {
            factor = 0.6F + randomFloat(-0.05F, 0.05F);
        } else if (absDiff > 5.0F) {
            factor = 0.3F + randomFloat(-0.05F, 0.05F);
        } else if (absDiff > 1.5F) {
            factor = 0.15F + randomFloat(-0.03F, 0.03F);
        } else {
            factor = 0.06F + randomFloat(-0.02F, 0.02F);
        }
        return MathHelper.clamp(diff * factor, -maxSpeed, maxSpeed);
    }

    // --- Utility ---

    private static float getSetting(Module mod, String name, float fallback) {
        ModuleSetting s = mod.getSetting(name);
        return s != null ? s.getFloat() : fallback;
    }

    private static boolean getToggle(Module mod, String name) {
        ModuleSetting s = mod.getSetting(name);
        return s != null && s.getBool();
    }

    private static String getChoice(Module mod, String name) {
        ModuleSetting s = mod.getSetting(name);
        return s != null ? s.getChoiceValue() : "";
    }

    private static float randomFloat(float min, float max) {
        return min + ThreadLocalRandom.current().nextFloat() * (max - min);
    }

    private static int randomInt(int min, int max) {
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    private static float sign(float v) {
        return v >= 0 ? 1.0F : -1.0F;
    }

    private static void reset() {
        currentTarget = null;
        isAiming = false;
        bezierActive = false;
        correctionPhase = 0;
        pauseTicks = 0;
    }

    public static LivingEntity getCurrentTarget() {
        return currentTarget;
    }

    private KillAuraHandler() {
    }
}
