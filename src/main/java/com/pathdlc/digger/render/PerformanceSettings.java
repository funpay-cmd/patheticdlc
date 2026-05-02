package com.pathdlc.digger.render;

/**
 * Visual settings are no longer downgraded automatically. Glass blur, rounded
 * shaders and particle counts always run at the highest tier so the UI
 * looks the same regardless of frame rate. Frame timing is still tracked so
 * that consumers can throttle background work (text-width caching, music
 * reflection, etc.) without ever touching the rendered output.
 */
public final class PerformanceSettings {
   private static final Quality QUALITY = Quality.HIGH;
   private static final int BLUR_ITERATIONS = 3;
   private static final float BLUR_SPREAD = 2.5F;
   private static final int PARTICLE_COUNT = 40;

   private static long lastFrameTime = System.nanoTime();
   private static float smoothFps = 60.0F;
   private static long frameCounter = 0L;

   private PerformanceSettings() {
   }

   public static void onFrameEnd() {
      long now = System.nanoTime();
      long delta = now - lastFrameTime;
      lastFrameTime = now;
      if (delta > 0L) {
         float instantFps = 1.0E9F / (float)delta;
         smoothFps = smoothFps + (instantFps - smoothFps) * 0.05F;
      }
      frameCounter++;
   }

   public static long getFrameCounter() {
      return frameCounter;
   }

   public static Quality getQuality() {
      return QUALITY;
   }

   public static float getSmoothFps() {
      return smoothFps;
   }

   public static void setUserOverride(Quality q) {
   }

   public static Quality getUserOverride() {
      return null;
   }

   public static void cycleQuality() {
   }

   public static String getQualityLabel() {
      return "High";
   }

   public static int getBlurIterations() {
      return BLUR_ITERATIONS;
   }

   public static boolean useShaders() {
      return true;
   }

   public static boolean useGlassEffect() {
      return true;
   }

   public static boolean useRoundedShader() {
      return true;
   }

   public static int getParticleCount() {
      return PARTICLE_COUNT;
   }

   public static float getBlurSpread() {
      return BLUR_SPREAD;
   }

   public static enum Quality {
      LOW,
      MEDIUM,
      HIGH;
   }
}
