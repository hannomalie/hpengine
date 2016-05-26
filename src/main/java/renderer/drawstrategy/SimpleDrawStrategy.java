package renderer.drawstrategy;

import camera.Camera;
import component.ModelComponent;
import config.Config;
import engine.AppContext;
import engine.Transform;
import engine.model.Entity;
import octree.Octree;
import org.lwjgl.opengl.*;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;
import renderer.DeferredRenderer;
import renderer.OpenGLContext;
import renderer.RenderExtract;
import renderer.Renderer;
import renderer.constants.GlCap;
import renderer.constants.GlTextureTarget;
import renderer.drawstrategy.extensions.*;
import renderer.light.AreaLight;
import renderer.light.DirectionalLight;
import renderer.light.LightFactory;
import renderer.light.TubeLight;
import renderer.material.MaterialFactory;
import renderer.rendertarget.RenderTarget;
import scene.AABB;
import scene.EnvironmentProbe;
import scene.EnvironmentProbeFactory;
import shader.ComputeShaderProgram;
import shader.Program;
import shader.ProgramFactory;
import texture.CubeMap;
import texture.TextureFactory;
import util.stopwatch.GPUProfiler;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

import static renderer.constants.BlendMode.FUNC_ADD;
import static renderer.constants.BlendMode.Factor.ONE;
import static renderer.constants.CullMode.BACK;
import static renderer.constants.GlCap.*;
import static renderer.constants.GlDepthFunc.LESS;
import static renderer.constants.GlTextureTarget.*;

public class SimpleDrawStrategy extends BaseDrawStrategy {
    public static volatile boolean USE_COMPUTESHADER_FOR_REFLECTIONS = false;
    public static volatile int IMPORTANCE_SAMPLE_COUNT = 8;

    private Program depthPrePassProgram;
    private Program secondPassDirectionalProgram;
    private Program secondPassPointProgram;
    private Program secondPassTubeProgram;
    private Program secondPassAreaProgram;
    private Program combineProgram;
    private Program postProcessProgram;
    private Program instantRadiosityProgram;
    private Program aoScatteringProgram;
    private Program highZProgram;
    private Program reflectionProgram;
    private Program linesProgram;
    private Program probeFirstpassProgram;
    private ComputeShaderProgram secondPassPointComputeProgram;
    private ComputeShaderProgram tiledProbeLightingProgram;
    private ComputeShaderProgram tiledDirectLightingProgram;

    OpenGLContext openGLContext;
    private final List<RenderExtension> renderExtensions = new ArrayList<>();
    private final DirectionalLightShadowMapExtension directionalLightShadowMapExtension;
    private FirstPassResult firstPassResult = new FirstPassResult();

    public SimpleDrawStrategy() throws Exception {
        super();
        ProgramFactory programFactory = ProgramFactory.getInstance();
        secondPassPointProgram = programFactory.getProgram("second_pass_point_vertex.glsl", "second_pass_point_fragment.glsl", ModelComponent.POSITIONCHANNEL, false);
        secondPassTubeProgram = programFactory.getProgram("second_pass_point_vertex.glsl", "second_pass_tube_fragment.glsl", ModelComponent.POSITIONCHANNEL, false);
        secondPassAreaProgram = programFactory.getProgram("second_pass_area_vertex.glsl", "second_pass_area_fragment.glsl", ModelComponent.POSITIONCHANNEL, false);
        secondPassDirectionalProgram = programFactory.getProgram("second_pass_directional_vertex.glsl", "second_pass_directional_fragment.glsl", ModelComponent.POSITIONCHANNEL, false);
        instantRadiosityProgram = programFactory.getProgram("second_pass_area_vertex.glsl", "second_pass_instant_radiosity_fragment.glsl", ModelComponent.POSITIONCHANNEL, false);

        secondPassPointComputeProgram = programFactory.getComputeProgram("second_pass_point_compute.glsl");

        combineProgram = programFactory.getProgram("combine_pass_vertex.glsl", "combine_pass_fragment.glsl", DeferredRenderer.RENDERTOQUAD, false);
        postProcessProgram = programFactory.getProgram("passthrough_vertex.glsl", "postprocess_fragment.glsl", DeferredRenderer.RENDERTOQUAD, false);

        aoScatteringProgram = ProgramFactory.getInstance().getProgram("passthrough_vertex.glsl", "scattering_ao_fragment.glsl");
        highZProgram = ProgramFactory.getInstance().getProgram("passthrough_vertex.glsl", "highZ_fragment.glsl");
        reflectionProgram = ProgramFactory.getInstance().getProgram("passthrough_vertex.glsl", "reflections_fragment.glsl");
        linesProgram = ProgramFactory.getInstance().getProgram("mvp_vertex.glsl", "simple_color_fragment.glsl");
        probeFirstpassProgram = ProgramFactory.getInstance().getProgram("first_pass_vertex.glsl", "probe_first_pass_fragment.glsl");
        depthPrePassProgram = ProgramFactory.getInstance().getProgram("first_pass_vertex.glsl", "depth_prepass_fragment.glsl");
        tiledDirectLightingProgram = ProgramFactory.getInstance().getComputeProgram("tiled_direct_lighting_compute.glsl");
        tiledProbeLightingProgram = ProgramFactory.getInstance().getComputeProgram("tiled_probe_lighting_compute.glsl");

        openGLContext = OpenGLContext.getInstance();

        directionalLightShadowMapExtension = new DirectionalLightShadowMapExtension();

        registerRenderExtension(new DrawLinesExtension());
        registerRenderExtension(new VoxelConeTracingExtension());
        registerRenderExtension(new PixelPerfectPickingExtension());
    }

    @Override
    public DrawResult draw(RenderTarget target, RenderExtract renderExtract) {
        SecondPassResult secondPassResult = null;
        AppContext appContext = AppContext.getInstance();
        Octree octree = appContext.getScene().getOctree();

        LightFactory lightFactory = LightFactory.getInstance();
        EnvironmentProbeFactory environmentProbeFactory = EnvironmentProbeFactory.getInstance();
        DirectionalLight light = appContext.getScene().getDirectionalLight();

        GPUProfiler.start("First pass");
        FirstPassResult firstPassResult = drawFirstPass(appContext, renderExtract);
        GPUProfiler.end();

        if (!Config.DEBUGDRAW_PROBES) {
            environmentProbeFactory.drawAlternating(octree, renderExtract.camera, light, Renderer.getInstance().getFrameCount());
            Renderer.getInstance().executeRenderProbeCommands();
            GPUProfiler.start("Shadowmap pass");
            {
                directionalLightShadowMapExtension.renderFirstPass(renderExtract, firstPassResult);
            }
            lightFactory.renderAreaLightShadowMaps(octree);
            if (Config.USE_DPSM) {
                lightFactory.renderPointLightShadowMaps_dpsm(octree);
            } else {
                lightFactory.renderPointLightShadowMaps(renderExtract);
            }
            GPUProfiler.end();
            GPUProfiler.start("Second pass");
            secondPassResult = drawSecondPass(renderExtract.camera, light, appContext.getScene().getTubeLights(), appContext.getScene().getAreaLights(), Renderer.getInstance().getEnvironmentMap(), renderExtract);
            GPUProfiler.end();
            OpenGLContext.getInstance().viewPort(0, 0, Config.WIDTH, Config.HEIGHT);
            OpenGLContext.getInstance().clearDepthAndColorBuffer();
            OpenGLContext.getInstance().disable(DEPTH_TEST);
            GPUProfiler.start("Combine pass");
            combinePass(target, renderExtract.camera);
            GPUProfiler.end();
        } else {
            OpenGLContext.getInstance().viewPort(0, 0, Config.WIDTH, Config.HEIGHT);
            OpenGLContext.getInstance().clearDepthAndColorBuffer();
            OpenGLContext.getInstance().disable(DEPTH_TEST);

            OpenGLContext.getInstance().bindFrameBuffer(0);
            Renderer.getInstance().drawToQuad(Renderer.getInstance().getGBuffer().getColorReflectivenessMap());
        }

        return new DrawResult(firstPassResult, secondPassResult);
    }

    public FirstPassResult drawFirstPass(AppContext appContext, RenderExtract renderExtract) {
        firstPassResult.reset();

        Camera camera = renderExtract.camera;

        GPUProfiler.start("Set GPU state");
        openGLContext.enable(CULL_FACE);
        openGLContext.depthMask(true);
        Renderer.getInstance().getGBuffer().use(true);
        openGLContext.enable(DEPTH_TEST);
        openGLContext.depthFunc(LESS);
        openGLContext.disable(GlCap.BLEND);
        GPUProfiler.end();

        List<Entity> visibleEntities = renderExtract.visibleEntities;

        GPUProfiler.start("Draw entities");

        FloatBuffer viewMatrixAsBuffer = camera.getViewMatrixAsBuffer();
        FloatBuffer projectionMatrixAsBuffer = camera.getProjectionMatrixAsBuffer();

        if (Config.DRAWSCENE_ENABLED && AppContext.getInstance().getScene() != null) {
            GPUProfiler.start("Set global uniforms first pass");
            Program firstpassDefaultProgram = ProgramFactory.getInstance().getFirstpassDefaultProgram();
            firstpassDefaultProgram.use();
            firstpassDefaultProgram.bindShaderStorageBuffer(1, MaterialFactory.getInstance().getMaterialBuffer());
            firstpassDefaultProgram.bindShaderStorageBuffer(3, AppContext.getInstance().getScene().getEntitiesBuffer());
            firstpassDefaultProgram.setUniform("useRainEffect", Config.RAINEFFECT == 0.0 ? false : true);
            firstpassDefaultProgram.setUniform("rainEffect", Config.RAINEFFECT);
            firstpassDefaultProgram.setUniform("useNormalMaps", !Config.DRAWLINES_ENABLED);
            firstpassDefaultProgram.setUniformAsMatrix4("viewMatrix", viewMatrixAsBuffer);
            firstpassDefaultProgram.setUniformAsMatrix4("lastViewMatrix", viewMatrixAsBuffer);
            firstpassDefaultProgram.setUniformAsMatrix4("projectionMatrix", projectionMatrixAsBuffer);
            firstpassDefaultProgram.setUniform("eyePosition", camera.getPosition());
            firstpassDefaultProgram.setUniform("lightDirection", appContext.getScene().getDirectionalLight().getViewDirection());
            firstpassDefaultProgram.setUniform("near", camera.getNear());
            firstpassDefaultProgram.setUniform("far", camera.getFar());
            firstpassDefaultProgram.setUniform("time", (int) System.currentTimeMillis());
            firstpassDefaultProgram.setUniform("useParallax", Config.useParallax);
            firstpassDefaultProgram.setUniform("useSteepParallax", Config.useSteepParallax);
            GPUProfiler.end();

            for (Entity entity : visibleEntities) {
                if (entity.getComponents().containsKey("ModelComponent")) {
                    int currentVerticesCount = ModelComponent.class.cast(entity.getComponents().get("ModelComponent"))
                            .draw(camera, null, firstpassDefaultProgram, AppContext.getInstance().getScene().getEntities().indexOf(entity), entity.isVisible(), entity.isSelected(), Config.DRAWLINES_ENABLED);
                    firstPassResult.verticesDrawn += currentVerticesCount;
                    if (currentVerticesCount > 0) {
                        firstPassResult.entitiesDrawn++;
                    }
                }
            }
        }
        GPUProfiler.end();

        OpenGLContext.getInstance().bindTexture(6, TEXTURE_2D, directionalLightShadowMapExtension.getShadowMapId());
        for(RenderExtension extension : renderExtensions) {
            extension.renderFirstPass(renderExtract, firstPassResult);
        }

        if (Config.DEBUGDRAW_PROBES) {
            debugDrawProbes(camera);
            EnvironmentProbeFactory.getInstance().draw();
        }
        openGLContext.enable(CULL_FACE);

        GPUProfiler.start("Generate Mipmaps of colormap");
        openGLContext.activeTexture(0);
        TextureFactory.getInstance().generateMipMaps(Renderer.getInstance().getGBuffer().getColorReflectivenessMap());
        GPUProfiler.end();


        return firstPassResult;
    }

    public SecondPassResult drawSecondPass(Camera camera, DirectionalLight directionalLight, List<TubeLight> tubeLights, List<AreaLight> areaLights, CubeMap cubeMap, RenderExtract renderExtract) {
        SecondPassResult secondPassResult = new SecondPassResult();
        Vector3f camPosition = camera.getPosition();
        Vector3f.add(camPosition, (Vector3f) camera.getViewDirection().scale(-camera.getNear()), camPosition);
        Vector4f camPositionV4 = new Vector4f(camPosition.x, camPosition.y, camPosition.z, 0);

        GPUProfiler.start("Directional light");
        openGLContext.depthMask(false);
        openGLContext.enable(DEPTH_TEST);
        openGLContext.enable(BLEND);
        openGLContext.blendEquation(FUNC_ADD);
        openGLContext.blendFunc(ONE, ONE);

        GBuffer gBuffer = Renderer.getInstance().getGBuffer();
        gBuffer.getLightAccumulationBuffer().use(true);
//		laBuffer.resizeTextures();
        GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL30.GL_RENDERBUFFER, gBuffer.getDepthBufferTexture());
        OpenGLContext.getInstance().clearColor(0, 0, 0, 0);
        OpenGLContext.getInstance().clearColorBuffer();

        GPUProfiler.start("Activate GBuffer textures");
        openGLContext.bindTexture(0, TEXTURE_2D, gBuffer.getPositionMap());
        openGLContext.bindTexture(1, TEXTURE_2D, gBuffer.getNormalMap());
        openGLContext.bindTexture(2, TEXTURE_2D, gBuffer.getColorReflectivenessMap());
        openGLContext.bindTexture(3, TEXTURE_2D, gBuffer.getMotionMap());
        openGLContext.bindTexture(4, TEXTURE_CUBE_MAP, cubeMap.getTextureID());
        openGLContext.bindTexture(6, TEXTURE_2D, directionalLightShadowMapExtension.getShadowMapId());
        openGLContext.bindTexture(7, TEXTURE_2D, gBuffer.getVisibilityMap());
//		GL13.glActiveTexture(GL13.GL_TEXTURE0 + 7);
//		GL11.glBindTexture(GL11.GL_TEXTURE_2D, directionalLight.getShadowMapWorldPositionId()); // world position
//		GL13.glActiveTexture(GL13.GL_TEXTURE0 + 8);
//		GL11.glBindTexture(GL11.GL_TEXTURE_2D, getVisibilityMap());
        openGLContext.bindTexture(8, TEXTURE_CUBE_MAP_ARRAY, EnvironmentProbeFactory.getInstance().getEnvironmentMapsArray(3).getTextureID());

        GPUProfiler.end();

        secondPassDirectionalProgram.use();
        secondPassDirectionalProgram.setUniform("eyePosition", camera.getWorldPosition());
        secondPassDirectionalProgram.setUniform("ambientOcclusionRadius", Config.AMBIENTOCCLUSION_RADIUS);
        secondPassDirectionalProgram.setUniform("ambientOcclusionTotalStrength", Config.AMBIENTOCCLUSION_TOTAL_STRENGTH);
        secondPassDirectionalProgram.setUniform("screenWidth", (float) Config.WIDTH);
        secondPassDirectionalProgram.setUniform("screenHeight", (float) Config.HEIGHT);
        secondPassDirectionalProgram.setUniform("secondPassScale", GBuffer.SECONDPASSSCALE);
        FloatBuffer viewMatrix = camera.getViewMatrixAsBuffer();
        secondPassDirectionalProgram.setUniformAsMatrix4("viewMatrix", viewMatrix);
        FloatBuffer projectionMatrix = camera.getProjectionMatrixAsBuffer();
        secondPassDirectionalProgram.setUniformAsMatrix4("projectionMatrix", projectionMatrix);
        secondPassDirectionalProgram.setUniformAsMatrix4("shadowMatrix", directionalLight.getViewProjectionMatrixAsBuffer());
        secondPassDirectionalProgram.setUniform("lightDirection", directionalLight.getCamera().getViewDirection());
//        System.out.println("Light View Direction: " + directionalLight.getViewDirection());
//        System.out.println("Cam View Direction: " + directionalLight.getCamera().getViewDirection());
        secondPassDirectionalProgram.setUniform("lightDiffuse", directionalLight.getColor());
        EnvironmentProbeFactory.getInstance().bindEnvironmentProbePositions(secondPassDirectionalProgram);
        GPUProfiler.start("Draw fullscreen buffer");
        Renderer.getInstance().getFullscreenBuffer().draw();
        GPUProfiler.end();

        GPUProfiler.end();

        doTubeLights(tubeLights, camPositionV4, viewMatrix, projectionMatrix);

        doAreaLights(areaLights, viewMatrix, projectionMatrix);

        doPointLights(viewMatrix, projectionMatrix);

        for(RenderExtension extension : renderExtensions) {
            extension.renderSecondPassFullScreen(renderExtract, secondPassResult);
        }

        OpenGLContext.getInstance().disable(BLEND);

        gBuffer.getLightAccumulationBuffer().unuse();

        renderAOAndScattering(camera, viewMatrix, projectionMatrix, directionalLight);

        GPUProfiler.start("MipMap generation AO and light buffer");
        OpenGLContext.getInstance().activeTexture(0);
        TextureFactory.getInstance().generateMipMaps(gBuffer.getLightAccumulationMapOneId());
        TextureFactory.getInstance().generateMipMaps(gBuffer.getAmbientOcclusionMapId());
        GPUProfiler.end();

        if (Config.USE_GI) {
            GL11.glDepthMask(false);
            OpenGLContext.getInstance().disable(DEPTH_TEST);
            OpenGLContext.getInstance().disable(BLEND);
            OpenGLContext.getInstance().cullFace(BACK);
            renderReflections(viewMatrix, projectionMatrix);
        } else {
            gBuffer.getReflectionBuffer().use(true);
            gBuffer.getReflectionBuffer().unuse();
        }

        for(RenderExtension extension: renderExtensions) {
            extension.renderSecondPassFullScreen(renderExtract, secondPassResult);
        }

        GPUProfiler.start("Blurring");
        Renderer.getInstance().blur2DTexture(gBuffer.getHalfScreenBuffer().getRenderedTexture(), 0, Config.WIDTH / 2, Config.HEIGHT / 2, GL30.GL_RGBA16F, false, 1);
//		renderer.blur2DTexture(gBuffer.getLightAccumulationMapOneId(), 0, Config.WIDTH, Config.HEIGHT, GL30.GL_RGBA16F, false, 1);
//		renderer.blur2DTexture(getLightAccumulationMapOneId(), 0, (int)(Config.WIDTH*SECONDPASSSCALE), (int)(Config.HEIGHT*SECONDPASSSCALE), GL30.GL_RGBA16F, false, 1);
//		renderer.blur2DTexture(getAmbientOcclusionMapId(), (int)(renderer.WIDTH*SECONDPASSSCALE), (int)(renderer.HEIGHT*SECONDPASSSCALE), GL30.GL_RGBA16F, false, 1);
//		renderer.blur2DTextureBilateral(getLightAccumulationMapOneId(), 0, (int)(renderer.WIDTH*SECONDPASSSCALE), (int)(renderer.HEIGHT*SECONDPASSSCALE), GL30.GL_RGBA16F, false, 1);

//		renderer.blur2DTexture(halfScreenTarget.getRenderedTexture(0), (int)(renderer.WIDTH*0.5), (int)(renderer.HEIGHT*0.5), GL11.GL_RGBA8, false, 1);
//		renderer.blur2DTexture(halfScreenTarget.getRenderedTexture(1), (int)(renderer.WIDTH*0.5), (int)(renderer.HEIGHT*0.5), GL11.GL_RGBA8, false, 1);

        OpenGLContext.getInstance().cullFace(BACK);
        OpenGLContext.getInstance().depthFunc(LESS);
        GPUProfiler.end();

        return secondPassResult;
    }

    private void doPointLights(FloatBuffer viewMatrix, FloatBuffer projectionMatrix) {
        if (AppContext.getInstance().getScene().getPointLights().isEmpty()) {
            return;
        }
        GPUProfiler.start("Seconds pass PointLights");
        openGLContext.bindTexture(0, TEXTURE_2D, Renderer.getInstance().getGBuffer().getPositionMap());
        openGLContext.bindTexture(1, TEXTURE_2D, Renderer.getInstance().getGBuffer().getNormalMap());
        openGLContext.bindTexture(2, TEXTURE_2D, Renderer.getInstance().getGBuffer().getColorReflectivenessMap());
        openGLContext.bindTexture(3, TEXTURE_2D, Renderer.getInstance().getGBuffer().getMotionMap());
        openGLContext.bindTexture(4, TEXTURE_2D, Renderer.getInstance().getGBuffer().getLightAccumulationMapOneId());
        openGLContext.bindTexture(5, TEXTURE_2D, Renderer.getInstance().getGBuffer().getVisibilityMap());
        if (Config.USE_DPSM) {
            openGLContext.bindTexture(6, TEXTURE_2D_ARRAY, LightFactory.getInstance().getPointLightDepthMapsArrayFront());
            openGLContext.bindTexture(7, TEXTURE_2D_ARRAY, LightFactory.getInstance().getPointLightDepthMapsArrayBack());
        } else {
            openGLContext.bindTexture(8, TEXTURE_CUBE_MAP_ARRAY, LightFactory.getInstance().getPointLightDepthMapsArrayCube());
        }
        // TODO: Add glbindimagetexture to openglcontext class
        GL42.glBindImageTexture(4, Renderer.getInstance().getGBuffer().getLightAccumulationMapOneId(), 0, false, 0, GL15.GL_READ_WRITE, GL30.GL_RGBA16F);
        secondPassPointComputeProgram.use();
        secondPassPointComputeProgram.setUniform("screenWidth", (float) Config.WIDTH);
        secondPassPointComputeProgram.setUniform("screenHeight", (float) Config.HEIGHT);
        secondPassPointComputeProgram.setUniformAsMatrix4("viewMatrix", viewMatrix);
        secondPassPointComputeProgram.setUniformAsMatrix4("projectionMatrix", projectionMatrix);
        secondPassPointComputeProgram.setUniform("maxPointLightShadowmaps", LightFactory.MAX_POINTLIGHT_SHADOWMAPS);
        secondPassPointComputeProgram.bindShaderStorageBuffer(1, MaterialFactory.getInstance().getMaterialBuffer());
        secondPassPointComputeProgram.bindShaderStorageBuffer(2, LightFactory.getInstance().getLightBuffer());
        secondPassPointComputeProgram.dispatchCompute(Config.WIDTH / 16, Config.HEIGHT / 16, 1);
        GPUProfiler.end();
    }

    private void doTubeLights(List<TubeLight> tubeLights,
                              Vector4f camPositionV4, FloatBuffer viewMatrix,
                              FloatBuffer projectionMatrix) {


        if (tubeLights.isEmpty()) {
            return;
        }

        secondPassTubeProgram.use();
        secondPassTubeProgram.setUniform("screenWidth", (float) Config.WIDTH);
        secondPassTubeProgram.setUniform("screenHeight", (float) Config.HEIGHT);
        secondPassTubeProgram.setUniform("secondPassScale", GBuffer.SECONDPASSSCALE);
        secondPassTubeProgram.setUniformAsMatrix4("viewMatrix", viewMatrix);
        secondPassTubeProgram.setUniformAsMatrix4("projectionMatrix", projectionMatrix);
        for (TubeLight tubeLight : tubeLights) {
            boolean camInsideLightVolume = new AABB(tubeLight.getPosition(), tubeLight.getScale().x, tubeLight.getScale().y, tubeLight.getScale().z).contains(camPositionV4);
            if (camInsideLightVolume) {
                GL11.glCullFace(GL11.GL_FRONT);
                GL11.glDepthFunc(GL11.GL_GEQUAL);
            } else {
                GL11.glCullFace(GL11.GL_BACK);
                GL11.glDepthFunc(GL11.GL_LEQUAL);
            }
//			System.out.println("START " + tubeLight.getStart());
//			System.out.println("AT " + tubeLight.getPosition());
//			System.out.println("END " + tubeLight.getEnd());
//			System.out.println("RADIUS " + tubeLight.getRadius());
//			System.out.println("SCALE " + tubeLight.getScale());
            secondPassTubeProgram.setUniform("lightPosition", tubeLight.getPosition());
            secondPassTubeProgram.setUniform("lightStart", tubeLight.getStart());
            secondPassTubeProgram.setUniform("lightEnd", tubeLight.getEnd());
            secondPassTubeProgram.setUniform("lightOuterLeft", tubeLight.getOuterLeft());
            secondPassTubeProgram.setUniform("lightOuterRight", tubeLight.getOuterRight());
            secondPassTubeProgram.setUniform("lightRadius", tubeLight.getRadius());
            secondPassTubeProgram.setUniform("lightLength", tubeLight.getLength());
            secondPassTubeProgram.setUniform("lightDiffuse", tubeLight.getColor());
            tubeLight.draw(secondPassTubeProgram);
        }
    }

    private void doAreaLights(List<AreaLight> areaLights, FloatBuffer viewMatrix, FloatBuffer projectionMatrix) {

        OpenGLContext.getInstance().disable(CULL_FACE);
        OpenGLContext.getInstance().disable(DEPTH_TEST);
        if (areaLights.isEmpty()) {
            return;
        }

        GPUProfiler.start("Area lights: " + areaLights.size());

        secondPassAreaProgram.use();
        secondPassAreaProgram.setUniform("screenWidth", (float) Config.WIDTH);
        secondPassAreaProgram.setUniform("screenHeight", (float) Config.HEIGHT);
        secondPassAreaProgram.setUniform("secondPassScale", GBuffer.SECONDPASSSCALE);
        secondPassAreaProgram.setUniformAsMatrix4("viewMatrix", viewMatrix);
        secondPassAreaProgram.setUniformAsMatrix4("projectionMatrix", projectionMatrix);
        for (AreaLight areaLight : areaLights) {
//			boolean camInsideLightVolume = new AABB(areaLight.getPosition(), 2*areaLight.getScale().x, 2*areaLight.getScale().y, 2*areaLight.getScale().z).contains(camPositionV4);
//			if (camInsideLightVolume) {
//				GL11.glCullFace(GL11.GL_FRONT);
//				GL11.glDepthFunc(GL11.GL_GEQUAL);
//			} else {
//				GL11.glCullFace(GL11.GL_BACK);
//				GL11.glDepthFunc(GL11.GL_LEQUAL);
//			}
            secondPassAreaProgram.setUniform("lightPosition", areaLight.getPosition());
            secondPassAreaProgram.setUniform("lightRightDirection", areaLight.getRightDirection());
            secondPassAreaProgram.setUniform("lightViewDirection", areaLight.getViewDirection());
            secondPassAreaProgram.setUniform("lightUpDirection", areaLight.getUpDirection());
            secondPassAreaProgram.setUniform("lightWidth", areaLight.getWidth());
            secondPassAreaProgram.setUniform("lightHeight", areaLight.getHeight());
            secondPassAreaProgram.setUniform("lightRange", areaLight.getRange());
            secondPassAreaProgram.setUniform("lightDiffuse", areaLight.getColor());
            secondPassAreaProgram.setUniformAsMatrix4("shadowMatrix", areaLight.getViewProjectionMatrixAsBuffer());

            // TODO: Add textures to arealights
//			try {
//				GL13.glActiveTexture(GL13.GL_TEXTURE0 + 8);
//				Texture lightTexture = renderer.getTextureFactory().getTexture("brick.hptexture");
//				GL11.glBindTexture(GL11.GL_TEXTURE_2D, lightTexture.getTextureID());
//			} catch (IOException e) {
//				e.printStackTrace();
//			}
            OpenGLContext.getInstance().bindTexture(9, GlTextureTarget.TEXTURE_2D, LightFactory.getInstance().getDepthMapForAreaLight(areaLight));
            Renderer.getInstance().getFullscreenBuffer().draw();
//			areaLight.getVertexBuffer().drawDebug();
        }

        GPUProfiler.end();
    }

    private void renderAOAndScattering(Entity cameraEntity, FloatBuffer viewMatrix, FloatBuffer projectionMatrix, DirectionalLight directionalLight) {
        if (!Config.useAmbientOcclusion && !Config.SCATTERING) {
            return;
        }
        GBuffer gBuffer = Renderer.getInstance().getGBuffer();
        GPUProfiler.start("Scattering and AO");
        OpenGLContext.getInstance().disable(DEPTH_TEST);
        OpenGLContext.getInstance().bindTexture(0, TEXTURE_2D, gBuffer.getPositionMap());
        OpenGLContext.getInstance().bindTexture(1, TEXTURE_2D, gBuffer.getNormalMap());
        OpenGLContext.getInstance().bindTexture(2, TEXTURE_2D, gBuffer.getColorReflectivenessMap());
        OpenGLContext.getInstance().bindTexture(3, TEXTURE_2D, gBuffer.getMotionMap());
        OpenGLContext.getInstance().bindTexture(6, TEXTURE_2D, directionalLightShadowMapExtension.getShadowMapId());
        EnvironmentProbeFactory.getInstance().getEnvironmentMapsArray(3).bind(8);

        gBuffer.getHalfScreenBuffer().use(true);
//		halfScreenBuffer.setTargetTexture(halfScreenBuffer.getRenderedTexture(), 0);
        aoScatteringProgram.use();
        aoScatteringProgram.setUniform("eyePosition", cameraEntity.getPosition());
        aoScatteringProgram.setUniform("useAmbientOcclusion", Config.useAmbientOcclusion);
        aoScatteringProgram.setUniform("ambientOcclusionRadius", Config.AMBIENTOCCLUSION_RADIUS);
        aoScatteringProgram.setUniform("ambientOcclusionTotalStrength", Config.AMBIENTOCCLUSION_TOTAL_STRENGTH);
        aoScatteringProgram.setUniform("screenWidth", (float) Config.WIDTH / 2);
        aoScatteringProgram.setUniform("screenHeight", (float) Config.HEIGHT / 2);
        aoScatteringProgram.setUniformAsMatrix4("viewMatrix", viewMatrix);
        aoScatteringProgram.setUniformAsMatrix4("projectionMatrix", projectionMatrix);
        aoScatteringProgram.setUniformAsMatrix4("shadowMatrix", directionalLight.getViewProjectionMatrixAsBuffer());
        aoScatteringProgram.setUniform("lightDirection", directionalLight.getViewDirection());
        aoScatteringProgram.setUniform("lightDiffuse", directionalLight.getColor());
        aoScatteringProgram.setUniform("scatterFactor", directionalLight.getScatterFactor());
//        aoScatteringProgram.setUniform("sceneScale", Renderer.getInstance().getGBuffer().sceneScale);
//        aoScatteringProgram.setUniform("inverseSceneScale", 1f/Renderer.getInstance().getGBuffer().sceneScale);
//        aoScatteringProgram.setUniform("gridSize",Renderer.getInstance().getGBuffer().gridSize);

        EnvironmentProbeFactory.getInstance().bindEnvironmentProbePositions(aoScatteringProgram);
        Renderer.getInstance().getFullscreenBuffer().draw();
        OpenGLContext.getInstance().enable(DEPTH_TEST);
        TextureFactory.getInstance().generateMipMaps(gBuffer.getHalfScreenBuffer().getRenderedTexture());
        GPUProfiler.end();
    }

    private void renderReflections(FloatBuffer viewMatrix, FloatBuffer projectionMatrix) {
        GPUProfiler.start("Reflections and AO");
        GBuffer gBuffer = Renderer.getInstance().getGBuffer();
        RenderTarget reflectionBuffer = gBuffer.getReflectionBuffer();

        OpenGLContext.getInstance().bindTexture(0, TEXTURE_2D, gBuffer.getPositionMap());
        OpenGLContext.getInstance().bindTexture(1, TEXTURE_2D, gBuffer.getNormalMap());
        OpenGLContext.getInstance().bindTexture(2, TEXTURE_2D, gBuffer.getColorReflectivenessMap());
        OpenGLContext.getInstance().bindTexture(3, TEXTURE_2D, gBuffer.getMotionMap());
        OpenGLContext.getInstance().bindTexture(4, TEXTURE_2D, gBuffer.getLightAccumulationMapOneId());
        OpenGLContext.getInstance().bindTexture(5, TEXTURE_2D, gBuffer.getFinalMap());
        Renderer.getInstance().getEnvironmentMap().bind(6);
//        GL13.glActiveTexture(GL13.GL_TEXTURE0 + 7);
//        reflectionBuffer.getRenderedTexture(0);
        EnvironmentProbeFactory.getInstance().getEnvironmentMapsArray(3).bind(8);
        Renderer.getInstance().getEnvironmentMap().bind(9);
        EnvironmentProbeFactory.getInstance().getEnvironmentMapsArray(0).bind(10);
        OpenGLContext.getInstance().bindTexture(11, TEXTURE_2D, reflectionBuffer.getRenderedTexture());

        int copyTextureId = GL11.glGenTextures();
        OpenGLContext.getInstance().bindTexture(11, TEXTURE_2D, copyTextureId);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RGBA16F, reflectionBuffer.getWidth(), reflectionBuffer.getHeight(), 0, GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE, (FloatBuffer) null);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL43.glCopyImageSubData(reflectionBuffer.getRenderedTexture(), GL11.GL_TEXTURE_2D, 0, 0, 0, 0,
                copyTextureId, GL11.GL_TEXTURE_2D, 0, 0, 0, 0,
                reflectionBuffer.getWidth(), reflectionBuffer.getHeight(), 1);
        OpenGLContext.getInstance().bindTexture(11, TEXTURE_2D, copyTextureId);

        if (!USE_COMPUTESHADER_FOR_REFLECTIONS) {
            reflectionBuffer.use(true);
            reflectionProgram.use();
            reflectionProgram.setUniform("N", IMPORTANCE_SAMPLE_COUNT);
            reflectionProgram.setUniform("useAmbientOcclusion", Config.useAmbientOcclusion);
            reflectionProgram.setUniform("useSSR", Config.useSSR);
            reflectionProgram.setUniform("screenWidth", (float) Config.WIDTH);
            reflectionProgram.setUniform("screenHeight", (float) Config.HEIGHT);
            reflectionProgram.setUniformAsMatrix4("viewMatrix", viewMatrix);
            reflectionProgram.setUniformAsMatrix4("projectionMatrix", projectionMatrix);
            EnvironmentProbeFactory.getInstance().bindEnvironmentProbePositions(reflectionProgram);
            reflectionProgram.setUniform("activeProbeCount", EnvironmentProbeFactory.getInstance().getProbes().size());
            reflectionProgram.bindShaderStorageBuffer(0, gBuffer.getStorageBuffer());
            Renderer.getInstance().getFullscreenBuffer().draw();
            reflectionBuffer.unuse();
        } else {
            GL42.glBindImageTexture(6, reflectionBuffer.getRenderedTexture(0), 0, false, 0, GL15.GL_READ_WRITE, GL30.GL_RGBA16F);
            tiledProbeLightingProgram.use();
            tiledProbeLightingProgram.setUniform("N", IMPORTANCE_SAMPLE_COUNT);
            tiledProbeLightingProgram.setUniform("useAmbientOcclusion", Config.useAmbientOcclusion);
            tiledProbeLightingProgram.setUniform("useSSR", Config.useSSR);
            tiledProbeLightingProgram.setUniform("screenWidth", (float) Config.WIDTH);
            tiledProbeLightingProgram.setUniform("screenHeight", (float) Config.HEIGHT);
            tiledProbeLightingProgram.setUniformAsMatrix4("viewMatrix", viewMatrix);
            tiledProbeLightingProgram.setUniformAsMatrix4("projectionMatrix", projectionMatrix);
            tiledProbeLightingProgram.setUniform("activeProbeCount", EnvironmentProbeFactory.getInstance().getProbes().size());
            EnvironmentProbeFactory.getInstance().bindEnvironmentProbePositions(tiledProbeLightingProgram);
            tiledProbeLightingProgram.dispatchCompute(reflectionBuffer.getWidth() / 16, reflectionBuffer.getHeight() / 16, 1); //16+1
            //		GL42.glMemoryBarrier(GL42.GL_ALL_BARRIER_BITS);
        }

        GL11.glDeleteTextures(copyTextureId);
        GPUProfiler.end();
    }


    public void combinePass(RenderTarget target, Camera camera) {
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

        finalBuffer.use(true);
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

        if (target == null) {
            OpenGLContext.getInstance().bindFrameBuffer(0);
        } else {
            target.use(true);
        }

        GPUProfiler.start("Post processing");
        postProcessProgram.use();
        OpenGLContext.getInstance().bindTexture(0, TEXTURE_2D, finalBuffer.getRenderedTexture(0));
        postProcessProgram.setUniform("worldExposure", Config.EXPOSURE);
        postProcessProgram.setUniform("AUTO_EXPOSURE_ENABLED", Config.AUTO_EXPOSURE_ENABLED);
        postProcessProgram.setUniform("usePostProcessing", Config.ENABLE_POSTPROCESSING);
        postProcessProgram.setUniform("cameraRightDirection", camera.getTransform().getRightDirection());
        postProcessProgram.setUniform("cameraViewDirection", camera.getTransform().getViewDirection());
        postProcessProgram.setUniform("seconds", Renderer.getInstance().getDeltaInS());
        postProcessProgram.bindShaderStorageBuffer(0, gBuffer.getStorageBuffer());
//        postProcessProgram.bindShaderStorageBuffer(1, AppContext.getInstance().getRenderer().getMaterialFactory().getMaterialBuffer());
        OpenGLContext.getInstance().bindTexture(1, TEXTURE_2D, gBuffer.getNormalMap());
        OpenGLContext.getInstance().bindTexture(2, TEXTURE_2D, gBuffer.getMotionMap());
        Renderer.getInstance().getFullscreenBuffer().draw();

        GPUProfiler.end();
    }

    private void debugDrawProbes(Camera camera) {
        Entity probeBoxEntity = Renderer.getInstance().getGBuffer().getProbeBoxEntity();

        probeFirstpassProgram.use();
        EnvironmentProbeFactory.getInstance().bindEnvironmentProbePositions(probeFirstpassProgram);
        OpenGLContext.getInstance().activeTexture(8);
        EnvironmentProbeFactory.getInstance().getEnvironmentMapsArray(3).bind();
        probeFirstpassProgram.setUniform("showContent", Config.DEBUGDRAW_PROBES_WITH_CONTENT);

        Vector3f oldMaterialColor = new Vector3f(probeBoxEntity.getComponent(ModelComponent.class).getMaterial().getDiffuse());

        for (EnvironmentProbe probe : EnvironmentProbeFactory.getInstance().getProbes()) {
            Transform transform = new Transform();
            transform.setPosition(probe.getCenter());
            transform.setScale(probe.getSize());
            Vector3f colorHelper = probe.getDebugColor();
            probeBoxEntity.getComponent(ModelComponent.class).getMaterial().setDiffuse(colorHelper);
            probeBoxEntity.setTransform(transform);
            probeBoxEntity.update(0);
            probeFirstpassProgram.setUniform("probeCenter", probe.getCenter());
            probeFirstpassProgram.setUniform("probeIndex", probe.getIndex());
            probeBoxEntity.getComponent(ModelComponent.class).
                    draw(camera, probeBoxEntity.getModelMatrixAsBuffer(), -1);
        }

        probeBoxEntity.getComponent(ModelComponent.class).getMaterial().getDiffuse().x = oldMaterialColor.x;
        probeBoxEntity.getComponent(ModelComponent.class).getMaterial().getDiffuse().y = oldMaterialColor.y;
        probeBoxEntity.getComponent(ModelComponent.class).getMaterial().getDiffuse().z = oldMaterialColor.z;

    }

    public void registerRenderExtension(RenderExtension extension) {
        renderExtensions.add(extension);
    }
}
