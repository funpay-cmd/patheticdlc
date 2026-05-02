package com.pathdlc.digger.render;

/**
 * Small bag of easing helpers used by the HUD and ClickGUI.  Everything
 * here is frame-rate-independent: callers pass the current value, the
 * target, and a per-second factor in [0..1) describing how much of the
 * remaining distance should be closed in one second of wall time.  The
 * helper internally derives the per-frame factor from the elapsed wall
 * time so the animation feels the same at 30 FPS and 240 FPS.
 */
public final class Animations {
   private static long lastFrameNs = 0L;
   private static float lastDelta = 1.0F / 60.0F;

   private Animations() {
   }

   /** Call once at the top of the render frame to refresh the delta. */
   public static void beginFrame() {
      long now = System.nanoTime();
      if (lastFrameNs == 0L) {
         lastFrameNs = now;
         lastDelta = 1.0F / 60.0F;
         return;
      }
      float dt = (now - lastFrameNs) / 1.0E9F;
      if (dt < 0.0F) {
         dt = 1.0F / 60.0F;
      } else if (dt > 0.1F) {
         dt = 0.1F;
      }
      lastDelta = dt;
      lastFrameNs = now;
   }

   public static float delta() {
      return lastDelta;
   }

   /**
    * Frame-rate-independent exponential ease.  {@code halfLife} is the
    * number of seconds it takes to close half the gap between current and
    * target; smaller values feel snappier.
    */
   public static float ease(float current, float target, float halfLife) {
      if (halfLife <= 0.0F) {
         return target;
      }
      float k = 1.0F - (float) Math.pow(0.5, lastDelta / halfLife);
      float v = current + (target - current) * k;
      if (Math.abs(target - v) < 0.0005F) {
         return target;
      }
      return v;
   }

   /** Smoothstep on [0..1]; clamps inputs. */
   public static float smoothstep(float t) {
      if (t <= 0.0F) {
         return 0.0F;
      }
      if (t >= 1.0F) {
         return 1.0F;
      }
      return t * t * (3.0F - 2.0F * t);
   }

   /** Linear interpolation. */
   public static float lerp(float a, float b, float t) {
      return a + (b - a) * t;
   }

   /** Clamp helper. */
   public static float clamp01(float v) {
      if (v <= 0.0F) {
         return 0.0F;
      }
      if (v >= 1.0F) {
         return 1.0F;
      }
      return v;
   }
}
