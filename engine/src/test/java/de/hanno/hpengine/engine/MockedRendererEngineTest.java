package de.hanno.hpengine.engine;

import de.hanno.hpengine.engine.config.Config;
import de.hanno.hpengine.engine.graphics.renderer.MockContext;
import de.hanno.hpengine.engine.graphics.renderer.Renderer;
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DrawResult;
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.GBuffer;
import de.hanno.hpengine.engine.graphics.shader.Program;
import de.hanno.hpengine.engine.graphics.state.RenderState;
import de.hanno.hpengine.engine.scene.EnvironmentProbe;
import de.hanno.hpengine.util.fps.FPSCounter;
import org.joml.Vector3f;
import org.junit.Ignore;
import org.junit.Test;

public class MockedRendererEngineTest {

    @Ignore
    @Test
    public void testEngineWithMockedRenderer() {
        // TODO: Make running this test possible
        Config.getInstance().setRendererClass(MockRenderer.class);
        Config.getInstance().setGpuContextClass(MockContext.class);
        Engine.create();
    }

    public static class MockRenderer implements Renderer {

        @Override
        public void draw(DrawResult result, RenderState renderState) {

        }

        @Override
        public void update(Engine engine, float seconds) {

        }

        @Override
        public void batchLine(Vector3f from, Vector3f to) {

        }

        @Override
        public int drawLines(Program firstPassProgram) {
            return 0;
        }

        @Override
        public void addRenderProbeCommand(EnvironmentProbe probe, boolean urgent) {

        }

        @Override
        public void startFrame() {

        }

        @Override
        public void endFrame() {

        }

        @Override
        public GBuffer getGBuffer() {
            return null;
        }

        @Override
        public void executeRenderProbeCommands(RenderState extract) {

        }

        @Override
        public void drawToQuad(int texture) {

        }
    }
}
