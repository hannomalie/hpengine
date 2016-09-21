package renderer.drawstrategy.extensions;

import com.google.common.eventbus.Subscribe;
import component.ModelComponent;
import engine.AppContext;
import engine.PerEntityInfo;
import engine.model.Entity;
import engine.model.EntityFactory;
import event.DirectionalLightHasMovedEvent;
import event.EntityAddedEvent;
import net.engio.mbassy.listener.Handler;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import renderer.OpenGLContext;
import renderer.RenderExtract;
import renderer.drawstrategy.FirstPassResult;
import renderer.light.DirectionalLight;
import renderer.material.Material;
import renderer.material.MaterialFactory;
import renderer.rendertarget.ColorAttachmentDefinition;
import renderer.rendertarget.RenderTarget;
import renderer.rendertarget.RenderTargetBuilder;
import shader.Program;
import shader.ProgramFactory;
import texture.TextureFactory;
import util.stopwatch.GPUProfiler;

import java.util.List;
import java.util.stream.Collectors;

import static renderer.constants.GlCap.CULL_FACE;
import static renderer.constants.GlCap.DEPTH_TEST;

public class DirectionalLightShadowMapExtension implements ShadowMapExtension {

    public static final int SHADOWMAP_RESOLUTION = 2048;

    transient private RenderTarget renderTarget;
    transient private Program directionalShadowPassProgram;

    public DirectionalLightShadowMapExtension() {

        directionalShadowPassProgram = ProgramFactory.getInstance().getProgram("mvp_ssbo_vertex.glsl", "shadowmap_fragment.glsl", ModelComponent.DEFAULTCHANNELS, true);

        renderTarget = new RenderTargetBuilder()
                .setWidth(SHADOWMAP_RESOLUTION)
                .setHeight(SHADOWMAP_RESOLUTION)
                .setClearRGBA(1f, 1f, 1f, 1f)
                .add(3, new ColorAttachmentDefinition()
                        .setInternalFormat(GL30.GL_RGBA32F)
//                        .setTextureFilter(GL11.GL_NEAREST))
                        .setTextureFilter(GL11.GL_LINEAR))
                .build();

        AppContext.getEventBus().register(this);
    }

    @Override
    public void renderFirstPass(RenderExtract renderExtract, FirstPassResult firstPassResult) {
        if(renderExtract.directionalLightNeedsShadowMapRender) {
            GPUProfiler.start("Directional shadowmap");
            drawShadowMap(renderExtract, firstPassResult);
            GPUProfiler.end();
        }
    }

    private void drawShadowMap(RenderExtract renderExtract, FirstPassResult firstPassResult) {
        DirectionalLight directionalLight = renderExtract.directionalLight;
        if(!directionalLight.isInitialized()) { return; }
        OpenGLContext.getInstance().depthMask(true);
        OpenGLContext.getInstance().enable(DEPTH_TEST);
//		OpenGLContext.getInstance().cullFace(BACK);
        OpenGLContext.getInstance().disable(CULL_FACE);

        // TODO: Better instance culling
        List<PerEntityInfo> visibles = renderExtract.perEntityInfos();

        renderTarget.use(true);
        directionalShadowPassProgram.use();
        directionalShadowPassProgram.bindShaderStorageBuffer(1, MaterialFactory.getInstance().getMaterialBuffer());
        directionalShadowPassProgram.bindShaderStorageBuffer(3, EntityFactory.getInstance().getEntitiesBuffer());
        directionalShadowPassProgram.setUniformAsMatrix4("viewMatrix", directionalLight.getCamera().getViewMatrixAsBuffer());
        directionalShadowPassProgram.setUniformAsMatrix4("projectionMatrix", directionalLight.getCamera().getProjectionMatrixAsBuffer());

        for (PerEntityInfo e : visibles) {
            if (e.getMaterial().getMaterialType().equals(Material.MaterialType.FOLIAGE)) {
                OpenGLContext.getInstance().disable(CULL_FACE);
            } else {
                OpenGLContext.getInstance().enable(CULL_FACE);
            }
//                directionalShadowPassProgram.setUniformAsMatrix4("modelMatrix", e.getModelMatrixAsBuffer());
            e.getMaterial().setTexturesActive(directionalShadowPassProgram);
            directionalShadowPassProgram.setUniform("hasDiffuseMap", e.getMaterial().hasDiffuseMap());
            directionalShadowPassProgram.setUniform("entityIndex", e.getEntityIndex());
            directionalShadowPassProgram.setUniform("entityBaseIndex", e.getEntityBaseIndex());
            directionalShadowPassProgram.setUniform("color", e.getMaterial().getDiffuse());

            e.getVertexBuffer().drawInstanced(e.getInstanceCount());
        }
        TextureFactory.getInstance().generateMipMaps(getShadowMapId());
        firstPassResult.directionalLightShadowMapWasRendered = true;

//		OpenGLContext.getInstance().enable(CULL_FACE);
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
