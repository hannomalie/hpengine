package renderer.drawstrategy.extensions;

import renderer.RenderExtract;
import renderer.drawstrategy.FirstPassResult;

public interface RenderExtension {
    void run(RenderExtract renderExtract, FirstPassResult firstPassResult);
}
