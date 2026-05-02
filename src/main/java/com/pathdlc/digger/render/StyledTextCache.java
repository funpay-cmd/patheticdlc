package com.pathdlc.digger.render;

import com.pathdlc.digger.gui.GuiSettings;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * Memoises {@link Text} instances built with the WareVisuals custom font so
 * the GUI and HUD do not allocate a fresh styled component every frame for
 * the same string. Cache size is bounded; entries fall out in LRU order.
 *
 * <p>Lookups are extremely cheap relative to ferrying allocations through the
 * GC each frame (10+ modules in the ArrayList * 60 fps + the watermark/coords
 * panels). The cache is invalidated whenever the user toggles the custom font
 * setting so that toggling takes effect immediately on the next frame.
 */
public final class StyledTextCache {
   private static final int MAX_ENTRIES = 256;
   private static final Map<String, Text> CACHE = new LinkedHashMap<String, Text>(64, 0.75F, true) {
      @Override
      protected boolean removeEldestEntry(Map.Entry<String, Text> eldest) {
         return size() > MAX_ENTRIES;
      }
   };
   private static boolean lastCustomFont = GuiSettings.isCustomFontEnabled();

   private StyledTextCache() {
   }

   public static synchronized Text get(String s, Identifier customFont) {
      boolean customNow = GuiSettings.isCustomFontEnabled();
      if (customNow != lastCustomFont) {
         CACHE.clear();
         lastCustomFont = customNow;
      }
      Text cached = CACHE.get(s);
      if (cached != null) {
         return cached;
      }
      Text built = customNow
         ? Text.literal(s).styled(style -> style.withFont(customFont))
         : Text.literal(s);
      CACHE.put(s, built);
      return built;
   }

   public static synchronized void invalidate() {
      CACHE.clear();
      lastCustomFont = GuiSettings.isCustomFontEnabled();
   }
}
