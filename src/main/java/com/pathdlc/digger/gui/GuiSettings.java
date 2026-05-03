package com.pathdlc.digger.gui;

public class GuiSettings {
   private static GuiSettings.Layout layout = GuiSettings.Layout.COLUMNS;
   private static GuiSettings.AccentColor accentColor = GuiSettings.AccentColor.RED;
   private static boolean customFontEnabled = false;

   public static GuiSettings.Layout getLayout() {
      return layout;
   }

   public static void setLayout(GuiSettings.Layout layout) {
      GuiSettings.layout = layout;
   }

   public static void cycleLayout() {
      layout = layout.next();
   }

   public static GuiSettings.AccentColor getAccentColor() {
      return accentColor;
   }

   public static void setAccentColor(GuiSettings.AccentColor color) {
      accentColor = color;
   }

   public static void cycleAccentColor() {
      accentColor = accentColor.next();
   }

   public static boolean isCustomFontEnabled() {
      return customFontEnabled;
   }

   public static void toggleCustomFont() {
      customFontEnabled = !customFontEnabled;
   }

   public static enum AccentColor {
      RED("Red", 0.882F, 0.114F, 0.282F, 0xFFE11D48),
      ORANGE("Orange", 1.0F, 0.6F, 0.2F, -17562),
      PINK("Pink", 1.0F, 0.3F, 0.7F, -30533),
      PURPLE("Purple", 0.6F, 0.3F, 1.0F, -5601025),
      BLUE("Blue", 0.3F, 0.6F, 1.0F, -7816193),
      CYAN("Cyan", 0.2F, 0.9F, 0.9F, -10031361),
      GREEN("Green", 0.3F, 1.0F, 0.5F, -7798870);

      private final String label;
      public final float r;
      public final float g;
      public final float b;
      public final int textColor;

      private AccentColor(String label, float r, float g, float b, int textColor) {
         this.label = label;
         this.r = r;
         this.g = g;
         this.b = b;
         this.textColor = textColor;
      }

      public String getLabel() {
         return this.label;
      }

      public GuiSettings.AccentColor next() {
         GuiSettings.AccentColor[] values = values();
         return values[(this.ordinal() + 1) % values.length];
      }
   }

   public static enum Layout {
      COLUMNS("Columns"),
      SINGLE("Single Panel");

      private final String label;

      private Layout(String label) {
         this.label = label;
      }

      public String getLabel() {
         return this.label;
      }

      public GuiSettings.Layout next() {
         GuiSettings.Layout[] values = values();
         return values[(this.ordinal() + 1) % values.length];
      }
   }
}
