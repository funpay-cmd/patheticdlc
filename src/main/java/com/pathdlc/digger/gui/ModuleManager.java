package com.pathdlc.digger.gui;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ModuleManager {
   private static final Map<String, Module> modules = new LinkedHashMap<>();

   public static void register(Module module) {
      modules.put(module.getName().toLowerCase(), module);
   }

   public static Module get(String name) {
      return modules.get(name.toLowerCase());
   }

   public static boolean isEnabled(String name) {
      Module m = get(name);
      return m != null && m.isEnabled();
   }

   private ModuleManager() {
   }
}
