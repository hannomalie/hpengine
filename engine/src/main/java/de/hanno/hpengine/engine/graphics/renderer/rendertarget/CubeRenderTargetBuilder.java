package de.hanno.hpengine.engine.graphics.renderer.rendertarget;

import de.hanno.hpengine.engine.Engine;

public class CubeRenderTargetBuilder extends RenderTargetBuilder {

    private Engine engine;

    public CubeRenderTargetBuilder(Engine engine) {
        super(engine.getGpuContext());
        this.engine = engine;
    }

    @Override
    public CubeRenderTarget build() {
        return new CubeRenderTarget(engine, this);
    }
}
