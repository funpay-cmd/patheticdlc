package com.pathdlc.digger.farm;

import net.minecraft.client.MinecraftClient;

public interface FarmBot {
   String name();

   void start();

   void stop();

   void tick(MinecraftClient var1);

   boolean isRunning();

   String status();
}
