package de.hanno.hpengine.engine.graphics.renderer;

import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.config.Config;
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DrawResult;
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.GBuffer;
import de.hanno.hpengine.engine.graphics.shader.Program;
import de.hanno.hpengine.engine.graphics.state.RenderState;
import de.hanno.hpengine.engine.graphics.state.RenderSystem;
import de.hanno.hpengine.engine.graphics.state.multithreading.TripleBuffer;
import de.hanno.hpengine.engine.lifecycle.LifeCycle;
import org.joml.Vector3f;

public interface Renderer extends LifeCycle, RenderSystem {

    default void destroy() { }

    void update(Engine engine, float seconds);

    void batchLine(Vector3f from, Vector3f to);

    default void batchTriangle(Vector3f a, Vector3f b, Vector3f c) {
        batchLine(a, b);
        batchLine(b, c);
        batchLine(c, a);
    }

    int drawLines(Program firstPassProgram);

    default void startFrame() {}

    default void endFrame() {}

    GBuffer getGBuffer();

    void drawToQuad(int texture);

    default void batchVector(Vector3f vector) {
        batchVector(vector, 0.1f);
    }

    default void batchVector(Vector3f vector, float charWidth) {
        batchString(String.format("%.2f", vector.x()), charWidth, charWidth * 0.2f, 0, 2f * charWidth);
        batchString(String.format("%.2f", vector.y()), charWidth, charWidth * 0.2f, 0, charWidth);
        batchString(String.format("%.2f", vector.z()), charWidth, charWidth * 0.2f, 0, 0.f);
    }

    default void batchString(String text) {
        batchString(text, 0.1f);
    }

    default void batchString(String text, float charWidth) {
        batchString(text, charWidth, charWidth * 0.2f);
    }

    default void batchString(String text, float charWidthIn, float gapIn) {
        batchString(text, charWidthIn, gapIn, 0);
    }

    default void batchString(String text, float charWidthIn, float gapIn, int x) {
        batchString(text, charWidthIn, gapIn, x, 0);
    }

    default void batchString(String text, float charWidthIn, float gapIn, float x, float y) {
        float charMaxWidth = charWidthIn + Math.round(charWidthIn * 0.25f);
        float gap = gapIn;
        if (gap > charMaxWidth / 2f) {
            gap = charMaxWidth / 2f;
        }
        float charWidth = charWidthIn - gap;
        String s = text;
        for (char c : s.toUpperCase().toCharArray()) {
            if (c == 'A') {
                batchLine(new Vector3f(x, y + charWidth, 0f), new Vector3f(x + charWidth, y + charWidth, 0));
                batchLine(new Vector3f(x, y + charWidth / 2, 0f), new Vector3f(x + charWidth, y + charWidth / 2, 0));
                batchLine(new Vector3f(x + charWidth, y + charWidth, 0), new Vector3f(x + charWidth, y, 0));
                batchLine(new Vector3f(x, y + charWidth, 0), new Vector3f(x, y, 0));
                x += charMaxWidth;
            } else if (c == 'B') {
                batchLine(new Vector3f(x, y + charWidth, 0f), new Vector3f(x + 0.9f * charWidth, y + charWidth, 0));
                batchLine(new Vector3f(x, y + charWidth / 2, 0f), new Vector3f(x + charWidth, y + charWidth / 2, 0));
                batchLine(new Vector3f(x, y, 0f), new Vector3f(x + 0.9f * charWidth, y, 0));
                batchLine(new Vector3f(x, y + charWidth, 0), new Vector3f(x, y, 0));
                batchLine(new Vector3f(x + 0.9f * charWidth, y + charWidth, 0), new Vector3f(x + charWidth, y + charWidth / 2, 0));
                batchLine(new Vector3f(x + charWidth, y + charWidth / 2, 0), new Vector3f(x + 0.9f * charWidth, y, 0));
                x += charMaxWidth;
            } else if (c == 'C') {
                batchLine(new Vector3f(x, y + charWidth, 0f), new Vector3f(x + charWidth, y + charWidth, 0));
                batchLine(new Vector3f(x, y, 0f), new Vector3f(x + charWidth, y, 0));
                batchLine(new Vector3f(x, y + charWidth, 0), new Vector3f(x, y, 0));
                x += charMaxWidth;
            } else if (c == 'D') {
                batchLine(new Vector3f(x, y + charWidth, 0f), new Vector3f(x + charWidth, y + 0.9f * charWidth, 0));
                batchLine(new Vector3f(x, y, 0f), new Vector3f(x + charWidth, y + 0.1f * charWidth, 0));
                batchLine(new Vector3f(x, y + charWidth, 0), new Vector3f(x, y, 0));
                batchLine(new Vector3f(x + charWidth, y + 0.9f * charWidth, 0), new Vector3f(x + charWidth, y + 0.1f * charWidth, 0));
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
                batchLine(new Vector3f(x + charWidth / 2, y + charWidth / 2, 0f), new Vector3f(x + charWidth, y + charWidth / 2, 0));
                batchLine(new Vector3f(x, y, 0f), new Vector3f(x + charWidth, y, 0));
                batchLine(new Vector3f(x, y + charWidth, 0), new Vector3f(x, y, 0));
                batchLine(new Vector3f(x + charWidth, y + charWidth / 2, 0), new Vector3f(x + charWidth, y, 0));
                x += charMaxWidth;
            } else if (c == 'H') {
                batchLine(new Vector3f(x, y + charWidth / 2, 0f), new Vector3f(x + charWidth, y + charWidth / 2, 0));
                batchLine(new Vector3f(x + charWidth, y + charWidth, 0), new Vector3f(x + charWidth, y, 0));
                batchLine(new Vector3f(x, y + charWidth, 0), new Vector3f(x, y, 0));
                x += charMaxWidth;
            } else if (c == 'I') {
                batchLine(new Vector3f(x + charWidth / 2, y + charWidth, 0), new Vector3f(x + charWidth / 2, y, 0));
                x += charMaxWidth;
            } else if (c == 'J') {
                batchLine(new Vector3f(x, y, 0f), new Vector3f(x + charWidth / 2, y, 0));
                batchLine(new Vector3f(x + charWidth / 2, y + charWidth, 0), new Vector3f(x + charWidth / 2, y, 0));
                batchLine(new Vector3f(x, y, 0), new Vector3f(x, y + charWidth / 2, 0));
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
                batchLine(new Vector3f(x + charWidth, y + charWidth, 0), new Vector3f(x + charWidth / 2, y + charWidth / 2, 0));
                batchLine(new Vector3f(x, y + charWidth, 0), new Vector3f(x + charWidth / 2, y + charWidth / 2, 0));
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
                batchLine(new Vector3f(x + 0.1f * charWidth, y + charWidth / 2, 0f), new Vector3f(x + 0.9f * charWidth, y + charWidth / 2, 0));
                batchLine(new Vector3f(x, y, 0f), new Vector3f(x + charWidth, y, 0));
                batchLine(new Vector3f(x, y + charWidth, 0), new Vector3f(x + 0.1f * charWidth, y + charWidth / 2, 0));
                batchLine(new Vector3f(x + 0.9f * charWidth, y + charWidth / 2, 0), new Vector3f(x + charWidth, y, 0));
                x += charMaxWidth;
            } else if (c == 'T') {
                batchLine(new Vector3f(x, y + charWidth, 0f), new Vector3f(x + charWidth, y + charWidth, 0));
                batchLine(new Vector3f(x + charWidth / 2, y + charWidth, 0), new Vector3f(x + charWidth / 2, y, 0));
                x += charMaxWidth;
            } else if (c == 'U') {
                batchLine(new Vector3f(x, y, 0f), new Vector3f(x + charWidth, y, 0));
                batchLine(new Vector3f(x + charWidth, y + charWidth, 0), new Vector3f(x + charWidth, y, 0));
                batchLine(new Vector3f(x, y + charWidth, 0), new Vector3f(x, y, 0));
                x += charMaxWidth;
            } else if (c == 'V') {
                batchLine(new Vector3f(x + charWidth, y + charWidth, 0), new Vector3f(x + charWidth / 2, y, 0));
                batchLine(new Vector3f(x, y + charWidth, 0), new Vector3f(x + charWidth / 2, y, 0));
                x += charMaxWidth;
            } else if (c == 'W') {
                batchLine(new Vector3f(x, y + charWidth, 0), new Vector3f(x + charWidth / 4, y, 0));
                batchLine(new Vector3f(x + charWidth / 4, y, 0), new Vector3f(x + charWidth / 2, y + charWidth, 0));
                batchLine(new Vector3f(x + charWidth / 2, y + charWidth, 0), new Vector3f(x + 3f * charWidth / 4f, y, 0));
                batchLine(new Vector3f(x + 3f * charWidth / 4f, y, 0), new Vector3f(x + charWidth, y + charWidth, 0));
                x += charMaxWidth;
            } else if (c == 'X') {
                batchLine(new Vector3f(x + charWidth, y + charWidth, 0), new Vector3f(x, y, 0));
                batchLine(new Vector3f(x, y + charWidth, 0), new Vector3f(x + charWidth, y, 0));
                x += charMaxWidth;
            } else if (c == 'Y') {
                batchLine(new Vector3f(x, y + charWidth, 0), new Vector3f(x + charWidth / 2, y + charWidth / 2, 0));
                batchLine(new Vector3f(x + charWidth / 2, y + charWidth / 2, 0), new Vector3f(x + charWidth, y + charWidth, 0));
                batchLine(new Vector3f(x + charWidth / 2, y + charWidth / 2, 0), new Vector3f(x + charWidth / 2, y, 0));
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
                batchLine(new Vector3f(x, y + charWidth / 2, 0f), new Vector3f(x + charWidth / 2, y + charWidth, 0));
                batchLine(new Vector3f(x + charWidth / 2, y + charWidth, 0), new Vector3f(x + charWidth / 2, y, 0));
                x += charMaxWidth;
            } else if (c == '2') {
                batchLine(new Vector3f(x, y + charWidth, 0f), new Vector3f(x + charWidth, y + charWidth, 0));
                batchLine(new Vector3f(x, y + charWidth / 2, 0f), new Vector3f(x + charWidth, y + charWidth / 2, 0));
                batchLine(new Vector3f(x, y, 0f), new Vector3f(x + charWidth, y, 0));
                batchLine(new Vector3f(x, y + charWidth / 2, 0), new Vector3f(x, y, 0));
                batchLine(new Vector3f(x + charWidth, y + charWidth, 0), new Vector3f(x + charWidth, y + charWidth / 2, 0));
                x += charMaxWidth;
            } else if (c == '3') {
                batchLine(new Vector3f(x, y + charWidth, 0f), new Vector3f(x + charWidth, y + charWidth, 0));
                batchLine(new Vector3f(x, y + charWidth / 2, 0f), new Vector3f(x + charWidth, y + charWidth / 2, 0));
                batchLine(new Vector3f(x, y, 0f), new Vector3f(x + charWidth, y, 0));
                batchLine(new Vector3f(x + charWidth, y + charWidth, 0), new Vector3f(x + charWidth, y, 0));
                x += charMaxWidth;
            } else if (c == '4') {
                batchLine(new Vector3f(x, y + charWidth / 2, 0f), new Vector3f(x + charWidth, y + charWidth / 2, 0));
                batchLine(new Vector3f(x, y + charWidth, 0), new Vector3f(x, y + charWidth / 2, 0));
                batchLine(new Vector3f(x + charWidth, y + charWidth, 0), new Vector3f(x + charWidth, y, 0));
                x += charMaxWidth;
            } else if (c == '5') {
                batchLine(new Vector3f(x, y + charWidth, 0f), new Vector3f(x + charWidth, y + charWidth, 0));
                batchLine(new Vector3f(x, y + charWidth / 2, 0f), new Vector3f(x + charWidth, y + charWidth / 2, 0));
                batchLine(new Vector3f(x, y, 0f), new Vector3f(x + charWidth, y, 0));
                batchLine(new Vector3f(x + charWidth, y + charWidth / 2, 0), new Vector3f(x + charWidth, y, 0));
                batchLine(new Vector3f(x, y + charWidth, 0), new Vector3f(x, y + charWidth / 2, 0));
                x += charMaxWidth;
            } else if (c == '6') {
                batchLine(new Vector3f(x, y + charWidth, 0f), new Vector3f(x + charWidth, y + charWidth, 0));
                batchLine(new Vector3f(x, y + charWidth / 2, 0f), new Vector3f(x + charWidth, y + charWidth / 2, 0));
                batchLine(new Vector3f(x, y, 0f), new Vector3f(x + charWidth, y, 0));
                batchLine(new Vector3f(x, y + charWidth, 0), new Vector3f(x, y, 0));
                batchLine(new Vector3f(x + charWidth, y + charWidth / 2, 0), new Vector3f(x + charWidth, y, 0));
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
                batchLine(new Vector3f(x, y + charWidth, 0), new Vector3f(x, y + charWidth / 2, 0));
                batchLine(new Vector3f(x + charWidth, y + charWidth, 0), new Vector3f(x + charWidth, y, 0));
                x += charMaxWidth;
            } else if (c == '+') {
                batchLine(new Vector3f(x + charWidth / 2, y + 3f * charWidth / 4f, 0), new Vector3f(x + charWidth / 2, y + charWidth / 4f, 0));
                batchLine(new Vector3f(x + charWidth / 4f, y + charWidth / 2, 0f), new Vector3f(x + 3f * charWidth / 4f, y + charWidth / 2, 0));
                x += charMaxWidth;
            } else if (c == '-') {
                batchLine(new Vector3f(x + charWidth / 4f, y + charWidth / 2, 0f), new Vector3f(x + 3f * charWidth / 4f, y + charWidth / 2, 0));
                x += charMaxWidth;
            } else if (c == '.') {
                batchLine(new Vector3f(x + charWidth / 2, y + charWidth / 16f, 0), new Vector3f(x + charWidth / 2, y, 0));
                x += charMaxWidth;
            } else if (c == ',') {
                batchLine(new Vector3f(x + charWidth / 2, y + charWidth / 4f, 0), new Vector3f(x + charWidth / 2, y, 0));
                x += charMaxWidth;
            }
        }
    }

    static Renderer create(Engine engine) {
        try {
            Class<? extends Renderer> rendererClass = Config.getInstance().getRendererClass();
            Renderer instance = rendererClass.newInstance();
            instance.init(engine);
            return instance;
        } catch (InstantiationException | IllegalAccessException e) {
            e.printStackTrace();
        }
        return null;
    }


}
