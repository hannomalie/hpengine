package de.hanno.hpengine.engine.graphics.renderer.rendertarget;

import de.hanno.hpengine.engine.backend.Backend;
import de.hanno.hpengine.engine.backend.BackendType;

import java.util.concurrent.Callable;

public class CubeRenderTargetBuilder extends RenderTargetBuilder<CubeRenderTargetBuilder, RenderTarget> {

    private Backend<? extends BackendType> engine;

    public CubeRenderTargetBuilder(Backend engine) {
        super(engine.getGpuContext());
        this.engine = engine;
    }

    @Override
    public CubeMapRenderTarget build() {
        return engine.getGpuContext().calculate((Callable<CubeMapRenderTarget>) () -> new CubeMapRenderTarget(engine, this));
    }

}
