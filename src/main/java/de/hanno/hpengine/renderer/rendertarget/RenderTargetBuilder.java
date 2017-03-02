package de.hanno.hpengine.renderer.rendertarget;

import de.hanno.hpengine.config.Config;
import de.hanno.hpengine.util.AbstractBuilder;

import java.util.ArrayList;
import java.util.List;

public class RenderTargetBuilder extends AbstractBuilder<RenderTargetBuilder, RenderTarget> {
    int width = Config.getInstance().getWidth();
    int height = Config.getInstance().getHeight();
    float clearR = 0.0f;
    float clearG = 0.0f;
    float clearB = 0.0f;
    float clearA = 0f;
    List<ColorAttachmentDefinition> colorAttachments = new ArrayList<>();
    boolean useDepthBuffer = true;

    public RenderTargetBuilder setWidth(int width) {
        this.width = width;
        return me();
    }

    public RenderTargetBuilder setHeight(int height) {
        this.height = height;
        return me();
    }

    public RenderTargetBuilder setClearR(float clearR) {
        this.clearR = clearR;
        return me();
    }

    public RenderTargetBuilder setClearG(float clearG) {
        this.clearG = clearG;
        return me();
    }

    public RenderTargetBuilder setClearB(float clearB) {
        this.clearB = clearB;
        return me();
    }

    public RenderTargetBuilder setClearA(float clearA) {
        this.clearA = clearA;
        return me();
    }

    public RenderTargetBuilder setClearRGBA(float r, float g, float b, float a) {
        return setClearR(r).setClearG(g).setClearB(b).setClearA(a);
    }

    public RenderTargetBuilder removeDepthAttachment() {
        useDepthBuffer = false;
        return me();
    }

    public RenderTargetBuilder add(ColorAttachmentDefinition attachmentDefinition) {
        return add(1, attachmentDefinition);
    }
    public RenderTargetBuilder add(int times, ColorAttachmentDefinition attachmentDefinition) {
        for(int i = 0; i < times; i++) {
            this.colorAttachments.add(attachmentDefinition);
        }
        return me();
    }

    @Override
    public RenderTarget build() {
        return new RenderTarget(this);
    }

    @Override
    protected RenderTargetBuilder me() {
        return this;
    }
}
