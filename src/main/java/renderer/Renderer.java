package renderer;

import camera.Camera;
import com.bulletphysics.dynamics.DynamicsWorld;
import engine.World;
import engine.lifecycle.LifeCycle;
import engine.model.Entity;
import engine.model.Model;
import engine.model.OBJLoader;
import engine.model.VertexBuffer;
import octree.Octree;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.GLU;
import org.lwjgl.util.vector.Vector3f;
import renderer.command.Command;
import renderer.command.Result;
import renderer.drawstrategy.GBuffer;
import renderer.light.DirectionalLight;
import renderer.light.LightFactory;
import renderer.material.MaterialFactory;
import scene.EnvironmentProbe;
import scene.EnvironmentProbeFactory;
import shader.AbstractProgram;
import shader.Program;
import shader.ProgramFactory;
import shader.StorageBuffer;
import texture.CubeMap;
import texture.TextureFactory;

import java.util.List;
import java.util.concurrent.SynchronousQueue;

public interface Renderer extends LifeCycle {
    boolean CHECKERRORS = false;

    static void exitOnGLError(String errorMessage) {
        if (!CHECKERRORS) {
            return;
        }

        int errorValue = GL11.glGetError();

        if (errorValue != GL11.GL_NO_ERROR) {
            String errorString = GLU.gluErrorString(errorValue);
            System.err.println("ERROR - " + errorMessage + ": " + errorString);

            if (Display.isCreated()) Display.destroy();
            System.exit(-1);
        }
    }

    boolean isInitialized();

    void destroy();

    void draw(World world);

    void update(World world, float seconds);

    float getElapsedSeconds();

    Program getLastUsedProgram();

    void setLastUsedProgram(Program firstPassProgram);

    CubeMap getEnvironmentMap();

    MaterialFactory getMaterialFactory();

    TextureFactory getTextureFactory();

    OBJLoader getOBJLoader();

    Model getSphere();

    void batchLine(Vector3f from, Vector3f to);

    void drawLines(Program firstPassProgram);

    <OBJECT_TYPE, RESULT_TYPE extends Result<OBJECT_TYPE>> SynchronousQueue<RESULT_TYPE> addCommand(Command<RESULT_TYPE> command);

    ProgramFactory getProgramFactory();

    LightFactory getLightFactory();

    EnvironmentProbeFactory getEnvironmentProbeFactory();

    void init(Octree octree);

    int getMaxTextureUnits();

    void blur2DTexture(int sourceTextureId, int mipmap, int width, int height, int internalFormat, boolean upscaleToFullscreen, int blurTimes);

    void blur2DTextureBilateral(int sourceTextureId, int edgeTexture, int width, int height, int internalFormat, boolean upscaleToFullscreen, int blurTimes);

    void addRenderProbeCommand(EnvironmentProbe probe);

    void addRenderProbeCommand(EnvironmentProbe probe, boolean urgent);

    float getCurrentFPS();

    double getDeltaInS();

    int getFrameCount();

    StorageBuffer getStorageBuffer();

    String getCurrentState();

    void endFrame();

    GBuffer getGBuffer();

    void executeRenderProbeCommands();

    void drawToQuad(int colorReflectivenessMap);

    VertexBuffer getFullscreenBuffer();

    default void batchVector(Vector3f vector) {
        batchVector(vector, 0.1f);
    }
    default void batchVector(Vector3f vector, float charWidth) {
        batchString(String.format("%.2f", vector.getX()), charWidth, charWidth*0.2f, 0, 2f*charWidth);
        batchString(String.format("%.2f", vector.getY()), charWidth, charWidth*0.2f, 0, charWidth);
        batchString(String.format("%.2f", vector.getZ()), charWidth, charWidth*0.2f, 0, 0.f);
    }
    default void batchString(String text) {
        batchString(text, 0.1f);
    }
    default void batchString(String text, float charWidth) {
        batchString(text, charWidth, charWidth*0.2f);
    }

    default void batchString(String text, float charWidthIn, float gapIn) {
        batchString(text, charWidthIn, gapIn, 0);
    }

    default void batchString(String text, float charWidthIn, float gapIn, int x) {
        batchString(text, charWidthIn, gapIn, x, 0);
    }

    default void batchString(String text, float charWidthIn, float gapIn, float x, float y) {
        float charMaxWidth = charWidthIn + Math.round(charWidthIn*0.25f);
        float gap = gapIn;
        if(gap > charMaxWidth/2f) { gap = charMaxWidth/2f; }
        float charWidth = charWidthIn - gap;
        String s = text;
        for (char c : s.toUpperCase().toCharArray()) {
            if (c == 'A') {
                batchLine(new Vector3f(x, y + charWidth, 0f), new Vector3f(x + charWidth, y + charWidth, 0));
                batchLine(new Vector3f(x, y + charWidth/2, 0f), new Vector3f(x + charWidth, y + charWidth/2, 0));
                batchLine(new Vector3f(x + charWidth, y + charWidth, 0), new Vector3f(x + charWidth, y, 0));
                batchLine(new Vector3f(x, y + charWidth, 0), new Vector3f(x, y, 0));
                x += charMaxWidth;
            } else if (c == 'B') {
                batchLine(new Vector3f(x, y + charWidth, 0f), new Vector3f(x + 0.9f*charWidth, y + charWidth, 0));
                batchLine(new Vector3f(x, y + charWidth / 2, 0f), new Vector3f(x + charWidth, y + charWidth / 2, 0));
                batchLine(new Vector3f(x, y, 0f), new Vector3f(x + 0.9f*charWidth, y, 0));
                batchLine(new Vector3f(x, y + charWidth, 0), new Vector3f(x, y, 0));
                batchLine(new Vector3f(x + 0.9f*charWidth, y + charWidth, 0), new Vector3f(x + charWidth, y + charWidth/2, 0));
                batchLine(new Vector3f(x + charWidth, y + charWidth/2, 0), new Vector3f(x + 0.9f*charWidth, y, 0));
                x += charMaxWidth;
            } else if (c == 'C') {
                batchLine(new Vector3f(x, y + charWidth, 0f), new Vector3f(x + charWidth, y + charWidth, 0));
                batchLine(new Vector3f(x, y, 0f), new Vector3f(x + charWidth, y, 0));
                batchLine(new Vector3f(x, y + charWidth, 0), new Vector3f(x, y, 0));
                x += charMaxWidth;
            } else if (c == 'D') {
                batchLine(new Vector3f(x, y + charWidth, 0f), new Vector3f(x + charWidth, y + 0.9f*charWidth, 0));
                batchLine(new Vector3f(x, y, 0f), new Vector3f(x + charWidth, y + 0.1f*charWidth, 0));
                batchLine(new Vector3f(x, y + charWidth, 0), new Vector3f(x, y, 0));
                batchLine(new Vector3f(x + charWidth, y + 0.9f*charWidth, 0), new Vector3f(x + charWidth, y + 0.1f*charWidth, 0));
                x += charMaxWidth;
            } else if (c == 'E') {
                batchLine(new Vector3f(x, y + charWidth, 0f), new Vector3f(x + charWidth, y + charWidth, 0));
                batchLine(new Vector3f(x, y + charWidth / 2, 0f), new Vector3f(x + charWidth, y + charWidth / 2, 0));
                batchLine(new Vector3f(x, y, 0f), new Vector3f(x + charWidth, y, 0));
                batchLine(new Vector3f(x, y + charWidth, 0), new Vector3f(x, y, 0));
                x += charMaxWidth;
            } else if (c == 'F') {
                batchLine(new Vector3f(x, y + charWidth, 0f), new Vector3f(x + charWidth, y + charWidth, 0));
                batchLine(new Vector3f(x, y + charWidth / 2, 0f), new Vector3f(x + charWidth, y + charWidth / 2, 0));
                batchLine(new Vector3f(x, y + charWidth, 0), new Vector3f(x, y, 0));
                x += charMaxWidth;
            } else if (c == 'G') {
                batchLine(new Vector3f(x, y + charWidth, 0f), new Vector3f(x + charWidth, y + charWidth, 0));
                batchLine(new Vector3f(x + charWidth/2, y + charWidth / 2, 0f), new Vector3f(x + charWidth, y + charWidth / 2, 0));
                batchLine(new Vector3f(x, y, 0f), new Vector3f(x + charWidth, y, 0));
                batchLine(new Vector3f(x, y + charWidth, 0), new Vector3f(x, y, 0));
                batchLine(new Vector3f(x + charWidth, y + charWidth/2, 0), new Vector3f(x + charWidth, y, 0));
                x += charMaxWidth;
            } else if (c == 'H') {
                batchLine(new Vector3f(x, y + charWidth / 2, 0f), new Vector3f(x + charWidth, y + charWidth / 2, 0));
                batchLine(new Vector3f(x + charWidth, y + charWidth, 0), new Vector3f(x + charWidth, y, 0));
                batchLine(new Vector3f(x, y + charWidth, 0), new Vector3f(x, y, 0));
                x += charMaxWidth;
            } else if (c == 'I') {
                batchLine(new Vector3f(x + charWidth/2, y + charWidth, 0), new Vector3f(x + charWidth/2, y, 0));
                x += charMaxWidth;
            } else if (c == 'J') {
                batchLine(new Vector3f(x, y, 0f), new Vector3f(x + charWidth/2, y, 0));
                batchLine(new Vector3f(x + charWidth/2, y + charWidth, 0), new Vector3f(x + charWidth/2, y, 0));
                batchLine(new Vector3f(x, y, 0), new Vector3f(x, y + charWidth/2, 0));
                x += charMaxWidth;
            } else if (c == 'K') {
                batchLine(new Vector3f(x + charWidth, y + charWidth, 0f), new Vector3f(x, y + charWidth / 2, 0));
                batchLine(new Vector3f(x, y + charWidth / 2, 0f), new Vector3f(x + charWidth, y, 0));
                batchLine(new Vector3f(x, y + charWidth, 0), new Vector3f(x, y, 0));
                x += charMaxWidth;
            } else if (c == 'L') {
                batchLine(new Vector3f(x, y, 0f), new Vector3f(x + charWidth, y, 0));
                batchLine(new Vector3f(x, y + charWidth, 0), new Vector3f(x, y, 0));
                x += charMaxWidth;
            } else if (c == 'M') {
                batchLine(new Vector3f(x + charWidth, y + charWidth, 0), new Vector3f(x + charWidth / 2, y  + charWidth / 2, 0));
                batchLine(new Vector3f(x, y + charWidth, 0), new Vector3f(x + charWidth / 2, y  + charWidth / 2, 0));
                batchLine(new Vector3f(x + charWidth, y + charWidth, 0), new Vector3f(x + charWidth, y, 0));
                batchLine(new Vector3f(x, y + charWidth, 0), new Vector3f(x, y, 0));
                x += charMaxWidth;
            } else if (c == 'N') {
                batchLine(new Vector3f(x, y + charWidth, 0), new Vector3f(x + charWidth, y, 0));
                batchLine(new Vector3f(x + charWidth, y + charWidth, 0), new Vector3f(x + charWidth, y, 0));
                batchLine(new Vector3f(x, y + charWidth, 0), new Vector3f(x, y, 0));
                x += charMaxWidth;
            } else if (c == 'O') {
                batchLine(new Vector3f(x, y + charWidth, 0f), new Vector3f(x + charWidth, y + charWidth, 0));
                batchLine(new Vector3f(x, y, 0f), new Vector3f(x + charWidth, y, 0));
                batchLine(new Vector3f(x + charWidth, y + charWidth, 0), new Vector3f(x + charWidth, y, 0));
                batchLine(new Vector3f(x, y + charWidth, 0), new Vector3f(x, y, 0));
                x += charMaxWidth;
            } else if (c == 'P') {
                batchLine(new Vector3f(x, y + charWidth, 0f), new Vector3f(x + charWidth, y + charWidth, 0));
                batchLine(new Vector3f(x, y + charWidth / 2, 0f), new Vector3f(x + charWidth, y + charWidth / 2, 0));
                batchLine(new Vector3f(x, y + charWidth, 0), new Vector3f(x, y, 0));
                batchLine(new Vector3f(x + charWidth, y + charWidth, 0), new Vector3f(x + charWidth, y + charWidth / 2, 0));
                x += charMaxWidth;
            } else if (c == 'Q') {
                batchLine(new Vector3f(x, y + charWidth, 0f), new Vector3f(x + charWidth, y + charWidth, 0));
                batchLine(new Vector3f(x, y + charWidth, 0f), new Vector3f(x + charWidth, y, 0));
                batchLine(new Vector3f(x, y, 0f), new Vector3f(x + charWidth, y, 0));
                batchLine(new Vector3f(x + charWidth, y + charWidth, 0), new Vector3f(x + charWidth, y, 0));
                batchLine(new Vector3f(x, y + charWidth, 0), new Vector3f(x, y, 0));
                x += charMaxWidth;
            } else if (c == 'R') {
                batchLine(new Vector3f(x, y + charWidth, 0f), new Vector3f(x + charWidth, y + charWidth, 0));
                batchLine(new Vector3f(x, y + charWidth / 2, 0f), new Vector3f(x + charWidth, y + charWidth / 2, 0));
                batchLine(new Vector3f(x, y + charWidth / 2, 0f), new Vector3f(x + charWidth, y, 0));
                batchLine(new Vector3f(x, y + charWidth, 0), new Vector3f(x, y, 0));
                batchLine(new Vector3f(x + charWidth, y + charWidth, 0), new Vector3f(x + charWidth, y + charWidth / 2, 0));
                x += charMaxWidth;
            } else if (c == 'S') {
                batchLine(new Vector3f(x, y + charWidth, 0f), new Vector3f(x + charWidth, y + charWidth, 0));
                batchLine(new Vector3f(x + 0.1f*charWidth, y + charWidth / 2, 0f), new Vector3f(x + 0.9f*charWidth, y + charWidth / 2, 0));
                batchLine(new Vector3f(x, y, 0f), new Vector3f(x + charWidth, y, 0));
                batchLine(new Vector3f(x, y + charWidth, 0), new Vector3f(x + 0.1f*charWidth, y + charWidth/2, 0));
                batchLine(new Vector3f(x + 0.9f*charWidth, y + charWidth/2, 0), new Vector3f(x + charWidth, y, 0));
                x += charMaxWidth;
            } else if (c == 'T') {
                batchLine(new Vector3f(x, y + charWidth, 0f), new Vector3f(x + charWidth, y + charWidth, 0));
                batchLine(new Vector3f(x + charWidth/2, y + charWidth, 0), new Vector3f(x + charWidth/2, y, 0));
                x += charMaxWidth;
            } else if (c == 'U') {
                batchLine(new Vector3f(x, y, 0f), new Vector3f(x + charWidth, y, 0));
                batchLine(new Vector3f(x + charWidth, y + charWidth, 0), new Vector3f(x + charWidth, y, 0));
                batchLine(new Vector3f(x, y + charWidth, 0), new Vector3f(x, y, 0));
                x += charMaxWidth;
            } else if (c == 'V') {
                batchLine(new Vector3f(x + charWidth, y + charWidth, 0), new Vector3f(x + charWidth/2, y, 0));
                batchLine(new Vector3f(x, y + charWidth, 0), new Vector3f(x + charWidth/2, y, 0));
                x += charMaxWidth;
            } else if (c == 'W') {
                batchLine(new Vector3f(x, y + charWidth, 0), new Vector3f(x + charWidth/4, y, 0));
                batchLine(new Vector3f(x + charWidth/4, y, 0), new Vector3f(x + charWidth/2, y + charWidth, 0));
                batchLine(new Vector3f(x + charWidth/2, y + charWidth, 0), new Vector3f(x + 3f*charWidth/4f, y, 0));
                batchLine(new Vector3f(x + 3f*charWidth/4f, y, 0), new Vector3f(x + charWidth, y + charWidth, 0));
                x += charMaxWidth;
            } else if (c == 'X') {
                batchLine(new Vector3f(x + charWidth, y + charWidth, 0), new Vector3f(x, y, 0));
                batchLine(new Vector3f(x, y + charWidth, 0), new Vector3f(x + charWidth, y, 0));
                x += charMaxWidth;
            } else if (c == 'Y') {
                batchLine(new Vector3f(x, y + charWidth, 0), new Vector3f(x + charWidth/2, y + charWidth/2, 0));
                batchLine(new Vector3f(x + charWidth/2, y + charWidth/2, 0), new Vector3f(x + charWidth, y + charWidth, 0));
                batchLine(new Vector3f(x + charWidth/2, y + charWidth/2, 0), new Vector3f(x + charWidth/2, y, 0));
                x += charMaxWidth;
            } else if (c == 'Z') {
                batchLine(new Vector3f(x, y + charWidth, 0f), new Vector3f(x + charWidth, y + charWidth, 0));
                batchLine(new Vector3f(x + charWidth, y + charWidth, 0f), new Vector3f(x, y, 0));
                batchLine(new Vector3f(x, y, 0f), new Vector3f(x + charWidth, y, 0));
                x += charMaxWidth;
            } else if (c == '0') {
                batchLine(new Vector3f(x, y + charWidth, 0f), new Vector3f(x + charWidth, y + charWidth, 0));
                batchLine(new Vector3f(x, y, 0f), new Vector3f(x + charWidth, y, 0));
                batchLine(new Vector3f(x + charWidth, y + charWidth, 0), new Vector3f(x + charWidth, y, 0));
                batchLine(new Vector3f(x, y + charWidth, 0), new Vector3f(x, y, 0));
                x += charMaxWidth;
            } else if (c == '1') {
                batchLine(new Vector3f(x, y + charWidth/2, 0f), new Vector3f(x + charWidth/2, y + charWidth, 0));
                batchLine(new Vector3f(x + charWidth/2, y + charWidth, 0), new Vector3f(x + charWidth/2, y, 0));
                x += charMaxWidth;
            } else if (c == '2') {
                batchLine(new Vector3f(x, y + charWidth, 0f), new Vector3f(x + charWidth, y + charWidth, 0));
                batchLine(new Vector3f(x, y + charWidth / 2, 0f), new Vector3f(x + charWidth, y + charWidth / 2, 0));
                batchLine(new Vector3f(x, y, 0f), new Vector3f(x + charWidth, y, 0));
                batchLine(new Vector3f(x, y + charWidth/2, 0), new Vector3f(x, y, 0));
                batchLine(new Vector3f(x + charWidth, y + charWidth, 0), new Vector3f(x + charWidth, y + charWidth/2, 0));
                x += charMaxWidth;
            } else if (c == '3') {
                batchLine(new Vector3f(x, y + charWidth, 0f), new Vector3f(x + charWidth, y + charWidth, 0));
                batchLine(new Vector3f(x, y + charWidth / 2, 0f), new Vector3f(x + charWidth, y + charWidth / 2, 0));
                batchLine(new Vector3f(x, y, 0f), new Vector3f(x + charWidth, y, 0));
                batchLine(new Vector3f(x + charWidth, y + charWidth, 0), new Vector3f(x + charWidth, y, 0));
                x += charMaxWidth;
            } else if (c == '4') {
                batchLine(new Vector3f(x, y + charWidth / 2, 0f), new Vector3f(x + charWidth, y + charWidth / 2, 0));
                batchLine(new Vector3f(x, y + charWidth, 0), new Vector3f(x, y + charWidth/2, 0));
                batchLine(new Vector3f(x + charWidth, y + charWidth, 0), new Vector3f(x + charWidth, y, 0));
                x += charMaxWidth;
            } else if (c == '5') {
                batchLine(new Vector3f(x, y + charWidth, 0f), new Vector3f(x + charWidth, y + charWidth, 0));
                batchLine(new Vector3f(x, y + charWidth / 2, 0f), new Vector3f(x + charWidth, y + charWidth / 2, 0));
                batchLine(new Vector3f(x, y, 0f), new Vector3f(x + charWidth, y, 0));
                batchLine(new Vector3f(x + charWidth, y + charWidth/2, 0), new Vector3f(x + charWidth, y, 0));
                batchLine(new Vector3f(x, y + charWidth, 0), new Vector3f(x, y + charWidth/2, 0));
                x += charMaxWidth;
            } else if (c == '6') {
                batchLine(new Vector3f(x, y + charWidth, 0f), new Vector3f(x + charWidth, y + charWidth, 0));
                batchLine(new Vector3f(x, y + charWidth / 2, 0f), new Vector3f(x + charWidth, y + charWidth / 2, 0));
                batchLine(new Vector3f(x, y, 0f), new Vector3f(x + charWidth, y, 0));
                batchLine(new Vector3f(x, y + charWidth, 0), new Vector3f(x, y, 0));
                batchLine(new Vector3f(x + charWidth, y + charWidth/2, 0), new Vector3f(x + charWidth, y, 0));
                x += charMaxWidth;
            } else if (c == '7') {
                batchLine(new Vector3f(x, y + charWidth, 0f), new Vector3f(x + charWidth, y + charWidth, 0));
                batchLine(new Vector3f(x + charWidth, y + charWidth, 0), new Vector3f(x + charWidth, y, 0));
                x += charMaxWidth;
            } else if (c == '8') {
                batchLine(new Vector3f(x, y + charWidth, 0f), new Vector3f(x + charWidth, y + charWidth, 0));
                batchLine(new Vector3f(x, y + charWidth / 2, 0f), new Vector3f(x + charWidth, y + charWidth / 2, 0));
                batchLine(new Vector3f(x, y, 0f), new Vector3f(x + charWidth, y, 0));
                batchLine(new Vector3f(x + charWidth, y + charWidth, 0), new Vector3f(x + charWidth, y, 0));
                batchLine(new Vector3f(x, y + charWidth, 0), new Vector3f(x, y, 0));
                x += charMaxWidth;
            } else if (c == '9') {
                batchLine(new Vector3f(x, y + charWidth, 0f), new Vector3f(x + charWidth, y + charWidth, 0));
                batchLine(new Vector3f(x, y + charWidth / 2, 0f), new Vector3f(x + charWidth, y + charWidth / 2, 0));
                batchLine(new Vector3f(x, y, 0f), new Vector3f(x + charWidth, y, 0));
                batchLine(new Vector3f(x, y + charWidth, 0), new Vector3f(x, y + charWidth/2, 0));
                batchLine(new Vector3f(x + charWidth, y + charWidth, 0), new Vector3f(x + charWidth, y, 0));
                x += charMaxWidth;
            } else if (c == '+') {
                batchLine(new Vector3f(x + charWidth/2, y + 3f*charWidth/4f, 0), new Vector3f(x + charWidth/2, y + charWidth/4f, 0));
                batchLine(new Vector3f(x + charWidth/4f, y + charWidth/2, 0f), new Vector3f(x + 3f*charWidth/4f, y + charWidth/2, 0));
                x += charMaxWidth;
            } else if (c == '-') {
                batchLine(new Vector3f(x + charWidth/4f, y + charWidth/2, 0f), new Vector3f(x + 3f*charWidth/4f, y + charWidth/2, 0));
                x += charMaxWidth;
            } else if (c == '.') {
                batchLine(new Vector3f(x + charWidth/2, y + charWidth/16f, 0), new Vector3f(x + charWidth/2, y, 0));
                x += charMaxWidth;
            } else if (c == ',') {
                batchLine(new Vector3f(x + charWidth/2, y + charWidth/4f, 0), new Vector3f(x + charWidth/2, y, 0));
                x += charMaxWidth;
            }
        }
    }
}
