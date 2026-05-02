package com.pathdlc.digger.hud;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Polls the host operating system's "Now Playing" media APIs in a background
 * thread so the HUD Music widget can display whatever the user is actually
 * listening to (Spotify, browser-tab YouTube, VLC, native player, etc.) and
 * not just vanilla Minecraft music.
 *
 * <ul>
 *   <li>Windows uses GlobalSystemMediaTransportControlsSessionManager via a
 *       short PowerShell script (works on Windows 10 1803+ for any app that
 *       integrates with the SMTC, which today is Spotify desktop, every
 *       Chromium-based browser tab, Edge, Firefox, Groove, Foobar, etc.).</li>
 *   <li>macOS falls back to AppleScript against Music/Spotify desktop apps.</li>
 *   <li>Linux uses {@code playerctl} (MPRIS) when present.</li>
 * </ul>
 *
 * The probe runs at most once every {@link #POLL_INTERVAL_MS} milliseconds and
 * is fully decoupled from the render thread; readers always get the latest
 * cached value via {@link #currentTrack()} without blocking.
 */
public final class SystemMediaTracker {
   private static final long POLL_INTERVAL_MS = 1500L;
   private static final long PROCESS_TIMEOUT_MS = 2500L;
   /** Number of consecutive empty/error polls before a probe is permanently
    *  disabled.  Has to be high enough to absorb a slow PowerShell cold-start
    *  and the occasional WinRT/MPRIS hiccup, but low enough that a missing
    *  binary does not keep spawning processes for the entire session. */
   private static final int MAX_CONSECUTIVE_FAILURES = 5;

   private static final AtomicReference<String> CURRENT = new AtomicReference<>(null);
   private static volatile boolean started = false;
   private static volatile boolean disabled = false;

   private static final Probe PROBE = pickProbe();

   private SystemMediaTracker() {
   }

   public static void start() {
      if (started || disabled || PROBE == null) {
         return;
      }
      synchronized (SystemMediaTracker.class) {
         if (started) {
            return;
         }
         started = true;
      }
      Thread t = new Thread(SystemMediaTracker::pollLoop, "WareVisuals-Media");
      t.setDaemon(true);
      t.setPriority(Thread.MIN_PRIORITY);
      t.start();
   }

   /** May return null if no media is playing, the OS is unsupported, or the
    *  background poller has not finished its first probe yet. */
   public static String currentTrack() {
      return CURRENT.get();
   }

   private static void pollLoop() {
      while (!disabled) {
         try {
            String value = PROBE.poll();
            CURRENT.set(value);
         } catch (Throwable ignored) {
            CURRENT.set(null);
         }
         try {
            Thread.sleep(POLL_INTERVAL_MS);
         } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return;
         }
      }
   }

   private interface Probe {
      String poll();
   }

   private static Probe pickProbe() {
      String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
      if (os.contains("win")) {
         return new WindowsSmtcProbe();
      }
      if (os.contains("mac") || os.contains("darwin")) {
         return new MacOsProbe();
      }
      if (os.contains("nix") || os.contains("nux") || os.contains("aix")) {
         return new LinuxMprisProbe();
      }
      return null;
   }

   private static String runProcess(List<String> command) {
      ProcessBuilder pb = new ProcessBuilder(command);
      pb.redirectErrorStream(false);
      Process proc = null;
      try {
         proc = pb.start();
         final Process p = proc;
         final StringBuilder sb = new StringBuilder();
         // Read stdout on a side thread so a stuck child process (e.g. a
         // WinRT async deadlock in PowerShell) cannot block the daemon
         // forever — readLine itself does not honour PROCESS_TIMEOUT_MS.
         Thread reader = new Thread(() -> {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
               String line;
               while ((line = br.readLine()) != null) {
                  synchronized (sb) {
                     if (sb.length() > 0) {
                        sb.append('\n');
                     }
                     sb.append(line);
                     if (sb.length() > 1024) {
                        break;
                     }
                  }
               }
            } catch (IOException ignored) {
            }
         }, "WareVisuals-Media-Read");
         reader.setDaemon(true);
         reader.start();

         long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(PROCESS_TIMEOUT_MS);
         boolean exited = proc.waitFor(PROCESS_TIMEOUT_MS, TimeUnit.MILLISECONDS);
         if (!exited) {
            proc.destroyForcibly();
            reader.interrupt();
            try {
               reader.join(250L);
            } catch (InterruptedException ie) {
               Thread.currentThread().interrupt();
            }
            return null;
         }

         long remainingNs = deadline - System.nanoTime();
         long joinMs = remainingNs > 0L ? Math.max(50L, TimeUnit.NANOSECONDS.toMillis(remainingNs)) : 250L;
         reader.join(joinMs);
         if (reader.isAlive()) {
            // Process exited but the read thread is wedged — close stdout
            // by destroying the (already exited) process handle and bail.
            proc.destroyForcibly();
            reader.interrupt();
            return null;
         }
         synchronized (sb) {
            return sb.toString().trim();
         }
      } catch (IOException | InterruptedException e) {
         if (proc != null) {
            proc.destroyForcibly();
         }
         if (e instanceof InterruptedException) {
            Thread.currentThread().interrupt();
         }
         return null;
      }
   }

   private static String sanitize(String s) {
      if (s == null) {
         return null;
      }
      String trimmed = s.trim();
      if (trimmed.isEmpty()) {
         return null;
      }
      // Keep only the first line if a probe returns multi-line output.
      int nl = trimmed.indexOf('\n');
      if (nl >= 0) {
         trimmed = trimmed.substring(0, nl).trim();
      }
      if (trimmed.isEmpty()) {
         return null;
      }
      // YouTube titles can be ~100 chars; cap so the HUD widget stays compact.
      final int max = 60;
      if (trimmed.length() > max) {
         trimmed = trimmed.substring(0, max - 1).trim() + "\u2026";
      }
      return trimmed;
   }

   /**
    * Windows: GlobalSystemMediaTransportControlsSessionManager. Reaches every
    * app that registers with SMTC, which on modern Windows is essentially
    * every browser, every desktop player, and Spotify.
    */
   private static final class WindowsSmtcProbe implements Probe {
      private static final String SCRIPT = "[void][Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager,Windows.Media.Control,ContentType=WindowsRuntime];"
            + "Add-Type -AssemblyName System.Runtime.WindowsRuntime;"
            + "$asTaskGeneric=([System.WindowsRuntimeSystemExtensions].GetMethods()|?{$_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'})[0];"
            + "function Await($t,$r){$asTaskGeneric.MakeGenericMethod($r).Invoke($null,@($t)).GetAwaiter().GetResult()};"
            + "try{"
            + "$mgr=Await ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]);"
            + "$s=$mgr.GetCurrentSession();"
            + "if($s){"
            + "$status=$s.GetPlaybackInfo().PlaybackStatus;"
            + "$info=Await ($s.TryGetMediaPropertiesAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties]);"
            + "$artist=$info.Artist;$title=$info.Title;"
            + "if([string]::IsNullOrWhiteSpace($title)){return};"
            + "if($status -eq 'Paused' -or $status -eq 'Stopped'){return};"
            + "if([string]::IsNullOrWhiteSpace($artist)){Write-Output $title}else{Write-Output \"$artist - $title\"}"
            + "}"
            + "}catch{}";

      private final String encodedCommand;
      private volatile boolean broken = false;
      private volatile int consecutiveFailures = 0;

      WindowsSmtcProbe() {
         this.encodedCommand = Base64.getEncoder().encodeToString(SCRIPT.getBytes(StandardCharsets.UTF_16LE));
      }

      @Override
      public String poll() {
         if (broken) {
            return null;
         }
         String result = runProcess(List.of(
               "powershell.exe",
               "-NoProfile",
               "-NonInteractive",
               "-ExecutionPolicy", "Bypass",
               "-EncodedCommand", encodedCommand
         ));
         if (result == null) {
            // Could be missing powershell, slow cold-start, or a transient
            // WinRT timeout — only give up after several misses in a row so
            // a single slow probe does not kill music tracking forever.
            if (++consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
               broken = true;
            }
            return null;
         }
         consecutiveFailures = 0;
         return sanitize(result);
      }
   }

   /**
    * macOS: best-effort against the two most common players. The system-wide
    * MediaRemote API is private and not safely callable from Java.
    */
   private static final class MacOsProbe implements Probe {
      private static final String SCRIPT =
            "on tryApp(appName)\n"
                  + "  try\n"
                  + "    if application appName is running then\n"
                  + "      tell application appName\n"
                  + "        if player state is playing then\n"
                  + "          set t to name of current track\n"
                  + "          set a to artist of current track\n"
                  + "          if a is missing value or a is \"\" then return t\n"
                  + "          return a & \" - \" & t\n"
                  + "        end if\n"
                  + "      end tell\n"
                  + "    end if\n"
                  + "  end try\n"
                  + "  return \"\"\n"
                  + "end tryApp\n"
                  + "set r to tryApp(\"Spotify\")\n"
                  + "if r is \"\" then set r to tryApp(\"Music\")\n"
                  + "return r";

      @Override
      public String poll() {
         String result = runProcess(List.of("osascript", "-e", SCRIPT));
         return sanitize(result);
      }
   }

   /** Linux: rely on {@code playerctl} (D-Bus MPRIS). Common across distros. */
   private static final class LinuxMprisProbe implements Probe {
      private volatile boolean broken = false;
      private volatile int consecutiveFailures = 0;

      @Override
      public String poll() {
         if (broken) {
            return null;
         }
         String result = runProcess(List.of(
               "playerctl",
               "metadata",
               "--format",
               "{{ artist }} - {{ title }}"
         ));
         if (result == null) {
            if (++consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
               broken = true;
            }
            return null;
         }
         consecutiveFailures = 0;
         String trimmed = sanitize(result);
         if (trimmed == null || trimmed.equals("-") || trimmed.startsWith("- ")) {
            return null;
         }
         return trimmed;
      }
   }
}
