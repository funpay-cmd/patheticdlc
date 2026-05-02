package com.pathdlc.digger.visual;

import com.pathdlc.digger.gui.Module;
import com.pathdlc.digger.gui.ModuleManager;
import com.pathdlc.digger.gui.ModuleSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;

/**
 * Central hub for the World/Visuals modules that react to player events:
 * HitParticles (extra particles around target on attack), HitSound (custom
 * sound on attack), and JumpCircle (expanding ring of particles when the
 * player leaves the ground).
 */
public final class VisualEffectsHook {
   private static boolean wasOnGround = true;
   private static int slowTickCounter = 0;

   private VisualEffectsHook() {
   }

   public static void onAttackEntity(Entity target) {
      if (target == null) {
         return;
      }
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc == null || mc.world == null) {
         return;
      }
      spawnHitParticles(mc.world, target);
      playHitSound(mc);
   }

   public static void onClientTick(MinecraftClient mc) {
      if (mc == null) {
         return;
      }
      ClientPlayerEntity player = mc.player;
      ClientWorld world = mc.world;
      if (player == null || world == null) {
         return;
      }
      boolean onGround = player.isOnGround();
      if (ModuleManager.isEnabled("JumpCircle") && wasOnGround && !onGround && player.getVelocity().y > 0.0) {
         spawnJumpCircle(world, player);
      }
      wasOnGround = onGround;

      slowTickCounter++;
      if (slowTickCounter % 4 == 0) {
         tickTargetEffect(mc, world);
         tickChinaHat(mc, world);
      }
   }

   private static void tickTargetEffect(MinecraftClient mc, ClientWorld world) {
      if (!ModuleManager.isEnabled("TargetEffect")) {
         return;
      }
      HitResult hit = mc.crosshairTarget;
      if (!(hit instanceof EntityHitResult ehr)) {
         return;
      }
      Entity target = ehr.getEntity();
      if (!(target instanceof LivingEntity living)) {
         return;
      }
      ParticleEffect effect = pickParticle("TargetEffect", "Type", ParticleTypes.HEART);
      double cx = living.getX();
      double cy = living.getY() + 0.05;
      double cz = living.getZ();
      double radius = Math.max(0.45, living.getWidth() * 0.6);
      int count = 16;
      for (int i = 0; i < count; i++) {
         double angle = (Math.PI * 2.0 * i) / count;
         double px = cx + Math.cos(angle) * radius;
         double pz = cz + Math.sin(angle) * radius;
         world.addParticle(effect, px, cy, pz, 0.0, 0.0, 0.0);
      }
   }

   private static void tickChinaHat(MinecraftClient mc, ClientWorld world) {
      if (!ModuleManager.isEnabled("ChinaHat") || mc.player == null) {
         return;
      }
      ParticleEffect effect = pickParticle("ChinaHat", "Type", ParticleTypes.END_ROD);
      double maxSq = 64.0 * 64.0;
      for (AbstractClientPlayerEntity p : world.getPlayers()) {
         if (p == null || p.isInvisible()) {
            continue;
         }
         if (p == mc.player && mc.options.getPerspective().isFirstPerson()) {
            continue;
         }
         if (p.squaredDistanceTo(mc.player) > maxSq) {
            continue;
         }
         double cx = p.getX();
         double cy = p.getY() + p.getHeight() + 0.4;
         double cz = p.getZ();
         int rings = 3;
         for (int r = 0; r < rings; r++) {
            double radius = 0.55 - r * 0.18;
            double yOff = r * 0.22;
            int count = 12 - r * 3;
            for (int i = 0; i < count; i++) {
               double angle = (Math.PI * 2.0 * i) / count;
               double px = cx + Math.cos(angle) * radius;
               double pz = cz + Math.sin(angle) * radius;
               world.addParticle(effect, px, cy + yOff, pz, 0.0, 0.0, 0.0);
            }
         }
      }
   }

   private static void spawnHitParticles(ClientWorld world, Entity target) {
      if (!ModuleManager.isEnabled("HitParticles")) {
         return;
      }
      ParticleEffect effect = pickParticle("HitParticles", "Type", ParticleTypes.HEART);
      double cx = target.getX();
      double cy = target.getY() + target.getHeight() * 0.5;
      double cz = target.getZ();
      int count = 12;
      for (int i = 0; i < count; i++) {
         double angle = (Math.PI * 2.0 * i) / count;
         double radius = 0.6 + Math.random() * 0.2;
         double px = cx + Math.cos(angle) * radius;
         double pz = cz + Math.sin(angle) * radius;
         double py = cy + (Math.random() - 0.5) * target.getHeight() * 0.6;
         world.addParticle(effect, px, py, pz, 0.0, 0.05, 0.0);
      }
   }

   private static void playHitSound(MinecraftClient mc) {
      if (!ModuleManager.isEnabled("HitSound") || mc.player == null) {
         return;
      }
      Module mod = ModuleManager.get("HitSound");
      String choice = "BitClick";
      float volume = 0.6F;
      float pitch = 1.0F;
      if (mod != null) {
         ModuleSetting cs = mod.getSetting("Sound");
         if (cs != null) {
            choice = cs.getChoiceValue();
         }
         ModuleSetting vs = mod.getSetting("Volume");
         if (vs != null) {
            volume = vs.getFloat();
         }
         ModuleSetting ps = mod.getSetting("Pitch");
         if (ps != null) {
            pitch = ps.getFloat();
         }
      }
      SoundEvent sound;
      switch (choice) {
         case "Bell":
            sound = SoundEvents.BLOCK_NOTE_BLOCK_BELL.value();
            break;
         case "Anvil":
            sound = SoundEvents.BLOCK_ANVIL_LAND;
            break;
         case "Snap":
            sound = SoundEvents.UI_BUTTON_CLICK.value();
            break;
         case "Wood":
            sound = SoundEvents.BLOCK_BAMBOO_WOOD_BREAK;
            break;
         case "BitClick":
         default:
            sound = SoundEvents.BLOCK_NOTE_BLOCK_BIT.value();
            break;
      }
      mc.player.playSound(sound, volume, pitch);
   }

   private static void spawnJumpCircle(ClientWorld world, ClientPlayerEntity player) {
      ParticleEffect effect = pickParticle("JumpCircle", "Type", ParticleTypes.END_ROD);
      double cx = player.getX();
      double cy = player.getY() + 0.05;
      double cz = player.getZ();
      int count = 24;
      double radius = 0.8;
      for (int i = 0; i < count; i++) {
         double angle = (Math.PI * 2.0 * i) / count;
         double px = cx + Math.cos(angle) * radius;
         double pz = cz + Math.sin(angle) * radius;
         double vx = Math.cos(angle) * 0.08;
         double vz = Math.sin(angle) * 0.08;
         world.addParticle(effect, px, cy, pz, vx, 0.0, vz);
      }
   }

   private static ParticleEffect pickParticle(String moduleName, String settingName, ParticleEffect fallback) {
      Module mod = ModuleManager.get(moduleName);
      if (mod == null) {
         return fallback;
      }
      ModuleSetting setting = mod.getSetting(settingName);
      if (setting == null) {
         return fallback;
      }
      switch (setting.getChoiceValue()) {
         case "Hearts":
            return ParticleTypes.HEART;
         case "Sparks":
            return ParticleTypes.END_ROD;
         case "Crit":
            return ParticleTypes.CRIT;
         case "Magic":
            return ParticleTypes.ENCHANT;
         case "Soul":
            return ParticleTypes.SOUL_FIRE_FLAME;
         case "Flame":
            return ParticleTypes.FLAME;
         case "Cloud":
            return ParticleTypes.CLOUD;
         default:
            return fallback;
      }
   }
}
