package com.pathdlc.digger;

import com.pathdlc.digger.baritone.BaritoneBridge;
import com.pathdlc.digger.bot.DiggerBot;
import com.pathdlc.digger.clan.ClanCommandHandler;
import com.pathdlc.digger.clan.ClanRedstoneBot;
import com.pathdlc.digger.command.DotCommandHandler;
import com.pathdlc.digger.event.AutoEventBot;
import com.pathdlc.digger.event.EventBeaconRenderer;
import com.pathdlc.digger.farm.FarmCommandHandler;
import com.pathdlc.digger.farm.FarmManager;
import com.pathdlc.digger.gui.ClickGuiScreen;
import com.pathdlc.digger.gui.ModuleManager;
import com.pathdlc.digger.hud.HudRenderer;
import com.pathdlc.digger.hud.SystemMediaTracker;
import com.pathdlc.digger.render.BlockESPRenderer;
import com.pathdlc.digger.render.BlockOverlayRenderer;
import com.pathdlc.digger.render.HitEffectsRenderer;
import com.pathdlc.digger.render.PerformanceSettings;
import com.pathdlc.digger.render.SelectionRenderer;
import com.pathdlc.digger.selection.SelectionManager;
import com.pathdlc.digger.util.Chat;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents.AllowChat;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.AfterEntities;
import net.minecraft.client.option.KeyBinding;

public class PathDlcDiggerClient implements ClientModInitializer {
   public static final String MOD_ID = "pathdlc_digger";
   public static final String NAME = "WareVisuals";
   private static final SelectionManager SELECTION = new SelectionManager();
   private static final BaritoneBridge BARITONE = new BaritoneBridge();
   private static final DiggerBot DIGGER = new DiggerBot(SELECTION, BARITONE);
   private static final FarmManager FARMS = new FarmManager(BARITONE);
   private static final ClanRedstoneBot CLAN = new ClanRedstoneBot();
   private static final DotCommandHandler COMMANDS = new DotCommandHandler(SELECTION, DIGGER, BARITONE);
   private static final FarmCommandHandler FARM_COMMANDS = new FarmCommandHandler(FARMS);
   private static final ClanCommandHandler CLAN_COMMANDS = new ClanCommandHandler(CLAN);
   private static final KeyBinding CLICK_GUI_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.pathdlc.clickgui", 344, "category.pathdlc"));
   private static ClickGuiScreen clickGui;

   public void onInitializeClient() {
      ClientSendMessageEvents.ALLOW_CHAT.register((AllowChat)message -> {
         if (!message.startsWith(".")) {
            return true;
         } else {
            boolean handled = CLAN_COMMANDS.handle(message) || FARM_COMMANDS.handle(message) || COMMANDS.handle(message);
            return !handled;
         }
      });
      ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
         if (!overlay) {
            AutoEventBot.onChatMessage(message);
         }
      });
      ClientTickEvents.END_CLIENT_TICK
         .register(
            (EndTick)client -> {
               if (CLICK_GUI_KEY.wasPressed()) {
                  if (clickGui == null) {
                     clickGui = new ClickGuiScreen();
                     clickGui.initCategories(
                        () -> FARMS.apple().start(),
                        () -> FARMS.apple().stop(),
                        () -> DIGGER.startDigAndFill(),
                        () -> DIGGER.stop(),
                        () -> CLAN.start(),
                        () -> CLAN.stop()
                     );
                  }

                  client.setScreen(clickGui);
               }

               DIGGER.tick(client);
               FARMS.tick(client);
               CLAN.tick(client);
               AutoEventBot.onClientTick(client);
            }
         );
      WorldRenderEvents.AFTER_ENTITIES.register((AfterEntities)context -> {
         SelectionRenderer.render(context, SELECTION);
         if (ModuleManager.isEnabled("BlockOverlay")) {
            BlockOverlayRenderer.render(context);
         }

         if (ModuleManager.isEnabled("BlockESP")) {
            BlockESPRenderer.render(context);
         }

         EventBeaconRenderer.render(context);
      });
      HudRenderCallback.EVENT.register((HudRenderCallback)(context, tickCounter) -> {
         if (HitEffectsRenderer.hasActiveEffects()) {
            HitEffectsRenderer.renderHud(context);
         }

         HudRenderer.render(context);
         PerformanceSettings.onFrameEnd();
      });
      SystemMediaTracker.start();
      Chat.later("WareVisuals loaded. Commands: .pos, .fill, .dig baritone, .apple, .clan");
   }
}
