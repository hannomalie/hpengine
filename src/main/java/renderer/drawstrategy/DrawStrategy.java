package renderer.drawstrategy;

import engine.AppContext;
import renderer.RenderExtract;

public interface DrawStrategy {
    DrawResult draw(AppContext appContext, RenderExtract renderExtract);
}
