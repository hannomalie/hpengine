package de.hanno.hpengine.engine.graphics.renderer.rendertarget;

import de.hanno.hpengine.engine.graphics.GpuContext;
import de.hanno.hpengine.util.AbstractBuilder;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;

public class RenderTargetBuilder<BUILDER_TYPE extends RenderTargetBuilder, TARGET_TYPE extends RenderTarget> extends AbstractBuilder<TARGET_TYPE> {
    String name = "Unnamed";
    int width = 1280;
    int height = 720;
    float clearR = 0.0f;
    float clearG = 0.0f;
    float clearB = 0.0f;
    float clearA = 0f;
    List<ColorAttachmentDefinition> colorAttachments = new ArrayList<>();
    boolean useDepthBuffer = true;
    private final GpuContext gpuContext;

    public RenderTargetBuilder(GpuContext gpuContext) {
        this.gpuContext = gpuContext;
    }

    public BUILDER_TYPE setWidth(int width) {
        this.width = width;
        return (BUILDER_TYPE) this;
    }

    public BUILDER_TYPE setHeight(int height) {
        this.height = height;
        return (BUILDER_TYPE) this;
    }

    public BUILDER_TYPE setClearR(float clearR) {
        this.clearR = clearR;
        return (BUILDER_TYPE) this;
    }

    public BUILDER_TYPE setClearG(float clearG) {
        this.clearG = clearG;
        return (BUILDER_TYPE) this;
    }

    public BUILDER_TYPE setClearB(float clearB) {
        this.clearB = clearB;
        return (BUILDER_TYPE) this;
    }

    public BUILDER_TYPE setClearA(float clearA) {
        this.clearA = clearA;
        return (BUILDER_TYPE) this;
    }

    public BUILDER_TYPE setClearRGBA(float r, float g, float b, float a) {
        return (BUILDER_TYPE) setClearR(r).setClearG(g).setClearB(b).setClearA(a);
    }

    public BUILDER_TYPE removeDepthAttachment() {
        useDepthBuffer = false;
        return (BUILDER_TYPE) this;
    }

    public BUILDER_TYPE add(ColorAttachmentDefinitions attachmentDefinitions) {
        for(int i = 0; i < attachmentDefinitions.getNames().length; i++) {
            String name = attachmentDefinitions.getNames()[i];
            add(new ColorAttachmentDefinition(name, attachmentDefinitions.getInternalFormat(), attachmentDefinitions.getTextureFilter()));
        }
        return (BUILDER_TYPE) this;
    }
    public BUILDER_TYPE add(ColorAttachmentDefinition attachmentDefinition) {
        colorAttachments.add(attachmentDefinition);
        return (BUILDER_TYPE) this;
    }

    public BUILDER_TYPE setName(String name) {
        this.name = name;
        return (BUILDER_TYPE) this;
    }

    @Override
    public TARGET_TYPE build() {
        TARGET_TYPE target = (TARGET_TYPE) RenderTarget.Companion.invoke(gpuContext, this);
        return target;
    }

    public Vector4f getClear() {
        return new Vector4f(clearR, clearG, clearB, clearA);
    }

}
