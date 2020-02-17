package de.hanno.hpengine.engine.graphics.renderer.drawstrategy.extensions;

import de.hanno.hpengine.engine.backend.Backend;
import de.hanno.hpengine.engine.backend.BackendType;
import de.hanno.hpengine.engine.graphics.GpuContext;
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DrawResult;
import de.hanno.hpengine.engine.graphics.state.RenderState;
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.FirstPassResult;
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.SecondPassResult;
import org.jetbrains.annotations.NotNull;

public interface RenderExtension<TYPE extends BackendType> {
    default void update() {}
    default void renderFirstPass(@NotNull Backend<TYPE> backend, @NotNull GpuContext<TYPE> gpuContext, @NotNull FirstPassResult firstPassResult, @NotNull RenderState renderState) {}
    default void renderSecondPassFullScreen(@NotNull RenderState renderState, @NotNull SecondPassResult secondPassResult) {}
    default void renderSecondPassHalfScreen(@NotNull RenderState renderState, @NotNull SecondPassResult secondPassResult) {}
    default void renderEditor(@NotNull RenderState renderState, @NotNull DrawResult result) {}
}
