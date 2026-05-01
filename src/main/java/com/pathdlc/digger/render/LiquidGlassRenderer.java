package com.pathdlc.digger.render;

import com.mojang.blaze3d.systems.RenderSystem;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LiquidGlassRenderer {
   private static final Logger LOGGER = LoggerFactory.getLogger("PathDLC-Glass");
   private static int blurProgram;
   private static int glassProgram;
   private static int fboA;
   private static int texA;
   private static int fboB;
   private static int texB;
   private static int quadVao;
   private static int quadVbo;
   private static int lastWidth;
   private static int lastHeight;
   private static boolean initialized;
   private static boolean shadersFailed;
   private static long lastCaptureFrame = -1L;
   private static int uBlurSampler;
   private static int uBlurDirection;
   private static int uGlassSampler;
   private static int uGlassScreenSize;
   private static int uGlassPanelPos;
   private static int uGlassPanelSize;
   private static int uGlassRadius;
   private static int uGlassHover;
   private static int uGlassAccentColor;
   private static int uGlassAccentMix;
   private static final String BLUR_VSH = "#version 150\nin vec3 Position;\nin vec2 UV;\nout vec2 texCoord;\nvoid main() {\n    gl_Position = vec4(Position, 1.0);\n    texCoord = UV;\n}\n";
   private static final String BLUR_FSH = "#version 150\nuniform sampler2D Sampler0;\nuniform vec2 Direction;\nin vec2 texCoord;\nout vec4 fragColor;\nvoid main() {\n    vec4 color = vec4(0.0);\n    float total = 0.0;\n    for (float i = -8.0; i <= 8.0; i += 1.0) {\n        float weight = exp(-(i * i) / 18.0);\n        color += texture(Sampler0, texCoord + Direction * i) * weight;\n        total += weight;\n    }\n    fragColor = color / total;\n}\n";
   private static final String GLASS_VSH = "#version 150\nin vec3 Position;\nin vec2 UV;\nout vec2 texCoord;\nvoid main() {\n    gl_Position = vec4(Position, 1.0);\n    texCoord = UV;\n}\n";
   private static final String GLASS_FSH = "#version 150\nuniform sampler2D BlurredScene;\nuniform vec2 ScreenSize;\nuniform vec2 PanelPos;\nuniform vec2 PanelSize;\nuniform float Radius;\nuniform float HoverAmount;\nuniform vec3 AccentColor;\nuniform float AccentMix;\nin vec2 texCoord;\nout vec4 fragColor;\n\nfloat roundedBoxSDF(vec2 p, vec2 b, float r) {\n    vec2 d = abs(p) - b + r;\n    return length(max(d, 0.0)) - r;\n}\n\nvoid main() {\n    vec2 pixelPos = gl_FragCoord.xy;\n    vec2 center = PanelPos + PanelSize * 0.5;\n    vec2 relPos = pixelPos - center;\n\n    float dist = roundedBoxSDF(relPos, PanelSize * 0.5, Radius);\n    if (dist > 1.0) discard;\n\n    vec2 uv = gl_FragCoord.xy / ScreenSize;\n\n    float chromaOffset = 0.0008 + 0.0004 * HoverAmount;\n    float rCh = texture(BlurredScene, uv + vec2(chromaOffset, 0.0)).r;\n    float gCh = texture(BlurredScene, uv).g;\n    float bCh = texture(BlurredScene, uv - vec2(chromaOffset, 0.0)).b;\n    vec3 blurred = vec3(rCh, gCh, bCh);\n\n    vec3 glassTint = vec3(0.06, 0.06, 0.12);\n    vec3 glassColor = mix(blurred * 0.65, glassTint, 0.5);\n    glassColor = mix(glassColor, AccentColor, AccentMix);\n    glassColor += vec3(0.05) * HoverAmount;\n\n    vec2 normPos = relPos / (PanelSize * 0.5);\n    float gradient = normPos.y * 0.04 + 0.02;\n    glassColor += vec3(gradient);\n\n    float rimWidth = 1.5;\n    float rim = 1.0 - smoothstep(0.0, rimWidth, abs(dist));\n    glassColor += vec3(0.3) * rim;\n\n    float innerGlow = smoothstep(PanelSize.x * 0.4, 0.0, length(relPos));\n    glassColor += vec3(0.02) * innerGlow;\n\n    float alpha = 1.0 - smoothstep(-1.0, 0.5, dist);\n    float baseAlpha = 0.75 + 0.1 * HoverAmount;\n\n    fragColor = vec4(glassColor, baseAlpha * alpha);\n}\n";

   public static void init() {
      if (!initialized && !shadersFailed) {
         LOGGER.info("LiquidGlassRenderer init called");

         try {
            loadShaders();
            createQuad();
            initialized = true;
            LOGGER.info("LiquidGlassRenderer initialized successfully");
         } catch (Exception var1) {
            LOGGER.error("Shader load failed: " + var1.getMessage(), var1);
            shadersFailed = true;
         }
      }
   }

   public static boolean isReady() {
      return initialized && !shadersFailed && blurProgram != 0 && glassProgram != 0;
   }

   private static void loadShaders() {
      LOGGER.info("Compiling blur shader...");
      blurProgram = createProgram(
         "#version 150\nin vec3 Position;\nin vec2 UV;\nout vec2 texCoord;\nvoid main() {\n    gl_Position = vec4(Position, 1.0);\n    texCoord = UV;\n}\n",
         "#version 150\nuniform sampler2D Sampler0;\nuniform vec2 Direction;\nin vec2 texCoord;\nout vec4 fragColor;\nvoid main() {\n    vec4 color = vec4(0.0);\n    float total = 0.0;\n    for (float i = -8.0; i <= 8.0; i += 1.0) {\n        float weight = exp(-(i * i) / 18.0);\n        color += texture(Sampler0, texCoord + Direction * i) * weight;\n        total += weight;\n    }\n    fragColor = color / total;\n}\n"
      );
      uBlurSampler = GL20.glGetUniformLocation(blurProgram, "Sampler0");
      uBlurDirection = GL20.glGetUniformLocation(blurProgram, "Direction");
      LOGGER.info("Blur shader compiled: program={}", blurProgram);
      LOGGER.info("Compiling glass shader...");
      glassProgram = createProgram(
         "#version 150\nin vec3 Position;\nin vec2 UV;\nout vec2 texCoord;\nvoid main() {\n    gl_Position = vec4(Position, 1.0);\n    texCoord = UV;\n}\n",
         "#version 150\nuniform sampler2D BlurredScene;\nuniform vec2 ScreenSize;\nuniform vec2 PanelPos;\nuniform vec2 PanelSize;\nuniform float Radius;\nuniform float HoverAmount;\nuniform vec3 AccentColor;\nuniform float AccentMix;\nin vec2 texCoord;\nout vec4 fragColor;\n\nfloat roundedBoxSDF(vec2 p, vec2 b, float r) {\n    vec2 d = abs(p) - b + r;\n    return length(max(d, 0.0)) - r;\n}\n\nvoid main() {\n    vec2 pixelPos = gl_FragCoord.xy;\n    vec2 center = PanelPos + PanelSize * 0.5;\n    vec2 relPos = pixelPos - center;\n\n    float dist = roundedBoxSDF(relPos, PanelSize * 0.5, Radius);\n    if (dist > 1.0) discard;\n\n    vec2 uv = gl_FragCoord.xy / ScreenSize;\n\n    float chromaOffset = 0.0008 + 0.0004 * HoverAmount;\n    float rCh = texture(BlurredScene, uv + vec2(chromaOffset, 0.0)).r;\n    float gCh = texture(BlurredScene, uv).g;\n    float bCh = texture(BlurredScene, uv - vec2(chromaOffset, 0.0)).b;\n    vec3 blurred = vec3(rCh, gCh, bCh);\n\n    vec3 glassTint = vec3(0.06, 0.06, 0.12);\n    vec3 glassColor = mix(blurred * 0.65, glassTint, 0.5);\n    glassColor = mix(glassColor, AccentColor, AccentMix);\n    glassColor += vec3(0.05) * HoverAmount;\n\n    vec2 normPos = relPos / (PanelSize * 0.5);\n    float gradient = normPos.y * 0.04 + 0.02;\n    glassColor += vec3(gradient);\n\n    float rimWidth = 1.5;\n    float rim = 1.0 - smoothstep(0.0, rimWidth, abs(dist));\n    glassColor += vec3(0.3) * rim;\n\n    float innerGlow = smoothstep(PanelSize.x * 0.4, 0.0, length(relPos));\n    glassColor += vec3(0.02) * innerGlow;\n\n    float alpha = 1.0 - smoothstep(-1.0, 0.5, dist);\n    float baseAlpha = 0.75 + 0.1 * HoverAmount;\n\n    fragColor = vec4(glassColor, baseAlpha * alpha);\n}\n"
      );
      uGlassSampler = GL20.glGetUniformLocation(glassProgram, "BlurredScene");
      uGlassScreenSize = GL20.glGetUniformLocation(glassProgram, "ScreenSize");
      uGlassPanelPos = GL20.glGetUniformLocation(glassProgram, "PanelPos");
      uGlassPanelSize = GL20.glGetUniformLocation(glassProgram, "PanelSize");
      uGlassRadius = GL20.glGetUniformLocation(glassProgram, "Radius");
      uGlassHover = GL20.glGetUniformLocation(glassProgram, "HoverAmount");
      uGlassAccentColor = GL20.glGetUniformLocation(glassProgram, "AccentColor");
      uGlassAccentMix = GL20.glGetUniformLocation(glassProgram, "AccentMix");
      LOGGER.info("Glass shader compiled: program={}", glassProgram);
   }

   private static int createProgram(String vsh, String fsh) {
      int vs = GL20.glCreateShader(35633);
      GL20.glShaderSource(vs, vsh);
      GL20.glCompileShader(vs);
      if (GL20.glGetShaderi(vs, 35713) == 0) {
         String log = GL20.glGetShaderInfoLog(vs);
         GL20.glDeleteShader(vs);
         throw new RuntimeException("Vertex shader compile error: " + log);
      } else {
         int fs = GL20.glCreateShader(35632);
         GL20.glShaderSource(fs, fsh);
         GL20.glCompileShader(fs);
         if (GL20.glGetShaderi(fs, 35713) == 0) {
            String log = GL20.glGetShaderInfoLog(fs);
            GL20.glDeleteShader(vs);
            GL20.glDeleteShader(fs);
            throw new RuntimeException("Fragment shader compile error: " + log);
         } else {
            int prog = GL20.glCreateProgram();
            GL20.glAttachShader(prog, vs);
            GL20.glAttachShader(prog, fs);
            GL20.glBindAttribLocation(prog, 0, "Position");
            GL20.glBindAttribLocation(prog, 1, "UV");
            GL20.glLinkProgram(prog);
            if (GL20.glGetProgrami(prog, 35714) == 0) {
               String log = GL20.glGetProgramInfoLog(prog);
               GL20.glDeleteProgram(prog);
               GL20.glDeleteShader(vs);
               GL20.glDeleteShader(fs);
               throw new RuntimeException("Shader link error: " + log);
            } else {
               GL20.glDeleteShader(vs);
               GL20.glDeleteShader(fs);
               return prog;
            }
         }
      }
   }

   private static void createQuad() {
      float[] vertices = new float[]{
         -1.0F,
         -1.0F,
         0.0F,
         0.0F,
         0.0F,
         1.0F,
         -1.0F,
         0.0F,
         1.0F,
         0.0F,
         1.0F,
         1.0F,
         0.0F,
         1.0F,
         1.0F,
         -1.0F,
         -1.0F,
         0.0F,
         0.0F,
         0.0F,
         1.0F,
         1.0F,
         0.0F,
         1.0F,
         1.0F,
         -1.0F,
         1.0F,
         0.0F,
         0.0F,
         1.0F
      };
      quadVao = GL30.glGenVertexArrays();
      GL30.glBindVertexArray(quadVao);
      quadVbo = GL15.glGenBuffers();
      GL15.glBindBuffer(34962, quadVbo);
      FloatBuffer buf = BufferUtils.createFloatBuffer(vertices.length);
      buf.put(vertices).flip();
      GL15.glBufferData(34962, buf, 35044);
      GL20.glEnableVertexAttribArray(0);
      GL20.glVertexAttribPointer(0, 3, 5126, false, 20, 0L);
      GL20.glEnableVertexAttribArray(1);
      GL20.glVertexAttribPointer(1, 2, 5126, false, 20, 12L);
      GL30.glBindVertexArray(0);
      GL15.glBindBuffer(34962, 0);
   }

   private static void ensureFramebuffers(int width, int height) {
      if (width != lastWidth || height != lastHeight || fboA == 0) {
         if (fboA != 0) {
            GL30.glDeleteFramebuffers(fboA);
            GL11.glDeleteTextures(texA);
         }

         if (fboB != 0) {
            GL30.glDeleteFramebuffers(fboB);
            GL11.glDeleteTextures(texB);
         }

         lastWidth = width;
         lastHeight = height;
         fboA = GL30.glGenFramebuffers();
         texA = GL11.glGenTextures();
         setupFramebuffer(fboA, texA, width, height);
         fboB = GL30.glGenFramebuffers();
         texB = GL11.glGenTextures();
         setupFramebuffer(fboB, texB, width, height);
         GL30.glBindFramebuffer(36160, 0);
      }
   }

   private static void setupFramebuffer(int fbo, int tex, int width, int height) {
      GL11.glBindTexture(3553, tex);
      GL11.glTexImage2D(3553, 0, 32856, width, height, 0, 6408, 5121, (ByteBuffer)null);
      GL11.glTexParameteri(3553, 10241, 9729);
      GL11.glTexParameteri(3553, 10240, 9729);
      GL11.glTexParameteri(3553, 10242, 33071);
      GL11.glTexParameteri(3553, 10243, 33071);
      GL30.glBindFramebuffer(36160, fbo);
      GL30.glFramebufferTexture2D(36160, 36064, 3553, tex, 0);
   }

   public static void captureAndBlur() {
      if (PerformanceSettings.useShaders()) {
         if (!initialized && !shadersFailed) {
            init();
         }

         if (isReady()) {
            MinecraftClient mc = MinecraftClient.getInstance();
            long currentFrame = mc.world != null ? mc.world.getTime() : System.nanoTime() / 16666666L;
            if (currentFrame != lastCaptureFrame) {
               lastCaptureFrame = currentFrame;
               int fbWidth = mc.getWindow().getFramebufferWidth();
               int fbHeight = mc.getWindow().getFramebufferHeight();
               ensureFramebuffers(fbWidth, fbHeight);
               int mainFbo = GL30.glGetInteger(36006);
               GL30.glBindFramebuffer(36008, mainFbo);
               GL30.glBindFramebuffer(36009, fboA);
               GL30.glBlitFramebuffer(0, 0, fbWidth, fbHeight, 0, 0, fbWidth, fbHeight, 16384, 9728);
               int blurIter = PerformanceSettings.getBlurIterations();
               float blurSpread = PerformanceSettings.getBlurSpread();
               if (blurIter <= 0) {
                  GL30.glBindFramebuffer(36160, mainFbo);
                  GL11.glViewport(0, 0, fbWidth, fbHeight);
               } else {
                  int prevProgram = GL11.glGetInteger(35725);
                  int prevVao = GL11.glGetInteger(34229);
                  boolean prevBlend = GL11.glIsEnabled(3042);
                  boolean prevDepth = GL11.glIsEnabled(2929);
                  GL11.glDisable(2929);
                  GL11.glDisable(3042);
                  GL20.glUseProgram(blurProgram);
                  GL30.glBindVertexArray(quadVao);
                  GL20.glUniform1i(uBlurSampler, 0);

                  for (int i = 0; i < blurIter; i++) {
                     float spread = blurSpread * (float)(i + 1);
                     GL30.glBindFramebuffer(36160, fboB);
                     GL11.glViewport(0, 0, fbWidth, fbHeight);
                     GL13.glActiveTexture(33984);
                     GL11.glBindTexture(3553, texA);
                     GL20.glUniform2f(uBlurDirection, spread / (float)fbWidth, 0.0F);
                     GL11.glDrawArrays(4, 0, 6);
                     GL30.glBindFramebuffer(36160, fboA);
                     GL11.glViewport(0, 0, fbWidth, fbHeight);
                     GL11.glBindTexture(3553, texB);
                     GL20.glUniform2f(uBlurDirection, 0.0F, spread / (float)fbHeight);
                     GL11.glDrawArrays(4, 0, 6);
                  }

                  GL30.glBindVertexArray(prevVao);
                  GL20.glUseProgram(prevProgram);
                  if (prevBlend) {
                     GL11.glEnable(3042);
                  }

                  if (prevDepth) {
                     GL11.glEnable(2929);
                  }

                  GL30.glBindFramebuffer(36160, mainFbo);
                  GL11.glViewport(0, 0, fbWidth, fbHeight);
               }
            }
         }
      }
   }

   public static void drawGlassPanel(DrawContext context, float x, float y, float w, float h, float radius, float hoverAmount) {
      drawGlassPanel(context, x, y, w, h, radius, hoverAmount, 0.0F, 0.3F, 0.6F, 1.0F);
   }

   public static void drawGlassPanel(
      DrawContext context, float x, float y, float w, float h, float radius, float hoverAmount, float accentMix, float accentR, float accentG, float accentB
   ) {
      if (!isReady()) {
         drawFallbackPanel(context, (int)x, (int)y, (int)w, (int)h, accentMix > 0.0F);
      } else {
         MinecraftClient mc = MinecraftClient.getInstance();
         int fbWidth = mc.getWindow().getFramebufferWidth();
         int fbHeight = mc.getWindow().getFramebufferHeight();
         float scale = (float)mc.getWindow().getScaleFactor();
         float fbX = x * scale;
         float fbY = (float)fbHeight - (y + h) * scale;
         float fbW = w * scale;
         float fbH = h * scale;
         float fbRadius = radius * scale;
         int prevProgram = GL11.glGetInteger(35725);
         int prevVao = GL11.glGetInteger(34229);
         int prevTex = GL11.glGetInteger(32873);
         boolean prevBlend = GL11.glIsEnabled(3042);
         boolean prevDepth = GL11.glIsEnabled(2929);
         GL11.glEnable(3042);
         GL11.glDisable(2929);
         RenderSystem.defaultBlendFunc();
         GL20.glUseProgram(glassProgram);
         GL30.glBindVertexArray(quadVao);
         GL13.glActiveTexture(33984);
         GL11.glBindTexture(3553, texA);
         GL20.glUniform1i(uGlassSampler, 0);
         GL20.glUniform2f(uGlassScreenSize, (float)fbWidth, (float)fbHeight);
         GL20.glUniform2f(uGlassPanelPos, fbX, fbY);
         GL20.glUniform2f(uGlassPanelSize, fbW, fbH);
         GL20.glUniform1f(uGlassRadius, fbRadius);
         GL20.glUniform1f(uGlassHover, hoverAmount);
         GL20.glUniform3f(uGlassAccentColor, accentR, accentG, accentB);
         GL20.glUniform1f(uGlassAccentMix, accentMix);
         GL11.glDrawArrays(4, 0, 6);
         GL30.glBindVertexArray(prevVao);
         GL20.glUseProgram(prevProgram);
         GL11.glBindTexture(3553, prevTex);
         if (!prevBlend) {
            GL11.glDisable(3042);
         }

         if (prevDepth) {
            GL11.glEnable(2929);
         }
      }
   }

   public static void drawFallbackPanel(DrawContext context, int x, int y, int w, int h, boolean active) {
      int bg = active ? -1441124786 : -1441129938;
      context.fill(x, y, x + w, y + h, bg);
      context.fill(x, y, x + w, y + 1, 1442840575);
      context.fill(x, y, x + 1, y + h, 872415231);
      context.fill(x + w - 1, y, x + w, y + h, 587202559);
      context.fill(x, y + h - 1, x + w, y + h, 301989887);
   }

   public static void cleanup() {
      if (blurProgram != 0) {
         GL20.glDeleteProgram(blurProgram);
      }

      if (glassProgram != 0) {
         GL20.glDeleteProgram(glassProgram);
      }

      if (fboA != 0) {
         GL30.glDeleteFramebuffers(fboA);
         GL11.glDeleteTextures(texA);
      }

      if (fboB != 0) {
         GL30.glDeleteFramebuffers(fboB);
         GL11.glDeleteTextures(texB);
      }

      if (quadVao != 0) {
         GL30.glDeleteVertexArrays(quadVao);
      }

      if (quadVbo != 0) {
         GL15.glDeleteBuffers(quadVbo);
      }

      blurProgram = 0;
      glassProgram = 0;
      fboA = 0;
      fboB = 0;
      texA = 0;
      texB = 0;
      quadVao = 0;
      quadVbo = 0;
      initialized = false;
      shadersFailed = false;
   }

   private LiquidGlassRenderer() {
   }
}
