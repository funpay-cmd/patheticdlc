package com.pathdlc.digger.farm;

import com.pathdlc.digger.util.Chat;
import java.util.Locale;

public class FarmCommandHandler {
   private final FarmManager manager;

   public FarmCommandHandler(FarmManager manager) {
      this.manager = manager;
   }

   public boolean handle(String rawMessage) {
      String message = rawMessage.trim();
      if (!message.startsWith(".")) {
         return false;
      } else {
         String[] args = message.substring(1).trim().split("\\s+");
         if (args.length != 0 && !args[0].isBlank()) {
            String root = args[0].toLowerCase(Locale.ROOT);

            return switch (root) {
               case "apple", "autoapple" -> this.handleApple(args);
               default -> false;
            };
         } else {
            return false;
         }
      }
   }

   private boolean handleApple(String[] args) {
      AutoAppleFarm apple = this.manager.apple();
      if (args.length < 2) {
         Chat.info(apple.status());
         return true;
      } else {
         String action = args[1].toLowerCase(Locale.ROOT);
         if (action.equals("start") || action.equals("on")) {
            apple.start();
            return true;
         } else if (action.equals("stop") || action.equals("off")) {
            apple.stop();
            return true;
         } else if (action.equals("status")) {
            Chat.info(apple.status());
            return true;
         } else if (action.equals("set")) {
            if (args.length >= 3 && args[2].equalsIgnoreCase("look")) {
               apple.setSpotAtLook();
            } else {
               apple.setSpotAtPlayer();
            }

            return true;
         } else if (action.equals("clear")) {
            apple.clearSpot();
            return true;
         } else if (action.equals("radius")) {
            if (args.length < 3) {
               Chat.info("AutoApple radius = " + apple.getRadius());
               return true;
            } else {
               try {
                  int radius = Integer.parseInt(args[2]);
                  apple.setRadius(radius);
                  Chat.info("AutoApple radius set to " + apple.getRadius());
               } catch (NumberFormatException var5) {
                  Chat.warn("Use: .apple radius <4-64>");
               }

               return true;
            }
         } else if (action.equals("bonemeal")) {
            if (args.length < 3) {
               Chat.info("AutoApple bonemeal = " + apple.isBonemeal());
               return true;
            } else {
               String value = args[2].toLowerCase(Locale.ROOT);
               if (value.equals("on") || value.equals("true") || value.equals("1")) {
                  apple.setBonemeal(true);
                  Chat.info("AutoApple bonemeal enabled.");
                  return true;
               } else if (!value.equals("off") && !value.equals("false") && !value.equals("0")) {
                  Chat.warn("Use: .apple bonemeal on/off");
                  return true;
               } else {
                  apple.setBonemeal(false);
                  Chat.info("AutoApple bonemeal disabled.");
                  return true;
               }
            }
         } else if (action.equals("help")) {
            this.printHelp();
            return true;
         } else {
            this.printHelp();
            return true;
         }
      }
   }

   private void printHelp() {
      Chat.info("AutoApple commands:");
      Chat.info(".apple set / .apple set look / .apple clear");
      Chat.info(".apple start / .apple stop / .apple status");
      Chat.info(".apple radius 16 / .apple bonemeal on/off");
   }
}
