package de.hanno.hpengine.engine.graphics.renderer.drawstrategy.extensions;

import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.graphics.renderer.RenderBatch;
import de.hanno.hpengine.engine.graphics.renderer.GraphicsContext;
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
import de.hanno.hpengine.engine.graphics.shader.ProgramFactory;
import de.hanno.hpengine.engine.model.texture.TextureFactory;
import de.hanno.hpengine.util.stopwatch.GPUProfiler;

import java.io.File;
import java.util.List;

import static de.hanno.hpengine.engine.graphics.renderer.constants.GlCap.CULL_FACE;
import static de.hanno.hpengine.engine.graphics.renderer.constants.GlCap.DEPTH_TEST;

public class DirectionalLightShadowMapExtension implements ShadowMapExtension {

    public static final int SHADOWMAP_RESOLUTION = 2048;

    transient private RenderTarget renderTarget;
    transient private Program directionalShadowPassProgram;

    public DirectionalLightShadowMapExtension() {

        directionalShadowPassProgram = ProgramFactory.getInstance().getProgram(true, Shader.ShaderSourceFactory.getShaderSource(new File(Shader.getDirectory() + "mvp_ssbo_vertex.glsl")), Shader.ShaderSourceFactory.getShaderSource(new File(Shader.getDirectory() + "shadowmap_fragment.glsl")));

        renderTarget = new RenderTargetBuilder()
                .setWidth(SHADOWMAP_RESOLUTION)
                .setHeight(SHADOWMAP_RESOLUTION)
                .setClearRGBA(1f, 1f, 1f, 1f)
                .add(3, new ColorAttachmentDefinition()
                        .setInternalFormat(GL30.GL_RGBA32F)
//                        .setTextureFilter(GL11.GL_NEAREST))
                        .setTextureFilter(GL11.GL_LINEAR))
                .build();

        Engine.getEventBus().register(this);
    }

    private long renderedInCycle;
    @Override
    public void renderFirstPass(FirstPassResult firstPassResult, RenderState renderState) {
        GPUProfiler.start("Directional shadowmap");
        if(renderedInCycle < renderState.directionalLightHasMovedInCycle ||
                renderedInCycle < renderState.entitiesState.entityMovedInCycle ||
                renderedInCycle < renderState.entitiesState.entityAddedInCycle) {
            drawShadowMap(renderState, firstPassResult);
        }
        GPUProfiler.end();
    }

    private void drawShadowMap(RenderState renderState, FirstPassResult firstPassResult) {
        GraphicsContext.getInstance().depthMask(true);
        GraphicsContext.getInstance().enable(DEPTH_TEST);
//		OpenGLContext.getInstance().cullFace(BACK);
        GraphicsContext.getInstance().disable(CULL_FACE);

        // TODO: Better instance culling
        List<RenderBatch> visibles = renderState.perEntityInfos();

        renderTarget.use(true);
        directionalShadowPassProgram.use();
        directionalShadowPassProgram.bindShaderStorageBuffer(1, renderState.getMaterialBuffer());
        directionalShadowPassProgram.bindShaderStorageBuffer(3, renderState.getEntitiesBuffer());
        directionalShadowPassProgram.setUniformAsMatrix4("viewMatrix", renderState.getDirectionalLightViewMatrixAsBuffer());
        directionalShadowPassProgram.setUniformAsMatrix4("projectionMatrix", renderState.getDirectionalLightProjectionMatrixAsBuffer());

        for(int i = 0; i < visibles.size(); i++) {
            RenderBatch e = visibles.get(i);
//            if (e.getMaterial().getMaterialType().equals(Material.MaterialType.FOLIAGE)) {
//                OpenGLContext.getInstance().disable(CULL_FACE);
//            } else {
//                OpenGLContext.getInstance().enable(CULL_FACE);
//            }
            directionalShadowPassProgram.setUniform("entityBaseIndex", e.getEntityBufferIndex());

            DrawStrategy.draw(renderState.getVertexIndexBuffer().getVertexBuffer(), renderState.getVertexIndexBuffer().getIndexBuffer(), e, directionalShadowPassProgram, !e.isVisible());
        }
        TextureFactory.getInstance().generateMipMaps(getShadowMapId());
        firstPassResult.directionalLightShadowMapWasRendered = true;

//		OpenGLContext.getInstance().enable(CULL_FACE);
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