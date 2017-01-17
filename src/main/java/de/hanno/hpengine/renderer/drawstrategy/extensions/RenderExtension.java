package de.hanno.hpengine.renderer.drawstrategy.extensions;

import de.hanno.hpengine.renderer.RenderState;
import de.hanno.hpengine.renderer.drawstrategy.FirstPassResult;
import de.hanno.hpengine.renderer.drawstrategy.SecondPassResult;

public interface RenderExtension {
    default void renderFirstPass(RenderState renderState, FirstPassResult firstPassResult) {}
    default void renderSecondPassFullScreen(RenderState renderState, SecondPassResult secondPassResult) {}
    default void renderSecondPassHalfScreen(RenderState renderState, SecondPassResult secondPassResult) {}
}
