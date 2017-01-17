package de.hanno.hpengine.renderer.rendertarget;

public class CubeRenderTargetBuilder extends RenderTargetBuilder {

    @Override
    public CubeRenderTarget build() {
        return new CubeRenderTarget(this);
    }
}
