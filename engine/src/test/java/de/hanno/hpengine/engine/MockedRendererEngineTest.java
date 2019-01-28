package de.hanno.hpengine.engine;

import de.hanno.hpengine.engine.config.Config;
import de.hanno.hpengine.engine.graphics.renderer.MockContext;
import de.hanno.hpengine.engine.graphics.renderer.Renderer;
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DrawResult;
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DeferredRenderingBuffer;
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.extensions.RenderExtension;
import de.hanno.hpengine.engine.graphics.shader.Program;
import de.hanno.hpengine.engine.graphics.state.RenderState;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;
import org.junit.Ignore;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

public class MockedRendererEngineTest {

    @Ignore
    @Test
    public void testEngineWithMockedRenderer() {
        // TODO: Make running this test possible
        Config.getInstance().setGpuContextClass(MockContext.class);
        new EngineImpl();
    }

    public static class MockRenderer implements Renderer {

        @Override
        public void destroy() {

        }

        @Override
        public void update(Engine engine, float seconds) {

        }

        @Override
        public void batchLine(Vector3f from, Vector3f to) {

        }

        @Override
        public void batchTriangle(Vector3f a, Vector3f b, Vector3f c) {

        }

        @Override
        public int drawLines(Program firstPassProgram) {
            return 0;
        }

        @Override
        public void startFrame() {

        }

        @Override
        public void endFrame() {

        }

        @Override
        public void drawAllLines(Consumer<Program> action) {

        }

        @Override
        public DeferredRenderingBuffer getGBuffer() {
            return null;
        }

        @Override
        public void drawToQuad(int texture) {

        }

        @Override
        public void batchVector(Vector3f vector) {

        }

        @Override
        public void batchVector(Vector3f vector, float charWidth) {

        }

        @Override
        public void batchString(String text) {

        }

        @Override
        public void batchString(String text, float charWidth) {

        }

        @Override
        public void batchString(String text, float charWidthIn, float gapIn) {

        }

        @Override
        public void batchString(String text, float charWidthIn, float gapIn, int x) {

        }

        @Override
        public void batchString(String text, float charWidthIn, float gapIn, float x, float y) {

        }

        @Override
        public List<RenderExtension> getRenderExtensions() {
            return Collections.emptyList();

        }

        @Override
        public void render(@NotNull DrawResult result, @NotNull RenderState state) {

        }
    }
}
