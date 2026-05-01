package com.pathdlc.digger.clan;

import com.pathdlc.digger.util.Chat;
import java.util.Locale;

public class ClanCommandHandler {
   private final ClanRedstoneBot bot;

   public ClanCommandHandler(ClanRedstoneBot bot) {
      this.bot = bot;
   }

   public boolean handle(String rawMessage) {
      String message = rawMessage.trim();
      if (!message.startsWith(".")) {
         return false;
      } else {
         String[] args = message.substring(1).trim().split("\\s+");
         if (args.length != 0 && !args[0].isBlank()) {
            String root = args[0].toLowerCase(Locale.ROOT);
            if (!root.equals("clan")) {
               return false;
            } else if (args.length < 2) {
               this.printHelp();
               return true;
            } else {
               String action = args[1].toLowerCase(Locale.ROOT);
               if (action.equals("start") || action.equals("on")) {
                  this.bot.start();
                  return true;
               } else if (action.equals("stop") || action.equals("off")) {
                  this.bot.stop();
                  return true;
               } else if (action.equals("status")) {
                  Chat.info(this.bot.status());
                  return true;
               } else {
                  this.printHelp();
                  return true;
               }
            }
         } else {
            return false;
         }
      }
   }

   private void printHelp() {
      Chat.info("Clan commands:");
      Chat.info(".clan start / .clan stop / .clan status");
      Chat.info("Fast mode: places and breaks redstone dust without artificial delay.");
   }
}
