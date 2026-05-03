package com.pathdlc.digger.util;

import java.util.ArrayDeque;
import java.util.Queue;
import net.minecraft.text.Text;
import net.minecraft.client.MinecraftClient;

public final class Chat {
   private static final Queue<String> PENDING = new ArrayDeque<>();

   public static void info(String message) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.player == null) {
         later(message);
      } else {
         mc.player.sendMessage(Text.literal("§f[§dWareVisuals§f] §7" + message), false);
      }
   }

   public static void warn(String message) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.player == null) {
         later(message);
      } else {
         mc.player.sendMessage(Text.literal("§f[§dWareVisuals§f] §e" + message), false);
      }
   }

   public static void error(String message) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.player == null) {
         later(message);
      } else {
         mc.player.sendMessage(Text.literal("§f[§dWareVisuals§f] §c" + message), false);
      }
   }

   public static void later(String message) {
      PENDING.add(message);
   }

   public static void flush() {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.player != null) {
         while (!PENDING.isEmpty()) {
            info(PENDING.poll());
         }
      }
   }

   private Chat() {
   }
}
