package de.hanno.hpengine.renderer.drawstrategy.extensions;

import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.PerEntityInfo;
import de.hanno.hpengine.renderer.state.RenderState;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import de.hanno.hpengine.renderer.OpenGLContext;
import de.hanno.hpengine.renderer.drawstrategy.DrawStrategy;
import de.hanno.hpengine.renderer.drawstrategy.FirstPassResult;
import de.hanno.hpengine.renderer.rendertarget.ColorAttachmentDefinition;
import de.hanno.hpengine.renderer.rendertarget.RenderTarget;
import de.hanno.hpengine.renderer.rendertarget.RenderTargetBuilder;
import de.hanno.hpengine.shader.Program;
import de.hanno.hpengine.shader.ProgramFactory;
import de.hanno.hpengine.texture.TextureFactory;
import de.hanno.hpengine.util.stopwatch.GPUProfiler;

import java.util.List;

import static de.hanno.hpengine.renderer.constants.GlCap.CULL_FACE;
import static de.hanno.hpengine.renderer.constants.GlCap.DEPTH_TEST;

public class DirectionalLightShadowMapExtension implements ShadowMapExtension {

    public static final int SHADOWMAP_RESOLUTION = 2048;

    transient private RenderTarget renderTarget;
    transient private Program directionalShadowPassProgram;

    public DirectionalLightShadowMapExtension() {

        directionalShadowPassProgram = ProgramFactory.getInstance().getProgram("mvp_ssbo_vertex.glsl", "shadowmap_fragment.glsl", true);

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
        if(renderedInCycle < renderState.directionalLightHasMovedInCycle) {
            drawShadowMap(renderState, firstPassResult);
        }
        GPUProfiler.end();
    }

    private void drawShadowMap(RenderState renderState, FirstPassResult firstPassResult) {
        OpenGLContext.getInstance().depthMask(true);
        OpenGLContext.getInstance().enable(DEPTH_TEST);
//		OpenGLContext.getInstance().cullFace(BACK);
        OpenGLContext.getInstance().disable(CULL_FACE);

        // TODO: Better instance culling
        List<PerEntityInfo> visibles = renderState.perEntityInfos();

        renderTarget.use(true);
        directionalShadowPassProgram.use();
        directionalShadowPassProgram.bindShaderStorageBuffer(1, renderState.getMaterialBuffer());
        directionalShadowPassProgram.bindShaderStorageBuffer(3, renderState.getEntitiesBuffer());
        directionalShadowPassProgram.setUniformAsMatrix4("viewMatrix", renderState.getDirectionalLightViewMatrixAsBuffer());
        directionalShadowPassProgram.setUniformAsMatrix4("projectionMatrix", renderState.getDirectionalLightProjectionMatrixAsBuffer());

        for(int i = 0; i < visibles.size(); i++) {
            PerEntityInfo e = visibles.get(i);
//            if (e.getMaterial().getMaterialType().equals(Material.MaterialType.FOLIAGE)) {
//                OpenGLContext.getInstance().disable(CULL_FACE);
//            } else {
//                OpenGLContext.getInstance().enable(CULL_FACE);
//            }
            directionalShadowPassProgram.setUniform("entityBaseIndex", e.getEntityBufferIndex());

            DrawStrategy.draw(renderState, e, directionalShadowPassProgram, !e.isVisible() || !e.isVisibleForCamera());
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
