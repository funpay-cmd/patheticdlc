package com.pathdlc.digger.event;

import com.pathdlc.digger.gui.Module;
import com.pathdlc.digger.gui.ModuleManager;
import com.pathdlc.digger.gui.ModuleSetting;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

/**
 * Listens for FunTime "/event delay" responses and event announcements,
 * caches upcoming events with coordinates and start time, and exposes them
 * to the HUD. Read-only — no auto-pathing, no automated movement.
 *
 * <p>FunTime emits events as multi-line blocks, e.g.
 * <pre>
 * [Ивенты]:
 * [1] Вулкан:
 * || Статус: » Еще не активирован, до извержения 4 мин 53 сек
 * || Координаты: [1308 73 1828]
 * </pre>
 * The parser keeps a sliding window of the last few chat lines; when a
 * coords line shows up it walks the window backwards to find the matching
 * event header and timer.
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

   private static final long EVENT_TTL_MS = 45L * 60L * 1000L;
   private static final long QUERY_INTERVAL_MS = 60_000L;
   private static final int LINE_BUFFER = 16;

   private static final Map<String, EventEntry> EVENTS = new HashMap<>();
   private static final Deque<String> RECENT_LINES = new ArrayDeque<>();
   private static long lastQueryAt = 0L;
   private static int joinSettleTicks = 0;

   private static final Pattern COORDS_BRACKET = Pattern.compile(
      "\\[\\s*(-?\\d+)\\s+(-?\\d+)\\s+(-?\\d+)\\s*\\]"
   );
   private static final Pattern COORDS_TAGGED = Pattern.compile(
      "(?i)x\\s*[:=]\\s*(-?\\d+(?:\\.\\d+)?)\\s*[,;\\s]+y\\s*[:=]\\s*(-?\\d+(?:\\.\\d+)?)\\s*[,;\\s]+z\\s*[:=]\\s*(-?\\d+(?:\\.\\d+)?)"
   );
   private static final Pattern COORDS_PHRASE = Pattern.compile(
      "(?i)(?:координат[аы]х?|coords?)\\D*(-?\\d{1,7})\\s+(-?\\d{1,3})\\s+(-?\\d{1,7})"
   );
   private static final Pattern EVENT_HEADER = Pattern.compile(
      "\\[(?:\\d+|i)\\]\\s+([\\p{L}\\p{N} _-]{2,32})\\s*[:\u00BB\u00AB]?",
      Pattern.UNICODE_CHARACTER_CLASS
   );
   private static final Pattern EVENT_BRACKET_NAME = Pattern.compile(
      "\\[\\s*([\\p{L}][\\p{L}\\p{N} _-]{1,30})\\s*\\]",
      Pattern.UNICODE_CHARACTER_CLASS
   );

   private static final java.util.Set<String> RESERVED_BRACKET_TAGS = java.util.Set.of(
      "ивенты", "events", "event", "ивент", "статус", "координаты", "coords",
      "compass", "компас", "уровень", "сервер", "system", "info", "anti-x-ray",
      "анархия", "warevisuals"
   );
   private static final Pattern PLACEHOLDER_NAME = Pattern.compile(
      "(?i)(?:до\\s+следующ|следующ\\w*\\s+ивент|next\\s+event|нет\\s+активн|no\\s+active|неизвестн|unknown|none)",
      Pattern.UNICODE_CHARACTER_CLASS
   );
   private static final Pattern TIMER_RU = Pattern.compile(
      "(?i)(\\d{1,3})\\s*мин(?:ут)?[ауы]?\\s*(?:(\\d{1,2})\\s*сек(?:унд)?[ауы]?)?"
   );
   private static final Pattern TIMER_RU_SEC_ONLY = Pattern.compile(
      "(?i)(\\d{1,3})\\s*сек(?:унд)?[ауы]?"
   );
   private static final Pattern TIMER_HMS = Pattern.compile("(\\d{1,2}):(\\d{2})(?::(\\d{2}))?");

   private AutoEventBot() {
   }

   public static void onClientTick(MinecraftClient client) {
      if (client == null || client.player == null || client.world == null || client.getNetworkHandler() == null) {
         joinSettleTicks = 0;
         return;
      }
      long now = System.currentTimeMillis();
      synchronized (EVENTS) {
         Iterator<Map.Entry<String, EventEntry>> it = EVENTS.entrySet().iterator();
         while (it.hasNext()) {
            EventEntry e = it.next().getValue();
            boolean expiredByDiscovery = now - e.discoveredAt > EVENT_TTL_MS;
            boolean expiredByStart = e.startEpochMs > 0L && now - e.startEpochMs > 5L * 60L * 1000L;
            if (expiredByDiscovery || expiredByStart) {
               it.remove();
            }
         }
      }
      if (!isEnabled() || !autoQuery()) {
         return;
      }
      if (joinSettleTicks < 100) {
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
      if (text == null) {
         return false;
      }
      String raw = text.getString();
      if (raw == null || raw.length() < 3) {
         return false;
      }
      String line = strip(raw);
      if (line.isEmpty()) {
         return false;
      }
      synchronized (RECENT_LINES) {
         RECENT_LINES.addFirst(line);
         while (RECENT_LINES.size() > LINE_BUFFER) {
            RECENT_LINES.removeLast();
         }
      }
      tryExtractFromBuffer();
      return false;
   }

   private static void tryExtractFromBuffer() {
      List<String> recent;
      synchronized (RECENT_LINES) {
         recent = new ArrayList<>(RECENT_LINES);
      }
      if (recent.isEmpty()) {
         return;
      }
      String head = recent.get(0);

      int[] coords = findCoords(head);
      if (coords == null) {
         Matcher phrase = COORDS_PHRASE.matcher(head);
         if (phrase.find()) {
            try {
               coords = new int[] {
                  Integer.parseInt(phrase.group(1)),
                  Integer.parseInt(phrase.group(2)),
                  Integer.parseInt(phrase.group(3))
               };
            } catch (NumberFormatException ignored) {
               return;
            }
         }
      }
      if (coords == null) {
         return;
      }

      EventContext ctx = lookbackContext(recent);
      if (ctx.name == null) {
         return;
      }
      registerEvent(ctx.name, coords[0], coords[1], coords[2], ctx.startMs);
   }

   private static final java.util.regex.Pattern SPAWN_KEYWORD = java.util.regex.Pattern.compile(
      "(?i)(?:появи|открыл|старт|начал|spawn|start|begin|drop\\s*party|конкурс|раздач)"
   );

   private static final class EventContext {
      String name;
      long startMs = -1L;
   }

   private static EventContext lookbackContext(List<String> recent) {
      EventContext ctx = new EventContext();
      boolean hasEventBlockHeader = false;
      String headLine = recent.get(0);
      boolean spawnLikeHead = SPAWN_KEYWORD.matcher(headLine).find();

      for (int i = 0; i < Math.min(recent.size(), 10); i++) {
         String l = recent.get(i);
         Matcher m = EVENT_HEADER.matcher(l);
         if (m.find()) {
            String n = trim(m.group(1));
            if (!n.isEmpty() && ctx.name == null) {
               ctx.name = n;
            }
         }
         if (l.toLowerCase().contains("[ивенты]") || l.toLowerCase().contains("[events]")) {
            hasEventBlockHeader = true;
         }
         if (ctx.startMs <= 0L) {
            long t = parseTimer(l);
            if (t > 0L) {
               ctx.startMs = System.currentTimeMillis() + t * 1000L;
            }
         }
      }

      if (ctx.name == null) {
         for (int i = 0; i < Math.min(recent.size(), 10); i++) {
            String fromBracket = nameFromBracket(recent.get(i));
            if (fromBracket != null) {
               boolean nearbySpawn = false;
               int radius = Math.min(recent.size() - 1, i + 2);
               for (int j = Math.max(0, i - 2); j <= radius; j++) {
                  if (SPAWN_KEYWORD.matcher(recent.get(j)).find()) {
                     nearbySpawn = true;
                     break;
                  }
               }
               if (nearbySpawn || hasEventBlockHeader) {
                  ctx.name = fromBracket;
                  break;
               }
            }
         }
      }

      if (ctx.name == null && spawnLikeHead) {
         ctx.name = "Event";
      }
      return ctx;
   }

   private static int[] findCoords(String line) {
      Matcher b = COORDS_BRACKET.matcher(line);
      if (b.find()) {
         try {
            return new int[] {
               Integer.parseInt(b.group(1)),
               Integer.parseInt(b.group(2)),
               Integer.parseInt(b.group(3))
            };
         } catch (NumberFormatException ignored) {
            return null;
         }
      }
      Matcher t = COORDS_TAGGED.matcher(line);
      if (t.find()) {
         try {
            int x = (int) Math.round(Double.parseDouble(t.group(1)));
            int y = (int) Math.round(Double.parseDouble(t.group(2)));
            int z = (int) Math.round(Double.parseDouble(t.group(3)));
            return new int[] {x, y, z};
         } catch (NumberFormatException ignored) {
            return null;
         }
      }
      return null;
   }

   private static String lookbackName(List<String> recent) {
      for (int i = 0; i < Math.min(recent.size(), 8); i++) {
         String l = recent.get(i);
         Matcher m = EVENT_HEADER.matcher(l);
         if (m.find()) {
            String name = trim(m.group(1));
            if (!name.isEmpty()) {
               return name;
            }
         }
      }
      for (int i = 0; i < Math.min(recent.size(), 8); i++) {
         String fromBracket = nameFromBracket(recent.get(i));
         if (fromBracket != null) {
            return fromBracket;
         }
      }
      return "Event";
   }

   private static String nameFromBracket(String line) {
      Matcher m = EVENT_BRACKET_NAME.matcher(line);
      while (m.find()) {
         String name = trim(m.group(1));
         if (name.isEmpty()) {
            continue;
         }
         String lower = name.toLowerCase();
         if (RESERVED_BRACKET_TAGS.contains(lower)) {
            continue;
         }
         return name;
      }
      return null;
   }

   private static long lookbackStartTime(List<String> recent) {
      for (int i = 0; i < Math.min(recent.size(), 8); i++) {
         String l = recent.get(i);
         long t = parseTimer(l);
         if (t > 0L) {
            return System.currentTimeMillis() + t * 1000L;
         }
      }
      return -1L;
   }

   private static long parseTimer(String line) {
      Matcher m = TIMER_RU.matcher(line);
      if (m.find()) {
         try {
            int min = Integer.parseInt(m.group(1));
            int sec = m.group(2) != null ? Integer.parseInt(m.group(2)) : 0;
            return (long) min * 60L + sec;
         } catch (NumberFormatException ignored) {
         }
      }
      Matcher s = TIMER_RU_SEC_ONLY.matcher(line);
      if (s.find()) {
         try {
            return Integer.parseInt(s.group(1));
         } catch (NumberFormatException ignored) {
         }
      }
      Matcher h = TIMER_HMS.matcher(line);
      if (h.find()) {
         try {
            int hh = Integer.parseInt(h.group(1));
            int mm = Integer.parseInt(h.group(2));
            int ss = h.group(3) != null ? Integer.parseInt(h.group(3)) : 0;
            if (hh > 23 || mm > 59 || ss > 59) {
               return 0L;
            }
            return (long) hh * 3600L + (long) mm * 60L + ss;
         } catch (NumberFormatException ignored) {
         }
      }
      return 0L;
   }

   private static void registerEvent(String name, int x, int y, int z, long startMs) {
      if (Math.abs(x) > 30_000_000 || Math.abs(z) > 30_000_000 || y < -64 || y > 320) {
         return;
      }
      // Skip placeholder rows like "До следующего ивента" — those are status
      // banners about *when* the next event will start, not a real event we
      // can pin a beacon on.
      if (name != null && PLACEHOLDER_NAME.matcher(name).find()) {
         return;
      }
      String key = name == null || name.isBlank() ? "Event@" + x + "_" + z : name;
      EventEntry entry = new EventEntry(key, x, y, z, startMs, System.currentTimeMillis());
      synchronized (EVENTS) {
         EVENTS.put(key, entry);
      }
   }

   private static String strip(String s) {
      return s == null ? "" : s.replaceAll("\u00a7.", "").trim();
   }

   private static String trim(String s) {
      String t = s.trim();
      return t.length() > 24 ? t.substring(0, 24) : t;
   }

   public static List<EventEntry> snapshot() {
      synchronized (EVENTS) {
         return new ArrayList<>(EVENTS.values());
      }
   }

   public static EventEntry nearest() {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc == null || mc.player == null) {
         return null;
      }
      Vec3d p = mc.player.getPos();
      synchronized (EVENTS) {
         return EVENTS.values().stream()
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
      synchronized (RECENT_LINES) {
         RECENT_LINES.clear();
      }
   }
}
