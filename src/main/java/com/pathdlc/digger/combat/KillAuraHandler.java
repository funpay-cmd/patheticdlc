package com.pathdlc.digger.combat;

import com.pathdlc.digger.gui.Module;
import com.pathdlc.digger.gui.ModuleManager;
import com.pathdlc.digger.gui.ModuleSetting;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.util.Hand;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;
import net.minecraft.client.network.ClientPlayerEntity;

public final class KillAuraHandler {
   private static LivingEntity currentTarget;
   private static int switchTimer;
   private static int nextAttackDelay;
   private static int ticksSinceAttack;
   private static boolean isAiming;
   private static final float MAX_YAW_SPEED = 35.0F;
   private static final float MAX_PITCH_SPEED = 25.0F;
   private static final float AIM_THRESHOLD = 8.0F;

   public static void tick(MinecraftClient client) {
      if (!ModuleManager.isEnabled("KillAura")) {
         currentTarget = null;
         isAiming = false;
      } else {
         ClientPlayerEntity player = client.player;
         if (player != null && client.world != null) {
            if (client.interactionManager != null) {
               Module mod = ModuleManager.get("KillAura");
               if (mod != null) {
                  ModuleSetting rangeSetting = mod.getSetting("Range");
                  ModuleSetting onlyCrit = mod.getSetting("Only Crit");
                  ModuleSetting attackMobs = mod.getSetting("Attack Mobs");
                  ModuleSetting attackPlayers = mod.getSetting("Attack Players");
                  float range = rangeSetting != null ? rangeSetting.getFloat() : 4.0F;
                  boolean critOnly = onlyCrit != null && onlyCrit.getBool();
                  boolean hitMobs = attackMobs != null && attackMobs.getBool();
                  boolean hitPlayers = attackPlayers != null && attackPlayers.getBool();
                  List<LivingEntity> targets = findTargets(client, player, range, hitMobs, hitPlayers);
                  if (targets.isEmpty()) {
                     currentTarget = null;
                     isAiming = false;
                  } else {
                     switchTimer++;
                     if (currentTarget == null || !currentTarget.isAlive() || player.distanceTo(currentTarget) > range || switchTimer > 60) {
                        currentTarget = targets.get(0);
                        switchTimer = 0;
                     }

                     float[] targetAngles = getRotation(player, currentTarget);
                     float yawDiff = MathHelper.wrapDegrees(targetAngles[0] - player.getYaw());
                     float pitchDiff = targetAngles[1] - player.getPitch();
                     float jitterYaw = (ThreadLocalRandom.current().nextFloat() - 0.5F) * 2.0F;
                     float jitterPitch = (ThreadLocalRandom.current().nextFloat() - 0.5F) * 1.0F;
                     float yawStep = MathHelper.clamp(yawDiff + jitterYaw, -35.0F, 35.0F);
                     float pitchStep = MathHelper.clamp(pitchDiff + jitterPitch, -25.0F, 25.0F);
                     player.setYaw(player.getYaw() + yawStep);
                     player.setPitch(MathHelper.clamp(player.getPitch() + pitchStep, -90.0F, 90.0F));
                     isAiming = Math.abs(yawDiff) < 8.0F && Math.abs(pitchDiff) < 8.0F;
                     ticksSinceAttack++;
                     if (!(player.getAttackCooldownProgress(0.0F) < 1.0F)) {
                        if (ticksSinceAttack >= nextAttackDelay) {
                           if (isAiming) {
                              if (critOnly) {
                                 boolean falling = !player.isOnGround() && player.getVelocity().y < 0.0 && player.fallDistance > 0.0F;
                                 if (!falling) {
                                    return;
                                 }
                              }

                              client.interactionManager.attackEntity(player, currentTarget);
                              player.swingHand(Hand.MAIN_HAND);
                              ticksSinceAttack = 0;
                              nextAttackDelay = ThreadLocalRandom.current().nextInt(1, 4);
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private static float[] getRotation(ClientPlayerEntity player, LivingEntity target) {
      Vec3d playerEyes = player.getEyePos();
      double targetY = target.getY()
         + (double)target.getHeight() * 0.4
         + ThreadLocalRandom.current().nextDouble() * (double)target.getHeight() * 0.4;
      Vec3d targetPos = new Vec3d(target.getX(), targetY, target.getZ());
      double dx = targetPos.x - playerEyes.x;
      double dy = targetPos.y - playerEyes.y;
      double dz = targetPos.z - playerEyes.z;
      double dist = Math.sqrt(dx * dx + dz * dz);
      float yaw = (float)Math.toDegrees(Math.atan2(-dx, dz));
      float pitch = (float)Math.toDegrees(-Math.atan2(dy, dist));
      pitch = MathHelper.clamp(pitch, -90.0F, 90.0F);
      return new float[]{yaw, pitch};
   }

   private static List<LivingEntity> findTargets(MinecraftClient client, ClientPlayerEntity player, float range, boolean hitMobs, boolean hitPlayers) {
      List<LivingEntity> result = new ArrayList<>();

      for (Entity entity : client.world.getEntities()) {
         if (entity != player && entity instanceof LivingEntity) {
            LivingEntity living = (LivingEntity)entity;
            if (living.isAlive() && !(living.getHealth() <= 0.0F)) {
               double dist = (double)player.distanceTo(living);
               if (!(dist > (double)range)) {
                  boolean isMob = entity instanceof Monster;
                  boolean isAnimal = entity instanceof AnimalEntity;
                  boolean isPlayer = entity instanceof PlayerEntity;
                  if ((!isMob || hitMobs) && (!isAnimal || hitMobs) && (!isPlayer || hitPlayers) && (isMob || isAnimal || isPlayer)) {
                     result.add(living);
                  }
               }
            }
         }
      }

      result.sort(Comparator.comparingDouble(e -> {
         double d = (double)player.distanceTo(e);
         double healthPenalty = (double)e.getHealth() / 20.0 * 0.5;
         return d + healthPenalty;
      }));
      return result;
   }

   public static LivingEntity getCurrentTarget() {
      return currentTarget;
   }

   private KillAuraHandler() {
   }
}
