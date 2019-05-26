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
    public void update(@NotNull RenderState writeState) {
        prepare(writeState);
    }
}
