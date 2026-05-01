package com.pathdlc.digger.render;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.Identifier;
import net.minecraft.client.gui.DrawContext;

public final class HitEffectsRenderer {
   private static final Identifier HIT_STAR = Identifier.of("pathdlc_digger", "textures/gui/hit_star.png");
   private static final int TEX_SIZE = 32;
   private static final List<HitEffectsRenderer.HitStar> stars = new ArrayList<>();
   private static final Random RANDOM = new Random();

   public static void spawnAt(double screenX, double screenY) {
      int count = 5 + RANDOM.nextInt(4);

      for (int i = 0; i < count; i++) {
         double angle = RANDOM.nextDouble() * Math.PI * 2.0;
         double speed = 2.0 + RANDOM.nextDouble() * 5.0;
         double vx = Math.cos(angle) * speed;
         double vy = Math.sin(angle) * speed - 1.5;
         float scale = 0.4F + RANDOM.nextFloat() * 0.8F;
         float rotation = RANDOM.nextFloat() * 360.0F;
         float rotSpeed = (RANDOM.nextFloat() - 0.5F) * 15.0F;
         stars.add(
            new HitEffectsRenderer.HitStar(
               screenX + (RANDOM.nextDouble() - 0.5) * 20.0, screenY + (RANDOM.nextDouble() - 0.5) * 20.0, vx, vy, scale, rotation, rotSpeed
            )
         );
      }
   }

   public static void renderHud(DrawContext context) {
      if (!stars.isEmpty()) {
         Iterator<HitEffectsRenderer.HitStar> it = stars.iterator();

         while (it.hasNext()) {
            HitEffectsRenderer.HitStar s = it.next();
            s.tick();
            if (s.isDead()) {
               it.remove();
            } else {
               int drawSize = (int)(32.0F * s.scale);
               if (drawSize >= 2) {
                  int x = (int)s.x - drawSize / 2;
                  int y = (int)s.y - drawSize / 2;
                  context.getMatrices().push();
                  context.getMatrices().translate(s.x, s.y, 0.0);
                  context.getMatrices().scale(s.scale * s.alpha(), s.scale * s.alpha(), 1.0F);
                  context.getMatrices().translate(-s.x, -s.y, 0.0);
                  context.drawTexture(RenderLayer::getGuiTextured, HIT_STAR, (int)s.x - 16, (int)s.y - 16, 0.0F, 0.0F, 32, 32, 32, 32);
                  context.getMatrices().pop();
               }
            }
         }
      }
   }

   public static boolean hasActiveEffects() {
      return !stars.isEmpty();
   }

   private HitEffectsRenderer() {
   }

   private static class HitStar {
      double x;
      double y;
      double vx;
      double vy;
      float scale;
      float rotation;
      float rotSpeed;
      int age;
      static final int MAX_AGE = 25;

      HitStar(double x, double y, double vx, double vy, float scale, float rotation, float rotSpeed) {
         this.x = x;
         this.y = y;
         this.vx = vx;
         this.vy = vy;
         this.scale = scale;
         this.rotation = rotation;
         this.rotSpeed = rotSpeed;
      }

      void tick() {
         this.x = this.x + this.vx;
         this.y = this.y + this.vy;
         this.vy += 0.25;
         this.vx *= 0.95;
         this.vy *= 0.95;
         this.rotation = this.rotation + this.rotSpeed;
         this.age++;
      }

      boolean isDead() {
         return this.age >= 25;
      }

      float alpha() {
         return this.age < 3 ? (float)this.age / 3.0F : Math.max(0.0F, 1.0F - (float)(this.age - 3) / 22.0F);
      }
   }
}
