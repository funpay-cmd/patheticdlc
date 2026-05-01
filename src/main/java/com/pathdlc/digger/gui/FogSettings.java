package com.pathdlc.digger.gui;

public final class FogSettings {
   private static FogSettings.FogColor color = FogSettings.FogColor.WHITE;
   private static FogSettings.FogDensity density = FogSettings.FogDensity.MEDIUM;

   public static FogSettings.FogColor getColor() {
      return color;
   }

   public static void cycleColor() {
      color = color.next();
   }

   public static FogSettings.FogDensity getDensity() {
      return density;
   }

   public static void cycleDensity() {
      density = density.next();
   }

   private FogSettings() {
   }

   public static enum FogColor {
      WHITE("White", 1.0F, 1.0F, 1.0F),
      LIGHT_BLUE("Light Blue", 0.7F, 0.85F, 1.0F),
      PURPLE("Purple", 0.6F, 0.3F, 0.8F),
      RED("Red", 0.8F, 0.2F, 0.15F),
      GREEN("Green", 0.2F, 0.7F, 0.3F),
      DARK("Dark", 0.15F, 0.1F, 0.2F),
      GOLDEN("Golden", 0.9F, 0.75F, 0.3F);

      public final String label;
      public final float r;
      public final float g;
      public final float b;

      private FogColor(String label, float r, float g, float b) {
         this.label = label;
         this.r = r;
         this.g = g;
         this.b = b;
      }

      public FogSettings.FogColor next() {
         FogSettings.FogColor[] vals = values();
         return vals[(this.ordinal() + 1) % vals.length];
      }
   }

   public static enum FogDensity {
      LIGHT("Light", 0.6F, 1.0F),
      MEDIUM("Medium", 0.3F, 0.7F),
      HEAVY("Heavy", 0.1F, 0.4F),
      ULTRA("Ultra", 0.02F, 0.15F);

      public final String label;
      public final float startMul;
      public final float endMul;

      private FogDensity(String label, float startMul, float endMul) {
         this.label = label;
         this.startMul = startMul;
         this.endMul = endMul;
      }

      public FogSettings.FogDensity next() {
         FogSettings.FogDensity[] vals = values();
         return vals[(this.ordinal() + 1) % vals.length];
      }
   }
}
