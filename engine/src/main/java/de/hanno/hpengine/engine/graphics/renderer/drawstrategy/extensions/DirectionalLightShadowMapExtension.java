package de.hanno.hpengine.engine.graphics.renderer.drawstrategy.extensions;

import de.hanno.hpengine.engine.backend.Backend;
import de.hanno.hpengine.engine.backend.EngineContext;
import de.hanno.hpengine.engine.backend.OpenGl;
import de.hanno.hpengine.engine.graphics.GpuContext;
import de.hanno.hpengine.engine.graphics.renderer.RenderBatch;
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DrawUtils;
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.FirstPassResult;
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.ColorAttachmentDefinitions;
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.RenderTarget;
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.RenderTargetBuilder;
import de.hanno.hpengine.engine.graphics.shader.Program;
import de.hanno.hpengine.engine.graphics.shader.Shader;
import de.hanno.hpengine.engine.graphics.state.RenderState;
import de.hanno.hpengine.util.stopwatch.GPUProfiler;
import org.lwjgl.opengl.GL30;

import java.io.File;
import java.util.List;

import static de.hanno.hpengine.engine.graphics.renderer.constants.GlCap.CULL_FACE;
import static de.hanno.hpengine.engine.graphics.renderer.constants.GlCap.DEPTH_TEST;
import static de.hanno.hpengine.engine.graphics.renderer.constants.GlTextureTarget.TEXTURE_2D;
import static de.hanno.hpengine.engine.graphics.shader.ShaderKt.getShaderSource;

public class DirectionalLightShadowMapExtension implements ShadowMapExtension {

    public static final int SHADOWMAP_RESOLUTION = 2048;
    private final EngineContext engine;

    transient private RenderTarget renderTarget;
    transient private Program directionalShadowPassProgram;
    private final GpuContext gpuContext;
    private VoxelConeTracingExtension voxelConeTracingExtension;

    public DirectionalLightShadowMapExtension(EngineContext engine) {
        gpuContext = engine.getGpuContext();
        this.engine = engine;
        directionalShadowPassProgram = engine.getProgramManager().getProgram(getShaderSource(new File(Shader.directory + "directional_shadowmap_vertex.glsl")), getShaderSource(new File(Shader.directory + "shadowmap_fragment.glsl")));

        renderTarget = new RenderTargetBuilder<>(engine.getGpuContext())
                .setName("DirectionalLight Shadow")
                .setWidth(SHADOWMAP_RESOLUTION)
                .setHeight(SHADOWMAP_RESOLUTION)
                .setClearRGBA(1f, 1f, 1f, 1f)
//                Reflective shadowmaps?
//                .add(new ColorAttachmentDefinitions(new String[]{"Shadow", "Shadow", "Shadow"}, GL30.GL_RGBA32F))
                .add(new ColorAttachmentDefinitions(new String[]{"Shadow"}, GL30.GL_RGBA16F))
                .build();

        engine.getEventBus().register(this);
    }

    private long renderedInCycle;
    @Override
    public void renderFirstPass(Backend<OpenGl> backend, GpuContext<OpenGl> gpuContext, FirstPassResult firstPassResult, RenderState renderState) {
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
        directionalShadowPassProgram.bindShaderStorageBuffer(2, renderState.getDirectionalLightBuffer());
        directionalShadowPassProgram.bindShaderStorageBuffer(3, renderState.getEntitiesBuffer());

        for(int i = 0; i < visibles.size(); i++) {
            RenderBatch e = visibles.get(i);
            directionalShadowPassProgram.setUniform("entityBaseIndex", e.getEntityBufferIndex());
            DrawUtils.draw(gpuContext, renderState.getVertexIndexBufferStatic().getVertexBuffer(), renderState.getVertexIndexBufferStatic().getIndexBuffer(), e, directionalShadowPassProgram, !e.isVisible(), true);
        }
        engine.getTextureManager().generateMipMaps(TEXTURE_2D, getShadowMapId());
        firstPassResult.directionalLightShadowMapWasRendered = true;

        renderedInCycle = renderState.getCycle();

    }

    public RenderTarget getRenderTarget() {
        return renderTarget;
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

    public void setVoxelConeTracingExtension(VoxelConeTracingExtension voxelConeTracingExtension) {
        this.voxelConeTracingExtension = voxelConeTracingExtension;
    }

    public VoxelConeTracingExtension getVoxelConeTracingExtension() {
        return voxelConeTracingExtension;
    }
}
