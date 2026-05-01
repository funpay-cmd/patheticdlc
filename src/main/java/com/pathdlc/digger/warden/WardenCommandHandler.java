package com.pathdlc.digger.warden;

import com.pathdlc.digger.util.Chat;
import java.util.Locale;

public class WardenCommandHandler {
   private final AutoWardenBot bot;

   public WardenCommandHandler(AutoWardenBot bot) {
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
            if (!root.equals("warden") && !root.equals("autowarden")) {
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
               } else if (action.equals("radius")) {
                  if (args.length < 3) {
                     Chat.info("AutoWarden radius = " + this.bot.getRadius());
                     return true;
                  } else {
                     try {
                        this.bot.setRadius(Integer.parseInt(args[2]));
                     } catch (NumberFormatException var7) {
                        Chat.warn("Use: .warden radius <8-96>");
                     }

                     return true;
                  }
               } else if (action.equals("vertical")) {
                  if (args.length < 3) {
                     Chat.info("AutoWarden vertical radius = " + this.bot.getVerticalRadius());
                     return true;
                  } else {
                     try {
                        this.bot.setVerticalRadius(Integer.parseInt(args[2]));
                     } catch (NumberFormatException var8) {
                        Chat.warn("Use: .warden vertical <4-32>");
                     }

                     return true;
                  }
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
      Chat.info("AutoWarden commands:");
      Chat.info(".warden start / .warden stop / .warden status");
      Chat.info(".warden radius <8-96>");
      Chat.info(".warden vertical <4-32>");
   }
}
