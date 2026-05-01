package com.pathdlc.digger.gui;

import com.pathdlc.digger.render.LiquidGlassRenderer;
import com.pathdlc.digger.render.PerformanceSettings;
import com.pathdlc.digger.render.RoundedRectRenderer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;

public class ClickGuiScreen extends Screen {
   private static final int COL_WIDTH = 130;
   private static final int COL_GAP = 6;
   private static final int HEADER_H = 26;
   private static final int MODULE_H = 18;
   private static final int SETTING_H = 16;
   private static final int PAD = 6;
   private static final int CORNER_R = 8;
   private static final int SCROLL_SPEED = 10;
   private static final Identifier CUSTOM_FONT = Identifier.of("pathdlc_digger", "clickgui");
   private final List<Category> categories = new ArrayList<>();
   private float openProgress = 0.0F;
   private final Map<String, Integer> scrollOffsets = new HashMap<>();
   private final Map<String, Float> colDragY = new HashMap<>();
   private ModuleSetting draggingSlider;
   private float draggingSliderX;
   private float draggingSliderW;

   public ClickGuiScreen() {
      super(Text.literal("ClickGUI"));
   }

   protected void init() {
      super.init();
      this.openProgress = 0.0F;
   }

   public void initCategories(
      Runnable appleOn,
      Runnable appleOff,
      Runnable digOn,
      Runnable digOff,
      Runnable wardenOn,
      Runnable wardenOff,
      Runnable clanOn,
      Runnable clanOff,
      Runnable autoMineOn,
      Runnable autoMineOff,
      Runnable baseFinderOn,
      Runnable baseFinderOff,
      Runnable autoFarmOn,
      Runnable autoFarmOff,
      Runnable autoFishOn,
      Runnable autoFishOff,
      Runnable autoCraftOn,
      Runnable autoCraftOff,
      Runnable autoSellOn,
      Runnable autoSellOff,
      Runnable autoBuyOn,
      Runnable autoBuyOff
   ) {
      if (this.categories.isEmpty()) {
         Module killAura = new Module("KillAura");
         killAura.addSetting(ModuleSetting.slider("Range", 4.0F, 2.0F, 6.0F, 0.1F));
         killAura.addSetting(ModuleSetting.toggle("Only Crit", false));
         killAura.addSetting(ModuleSetting.toggle("Attack Mobs", true));
         killAura.addSetting(ModuleSetting.toggle("Attack Players", false));
         Module aimAssist = new Module("AimAssist");
         aimAssist.addSetting(ModuleSetting.slider("Speed", 50.0F, 10.0F, 100.0F, 5.0F));
         aimAssist.addSetting(ModuleSetting.slider("FOV", 90.0F, 30.0F, 180.0F, 10.0F));
         aimAssist.addSetting(ModuleSetting.toggle("Visible Only", true));
         Module antiKnockback = new Module("AntiKB");
         antiKnockback.addSetting(ModuleSetting.slider("Horizontal", 0.0F, 0.0F, 100.0F, 5.0F));
         antiKnockback.addSetting(ModuleSetting.slider("Vertical", 0.0F, 0.0F, 100.0F, 5.0F));
         Module velocity = new Module("Velocity");
         velocity.addSetting(ModuleSetting.choice("Mode", new String[]{"Cancel", "Reduce", "Reverse"}, 0));
         Module autoArmor = new Module("AutoArmor");
         Module autoTotem = new Module("AutoTotem");
         Module triggerBot = new Module("TriggerBot");
         triggerBot.addSetting(ModuleSetting.slider("Delay", 1.0F, 0.0F, 5.0F, 1.0F));
         Module reach = new Module("Reach");
         reach.addSetting(ModuleSetting.slider("Distance", 3.5F, 3.0F, 6.0F, 0.1F));
         Module antiBot = new Module("AntiBot");
         antiBot.addSetting(ModuleSetting.choice("Mode", new String[]{"Default", "Advanced", "FunTime"}, 0));
         Module criticals = new Module("Criticals");
         criticals.addSetting(ModuleSetting.choice("Mode", new String[]{"Packet", "Jump", "Mini Jump"}, 0));
         Module autoClicker = new Module("AutoClicker");
         autoClicker.addSetting(ModuleSetting.slider("CPS", 12.0F, 1.0F, 20.0F, 1.0F));
         autoClicker.addSetting(ModuleSetting.toggle("Right Click", false));
         Module clan = new Module("Clan", clanOn, clanOff);
         Category combat = new Category("Combat", 0.0F, 0.0F);
         combat.addModule(killAura);
         combat.addModule(aimAssist);
         combat.addModule(antiKnockback);
         combat.addModule(velocity);
         combat.addModule(autoArmor);
         combat.addModule(autoTotem);
         combat.addModule(triggerBot);
         combat.addModule(reach);
         combat.addModule(antiBot);
         combat.addModule(criticals);
         combat.addModule(autoClicker);
         combat.addModule(clan);
         this.categories.add(combat);
         Module speed = new Module("Speed");
         speed.addSetting(ModuleSetting.choice("Mode", new String[]{"Vanilla", "Strafe", "BHop", "Low Hop"}, 0));
         speed.addSetting(ModuleSetting.slider("Speed", 1.5F, 0.5F, 5.0F, 0.1F));
         Module flight = new Module("Flight");
         flight.addSetting(ModuleSetting.choice("Mode", new String[]{"Vanilla", "Glide", "Jetpack", "Creative"}, 0));
         flight.addSetting(ModuleSetting.slider("Speed", 2.0F, 0.5F, 10.0F, 0.5F));
         Module noFall = new Module("NoFall");
         noFall.addSetting(ModuleSetting.choice("Mode", new String[]{"Packet", "Spoof", "MLG"}, 0));
         Module sprint = new Module("Sprint");
         sprint.addSetting(ModuleSetting.choice("Mode", new String[]{"Legit", "Omnidirectional"}, 0));
         Module step = new Module("Step");
         step.addSetting(ModuleSetting.slider("Height", 1.0F, 0.5F, 2.5F, 0.5F));
         Module noSlowdown = new Module("NoSlow");
         noSlowdown.addSetting(ModuleSetting.toggle("Items", true));
         noSlowdown.addSetting(ModuleSetting.toggle("Soulsand", true));
         noSlowdown.addSetting(ModuleSetting.toggle("Web", true));
         Module elytraFly = new Module("ElytraFly");
         elytraFly.addSetting(ModuleSetting.choice("Mode", new String[]{"Vanilla", "Boost", "Control"}, 0));
         elytraFly.addSetting(ModuleSetting.slider("Speed", 1.5F, 0.5F, 5.0F, 0.1F));
         Module jesus = new Module("Jesus");
         jesus.addSetting(ModuleSetting.choice("Mode", new String[]{"Solid", "Dolphin", "Trident"}, 0));
         Module sneak = new Module("Sneak");
         sneak.addSetting(ModuleSetting.choice("Mode", new String[]{"Vanilla", "Packet", "Legit"}, 0));
         Module spider = new Module("Spider");
         spider.addSetting(ModuleSetting.slider("Speed", 0.5F, 0.1F, 2.0F, 0.1F));
         Module phase = new Module("Phase");
         Module safewalk = new Module("SafeWalk");
         Module bunnyHop = new Module("BunnyHop");
         Module invWalk = new Module("InvWalk");
         Module parkour = new Module("Parkour");
         Module antiVoid = new Module("AntiVoid");
         Module longJump = new Module("LongJump");
         longJump.addSetting(ModuleSetting.slider("Boost", 1.5F, 1.0F, 4.0F, 0.1F));
         Category movement = new Category("Movement", 0.0F, 0.0F);
         movement.addModule(speed);
         movement.addModule(flight);
         movement.addModule(noFall);
         movement.addModule(sprint);
         movement.addModule(step);
         movement.addModule(noSlowdown);
         movement.addModule(elytraFly);
         movement.addModule(jesus);
         movement.addModule(sneak);
         movement.addModule(spider);
         movement.addModule(phase);
         movement.addModule(safewalk);
         movement.addModule(bunnyHop);
         movement.addModule(invWalk);
         movement.addModule(parkour);
         movement.addModule(antiVoid);
         movement.addModule(longJump);
         this.categories.add(movement);
         Module esp = new Module("ESP");
         esp.addSetting(ModuleSetting.choice("Mode", new String[]{"Box", "Glow", "2D", "Outline"}, 0));
         esp.addSetting(ModuleSetting.toggle("Players", true));
         esp.addSetting(ModuleSetting.toggle("Mobs", true));
         esp.addSetting(ModuleSetting.toggle("Items", false));
         Module tracers = new Module("Tracers");
         tracers.addSetting(ModuleSetting.toggle("Players", true));
         tracers.addSetting(ModuleSetting.toggle("Mobs", false));
         Module nametags = new Module("Nametags");
         nametags.addSetting(ModuleSetting.toggle("Health", true));
         nametags.addSetting(ModuleSetting.toggle("Armor", true));
         nametags.addSetting(ModuleSetting.slider("Scale", 1.5F, 0.5F, 3.0F, 0.1F));
         Module chams = new Module("Chams");
         chams.addSetting(ModuleSetting.choice("Mode", new String[]{"Colored", "Textured", "Flat"}, 0));
         Module fullBright = new Module("FullBright");
         fullBright.addSetting(ModuleSetting.choice("Mode", new String[]{"Gamma", "Night Vision"}, 0));
         Module xray = new Module("XRay");
         xray.addSetting(ModuleSetting.choice("Mode", new String[]{"Default", "Ores Only", "Custom"}, 0));
         Module blockOverlay = new Module("BlockOverlay");
         blockOverlay.addSetting(ModuleSetting.choice("Texture", new String[]{"Kitten", "Sky", "Devil"}, 0));
         Module blockEsp = new Module("BlockESP");
         blockEsp.addSetting(ModuleSetting.slider("Radius", 32.0F, 8.0F, 64.0F, 4.0F));
         Module motionBlur = new Module("MotionBlur");
         motionBlur.addSetting(ModuleSetting.slider("Strength", 0.5F, 0.1F, 0.9F, 0.05F));
         Module hitEffects = new Module("HitEffects");
         Module noRender = new Module("NoRender");
         noRender.addSetting(ModuleSetting.toggle("Fire", true));
         noRender.addSetting(ModuleSetting.toggle("Pumpkin", true));
         noRender.addSetting(ModuleSetting.toggle("Totem", true));
         noRender.addSetting(ModuleSetting.toggle("Fog", true));
         noRender.addSetting(ModuleSetting.toggle("Blindness", true));
         Module freecam = new Module("Freecam");
         freecam.addSetting(ModuleSetting.slider("Speed", 1.0F, 0.1F, 5.0F, 0.1F));
         Module waypoints = new Module("Waypoints");
         Module fog = new Module("Fog");
         fog.addSetting(ModuleSetting.choice("Color", new String[]{"White", "Light Blue", "Purple", "Red", "Green", "Dark", "Golden"}, 0));
         fog.addSetting(ModuleSetting.slider("Density", 0.5F, 0.0F, 1.0F, 0.1F));
         Module aspectRatio = new Module("AspectRatio");
         aspectRatio.addSetting(ModuleSetting.slider("FOV Scale", 1.33F, 1.0F, 2.0F, 0.01F));
         Module breadcrumbs = new Module("Breadcrumbs");
         Module storageESP = new Module("StorageESP");
         storageESP.addSetting(ModuleSetting.toggle("Chests", true));
         storageESP.addSetting(ModuleSetting.toggle("Ender Chests", true));
         storageESP.addSetting(ModuleSetting.toggle("Shulkers", true));
         Category render = new Category("Render", 0.0F, 0.0F);
         render.addModule(esp);
         render.addModule(tracers);
         render.addModule(nametags);
         render.addModule(chams);
         render.addModule(fullBright);
         render.addModule(xray);
         render.addModule(blockOverlay);
         render.addModule(blockEsp);
         render.addModule(motionBlur);
         render.addModule(hitEffects);
         render.addModule(noRender);
         render.addModule(freecam);
         render.addModule(waypoints);
         render.addModule(fog);
         render.addModule(aspectRatio);
         render.addModule(breadcrumbs);
         render.addModule(storageESP);
         this.categories.add(render);
         Module autoFish = new Module("AutoFish", autoFishOn, autoFishOff);
         Module noRotate = new Module("NoRotate");
         Module fastPlace = new Module("FastPlace");
         fastPlace.addSetting(ModuleSetting.slider("Delay", 0.0F, 0.0F, 4.0F, 1.0F));
         Module fastBreak = new Module("FastBreak");
         fastBreak.addSetting(ModuleSetting.slider("Multiplier", 1.5F, 1.0F, 5.0F, 0.1F));
         Module autoTool = new Module("AutoTool");
         Module scaffold = new Module("Scaffold");
         scaffold.addSetting(ModuleSetting.choice("Mode", new String[]{"Normal", "Expand", "Tower"}, 0));
         scaffold.addSetting(ModuleSetting.toggle("Safe Walk", true));
         Module timer = new Module("Timer");
         timer.addSetting(ModuleSetting.slider("Speed", 1.0F, 0.1F, 5.0F, 0.1F));
         Module blink = new Module("Blink");
         Module antiHunger = new Module("AntiHunger");
         Module autoEat = new Module("AutoEat");
         autoEat.addSetting(ModuleSetting.slider("Health", 10.0F, 1.0F, 19.0F, 1.0F));
         Module chestStealer = new Module("ChestStealer");
         chestStealer.addSetting(ModuleSetting.slider("Delay", 50.0F, 0.0F, 500.0F, 25.0F));
         Module inventoryCleaner = new Module("InvCleaner");
         Module autoRespawn = new Module("AutoRespawn");
         Module pingSpoof = new Module("PingSpoof");
         pingSpoof.addSetting(ModuleSetting.slider("Ping", 100.0F, 0.0F, 1000.0F, 50.0F));
         Module skinBlink = new Module("SkinBlink");
         Module autoDisconnect = new Module("AutoLeave");
         autoDisconnect.addSetting(ModuleSetting.slider("Health", 5.0F, 1.0F, 19.0F, 1.0F));
         Category player = new Category("Player", 0.0F, 0.0F);
         player.addModule(autoFish);
         player.addModule(noRotate);
         player.addModule(fastPlace);
         player.addModule(fastBreak);
         player.addModule(autoTool);
         player.addModule(scaffold);
         player.addModule(timer);
         player.addModule(blink);
         player.addModule(antiHunger);
         player.addModule(autoEat);
         player.addModule(chestStealer);
         player.addModule(inventoryCleaner);
         player.addModule(autoRespawn);
         player.addModule(pingSpoof);
         player.addModule(skinBlink);
         player.addModule(autoDisconnect);
         this.categories.add(player);
         Module warden = new Module("Warden", wardenOn, wardenOff);
         Module baseFinder = new Module("BaseFinder", baseFinderOn, baseFinderOff);
         baseFinder.addSetting(ModuleSetting.slider("Radius", 64.0F, 16.0F, 128.0F, 16.0F));
         Module nuker = new Module("Nuker");
         nuker.addSetting(ModuleSetting.slider("Radius", 4.0F, 1.0F, 6.0F, 1.0F));
         nuker.addSetting(ModuleSetting.choice("Mode", new String[]{"All", "Flat", "Smash"}, 0));
         Module autoSign = new Module("AutoSign");
         Module fucker = new Module("Fucker");
         fucker.addSetting(ModuleSetting.choice("Block", new String[]{"Bed", "Cake", "Spawner", "Egg"}, 0));
         Module autoFarm = new Module("AutoFarm", autoFarmOn, autoFarmOff);
         autoFarm.addSetting(ModuleSetting.slider("Radius", 4.0F, 2.0F, 8.0F, 1.0F));
         Module autoMine = new Module("AutoMine", autoMineOn, autoMineOff);
         autoMine.addSetting(ModuleSetting.choice("Ore", new String[]{"Diamond", "Emerald", "Gold", "Iron", "Netherite", "All Ores"}, 0));
         Module apple = new Module("Apple", appleOn, appleOff);
         Module dig = new Module("Dig", digOn, digOff);
         Module tunneller = new Module("Tunneller");
         tunneller.addSetting(ModuleSetting.choice("Size", new String[]{"1x2", "2x2", "3x3"}, 0));
         Module veinMiner = new Module("VeinMiner");
         Category world = new Category("World", 0.0F, 0.0F);
         world.addModule(warden);
         world.addModule(baseFinder);
         world.addModule(nuker);
         world.addModule(autoSign);
         world.addModule(fucker);
         world.addModule(autoFarm);
         world.addModule(autoMine);
         world.addModule(apple);
         world.addModule(dig);
         world.addModule(tunneller);
         world.addModule(veinMiner);
         this.categories.add(world);
         Module disabler = new Module("Disabler");
         disabler.addSetting(ModuleSetting.choice("Mode", new String[]{"FunTime", "Matrix", "Vulcan", "Grim"}, 0));
         Module fastBow = new Module("FastBow");
         Module ghostHand = new Module("GhostHand");
         Module packetMine = new Module("PacketMine");
         Module autoGapple = new Module("AutoGapple");
         Module portalGodMode = new Module("PortalGod");
         Module tpAura = new Module("TPAura");
         tpAura.addSetting(ModuleSetting.slider("Range", 8.0F, 3.0F, 32.0F, 1.0F));
         Module boatFly = new Module("BoatFly");
         boatFly.addSetting(ModuleSetting.slider("Speed", 2.0F, 0.5F, 10.0F, 0.5F));
         Module nameProtect = new Module("NameProtect");
         Module serverCrasher = new Module("Crasher");
         serverCrasher.addSetting(ModuleSetting.choice("Mode", new String[]{"Packet", "Book", "Movement"}, 0));
         Category exploit = new Category("Exploit", 0.0F, 0.0F);
         exploit.addModule(disabler);
         exploit.addModule(fastBow);
         exploit.addModule(ghostHand);
         exploit.addModule(packetMine);
         exploit.addModule(autoGapple);
         exploit.addModule(portalGodMode);
         exploit.addModule(tpAura);
         exploit.addModule(boatFly);
         exploit.addModule(nameProtect);
         exploit.addModule(serverCrasher);
         this.categories.add(exploit);
         Module autoCraft = new Module("AutoCraft", autoCraftOn, autoCraftOff);
         autoCraft.addSetting(ModuleSetting.choice("Recipe", new String[]{"Planks", "Sticks", "Torches", "Bread", "Golden Apple"}, 0));
         Module autoSell = new Module("AutoSell", autoSellOn, autoSellOff);
         autoSell.addSetting(ModuleSetting.choice("Mode", new String[]{"Buyer", "Junk Only", "Sell All"}, 0));
         autoSell.addSetting(ModuleSetting.slider("Interval", 30.0F, 10.0F, 120.0F, 5.0F));
         Module autoBuy = new Module("AutoBuy", autoBuyOn, autoBuyOff);
         autoBuy.addSetting(
            ModuleSetting.choice("Item", new String[]{"Diamond", "Emerald", "Netherite", "God Apple", "Elytra", "Totem", "Shulker", "Beacon"}, 0)
         );
         autoBuy.addSetting(ModuleSetting.slider("Max Price", 10000.0F, 100.0F, 100000.0F, 500.0F));
         autoBuy.addSetting(ModuleSetting.slider("Interval", 10.0F, 3.0F, 60.0F, 1.0F));
         autoBuy.addSetting(ModuleSetting.choice("Search", new String[]{"Browse /ah", "Search /ah search"}, 1));
         Module autoEvent = new Module("AutoEvent");
         autoEvent.addSetting(ModuleSetting.toggle("Auto Join", true));
         Module salary = new Module("Salary");
         salary.addSetting(ModuleSetting.slider("Interval", 60.0F, 30.0F, 300.0F, 10.0F));
         Category funtime = new Category("FunTime", 0.0F, 0.0F);
         funtime.addModule(autoCraft);
         funtime.addModule(autoSell);
         funtime.addModule(autoBuy);
         funtime.addModule(autoEvent);
         funtime.addModule(salary);
         this.categories.add(funtime);

         for (Category cat : this.categories) {
            for (ModuleButton btn : cat.getModules()) {
               ModuleManager.register(btn.getModule());
            }
         }
      }
   }

   private int totalWidth() {
      return this.categories.size() * 130 + (this.categories.size() - 1) * 6;
   }

   private int startX() {
      return (this.width - this.totalWidth()) / 2;
   }

   private int startY() {
      return 40;
   }

   public void render(DrawContext context, int mouseX, int mouseY, float delta) {
      this.openProgress = this.openProgress + (1.0F - this.openProgress) * 0.15F;
      if (this.openProgress > 0.99F) {
         this.openProgress = 1.0F;
      }

      if (!(this.openProgress < 0.01F)) {
         LiquidGlassRenderer.captureAndBlur();
         int dimAlpha = (int)(this.openProgress * 102.0F);
         context.fill(0, 0, this.width, this.height, dimAlpha << 24);
         GuiSettings.AccentColor accent = GuiSettings.getAccentColor();
         int sx = this.startX();
         int sy = this.startY();

         for (int i = 0; i < this.categories.size(); i++) {
            Category cat = this.categories.get(i);
            int cx = sx + i * 136;
            int colH = this.getColumnHeight(cat);
            float slideOffset = (1.0F - this.openProgress) * (float)(30 + i * 12);
            int cy = sy + (int)slideOffset;
            float alpha = this.openProgress;
            this.renderColumn(context, cat, cx, cy, colH, mouseX, mouseY, accent, alpha);
         }

         super.render(context, mouseX, mouseY, delta);
      }
   }

   private void renderColumn(DrawContext context, Category cat, int cx, int cy, int colH, int mouseX, int mouseY, GuiSettings.AccentColor accent, float alpha) {
      if (PerformanceSettings.useGlassEffect() && LiquidGlassRenderer.isReady()) {
         LiquidGlassRenderer.drawGlassPanel(context, (float)cx, (float)cy, 130.0F, (float)colH, 8.0F, 0.0F, 0.08F, accent.r, accent.g, accent.b);
      } else {
         RoundedRectRenderer.draw(context, cx, cy, 130, colH, 8, -871230694);
      }

      RoundedRectRenderer.draw(context, cx, cy, 130, 26, 8, 8, 0, 0, (int)(85.0F * alpha) << 24 | accent.textColor & 16777215);
      String name = cat.getName();
      int textW = this.textRenderer.getWidth(this.styledText(name));
      int textX = cx + 65 - textW / 2;
      this.drawStyledText(context, name, textX, cy + 13 - 4, -1);
      int scroll = this.scrollOffsets.getOrDefault(cat.getName(), 0);
      int contentY = cy + 26 + 2;
      int maxContentH = this.height - contentY - 20;
      context.enableScissor(cx, contentY, cx + 130, contentY + maxContentH);
      int y = contentY - scroll;

      for (ModuleButton btn : cat.getModules()) {
         Module mod = btn.getModule();
         boolean hovered = mouseX >= cx + 2
            && mouseX <= cx + 130 - 2
            && mouseY >= y
            && mouseY < y + 18
            && mouseY >= contentY
            && mouseY < contentY + maxContentH;
         btn.updateHover(hovered);
         if (mod.isEnabled()) {
            RoundedRectRenderer.draw(context, cx + 3, y + 1, 124, 16, 4, (int)(68.0F * alpha) << 24 | accent.textColor & 16777215);
         } else if (btn.hoverAmount > 0.01F) {
            int hAlpha = (int)(btn.hoverAmount * 32.0F * alpha);
            RoundedRectRenderer.draw(context, cx + 3, y + 1, 124, 16, 4, hAlpha << 24 | 16777215);
         }

         int nameColor = mod.isEnabled() ? -1 : -5592406;
         this.drawStyledText(context, mod.getName(), cx + 6 + 2, y + 5, nameColor);
         if (mod.hasSettings()) {
            String arrow = mod.isSettingsExpanded() ? "v" : ">";
            int arrowColor = mod.isSettingsExpanded() ? accent.textColor : -11184811;
            this.drawStyledText(context, arrow, cx + 130 - 6 - 8, y + 5, arrowColor);
         }

         y += 18;
         if (mod.isSettingsExpanded() && mod.hasSettings()) {
            for (ModuleSetting setting : mod.getSettings()) {
               this.renderSetting(context, setting, cx, y, mouseX, mouseY, accent, alpha, contentY, maxContentH);
               y += 16;
            }

            y += 2;
         }
      }

      context.disableScissor();
   }

   private void renderSetting(
      DrawContext context,
      ModuleSetting setting,
      int cx,
      int y,
      int mouseX,
      int mouseY,
      GuiSettings.AccentColor accent,
      float alpha,
      int contentY,
      int maxContentH
   ) {
      int left = cx + 6 + 4;
      int right = cx + 130 - 6 - 2;
      int w = right - left;
      RoundedRectRenderer.draw(context, left - 1, y, w + 2, 16, 3, (int)(24.0F * alpha) << 24);
      if (setting.getType() == ModuleSetting.Type.SLIDER) {
         this.drawStyledText(context, setting.getName(), left + 2, y + 1, -8947849);
         float sliderX = (float)(left + 2);
         float sliderW = (float)(w - 4);
         float sliderY = (float)(y + 16 - 5);
         float norm = setting.getNormalized();
         RoundedRectRenderer.draw(context, (int)sliderX, (int)sliderY, (int)sliderW, 3, 2, 872415231);
         int fillW = (int)(sliderW * norm);
         if (fillW > 0) {
            RoundedRectRenderer.draw(context, (int)sliderX, (int)sliderY, fillW, 3, 2, accent.textColor);
         }

         int knobCx = (int)(sliderX + sliderW * norm);
         RoundedRectRenderer.draw(context, knobCx - 3, (int)sliderY - 2, 6, 7, 3, -1);
         String val = setting.getDisplayValue();
         int valW = this.textRenderer.getWidth(this.styledText(val));
         this.drawStyledText(context, val, right - valW - 1, y + 1, -4473925);
      } else if (setting.getType() == ModuleSetting.Type.TOGGLE) {
         this.drawStyledText(context, setting.getName(), left + 2, y + 4, -8947849);
         int sw = 16;
         int sh = 8;
         int tx = right - sw - 2;
         int ty = y + 8 - sh / 2;
         int bgColor = setting.getBool() ? -1157627904 | accent.textColor & 16777215 : 1145324612;
         RoundedRectRenderer.draw(context, tx, ty, sw, sh, sh / 2, bgColor);
         int knobD = sh - 2;
         int knobX = setting.getBool() ? tx + sw - knobD - 1 : tx + 1;
         RoundedRectRenderer.draw(context, knobX, ty + 1, knobD, knobD, knobD / 2, -1);
      } else if (setting.getType() == ModuleSetting.Type.CHOICE) {
         this.drawStyledText(context, setting.getName(), left + 2, y + 4, -8947849);
         String val = setting.getChoiceValue();
         int valW = this.textRenderer.getWidth(this.styledText(val));
         int valX = right - valW - 4;
         RoundedRectRenderer.draw(context, valX - 3, y + 2, valW + 6, 12, 3, 587202559);
         this.drawStyledText(context, val, valX, y + 4, -3355444);
      }
   }

   private int getColumnHeight(Category cat) {
      int h = 30;

      for (ModuleButton btn : cat.getModules()) {
         h += 18;
         Module mod = btn.getModule();
         if (mod.isSettingsExpanded() && mod.hasSettings()) {
            h += mod.getSettings().size() * 16 + 2;
         }
      }

      return h + 4;
   }

   private Text styledText(String text) {
      return GuiSettings.isCustomFontEnabled()
         ? Text.literal(text).styled(style -> style.withFont(CUSTOM_FONT))
         : Text.literal(text);
   }

   private void drawStyledText(DrawContext context, String text, int x, int y, int color) {
      context.drawText(this.textRenderer, this.styledText(text), x, y, color, true);
   }

   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      int sx = this.startX();
      int sy = this.startY();

      for (int i = 0; i < this.categories.size(); i++) {
         Category cat = this.categories.get(i);
         int cx = sx + i * 136;
         float slideOffset = (1.0F - this.openProgress) * (float)(30 + i * 12);
         int cy = sy + (int)slideOffset;
         int colH = this.getColumnHeight(cat);
         if (!(mouseX < (double)cx) && !(mouseX > (double)(cx + 130)) && !(mouseY < (double)cy) && !(mouseY > (double)(cy + colH))) {
            if (mouseY < (double)(cy + 26)) {
               return true;
            }

            int scroll = this.scrollOffsets.getOrDefault(cat.getName(), 0);
            int contentY = cy + 26 + 2;
            int y = contentY - scroll;

            for (ModuleButton btn : cat.getModules()) {
               Module mod = btn.getModule();
               if (mouseY >= (double)y && mouseY < (double)(y + 18) && mouseY >= (double)contentY) {
                  if (button == 0) {
                     if (mod.hasSettings() && mouseX >= (double)(cx + 130 - 6 - 14)) {
                        mod.toggleSettingsExpanded();
                     } else {
                        mod.toggle();
                     }
                  } else if (button == 1 && mod.hasSettings()) {
                     mod.toggleSettingsExpanded();
                  }

                  return true;
               }

               y += 18;
               if (mod.isSettingsExpanded() && mod.hasSettings()) {
                  for (ModuleSetting setting : mod.getSettings()) {
                     if (mouseY >= (double)y && mouseY < (double)(y + 16) && mouseY >= (double)contentY) {
                        this.handleSettingClick(setting, cx, y, mouseX);
                        return true;
                     }

                     y += 16;
                  }

                  y += 2;
               }
            }
         }
      }

      return super.mouseClicked(mouseX, mouseY, button);
   }

   private void handleSettingClick(ModuleSetting setting, int cx, int y, double mouseX) {
      if (setting.getType() == ModuleSetting.Type.TOGGLE) {
         setting.toggleBool();
      } else if (setting.getType() == ModuleSetting.Type.CHOICE) {
         setting.cycleChoice();
      } else if (setting.getType() == ModuleSetting.Type.SLIDER) {
         int left = cx + 6 + 4;
         int right = cx + 130 - 6 - 2;
         int w = right - left;
         float sliderX = (float)(left + 2);
         float sliderW = (float)(w - 4);
         float norm = (float)((mouseX - (double)sliderX) / (double)sliderW);
         norm = Math.max(0.0F, Math.min(1.0F, norm));
         setting.setFromNormalized(norm);
         this.draggingSlider = setting;
         this.draggingSliderX = sliderX;
         this.draggingSliderW = sliderW;
      }
   }

   public boolean mouseReleased(double mouseX, double mouseY, int button) {
      if (button == 0) {
         this.draggingSlider = null;
      }

      return super.mouseReleased(mouseX, mouseY, button);
   }

   public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
      if (button == 0 && this.draggingSlider != null) {
         float norm = (float)((mouseX - (double)this.draggingSliderX) / (double)this.draggingSliderW);
         norm = Math.max(0.0F, Math.min(1.0F, norm));
         this.draggingSlider.setFromNormalized(norm);
         return true;
      } else {
         return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
      }
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
      int sx = this.startX();
      int sy = this.startY();

      for (int i = 0; i < this.categories.size(); i++) {
         Category cat = this.categories.get(i);
         int cx = sx + i * 136;
         float slideOffset = (1.0F - this.openProgress) * (float)(30 + i * 12);
         int cy = sy + (int)slideOffset;
         int colH = this.getColumnHeight(cat);
         if (mouseX >= (double)cx && mouseX <= (double)(cx + 130) && mouseY >= (double)cy && mouseY <= (double)(cy + colH)) {
            int scroll = this.scrollOffsets.getOrDefault(cat.getName(), 0);
            scroll -= (int)(verticalAmount * 10.0);
            int contentH = this.height - cy - 26 - 22;
            int totalH = colH - 26 - 4;
            int maxScroll = Math.max(0, totalH - contentH);
            scroll = Math.max(0, Math.min(maxScroll, scroll));
            this.scrollOffsets.put(cat.getName(), scroll);
            return true;
         }
      }

      return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
   }

   public boolean shouldPause() {
      return false;
   }
}
