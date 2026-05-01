package com.pathdlc.digger.render;

import com.pathdlc.digger.gui.Module;
import com.pathdlc.digger.gui.ModuleManager;
import com.pathdlc.digger.gui.ModuleSetting;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MotionBlurRenderer {
   private static final Logger LOGGER = LoggerFactory.getLogger("PathDLC-MotionBlur");
   private static int program;
   private static int prevFbo;
   private static int prevTex;
   private static int captureFbo;
   private static int captureTex;
   private static int quadVao;
   private static int quadVbo;
   private static int lastWidth;
   private static int lastHeight;
   private static boolean initialized;
   private static boolean failed;
   private static boolean hasPrevFrame;
   private static int uCurrentFrame;
   private static int uPrevFrame;
   private static int uStrength;
   private static final String VSH = "#version 150\nin vec2 Position;\nin vec2 UV;\nout vec2 texCoord;\nvoid main() {\n    texCoord = UV;\n    gl_Position = vec4(Position, 0.0, 1.0);\n}\n";
   private static final String FSH = "#version 150\nuniform sampler2D CurrentFrame;\nuniform sampler2D PrevFrame;\nuniform float Strength;\nin vec2 texCoord;\nout vec4 fragColor;\nvoid main() {\n    vec4 current = texture(CurrentFrame, texCoord);\n    vec4 prev = texture(PrevFrame, texCoord);\n    fragColor = mix(current, prev, Strength);\n}\n";

   public static void onFrameEnd(int screenWidth, int screenHeight) {
      if (!ModuleManager.isEnabled("MotionBlur")) {
         hasPrevFrame = false;
      } else {
         if (!initialized && !failed) {
            try {
               init();
            } catch (Exception var7) {
               LOGGER.error("MotionBlur init failed: " + var7.getMessage(), var7);
               failed = true;
               return;
            }
         }

         if (!failed) {
            if (screenWidth != lastWidth || screenHeight != lastHeight) {
               resizeFbo(screenWidth, screenHeight);
               lastWidth = screenWidth;
               lastHeight = screenHeight;
               hasPrevFrame = false;
            }

            float strength = 0.5F;
            Module mod = ModuleManager.get("MotionBlur");
            if (mod != null) {
               ModuleSetting s = mod.getSetting("Strength");
               if (s != null) {
                  strength = s.getFloat();
               }
            }

            int currentFbo = GL11.glGetInteger(36006);
            GL30.glBindFramebuffer(36008, currentFbo);
            GL30.glBindFramebuffer(36009, captureFbo);
            GL30.glBlitFramebuffer(0, 0, screenWidth, screenHeight, 0, 0, screenWidth, screenHeight, 16384, 9728);
            GL30.glBindFramebuffer(36160, currentFbo);
            if (!hasPrevFrame) {
               GL30.glBindFramebuffer(36008, currentFbo);
               GL30.glBindFramebuffer(36009, prevFbo);
               GL30.glBlitFramebuffer(0, 0, screenWidth, screenHeight, 0, 0, screenWidth, screenHeight, 16384, 9728);
               GL30.glBindFramebuffer(36160, currentFbo);
               hasPrevFrame = true;
            } else {
               boolean blendWas = GL11.glIsEnabled(3042);
               boolean depthWas = GL11.glIsEnabled(2929);
               GL11.glDisable(2929);
               GL11.glEnable(3042);
               GL11.glBlendFunc(770, 771);
               GL20.glUseProgram(program);
               GL13.glActiveTexture(33984);
               GL11.glBindTexture(3553, captureTex);
               GL20.glUniform1i(uCurrentFrame, 0);
               GL13.glActiveTexture(33985);
               GL11.glBindTexture(3553, prevTex);
               GL20.glUniform1i(uPrevFrame, 1);
               GL20.glUniform1f(uStrength, strength);
               GL30.glBindFramebuffer(36160, currentFbo);
               GL30.glBindVertexArray(quadVao);
               GL11.glDrawArrays(5, 0, 4);
               GL30.glBindVertexArray(0);
               GL20.glUseProgram(0);
               GL13.glActiveTexture(33984);
               GL30.glBindFramebuffer(36008, currentFbo);
               GL30.glBindFramebuffer(36009, prevFbo);
               GL30.glBlitFramebuffer(0, 0, screenWidth, screenHeight, 0, 0, screenWidth, screenHeight, 16384, 9728);
               GL30.glBindFramebuffer(36160, currentFbo);
               if (!blendWas) {
                  GL11.glDisable(3042);
               }

               if (depthWas) {
                  GL11.glEnable(2929);
               }
            }
         }
      }
   }

   private static void init() {
      LOGGER.info("Initializing MotionBlur renderer...");
      program = createProgram(
         "#version 150\nin vec2 Position;\nin vec2 UV;\nout vec2 texCoord;\nvoid main() {\n    texCoord = UV;\n    gl_Position = vec4(Position, 0.0, 1.0);\n}\n",
         "#version 150\nuniform sampler2D CurrentFrame;\nuniform sampler2D PrevFrame;\nuniform float Strength;\nin vec2 texCoord;\nout vec4 fragColor;\nvoid main() {\n    vec4 current = texture(CurrentFrame, texCoord);\n    vec4 prev = texture(PrevFrame, texCoord);\n    fragColor = mix(current, prev, Strength);\n}\n"
      );
      uCurrentFrame = GL20.glGetUniformLocation(program, "CurrentFrame");
      uPrevFrame = GL20.glGetUniformLocation(program, "PrevFrame");
      uStrength = GL20.glGetUniformLocation(program, "Strength");
      createQuad();
      prevFbo = GL30.glGenFramebuffers();
      prevTex = GL11.glGenTextures();
      captureFbo = GL30.glGenFramebuffers();
      captureTex = GL11.glGenTextures();
      initialized = true;
      LOGGER.info("MotionBlur initialized, program={}", program);
   }

   private static void resizeFbo(int w, int h) {
      GL11.glBindTexture(3553, prevTex);
      GL11.glTexImage2D(3553, 0, 32856, w, h, 0, 6408, 5121, (ByteBuffer)null);
      GL11.glTexParameteri(3553, 10241, 9729);
      GL11.glTexParameteri(3553, 10240, 9729);
      GL30.glBindFramebuffer(36160, prevFbo);
      GL30.glFramebufferTexture2D(36160, 36064, 3553, prevTex, 0);
      GL11.glBindTexture(3553, captureTex);
      GL11.glTexImage2D(3553, 0, 32856, w, h, 0, 6408, 5121, (ByteBuffer)null);
      GL11.glTexParameteri(3553, 10241, 9729);
      GL11.glTexParameteri(3553, 10240, 9729);
      GL30.glBindFramebuffer(36160, captureFbo);
      GL30.glFramebufferTexture2D(36160, 36064, 3553, captureTex, 0);
      GL30.glBindFramebuffer(36160, 0);
   }

   private static void createQuad() {
      float[] verts = new float[]{-1.0F, -1.0F, 0.0F, 0.0F, 1.0F, -1.0F, 1.0F, 0.0F, -1.0F, 1.0F, 0.0F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F};
      FloatBuffer buf = BufferUtils.createFloatBuffer(verts.length);
      buf.put(verts).flip();
      quadVao = GL30.glGenVertexArrays();
      quadVbo = GL15.glGenBuffers();
      GL30.glBindVertexArray(quadVao);
      GL15.glBindBuffer(34962, quadVbo);
      GL15.glBufferData(34962, buf, 35044);
      GL20.glEnableVertexAttribArray(0);
      GL20.glVertexAttribPointer(0, 2, 5126, false, 16, 0L);
      GL20.glEnableVertexAttribArray(1);
      GL20.glVertexAttribPointer(1, 2, 5126, false, 16, 8L);
      GL30.glBindVertexArray(0);
   }

   private static int createProgram(String vsh, String fsh) {
      int vs = GL20.glCreateShader(35633);
      GL20.glShaderSource(vs, vsh);
      GL20.glCompileShader(vs);
      if (GL20.glGetShaderi(vs, 35713) == 0) {
         String log = GL20.glGetShaderInfoLog(vs);
         throw new RuntimeException("MotionBlur VS compile error: " + log);
      } else {
         int fs = GL20.glCreateShader(35632);
         GL20.glShaderSource(fs, fsh);
         GL20.glCompileShader(fs);
         if (GL20.glGetShaderi(fs, 35713) == 0) {
            String log = GL20.glGetShaderInfoLog(fs);
            throw new RuntimeException("MotionBlur FS compile error: " + log);
         } else {
            int prog = GL20.glCreateProgram();
            GL20.glAttachShader(prog, vs);
            GL20.glAttachShader(prog, fs);
            GL20.glBindAttribLocation(prog, 0, "Position");
            GL20.glBindAttribLocation(prog, 1, "UV");
            GL20.glLinkProgram(prog);
            if (GL20.glGetProgrami(prog, 35714) == 0) {
               String log = GL20.glGetProgramInfoLog(prog);
               throw new RuntimeException("MotionBlur link error: " + log);
            } else {
               GL20.glDeleteShader(vs);
               GL20.glDeleteShader(fs);
               return prog;
            }
         }
      }
   }

   private MotionBlurRenderer() {
   }
}
