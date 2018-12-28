package de.hanno.hpengine.engine.graphics.renderer.rendertarget;

import de.hanno.hpengine.engine.backend.Backend;

public class CubeRenderTargetBuilder extends RenderTargetBuilder<CubeRenderTargetBuilder, RenderTarget> {

    private Backend engine;

    public CubeRenderTargetBuilder(Backend engine) {
        super(engine.getGpuContext());
        this.engine = engine;
    }

    @Override
    public CubeRenderTarget build() {
        return engine.getGpuContext().calculate(() -> new CubeRenderTarget(engine, this));
    }

}
