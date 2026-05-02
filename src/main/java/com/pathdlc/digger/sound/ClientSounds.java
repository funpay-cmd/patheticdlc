package com.pathdlc.digger.sound;

import com.pathdlc.digger.gui.Module;
import com.pathdlc.digger.gui.ModuleManager;
import com.pathdlc.digger.gui.ModuleSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

/**
 * Helper for the bundled UI sound bank (clickgui_open, toggle, typing, ...).
 * Each play call honours the master "Sounds" toggle so users can mute the
 * whole pack with a single switch in the ClickGUI.
 */
public final class ClientSounds {
   public static final Identifier CLICKGUI_OPEN = id("clickgui_open");
   public static final Identifier TOGGLE = id("toggle");
   public static final Identifier APPLEPAY = id("applepay");
   public static final Identifier WELCOME = id("welcome");
   public static final Identifier TYPING = id("typing");
   public static final Identifier CRITICAL = id("critical");
   public static final Identifier CLOCK = id("clock");
   public static final Identifier BASS = id("bass");
   public static final Identifier BONK = id("bonk");
   public static final Identifier CHIME = id("chime");
   public static final Identifier UWU = id("uwu");
   public static final Identifier PUNCH = id("punch");

   private static Identifier id(String name) {
      return Identifier.of("pathdlc_digger", name);
   }

   private ClientSounds() {
   }

   public static void play(Identifier id, float volume, float pitch) {
      Module sounds = ModuleManager.get("Sounds");
      if (sounds != null && !sounds.isEnabled()) {
         return;
      }
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc == null) {
         return;
      }
      float vol = volume;
      if (sounds != null) {
         ModuleSetting v = sounds.getSetting("Volume");
         if (v != null) {
            vol *= Math.max(0.0F, Math.min(1.0F, v.getFloat()));
         }
      }
      if (vol <= 0.0F) {
         return;
      }
      mc.getSoundManager().play(PositionedSoundInstance.master(SoundEvent.of(id), pitch, vol));
   }

   public static void play(Identifier id) {
      play(id, 1.0F, 1.0F);
   }
}
