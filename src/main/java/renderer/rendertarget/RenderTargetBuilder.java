package renderer.rendertarget;

import config.Config;

import java.util.ArrayList;
import java.util.List;

public class RenderTargetBuilder {
    int width = Config.WIDTH;
    int height = Config.HEIGHT;
    float clearR = 0.0f;
    float clearG = 0.0f;
    float clearB = 0.0f;
    float clearA = 0f;
    List<ColorAttachmentDefinition> colorAttachments = new ArrayList<>();
    boolean useDepthBuffer = true;

    public RenderTargetBuilder setWidth(int width) {
        this.width = width;
        return this;
    }

    public RenderTargetBuilder setHeight(int height) {
        this.height = height;
        return this;
    }

    public RenderTargetBuilder setClearR(float clearR) {
        this.clearR = clearR;
        return this;
    }

    public RenderTargetBuilder setClearG(float clearG) {
        this.clearG = clearG;
        return this;
    }

    public RenderTargetBuilder setClearB(float clearB) {
        this.clearB = clearB;
        return this;
    }

    public RenderTargetBuilder setClearA(float clearA) {
        this.clearA = clearA;
        return this;
    }

    public RenderTargetBuilder setClearRGBA(float r, float g, float b, float a) {
        return setClearR(r).setClearG(g).setClearB(b).setClearA(a);
    }

    public RenderTargetBuilder removeDepthAttachment() {
        useDepthBuffer = false;
        return this;
    }

    public RenderTargetBuilder add(ColorAttachmentDefinition attachmentDefinition) {
        return add(1, attachmentDefinition);
    }
    public RenderTargetBuilder add(int times, ColorAttachmentDefinition attachmentDefinition) {
        for(int i = 0; i < times; i++) {
            this.colorAttachments.add(attachmentDefinition);
        }
        return this;
    }

    public RenderTarget build() {
        return new RenderTarget(this);
    }
}