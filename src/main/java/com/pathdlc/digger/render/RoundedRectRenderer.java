package com.pathdlc.digger.render;

import com.mojang.blaze3d.systems.RenderSystem;
import java.nio.FloatBuffer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class RoundedRectRenderer {
   private static final Logger LOGGER = LoggerFactory.getLogger("PathDLC-RoundRect");
   private static int program;
   private static int vao;
   private static int vbo;
   private static boolean initialized;
   private static boolean failed;
   private static int uScreenSize;
   private static int uRectPos;
   private static int uRectSize;
   private static int uRadius;
   private static int uColor;
   private static int uRadiusTL;
   private static int uRadiusTR;
   private static int uRadiusBL;
   private static int uRadiusBR;
   private static final String VERT_SRC = "#version 150\nin vec2 aPos;\nvoid main() {\n    gl_Position = vec4(aPos * 2.0 - 1.0, 0.0, 1.0);\n}\n";
   private static final String FRAG_SRC = "#version 150\nuniform vec2 uScreenSize;\nuniform vec2 uRectPos;\nuniform vec2 uRectSize;\nuniform float uRadiusTL;\nuniform float uRadiusTR;\nuniform float uRadiusBL;\nuniform float uRadiusBR;\nuniform vec4 uColor;\nout vec4 fragColor;\n\nfloat roundedBoxSDF(vec2 p, vec2 b, float tl, float tr, float bl, float br) {\n    float rx = (p.x > 0.0) ? ((p.y > 0.0) ? tr : br) : ((p.y > 0.0) ? tl : bl);\n    vec2 q = abs(p) - b + rx;\n    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - rx;\n}\n\nvoid main() {\n    vec2 fragCoord = gl_FragCoord.xy;\n    vec2 center = uRectPos + uRectSize * 0.5;\n    vec2 halfSize = uRectSize * 0.5;\n    vec2 p = fragCoord - center;\n\n    float dist = roundedBoxSDF(p, halfSize, uRadiusTL, uRadiusTR, uRadiusBL, uRadiusBR);\n\n    float aa = 1.0;\n    float alpha = 1.0 - smoothstep(-aa, aa, dist);\n\n    fragColor = uColor * alpha;\n}\n";

   private RoundedRectRenderer() {
   }

   private static void init() {
      if (!initialized && !failed) {
         try {
            int vert = GL20.glCreateShader(35633);
            GL20.glShaderSource(vert, "#version 150\nin vec2 aPos;\nvoid main() {\n    gl_Position = vec4(aPos * 2.0 - 1.0, 0.0, 1.0);\n}\n");
            GL20.glCompileShader(vert);
            if (GL20.glGetShaderi(vert, 35713) == 0) {
               LOGGER.error("RoundRect vert: {}", GL20.glGetShaderInfoLog(vert));
               failed = true;
               return;
            }

            int frag = GL20.glCreateShader(35632);
            GL20.glShaderSource(
               frag,
               "#version 150\nuniform vec2 uScreenSize;\nuniform vec2 uRectPos;\nuniform vec2 uRectSize;\nuniform float uRadiusTL;\nuniform float uRadiusTR;\nuniform float uRadiusBL;\nuniform float uRadiusBR;\nuniform vec4 uColor;\nout vec4 fragColor;\n\nfloat roundedBoxSDF(vec2 p, vec2 b, float tl, float tr, float bl, float br) {\n    float rx = (p.x > 0.0) ? ((p.y > 0.0) ? tr : br) : ((p.y > 0.0) ? tl : bl);\n    vec2 q = abs(p) - b + rx;\n    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - rx;\n}\n\nvoid main() {\n    vec2 fragCoord = gl_FragCoord.xy;\n    vec2 center = uRectPos + uRectSize * 0.5;\n    vec2 halfSize = uRectSize * 0.5;\n    vec2 p = fragCoord - center;\n\n    float dist = roundedBoxSDF(p, halfSize, uRadiusTL, uRadiusTR, uRadiusBL, uRadiusBR);\n\n    float aa = 1.0;\n    float alpha = 1.0 - smoothstep(-aa, aa, dist);\n\n    fragColor = uColor * alpha;\n}\n"
            );
            GL20.glCompileShader(frag);
            if (GL20.glGetShaderi(frag, 35713) == 0) {
               LOGGER.error("RoundRect frag: {}", GL20.glGetShaderInfoLog(frag));
               failed = true;
               return;
            }

            program = GL20.glCreateProgram();
            GL20.glAttachShader(program, vert);
            GL20.glAttachShader(program, frag);
            GL20.glLinkProgram(program);
            if (GL20.glGetProgrami(program, 35714) == 0) {
               LOGGER.error("RoundRect link: {}", GL20.glGetProgramInfoLog(program));
               failed = true;
               return;
            }

            GL20.glDeleteShader(vert);
            GL20.glDeleteShader(frag);
            uScreenSize = GL20.glGetUniformLocation(program, "uScreenSize");
            uRectPos = GL20.glGetUniformLocation(program, "uRectPos");
            uRectSize = GL20.glGetUniformLocation(program, "uRectSize");
            uRadiusTL = GL20.glGetUniformLocation(program, "uRadiusTL");
            uRadiusTR = GL20.glGetUniformLocation(program, "uRadiusTR");
            uRadiusBL = GL20.glGetUniformLocation(program, "uRadiusBL");
            uRadiusBR = GL20.glGetUniformLocation(program, "uRadiusBR");
            uColor = GL20.glGetUniformLocation(program, "uColor");
            vao = GL30.glGenVertexArrays();
            GL30.glBindVertexArray(vao);
            float[] quad = new float[]{0.0F, 0.0F, 1.0F, 0.0F, 1.0F, 1.0F, 0.0F, 0.0F, 1.0F, 1.0F, 0.0F, 1.0F};
            FloatBuffer buf = BufferUtils.createFloatBuffer(quad.length);
            buf.put(quad).flip();
            vbo = GL15.glGenBuffers();
            GL15.glBindBuffer(34962, vbo);
            GL15.glBufferData(34962, buf, 35044);
            GL20.glEnableVertexAttribArray(0);
            GL20.glVertexAttribPointer(0, 2, 5126, false, 0, 0L);
            GL30.glBindVertexArray(0);
            initialized = true;
         } catch (Exception var4) {
            LOGGER.error("RoundRect init failed", var4);
            failed = true;
         }
      }
   }

   public static void draw(DrawContext context, int x, int y, int w, int h, int radius, int argb) {
      draw(context, x, y, w, h, radius, radius, radius, radius, argb);
   }

   public static void draw(DrawContext context, int x, int y, int w, int h, int tl, int tr, int bl, int br, int argb) {
      if (!PerformanceSettings.useRoundedShader()) {
         context.fill(x, y, x + w, y + h, argb);
      } else {
         init();
         if (!failed && initialized) {
            MinecraftClient mc = MinecraftClient.getInstance();
            int fbW = mc.getWindow().getFramebufferWidth();
            int fbH = mc.getWindow().getFramebufferHeight();
            float scale = (float)mc.getWindow().getScaleFactor();
            float fx = (float)x * scale;
            float fy = (float)fbH - (float)(y + h) * scale;
            float fw = (float)w * scale;
            float fh = (float)h * scale;
            float a = (float)(argb >> 24 & 0xFF) / 255.0F;
            float r = (float)(argb >> 16 & 0xFF) / 255.0F;
            float g = (float)(argb >> 8 & 0xFF) / 255.0F;
            float b = (float)(argb & 0xFF) / 255.0F;
            int prevProg = GL11.glGetInteger(35725);
            int prevVao = GL11.glGetInteger(34229);
            boolean prevBlend = GL11.glIsEnabled(3042);
            boolean prevDepth = GL11.glIsEnabled(2929);
            GL11.glEnable(3042);
            GL11.glDisable(2929);
            RenderSystem.defaultBlendFunc();
            GL20.glUseProgram(program);
            GL30.glBindVertexArray(vao);
            GL20.glUniform2f(uScreenSize, (float)fbW, (float)fbH);
            GL20.glUniform2f(uRectPos, fx, fy);
            GL20.glUniform2f(uRectSize, fw, fh);
            GL20.glUniform1f(uRadiusTL, (float)tl * scale);
            GL20.glUniform1f(uRadiusTR, (float)tr * scale);
            GL20.glUniform1f(uRadiusBL, (float)bl * scale);
            GL20.glUniform1f(uRadiusBR, (float)br * scale);
            GL20.glUniform4f(uColor, r, g, b, a);
            GL11.glDrawArrays(4, 0, 6);
            GL30.glBindVertexArray(prevVao);
            GL20.glUseProgram(prevProg);
            if (!prevBlend) {
               GL11.glDisable(3042);
            }

            if (prevDepth) {
               GL11.glEnable(2929);
            }
         } else {
            context.fill(x, y, x + w, y + h, argb);
         }
      }
   }
}
