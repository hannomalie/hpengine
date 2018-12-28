package de.hanno.hpengine.engine.graphics.renderer.drawstrategy.extensions;

import de.hanno.hpengine.engine.backend.Backend;
import de.hanno.hpengine.engine.graphics.GpuContext;
import de.hanno.hpengine.engine.graphics.state.RenderState;
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.FirstPassResult;
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.SecondPassResult;

public interface RenderExtension {
    default void update() {}
    default void renderFirstPass(Backend backend, GpuContext gpuContext, FirstPassResult firstPassResult, RenderState renderState) {}
    default void renderSecondPassFullScreen(RenderState renderState, SecondPassResult secondPassResult) {}
    default void renderSecondPassHalfScreen(RenderState renderState, SecondPassResult secondPassResult) {}
}
