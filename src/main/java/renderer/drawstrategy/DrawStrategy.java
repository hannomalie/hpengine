package renderer.drawstrategy;

import renderer.RenderExtract;
import renderer.rendertarget.RenderTarget;

import javax.annotation.Nullable;

public interface DrawStrategy {
    default DrawResult draw(RenderExtract renderExtract) {
            return draw((RenderTarget) null, renderExtract);
    }

    DrawResult draw(@Nullable RenderTarget renderTarget, RenderExtract renderExtract);
}
