package com.pathdlc.digger.event;

import com.pathdlc.digger.gui.Module;
import com.pathdlc.digger.gui.ModuleManager;
import com.pathdlc.digger.gui.ModuleSetting;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

/**
 * Listens for FunTime "/event delay" responses, caches upcoming events with
 * coordinates and start time, and exposes them to the HUD. Read-only —
 * no auto-pathing, no automated movement.
 */
public final class AutoEventBot {
   public static final class EventEntry {
      public final String name;
      public final int x;
      public final int y;
      public final int z;
      public final long startEpochMs;
      public final long discoveredAt;

      EventEntry(String name, int x, int y, int z, long startEpochMs, long discoveredAt) {
         this.name = name;
         this.x = x;
         this.y = y;
         this.z = z;
         this.startEpochMs = startEpochMs;
         this.discoveredAt = discoveredAt;
      }
   }

   private static final List<EventEntry> EVENTS = new ArrayList<>();
   private static final long EVENT_TTL_MS = 30L * 60L * 1000L;
   private static final long QUERY_INTERVAL_MS = 30_000L;
   private static long lastQueryAt = 0L;
   private static int joinSettleTicks = 0;

   private static final Pattern COORDS_TAGGED = Pattern.compile(
      "(?i)x\\s*[:=]?\\s*(-?\\d+)[\\s,;]+y\\s*[:=]?\\s*(-?\\d+)[\\s,;]+z\\s*[:=]?\\s*(-?\\d+)"
   );
   private static final Pattern COORDS_PLAIN = Pattern.compile(
      "(-?\\d{1,7})\\s+(-?\\d{1,3})\\s+(-?\\d{1,7})"
   );
   private static final Pattern TIME_HMS = Pattern.compile(
      "(\\d{1,2}):(\\d{2})(?::(\\d{2}))?"
   );
   private static final Pattern TIME_RELATIVE = Pattern.compile(
      "(?i)\\b(\\d{1,3})\\s*(сек|секунд[ыау]?|мин|минут[ыау]?|час[аов]?|s|sec|m|min|h|hour)s?\\b"
   );
   private static final Pattern KEYWORDS = Pattern.compile(
      "(?i)событ|event|ивент|босс|boss|конкурс|drop\\s*party|раздач|спавн|loot|сундук|анарх"
   );
   private static final Pattern QUOTED = Pattern.compile("[\"\u00ab\u201c]([^\"\u00bb\u201d]{2,40})[\"\u00bb\u201d]");

   private AutoEventBot() {
   }

   public static void onClientTick(MinecraftClient client) {
      if (!isEnabled()) {
         return;
      }
      if (client == null || client.player == null || client.world == null || client.getNetworkHandler() == null) {
         joinSettleTicks = 0;
         return;
      }
      long now = System.currentTimeMillis();
      synchronized (EVENTS) {
         EVENTS.removeIf(e -> now - e.discoveredAt > EVENT_TTL_MS);
      }
      if (!autoQuery()) {
         return;
      }
      if (joinSettleTicks < 60) {
         joinSettleTicks++;
         return;
      }
      if (now - lastQueryAt < QUERY_INTERVAL_MS) {
         return;
      }
      lastQueryAt = now;
      sendEventDelayCommand(client);
   }

   private static void sendEventDelayCommand(MinecraftClient client) {
      ClientPlayNetworkHandler net = client.getNetworkHandler();
      if (net == null) {
         return;
      }
      try {
         net.sendChatCommand("event delay");
      } catch (Throwable ignored) {
      }
   }

   public static boolean onChatMessage(Text text) {
      if (!isEnabled() || text == null) {
         return false;
      }
      String message = text.getString();
      if (message == null || message.length() < 3) {
         return false;
      }
      String stripped = message.replaceAll("\u00a7.", "").trim();
      if (!KEYWORDS.matcher(stripped).find()) {
         return false;
      }

      int[] coords = extractCoords(stripped);
      if (coords == null) {
         return false;
      }
      int x = coords[0];
      int y = coords[1];
      int z = coords[2];
      if (Math.abs(x) > 30_000_000 || Math.abs(z) > 30_000_000 || y < -64 || y > 320) {
         return false;
      }

      long startMs = extractStartTime(stripped);
      String name = extractName(stripped);
      long discoveredAt = System.currentTimeMillis();

      synchronized (EVENTS) {
         EVENTS.removeIf(e -> e.x == x && e.y == y && e.z == z);
         EVENTS.add(new EventEntry(name, x, y, z, startMs, discoveredAt));
         while (EVENTS.size() > 8) {
            EVENTS.remove(0);
         }
      }
      return true;
   }

   private static int[] extractCoords(String message) {
      Matcher m = COORDS_TAGGED.matcher(message);
      if (m.find()) {
         return parseTriplet(m);
      }
      m = COORDS_PLAIN.matcher(message);
      if (m.find()) {
         return parseTriplet(m);
      }
      return null;
   }

   private static int[] parseTriplet(Matcher m) {
      try {
         return new int[] {
            Integer.parseInt(m.group(1)),
            Integer.parseInt(m.group(2)),
            Integer.parseInt(m.group(3))
         };
      } catch (NumberFormatException e) {
         return null;
      }
   }

   private static long extractStartTime(String message) {
      Matcher m = TIME_HMS.matcher(message);
      while (m.find()) {
         try {
            int h = Integer.parseInt(m.group(1));
            int mn = Integer.parseInt(m.group(2));
            int sc = m.group(3) != null ? Integer.parseInt(m.group(3)) : 0;
            if (h <= 23 && mn <= 59 && sc <= 59) {
               long secs = (long) h * 3600L + (long) mn * 60L + (long) sc;
               if (secs > 0L && secs < 24L * 3600L) {
                  return System.currentTimeMillis() + secs * 1000L;
               }
            }
         } catch (NumberFormatException ignored) {
         }
      }
      Matcher r = TIME_RELATIVE.matcher(message);
      if (r.find()) {
         try {
            int n = Integer.parseInt(r.group(1));
            String unit = r.group(2).toLowerCase();
            long secs;
            if (unit.startsWith("ч") || unit.startsWith("h")) {
               secs = (long) n * 3600L;
            } else if (unit.startsWith("м") || unit.equals("m") || unit.startsWith("min")) {
               secs = (long) n * 60L;
            } else {
               secs = n;
            }
            return System.currentTimeMillis() + secs * 1000L;
         } catch (NumberFormatException ignored) {
         }
      }
      return -1L;
   }

   private static String extractName(String message) {
      Matcher q = QUOTED.matcher(message);
      if (q.find()) {
         return trim(q.group(1));
      }
      String[] tokens = message.split("\\s+");
      for (int i = 0; i < tokens.length - 1; i++) {
         String t = tokens[i].toLowerCase();
         if (t.startsWith("событ") || t.equals("event") || t.equals("ивент") || t.equals("босс") || t.equals("boss") || t.equals("конкурс")) {
            String next = tokens[i + 1].replaceAll("[^A-Za-z\u0400-\u04FF0-9]+", "");
            if (next.length() >= 3) {
               return trim(next);
            }
         }
      }
      return "Event";
   }

   private static String trim(String s) {
      String t = s.trim();
      return t.length() > 24 ? t.substring(0, 24) : t;
   }

   public static List<EventEntry> snapshot() {
      synchronized (EVENTS) {
         return new ArrayList<>(EVENTS);
      }
   }

   public static EventEntry nearest() {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc == null || mc.player == null) {
         return null;
      }
      Vec3d p = mc.player.getPos();
      synchronized (EVENTS) {
         return EVENTS.stream()
            .min(Comparator.comparingDouble(e -> distanceSquared(p, e)))
            .orElse(null);
      }
   }

   public static double distance(EventEntry e) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc == null || mc.player == null || e == null) {
         return -1.0;
      }
      return Math.sqrt(distanceSquared(mc.player.getPos(), e));
   }

   private static double distanceSquared(Vec3d p, EventEntry e) {
      double dx = p.x - e.x;
      double dy = p.y - e.y;
      double dz = p.z - e.z;
      return dx * dx + dy * dy + dz * dz;
   }

   public static String formatCountdown(EventEntry e) {
      if (e == null || e.startEpochMs <= 0L) {
         return "--:--";
      }
      long remaining = e.startEpochMs - System.currentTimeMillis();
      if (remaining <= 0L) {
         return "live";
      }
      long secs = remaining / 1000L;
      long h = secs / 3600L;
      long m = (secs % 3600L) / 60L;
      long s = secs % 60L;
      if (h > 0L) {
         return String.format("%d:%02d:%02d", h, m, s);
      }
      return String.format("%02d:%02d", m, s);
   }

   private static boolean isEnabled() {
      return ModuleManager.isEnabled("AutoEvent");
   }

   private static boolean autoQuery() {
      Module m = ModuleManager.get("AutoEvent");
      if (m == null) {
         return false;
      }
      ModuleSetting s = m.getSetting("Auto Refresh");
      return s == null || s.getBool();
   }

   public static void resetForReconnect() {
      joinSettleTicks = 0;
      lastQueryAt = 0L;
      synchronized (EVENTS) {
         EVENTS.clear();
      }
   }
}
