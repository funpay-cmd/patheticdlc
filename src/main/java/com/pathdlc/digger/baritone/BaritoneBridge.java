package com.pathdlc.digger.baritone;

import java.lang.reflect.Method;
import net.minecraft.util.math.BlockPos;

public class BaritoneBridge {
   private long lastErrorAt = 0L;

   public boolean isAvailable() {
      try {
         Class.forName("baritone.api.BaritoneAPI");
         return true;
      } catch (Throwable var2) {
         return false;
      }
   }

   public boolean execute(String command) {
      try {
         Object baritone = this.getPrimaryBaritone();
         Object manager = this.invokeNoArgs(baritone, "getCommandManager");
         Method execute = manager.getClass().getMethod("execute", String.class);
         execute.invoke(manager, command);
         return true;
      } catch (Throwable var5) {
         this.rememberError();
         return false;
      }
   }

   public boolean gotoBlock(BlockPos pos) {
      return pos == null ? false : this.execute("goto " + pos.getX() + " " + pos.getY() + " " + pos.getZ());
   }

   public void cancel() {
      this.execute("cancel");
   }

   public boolean clearArea(BlockPos a, BlockPos b) {
      try {
         Object baritone = this.getPrimaryBaritone();
         Object builder = this.invokeNoArgs(baritone, "getBuilderProcess");
         Method clearArea = builder.getClass().getMethod("clearArea", BlockPos.class, BlockPos.class);
         clearArea.invoke(builder, a, b);
         return true;
      } catch (Throwable var6) {
         this.rememberError();
         return false;
      }
   }

   public boolean buildSchematic(String name, Object schematic, BlockPos origin) {
      if (schematic != null && origin != null) {
         try {
            Object baritone = this.getPrimaryBaritone();
            Object builder = this.invokeNoArgs(baritone, "getBuilderProcess");

            for (Method method : builder.getClass().getMethods()) {
               if (method.getName().equals("build")) {
                  Class<?>[] parameters = method.getParameterTypes();
                  if (parameters.length == 3
                     && parameters[0].isAssignableFrom(String.class)
                     && parameters[1].isInstance(schematic)
                     && parameters[2].isAssignableFrom(origin.getClass())) {
                     method.invoke(builder, name, schematic, origin);
                     return true;
                  }
               }
            }

            this.rememberError();
            return false;
         } catch (Throwable var11) {
            this.rememberError();
            return false;
         }
      } else {
         return false;
      }
   }

   public boolean hadRecentError() {
      return System.currentTimeMillis() - this.lastErrorAt < 5000L;
   }

   private Object getPrimaryBaritone() throws ReflectiveOperationException {
      Class<?> apiClass = Class.forName("baritone.api.BaritoneAPI");
      Method getProvider = apiClass.getMethod("getProvider");
      Object provider = getProvider.invoke(null);
      return this.invokeNoArgs(provider, "getPrimaryBaritone");
   }

   private Object invokeNoArgs(Object owner, String methodName) throws ReflectiveOperationException {
      Method method = owner.getClass().getMethod(methodName);
      return method.invoke(owner);
   }

   private void rememberError() {
      this.lastErrorAt = System.currentTimeMillis();
   }
}
