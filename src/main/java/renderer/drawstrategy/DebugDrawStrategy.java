package renderer.drawstrategy;

import camera.Camera;
import component.ModelComponent;
import config.Config;
import engine.AppContext;
import engine.model.Entity;
import event.EntitySelectedEvent;
import octree.Octree;
import org.lwjgl.BufferUtils;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.*;
import org.lwjgl.util.vector.Vector3f;
import renderer.DeferredRenderer;
import renderer.OpenGLContext;
import renderer.RenderExtract;
import renderer.Renderer;
import renderer.constants.GlCap;
import renderer.light.*;
import renderer.material.MaterialFactory;
import renderer.rendertarget.RenderTarget;
import scene.EnvironmentProbeFactory;
import shader.Program;
import shader.ProgramFactory;
import texture.TextureFactory;
import util.stopwatch.GPUProfiler;

import java.nio.FloatBuffer;
import java.util.List;

import static renderer.constants.GlCap.*;
import static renderer.constants.GlDepthFunc.LESS;
import static renderer.constants.GlTextureTarget.*;

public class DebugDrawStrategy extends BaseDrawStrategy {
    public static volatile boolean USE_COMPUTESHADER_FOR_REFLECTIONS = false;
    public static volatile int IMPORTANCE_SAMPLE_COUNT = 8;

    private Program firstPassProgram;
    private Program combineProgram;
    private Program linesProgram;

    OpenGLContext openGLContext;

    public DebugDrawStrategy() throws Exception {
        super();
        ProgramFactory programFactory = ProgramFactory.getInstance();
        firstPassProgram = programFactory.getProgram("first_pass_vertex.glsl", "first_pass_ambientcolor_fragment.glsl");

        combineProgram = programFactory.getProgram("combine_pass_vertex.glsl", "combine_pass_ambientcolor_fragment.glsl", DeferredRenderer.RENDERTOQUAD, false);
        linesProgram = ProgramFactory.getInstance().getProgram("mvp_vertex.glsl", "simple_color_fragment.glsl");

        openGLContext = OpenGLContext.getInstance();
    }

    public DrawResult draw(Camera camera, AppContext appContext, RenderExtract renderExtract) {
        return draw(appContext, appContext.getScene().getOctree(), camera, renderExtract);
    }

    public DrawResult draw(AppContext appContext, RenderExtract renderExtract) {
        return draw(appContext.getActiveCamera(), appContext, renderExtract);
    }

    private DrawResult draw(AppContext appContext, Octree octree, Camera camera, RenderExtract renderExtract) {
        SecondPassResult secondPassResult = null;

        GPUProfiler.start("First pass");
        FirstPassResult firstPassResult = drawFirstPass(appContext, camera, octree);
        GPUProfiler.end();

        OpenGLContext.getInstance().disable(DEPTH_TEST);
        GPUProfiler.start("Combine pass");
        combinePass(camera);
        GPUProfiler.end();

        return new DrawResult(firstPassResult, secondPassResult);
    }

    public FirstPassResult drawFirstPass(AppContext appContext, Camera camera, Octree octree) {
        openGLContext.disable(CULL_FACE);
        openGLContext.depthMask(true);
        Renderer.getInstance().getGBuffer().use(true);
        openGLContext.enable(DEPTH_TEST);
        openGLContext.depthFunc(LESS);
        openGLContext.disable(GlCap.BLEND);

        GPUProfiler.start("Culling");
        List<Entity> entities;

        if (Config.useFrustumCulling) {
            entities = (octree.getVisible(camera));

            for (int i = 0; i < entities.size(); i++) {
                if (!entities.get(i).isInFrustum(camera)) {
                    entities.remove(i);
                }
            }
        } else {
            entities = (octree.getEntities());
        }
        GPUProfiler.end();

        int verticesDrawn = 0;
        int entityCount = 0;
        GPUProfiler.start("Draw entities");

        if(Config.DRAWSCENE_ENABLED) {
            GPUProfiler.start("Set global uniforms first pass");
            Program firstpassDefaultProgram = ProgramFactory.getInstance().getFirstpassDefaultProgram();
            firstpassDefaultProgram.use();
            firstpassDefaultProgram.bindShaderStorageBuffer(1, MaterialFactory.getInstance().getMaterialBuffer());
            firstpassDefaultProgram.bindShaderStorageBuffer(3, AppContext.getInstance().getScene().getEntitiesBuffer());
            firstpassDefaultProgram.setUniform("useRainEffect", Config.RAINEFFECT == 0.0 ? false : true);
            firstpassDefaultProgram.setUniform("rainEffect", Config.RAINEFFECT);
            firstpassDefaultProgram.setUniformAsMatrix4("viewMatrix", camera.getViewMatrixAsBuffer());
            firstpassDefaultProgram.setUniformAsMatrix4("lastViewMatrix", camera.getLastViewMatrixAsBuffer());
            firstpassDefaultProgram.setUniformAsMatrix4("projectionMatrix", camera.getProjectionMatrixAsBuffer());
            firstpassDefaultProgram.setUniform("eyePosition", camera.getPosition());
            firstpassDefaultProgram.setUniform("lightDirection", appContext.getScene().getDirectionalLight().getViewDirection());
            firstpassDefaultProgram.setUniform("near", camera.getNear());
            firstpassDefaultProgram.setUniform("far", camera.getFar());
            firstpassDefaultProgram.setUniform("time", (int)System.currentTimeMillis());
            firstpassDefaultProgram.setUniform("useParallax", Config.useParallax);
            firstpassDefaultProgram.setUniform("useSteepParallax", Config.useSteepParallax);
            GPUProfiler.end();

            for (Entity entity : entities) {
                if(entity.getComponents().containsKey("ModelComponent")) {
                    int currentVerticesCount = ModelComponent.class.cast(entity.getComponents().get("ModelComponent"))
                            .draw(camera, null, firstpassDefaultProgram, AppContext.getInstance().getScene().getEntities().indexOf(entity), entity.isVisible(), entity.isSelected(), true);
                    verticesDrawn += currentVerticesCount;
                    if(currentVerticesCount > 0) { entityCount++; }
                }
            }
        }
        GPUProfiler.end();

        openGLContext.enable(CULL_FACE);

        GPUProfiler.start("Generate Mipmaps of colormap");
        openGLContext.activeTexture(0);
        TextureFactory.getInstance().generateMipMaps(Renderer.getInstance().getGBuffer().getColorReflectivenessMap());
        GPUProfiler.end();

        if(appContext.PICKING_CLICK == 1) {
            openGLContext.readBuffer(4);

            FloatBuffer floatBuffer = BufferUtils.createFloatBuffer(4); // 4 channels
            GL11.glReadPixels(Mouse.getX(), Mouse.getY(), 1, 1, GL11.GL_RGBA, GL11.GL_FLOAT, floatBuffer);
            try {
                int componentIndex = 3; // alpha component
                appContext.getScene().getEntities().parallelStream().forEach(e -> { e.setSelected(false); });
                Entity entity = appContext.getScene().getEntities().get((int)floatBuffer.get(componentIndex));
                entity.setSelected(true);
                AppContext.getEventBus().post(new EntitySelectedEvent(entity));
            } catch (Exception e) {
                e.printStackTrace();
            }
            floatBuffer = null;
            appContext.PICKING_CLICK = 0;
        }

        return new FirstPassResult(verticesDrawn, entityCount);
    }



    public void combinePass(Camera camera) {
        GBuffer gBuffer = Renderer.getInstance().getGBuffer();
        RenderTarget finalBuffer = gBuffer.getFinalBuffer();
        TextureFactory.getInstance().generateMipMaps(finalBuffer.getRenderedTexture(0));

        combineProgram.use();
        combineProgram.setUniformAsMatrix4("projectionMatrix", camera.getProjectionMatrixAsBuffer());
        combineProgram.setUniformAsMatrix4("viewMatrix", camera.getViewMatrixAsBuffer());
        combineProgram.setUniform("screenWidth", (float) Config.WIDTH);
        combineProgram.setUniform("screenHeight", (float) Config.HEIGHT);
        combineProgram.setUniform("camPosition", camera.getPosition());
        combineProgram.setUniform("ambientColor", Config.AMBIENT_LIGHT);
        combineProgram.setUniform("useAmbientOcclusion", Config.useAmbientOcclusion);
        combineProgram.setUniform("worldExposure", Config.EXPOSURE);
        combineProgram.setUniform("AUTO_EXPOSURE_ENABLED", Config.AUTO_EXPOSURE_ENABLED);
        combineProgram.setUniform("fullScreenMipmapCount", gBuffer.getFullScreenMipmapCount());
        combineProgram.setUniform("activeProbeCount", EnvironmentProbeFactory.getInstance().getProbes().size());
        combineProgram.bindShaderStorageBuffer(0, gBuffer.getStorageBuffer());

//        finalBuffer.use(true);
        OpenGLContext.getInstance().bindFrameBuffer(0);
        OpenGLContext.getInstance().disable(DEPTH_TEST);

        OpenGLContext.getInstance().bindTexture(0, TEXTURE_2D, gBuffer.getColorReflectivenessMap());
        OpenGLContext.getInstance().bindTexture(1, TEXTURE_2D, gBuffer.getLightAccumulationMapOneId());
        OpenGLContext.getInstance().bindTexture(2, TEXTURE_2D, gBuffer.getLightAccumulationBuffer().getRenderedTexture(1));
        OpenGLContext.getInstance().bindTexture(3, TEXTURE_2D, gBuffer.getMotionMap());
        OpenGLContext.getInstance().bindTexture(4, TEXTURE_2D, gBuffer.getPositionMap());
        OpenGLContext.getInstance().bindTexture(5, TEXTURE_2D, gBuffer.getNormalMap());
        Renderer.getInstance().getEnvironmentMap().bind(6);
        EnvironmentProbeFactory.getInstance().getEnvironmentMapsArray().bind(7);
        OpenGLContext.getInstance().bindTexture(8, TEXTURE_2D, gBuffer.getReflectionMap());
        OpenGLContext.getInstance().bindTexture(9, TEXTURE_2D, gBuffer.getRefractedMap());
        OpenGLContext.getInstance().bindTexture(11, TEXTURE_2D, gBuffer.getAmbientOcclusionScatteringMap());
        OpenGLContext.getInstance().bindTexture(12, TEXTURE_CUBE_MAP_ARRAY, LightFactory.getInstance().getPointLightDepthMapsArrayCube());

        Renderer.getInstance().getFullscreenBuffer().draw();
    }

}
