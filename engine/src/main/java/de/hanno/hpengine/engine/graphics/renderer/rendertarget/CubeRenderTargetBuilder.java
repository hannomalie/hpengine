package de.hanno.hpengine.engine.graphics.renderer.rendertarget;

public class CubeRenderTargetBuilder extends RenderTargetBuilder {

    @Override
    public CubeRenderTarget build() {
        return new CubeRenderTarget(this);
    }
}
