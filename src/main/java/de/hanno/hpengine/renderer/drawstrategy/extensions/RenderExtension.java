package de.hanno.hpengine.renderer.drawstrategy.extensions;

import de.hanno.hpengine.renderer.RenderExtract;
import de.hanno.hpengine.renderer.drawstrategy.FirstPassResult;
import de.hanno.hpengine.renderer.drawstrategy.SecondPassResult;

public interface RenderExtension {
    default void renderFirstPass(RenderExtract renderExtract, FirstPassResult firstPassResult) {}
    default void renderSecondPassFullScreen(RenderExtract renderExtract, SecondPassResult secondPassResult) {}
    default void renderSecondPassHalfScreen(RenderExtract renderExtract, SecondPassResult secondPassResult) {}
}
