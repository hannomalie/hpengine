package de.hanno.hpengine.engine.graphics.renderer.drawstrategy.extensions;

import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.graphics.GpuContext;
import de.hanno.hpengine.engine.graphics.renderer.RenderBatch;
import de.hanno.hpengine.engine.graphics.shader.define.Defines;
import de.hanno.hpengine.engine.graphics.state.RenderState;
import de.hanno.hpengine.engine.graphics.shader.Shader;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DrawStrategy;
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.FirstPassResult;
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.ColorAttachmentDefinition;
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.RenderTarget;
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.RenderTargetBuilder;
import de.hanno.hpengine.engine.graphics.shader.Program;
import de.hanno.hpengine.util.stopwatch.GPUProfiler;

import java.io.File;
import java.util.List;

import static de.hanno.hpengine.engine.graphics.renderer.constants.GlCap.CULL_FACE;
import static de.hanno.hpengine.engine.graphics.renderer.constants.GlCap.DEPTH_TEST;

public class DirectionalLightShadowMapExtension implements ShadowMapExtension {

    public static final int SHADOWMAP_RESOLUTION = 2048;
    private final Engine engine;

    transient private RenderTarget renderTarget;
    transient private Program directionalShadowPassProgram;
    private final GpuContext gpuContext;

    public DirectionalLightShadowMapExtension(Engine engine) {
        gpuContext = engine.getGpuContext();
        this.engine = engine;
        directionalShadowPassProgram = engine.getProgramManager().getProgram(Shader.ShaderSourceFactory.getShaderSource(new File(Shader.getDirectory() + "mvp_ssbo_vertex.glsl")), Shader.ShaderSourceFactory.getShaderSource(new File(Shader.getDirectory() + "shadowmap_fragment.glsl")), new Defines());

        renderTarget = new RenderTargetBuilder<RenderTargetBuilder, RenderTarget>(engine.getGpuContext())
                .setWidth(SHADOWMAP_RESOLUTION)
                .setHeight(SHADOWMAP_RESOLUTION)
                .setClearRGBA(1f, 1f, 1f, 1f)
                .add(3, new ColorAttachmentDefinition()
                        .setInternalFormat(GL30.GL_RGBA32F)
//                        .setTextureFilter(GL11.GL_NEAREST))
                        .setTextureFilter(GL11.GL_LINEAR))
                .build();

        engine.getEventBus().register(this);
    }

    private long renderedInCycle;
    @Override
    public void renderFirstPass(Engine engine, GpuContext gpuContext, FirstPassResult firstPassResult, RenderState renderState) {
        GPUProfiler.start("Directional shadowmap");
        if(renderedInCycle < renderState.getDirectionalLightHasMovedInCycle() ||
                renderedInCycle < renderState.getEntitiesState().entityMovedInCycle ||
                renderedInCycle < renderState.getEntitiesState().entityAddedInCycle) {
            drawShadowMap(renderState, firstPassResult);
        }
        GPUProfiler.end();
    }

    private void drawShadowMap(RenderState renderState, FirstPassResult firstPassResult) {
        gpuContext.depthMask(true);
        gpuContext.enable(DEPTH_TEST);
        gpuContext.disable(CULL_FACE);

        // TODO: Better instance culling
        List<RenderBatch> visibles = renderState.getRenderBatchesStatic();

//         TODO: Shadowmap should use pipeline for animated object support
        renderTarget.use(true);
        directionalShadowPassProgram.use();
        directionalShadowPassProgram.bindShaderStorageBuffer(1, renderState.getMaterialBuffer());
        directionalShadowPassProgram.bindShaderStorageBuffer(3, renderState.getEntitiesBuffer());
        directionalShadowPassProgram.setUniformAsMatrix4("viewMatrix", renderState.getDirectionalLightViewMatrixAsBuffer());
        directionalShadowPassProgram.setUniformAsMatrix4("projectionMatrix", renderState.getDirectionalLightProjectionMatrixAsBuffer());

        for(int i = 0; i < visibles.size(); i++) {
            RenderBatch e = visibles.get(i);
            directionalShadowPassProgram.setUniform("entityBaseIndex", e.getEntityBufferIndex());
            DrawStrategy.draw(gpuContext, renderState.getVertexIndexBufferStatic().getVertexBuffer(), renderState.getVertexIndexBufferStatic().getIndexBuffer(), e, directionalShadowPassProgram, !e.isVisible());
        }
        engine.getTextureManager().generateMipMaps(getShadowMapId());
        firstPassResult.directionalLightShadowMapWasRendered = true;

        renderedInCycle = renderState.getCycle();

    }

    public int getShadowMapId() {
        return renderTarget.getRenderedTexture();
    }
    public int getShadowMapWorldPositionId() {
        return renderTarget.getRenderedTexture(2);
    }
    public int getShadowMapColorMapId() {
        return renderTarget.getRenderedTexture(1);
    }

}
