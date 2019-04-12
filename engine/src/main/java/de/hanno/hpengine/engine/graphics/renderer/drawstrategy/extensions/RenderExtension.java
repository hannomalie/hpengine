package de.hanno.hpengine.engine.graphics.renderer.drawstrategy.extensions;

import de.hanno.hpengine.engine.backend.Backend;
import de.hanno.hpengine.engine.backend.BackendType;
import de.hanno.hpengine.engine.graphics.GpuContext;
import de.hanno.hpengine.engine.graphics.state.RenderState;
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.FirstPassResult;
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.SecondPassResult;

public interface RenderExtension<TYPE extends BackendType> {
    default void update() {}
    default void renderFirstPass(Backend<TYPE> backend, GpuContext<TYPE> gpuContext, FirstPassResult firstPassResult, RenderState renderState) {}
    default void renderSecondPassFullScreen(RenderState renderState, SecondPassResult secondPassResult) {}
    default void renderSecondPassHalfScreen(RenderState renderState, SecondPassResult secondPassResult) {}
}
