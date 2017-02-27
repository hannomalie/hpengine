package de.hanno.hpengine.renderer.drawstrategy.extensions;

import de.hanno.hpengine.renderer.state.RenderState;
import de.hanno.hpengine.renderer.drawstrategy.FirstPassResult;
import de.hanno.hpengine.renderer.drawstrategy.SecondPassResult;

public interface RenderExtension {
    default void renderFirstPass(FirstPassResult firstPassResult, RenderState renderState) {}
    default void renderSecondPassFullScreen(RenderState renderState, SecondPassResult secondPassResult) {}
    default void renderSecondPassHalfScreen(RenderState renderState, SecondPassResult secondPassResult) {}
}
