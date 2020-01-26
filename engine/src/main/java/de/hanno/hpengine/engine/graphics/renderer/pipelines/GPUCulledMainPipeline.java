package de.hanno.hpengine.engine.graphics.renderer.pipelines;

import de.hanno.hpengine.engine.backend.EngineContext;
import de.hanno.hpengine.engine.graphics.state.RenderState;
import de.hanno.hpengine.engine.graphics.state.RenderSystem;
import org.jetbrains.annotations.NotNull;

import java.nio.FloatBuffer;

public class GPUCulledMainPipeline extends GPUOcclusionCulledPipeline {

    public GPUCulledMainPipeline(EngineContext engineContext, RenderSystem renderer) {
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
}
