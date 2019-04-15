package de.hanno.hpengine.engine.graphics.renderer.pipelines;

import de.hanno.hpengine.engine.backend.EngineContext;
import de.hanno.hpengine.engine.camera.Camera;
import de.hanno.hpengine.engine.config.Config;
import de.hanno.hpengine.engine.graphics.renderer.Renderer;
import de.hanno.hpengine.engine.graphics.shader.Program;
import de.hanno.hpengine.engine.graphics.state.RenderState;
import org.jetbrains.annotations.NotNull;

import java.nio.FloatBuffer;

public class GPUCulledMainPipeline extends GPUOcclusionCulledPipeline {

    public GPUCulledMainPipeline(EngineContext engineContext, Renderer renderer) {
        super(engineContext, renderer, true, true, true);
    }

//    This can be used for debug drawing
//    @Override
//    public Camera getCullCam() {
//        return getDebugCam();
//    }

//    private Camera getDebugCam() {
//        return engine.getSceneManager().getScene().getComponentSystems().get(CameraComponentSystem.class).getComponents().stream().findFirst().orElse(engine.getScene().getActiveCamera());
//    }
//    @Override
//    public void renderHighZMap() {
//
//    }

    @Override
    public void beforeDrawStatic(RenderState renderState, Program program) {
        beforeDraw(renderState, program);
    }

    @Override
    public void beforeDrawAnimated(RenderState renderState, Program program) {
        beforeDraw(renderState, program);
    }

    protected void beforeDraw(RenderState renderState, Program program) {

        Camera camera = getRenderCam();
        if (camera == null) {
            camera = renderState.getCamera();
        }

        FloatBuffer viewMatrixAsBuffer = camera.getViewMatrixAsBuffer();
        FloatBuffer projectionMatrixAsBuffer = camera.getProjectionMatrixAsBuffer();
        FloatBuffer viewProjectionMatrixAsBuffer = camera.getViewProjectionMatrixAsBuffer();

        program.use();
        program.bindShaderStorageBuffer(1, renderState.getMaterialBuffer());
        program.bindShaderStorageBuffer(3, renderState.getEntitiesBuffer());
        program.setUniform("useRainEffect", !(Config.getInstance().getRainEffect() == 0.0));
        program.setUniform("rainEffect", Config.getInstance().getRainEffect());
        program.setUniformAsMatrix4("viewMatrix", viewMatrixAsBuffer);
        program.setUniformAsMatrix4("lastViewMatrix", viewMatrixAsBuffer);
        program.setUniformAsMatrix4("projectionMatrix", projectionMatrixAsBuffer);
        program.setUniformAsMatrix4("viewProjectionMatrix", viewProjectionMatrixAsBuffer);
        program.setUniform("eyePosition", camera.getPosition());
        program.setUniform("near", camera.getNear());
        program.setUniform("far", camera.getFar());
        program.setUniform("time", (int) System.currentTimeMillis());
        program.setUniform("useParallax", Config.getInstance().isUseParallax());
        program.setUniform("useSteepParallax", Config.getInstance().isUseSteepParallax());
    }

    @Override
    public void update(@NotNull RenderState writeState) {
        prepare(writeState);
    }
}
