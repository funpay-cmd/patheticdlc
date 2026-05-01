package com.pathdlc.digger.gui;

public class ModuleSetting {
   private final String name;
   private final ModuleSetting.Type type;
   private boolean boolValue;
   private float floatValue;
   private final float min;
   private final float max;
   private final float step;
   private final String[] choices;
   private int choiceIndex;

   public static ModuleSetting toggle(String name, boolean defaultValue) {
      ModuleSetting s = new ModuleSetting(name, ModuleSetting.Type.TOGGLE, 0.0F, 1.0F, 1.0F, null);
      s.boolValue = defaultValue;
      return s;
   }

   public static ModuleSetting slider(String name, float defaultValue, float min, float max, float step) {
      ModuleSetting s = new ModuleSetting(name, ModuleSetting.Type.SLIDER, min, max, step, null);
      s.floatValue = defaultValue;
      return s;
   }

   public static ModuleSetting choice(String name, String[] choices, int defaultIndex) {
      ModuleSetting s = new ModuleSetting(name, ModuleSetting.Type.CHOICE, 0.0F, (float)(choices.length - 1), 1.0F, choices);
      s.choiceIndex = defaultIndex;
      return s;
   }

   private ModuleSetting(String name, ModuleSetting.Type type, float min, float max, float step, String[] choices) {
      this.name = name;
      this.type = type;
      this.min = min;
      this.max = max;
      this.step = step;
      this.choices = choices;
   }

   public String getName() {
      return this.name;
   }

   public ModuleSetting.Type getType() {
      return this.type;
   }

   public boolean getBool() {
      return this.boolValue;
   }

   public void setBool(boolean v) {
      this.boolValue = v;
   }

   public void toggleBool() {
      this.boolValue = !this.boolValue;
   }

   public float getFloat() {
      return this.floatValue;
   }

   public void setFloat(float v) {
      this.floatValue = Math.max(this.min, Math.min(this.max, v));
      this.floatValue = (float)Math.round(this.floatValue / this.step) * this.step;
   }

   public float getMin() {
      return this.min;
   }

   public float getMax() {
      return this.max;
   }

   public float getStep() {
      return this.step;
   }

   public float getNormalized() {
      return this.max == this.min ? 0.0F : (this.floatValue - this.min) / (this.max - this.min);
   }

   public void setFromNormalized(float norm) {
      this.setFloat(this.min + norm * (this.max - this.min));
   }

   public String[] getChoices() {
      return this.choices;
   }

   public int getChoiceIndex() {
      return this.choiceIndex;
   }

   public String getChoiceValue() {
      return this.choices != null ? this.choices[this.choiceIndex] : "";
   }

   public void cycleChoice() {
      if (this.choices != null) {
         this.choiceIndex = (this.choiceIndex + 1) % this.choices.length;
      }
   }

   public String getDisplayValue() {
      return switch (this.type) {
         case TOGGLE -> this.boolValue ? "ON" : "OFF";
         case SLIDER -> String.format("%.1f", this.floatValue);
         case CHOICE -> this.getChoiceValue();
      };
   }

   public static enum Type {
      TOGGLE,
      SLIDER,
      CHOICE;
   }
}
