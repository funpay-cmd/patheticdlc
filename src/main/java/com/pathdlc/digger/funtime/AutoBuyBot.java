package com.pathdlc.digger.funtime;

import com.pathdlc.digger.gui.Module;
import com.pathdlc.digger.gui.ModuleManager;
import com.pathdlc.digger.gui.ModuleSetting;
import com.pathdlc.digger.util.Chat;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.DataComponentTypes;

public class AutoBuyBot {
   private boolean running;
   private int tickCounter;
   private int scanDelay;
   private boolean guiOpened;
   private boolean scanning;
   private int scanSlot;
   private static final Pattern PRICE_PATTERN = Pattern.compile("(?:Цена|Price|Стоимость|Cost)[:\\s]*([\\d,.]+)");
   private static final Pattern PRICE_NUMBER = Pattern.compile("([\\d]+[.,]?[\\d]*)");

   public void start() {
      this.running = true;
      this.tickCounter = 0;
      this.scanDelay = 0;
      this.guiOpened = false;
      this.scanning = false;
      this.scanSlot = 0;
      Chat.info("AutoBuy ON - sniping /ah auction");
   }

   public void stop() {
      this.running = false;
      this.guiOpened = false;
      this.scanning = false;
      Chat.info("AutoBuy OFF");
   }

   public void tick(MinecraftClient client) {
      if (this.running) {
         if (!ModuleManager.isEnabled("AutoBuy")) {
            this.stop();
         } else if (client.player != null) {
            if (client.interactionManager != null) {
               Module mod = ModuleManager.get("AutoBuy");
               if (mod != null) {
                  ModuleSetting intervalSetting = mod.getSetting("Interval");
                  int interval = intervalSetting != null ? (int)(intervalSetting.getFloat() * 20.0F) : 200;
                  ModuleSetting maxPriceSetting = mod.getSetting("Max Price");
                  int maxPrice = maxPriceSetting != null ? (int)maxPriceSetting.getFloat() : 10000;
                  ModuleSetting itemSetting = mod.getSetting("Item");
                  int itemIndex = itemSetting != null ? itemSetting.getChoiceIndex() : 0;
                  String targetName = this.getTargetName(itemIndex).toLowerCase();
                  if (client.currentScreen instanceof GenericContainerScreen containerScreen) {
                     GenericContainerScreenHandler handler = (GenericContainerScreenHandler)containerScreen.getScreenHandler();
                     int containerSlots = handler.getRows() * 9;
                     if (this.scanDelay > 0) {
                        this.scanDelay--;
                     } else if (!this.scanning) {
                        this.scanning = true;
                        this.scanSlot = 0;
                        this.scanDelay = 5;
                     } else {
                        for (; this.scanSlot < containerSlots; this.scanSlot++) {
                           ItemStack stack = handler.getSlot(this.scanSlot).getStack();
                           if (!stack.isEmpty()) {
                              String itemName = stack.getName().getString().toLowerCase();
                              int price = this.extractPrice(stack);
                              if (itemName.contains(targetName) && price > 0 && price <= maxPrice) {
                                 client.interactionManager.clickSlot(handler.syncId, this.scanSlot, 0, SlotActionType.PICKUP, client.player);
                                 Chat.info("AutoBuy: bought \"" + stack.getName().getString() + "\" for " + price);
                                 this.scanSlot++;
                                 this.scanDelay = 3;
                                 return;
                              }
                           }
                        }

                        this.scanning = false;
                        this.guiOpened = false;
                        client.player.closeHandledScreen();
                     }
                  } else {
                     this.guiOpened = false;
                     this.scanning = false;
                     this.tickCounter++;
                     if (this.tickCounter >= interval) {
                        this.tickCounter = 0;
                        ModuleSetting searchSetting = mod.getSetting("Search");
                        int searchMode = searchSetting != null ? searchSetting.getChoiceIndex() : 0;
                        if (searchMode == 1) {
                           String searchTarget = this.getTargetName(itemIndex);
                           client.player.networkHandler.sendChatMessage("/ah search " + searchTarget);
                        } else {
                           client.player.networkHandler.sendChatMessage("/ah");
                        }

                        this.guiOpened = true;
                     }
                  }
               }
            }
         }
      }
   }

   private int extractPrice(ItemStack stack) {
      List<Text> lore = List.of();
      LoreComponent loreComponent = (LoreComponent)stack.get(DataComponentTypes.LORE);
      if (loreComponent != null) {
         lore = loreComponent.lines();
      }

      for (Text line : lore) {
         String text = line.getString();
         Matcher matcher = PRICE_PATTERN.matcher(text);
         if (matcher.find()) {
            return this.parseNumber(matcher.group(1));
         }
      }

      for (Text linex : lore) {
         String text = linex.getString();
         if (text.contains("$") || text.contains("монет") || text.contains("coin") || text.contains("руб")) {
            Matcher numMatcher = PRICE_NUMBER.matcher(text);
            if (numMatcher.find()) {
               return this.parseNumber(numMatcher.group(1));
            }
         }
      }

      String name = stack.getName().getString();
      Matcher nameMatcher = PRICE_PATTERN.matcher(name);
      return nameMatcher.find() ? this.parseNumber(nameMatcher.group(1)) : -1;
   }

   private int parseNumber(String str) {
      try {
         String clean = str.replace(",", "").replace(".", "").trim();
         return Integer.parseInt(clean);
      } catch (NumberFormatException var3) {
         return -1;
      }
   }

   private String getTargetName(int index) {
      return switch (index) {
         case 0 -> "diamond";
         case 1 -> "emerald";
         case 2 -> "netherite";
         case 3 -> "enchanted golden apple";
         case 4 -> "elytra";
         case 5 -> "totem";
         case 6 -> "shulker";
         case 7 -> "beacon";
         default -> "diamond";
      };
   }

   public boolean isRunning() {
      return this.running;
   }
}
