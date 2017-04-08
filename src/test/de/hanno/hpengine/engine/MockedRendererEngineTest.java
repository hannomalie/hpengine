package de.hanno.hpengine.engine;

import de.hanno.hpengine.config.Config;
import de.hanno.hpengine.renderer.MockContext;
import de.hanno.hpengine.renderer.Renderer;
import de.hanno.hpengine.renderer.drawstrategy.DrawResult;
import de.hanno.hpengine.renderer.drawstrategy.GBuffer;
import de.hanno.hpengine.renderer.fps.FPSCounter;
import de.hanno.hpengine.renderer.state.RenderState;
import de.hanno.hpengine.scene.EnvironmentProbe;
import de.hanno.hpengine.shader.Program;
import org.junit.Ignore;
import org.junit.Test;
import org.lwjgl.util.vector.Vector3f;

import java.awt.*;

public class MockedRendererEngineTest {

    @Ignore
    @Test
    public void testEngineWithMockedRenderer() {
        // TODO: Make running this test possible
        Config.getInstance().setRendererClass(MockRenderer.class);
        Config.getInstance().setGpuContextClass(MockContext.class);
        Engine.init(new Canvas());
    }

    public static class MockRenderer implements Renderer {

        @Override
        public boolean isInitialized() {
            return false;
        }

        @Override
        public void draw(DrawResult result, RenderState renderState) {

        }

        @Override
        public void update(float seconds) {

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
        public FPSCounter getFPSCounter() {
            return null;
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
