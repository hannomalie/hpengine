package renderer.drawstrategy.extensions;

import renderer.RenderExtract;
import renderer.drawstrategy.FirstPassResult;
import renderer.drawstrategy.SecondPassResult;

public interface RenderExtension {
    default void renderFirstPass(RenderExtract renderExtract, FirstPassResult firstPassResult) {}
    default void renderSecondPassFullScreen(RenderExtract renderExtract, SecondPassResult secondPassResult) {}
    default void renderSecondPassHalfScreen(RenderExtract renderExtract, SecondPassResult secondPassResult) {}
}
