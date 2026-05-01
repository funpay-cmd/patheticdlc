package com.pathdlc.digger.render;

public final class PerformanceSettings {
   private static PerformanceSettings.Quality quality = PerformanceSettings.Quality.HIGH;
   private static PerformanceSettings.Quality userOverride = null;
   private static long lastFrameTime = System.nanoTime();
   private static float smoothFps = 60.0F;
   private static int frameCount = 0;
   private static long fpsAccum = 0L;

   private PerformanceSettings() {
   }

   public static void onFrameEnd() {
      long now = System.nanoTime();
      long delta = now - lastFrameTime;
      lastFrameTime = now;
      if (delta > 0L) {
         float instantFps = 1.0E9F / (float)delta;
         smoothFps = smoothFps + (instantFps - smoothFps) * 0.05F;
         frameCount++;
         if (frameCount >= 60) {
            if (userOverride != null) {
               quality = userOverride;
            } else if (smoothFps < 25.0F) {
               quality = PerformanceSettings.Quality.LOW;
            } else if (smoothFps < 45.0F) {
               quality = PerformanceSettings.Quality.MEDIUM;
            } else {
               quality = PerformanceSettings.Quality.HIGH;
            }

            frameCount = 0;
         }
      }
   }

   public static PerformanceSettings.Quality getQuality() {
      return quality;
   }

   public static float getSmoothFps() {
      return smoothFps;
   }

   public static void setUserOverride(PerformanceSettings.Quality q) {
      userOverride = q;
      if (q != null) {
         quality = q;
      }
   }

   public static PerformanceSettings.Quality getUserOverride() {
      return userOverride;
   }

   public static void cycleQuality() {
      if (userOverride == null) {
         userOverride = PerformanceSettings.Quality.HIGH;
      } else {
         userOverride = switch (userOverride) {
            case LOW -> null;
            case MEDIUM -> PerformanceSettings.Quality.LOW;
            case HIGH -> PerformanceSettings.Quality.MEDIUM;
         };
      }

      if (userOverride != null) {
         quality = userOverride;
      }
   }

   public static String getQualityLabel() {
      return userOverride == null
         ? "Auto (" + quality.name().charAt(0) + quality.name().substring(1).toLowerCase() + ")"
         : quality.name().charAt(0) + quality.name().substring(1).toLowerCase();
   }

   public static int getBlurIterations() {
      return switch (quality) {
         case LOW -> 0;
         case MEDIUM -> 1;
         case HIGH -> 3;
      };
   }

   public static boolean useShaders() {
      return quality != PerformanceSettings.Quality.LOW;
   }

   public static boolean useGlassEffect() {
      return quality == PerformanceSettings.Quality.HIGH;
   }

   public static boolean useRoundedShader() {
      return quality != PerformanceSettings.Quality.LOW;
   }

   public static int getParticleCount() {
      return switch (quality) {
         case LOW -> 0;
         case MEDIUM -> 15;
         case HIGH -> 40;
      };
   }

   public static float getBlurSpread() {
      return switch (quality) {
         case LOW -> 0.0F;
         case MEDIUM -> 2.0F;
         case HIGH -> 2.5F;
      };
   }

   public static enum Quality {
      LOW,
      MEDIUM,
      HIGH;
   }
}
