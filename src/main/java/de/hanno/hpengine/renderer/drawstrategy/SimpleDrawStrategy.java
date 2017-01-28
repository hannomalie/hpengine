package de.hanno.hpengine.renderer.drawstrategy;

import de.hanno.hpengine.camera.Camera;
import de.hanno.hpengine.config.Config;
import de.hanno.hpengine.container.EntitiesContainer;
import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.PerEntityInfo;
import de.hanno.hpengine.engine.model.QuadVertexBuffer;
import de.hanno.hpengine.renderer.*;
import org.lwjgl.opengl.*;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;
import de.hanno.hpengine.renderer.constants.GlCap;
import de.hanno.hpengine.renderer.constants.GlTextureTarget;
import de.hanno.hpengine.renderer.drawstrategy.extensions.*;
import de.hanno.hpengine.renderer.light.AreaLight;
import de.hanno.hpengine.renderer.light.DirectionalLight;
import de.hanno.hpengine.renderer.light.LightFactory;
import de.hanno.hpengine.renderer.light.TubeLight;
import de.hanno.hpengine.renderer.rendertarget.RenderTarget;
import de.hanno.hpengine.scene.AABB;
import de.hanno.hpengine.scene.EnvironmentProbeFactory;
import de.hanno.hpengine.scene.LightmapManager;
import de.hanno.hpengine.shader.ComputeShaderProgram;
import de.hanno.hpengine.shader.Program;
import de.hanno.hpengine.shader.ProgramFactory;
import de.hanno.hpengine.texture.TextureFactory;
import de.hanno.hpengine.util.stopwatch.GPUProfiler;

import java.nio.FloatBuffer;
import java.util.*;

import static de.hanno.hpengine.renderer.constants.BlendMode.FUNC_ADD;
import static de.hanno.hpengine.renderer.constants.BlendMode.Factor.ONE;
import static de.hanno.hpengine.renderer.constants.CullMode.BACK;
import static de.hanno.hpengine.renderer.constants.GlCap.*;
import static de.hanno.hpengine.renderer.constants.GlDepthFunc.LESS;
import static de.hanno.hpengine.renderer.constants.GlTextureTarget.*;

public class SimpleDrawStrategy extends BaseDrawStrategy {
    public static final boolean INDIRECT_DRAWING = true;
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
    private final DrawLightMapExtension lightMapExtension;
    private FirstPassResult firstPassResult = new FirstPassResult();
    private Pipeline pipeline;

    public SimpleDrawStrategy() throws Exception {
        super();
        ProgramFactory programFactory = ProgramFactory.getInstance();
        secondPassPointProgram = programFactory.getProgram("second_pass_point_vertex.glsl", "second_pass_point_fragment.glsl", false);
        secondPassTubeProgram = programFactory.getProgram("second_pass_point_vertex.glsl", "second_pass_tube_fragment.glsl", false);
        secondPassAreaProgram = programFactory.getProgram("second_pass_area_vertex.glsl", "second_pass_area_fragment.glsl", false);
        secondPassDirectionalProgram = programFactory.getProgram("second_pass_directional_vertex.glsl", "second_pass_directional_fragment.glsl", false);
        instantRadiosityProgram = programFactory.getProgram("second_pass_area_vertex.glsl", "second_pass_instant_radiosity_fragment.glsl", false);

        secondPassPointComputeProgram = programFactory.getComputeProgram("second_pass_point_compute.glsl");

        combineProgram = programFactory.getProgram("combine_pass_vertex.glsl", "combine_pass_fragment.glsl", false);
        postProcessProgram = programFactory.getProgram("passthrough_vertex.glsl", "postprocess_fragment.glsl", false);

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
//        registerRenderExtension(new VoxelConeTracingExtension());
        registerRenderExtension(new PixelPerfectPickingExtension());
        lightMapExtension = new DrawLightMapExtension();
        pipeline = new Pipeline();
    }

    @Override
    public DrawResult draw(RenderTarget target, RenderState renderState) {
        SecondPassResult secondPassResult = null;
        Engine engine = Engine.getInstance();
        EntitiesContainer octree = engine.getScene().getEntitiesContainer();

        LightFactory lightFactory = LightFactory.getInstance();
        EnvironmentProbeFactory environmentProbeFactory = EnvironmentProbeFactory.getInstance();
        DirectionalLight light = renderState.directionalLight;

        GPUProfiler.start("First pass");
        FirstPassResult firstPassResult = drawFirstPass(renderState);
        GPUProfiler.end();

        environmentProbeFactory.drawAlternating(renderState.camera);
        Renderer.getInstance().executeRenderProbeCommands(renderState);
        GPUProfiler.start("Shadowmap pass");
        {
            directionalLightShadowMapExtension.renderFirstPass(renderState, firstPassResult);
        }
        lightFactory.renderAreaLightShadowMaps(renderState, octree);
        if (Config.USE_DPSM) {
            lightFactory.renderPointLightShadowMaps_dpsm(renderState, octree);
        } else {
            lightFactory.renderPointLightShadowMaps(renderState);
        }
        GPUProfiler.end();

        if (!Config.DIRECT_TEXTURE_OUTPUT) {
            GPUProfiler.start("Second pass");
            secondPassResult = drawSecondPass(renderState.camera, light, engine.getScene().getTubeLights(), engine.getScene().getAreaLights(), renderState);
            GPUProfiler.end();
            OpenGLContext.getInstance().viewPort(0, 0, Config.WIDTH, Config.HEIGHT);
            OpenGLContext.getInstance().clearDepthAndColorBuffer();
            OpenGLContext.getInstance().disable(DEPTH_TEST);
            GPUProfiler.start("Combine pass");
            combinePass(target, renderState);
            GPUProfiler.end();
        } else {
            OpenGLContext.getInstance().disable(DEPTH_TEST);
            RenderTarget.getFrontBuffer().use(true);
            Renderer.getInstance().drawToQuad(Renderer.getInstance().getGBuffer().getColorReflectivenessMap());
        }

        return new DrawResult(firstPassResult, secondPassResult);
    }

    public FirstPassResult drawFirstPass(RenderState renderState) {
        firstPassResult.reset();

        Camera camera = renderState.camera;

        GPUProfiler.start("Set GPU state");
        openGLContext.enable(CULL_FACE);
        openGLContext.depthMask(true);
        Renderer.getInstance().getGBuffer().use(true);
        openGLContext.enable(DEPTH_TEST);
        openGLContext.depthFunc(LESS);
        openGLContext.disable(GlCap.BLEND);
        GPUProfiler.end();

        FloatBuffer viewMatrixAsBuffer = camera.getViewMatrixAsBuffer();
        FloatBuffer projectionMatrixAsBuffer = camera.getProjectionMatrixAsBuffer();

        GPUProfiler.start("Draw entities");

        if (Config.DRAWSCENE_ENABLED && Engine.getInstance().getScene() != null) {
            GPUProfiler.start("Set global uniforms first pass");
            Program firstpassDefaultProgram = ProgramFactory.getInstance().getFirstpassDefaultProgram();
            firstpassDefaultProgram.use();
            firstpassDefaultProgram.bindShaderStorageBuffer(1, renderState.getMaterialBuffer());
            firstpassDefaultProgram.bindShaderStorageBuffer(3, renderState.getEntitiesBuffer());
            firstpassDefaultProgram.bindShaderStorageBuffer(4, pipeline.getEntityOffsetBuffer());
            firstpassDefaultProgram.setUniform("useRainEffect", Config.RAINEFFECT == 0.0 ? false : true);
            firstpassDefaultProgram.setUniform("rainEffect", Config.RAINEFFECT);
//            firstpassDefaultProgram.setUniform("useNormalMaps", !Config.DRAWLINES_ENABLED);
            firstpassDefaultProgram.setUniformAsMatrix4("viewMatrix", viewMatrixAsBuffer);
            firstpassDefaultProgram.setUniformAsMatrix4("lastViewMatrix", viewMatrixAsBuffer);
            firstpassDefaultProgram.setUniformAsMatrix4("projectionMatrix", projectionMatrixAsBuffer);
            firstpassDefaultProgram.setUniform("eyePosition", camera.getPosition());
            firstpassDefaultProgram.setUniform("lightDirection", renderState.directionalLight.getViewDirection());
            firstpassDefaultProgram.setUniform("near", camera.getNear());
            firstpassDefaultProgram.setUniform("far", camera.getFar());
            firstpassDefaultProgram.setUniform("time", (int) System.currentTimeMillis());
            firstpassDefaultProgram.setUniform("useParallax", Config.useParallax);
            firstpassDefaultProgram.setUniform("useSteepParallax", Config.useSteepParallax);
            firstpassDefaultProgram.setUniform("lightmapWidth", LightmapManager.getInstance().getWidth());
            firstpassDefaultProgram.setUniform("lightmapHeight", LightmapManager.getInstance().getHeight());
            OpenGLContext.getInstance().bindTexture(7, TEXTURE_2D, lightMapExtension.getLightMapTarget().getRenderedTexture(4));
            GPUProfiler.end();

            GPUProfiler.start("Actual draw entities");

            if(INDIRECT_DRAWING) {
                pipeline.prepareAndDraw(renderState, firstpassDefaultProgram, firstPassResult);
                for(PerEntityInfo info : renderState.perEntityInfos()) {
                    info.getMaterial().setTexturesUsed();
                }
            } else {
                for(PerEntityInfo info : renderState.perEntityInfos()) {
                    if (!info.isVisibleForCamera()) {
                        continue;
                    }
                    int currentVerticesCount = DrawStrategy.draw(renderState, info);
                    firstPassResult.verticesDrawn += currentVerticesCount;
                    if (currentVerticesCount > 0) {
                        firstPassResult.entitiesDrawn++;
                    }
                }
            }
            GPUProfiler.end();
        }
        GPUProfiler.end();

        OpenGLContext.getInstance().bindTexture(6, TEXTURE_2D, directionalLightShadowMapExtension.getShadowMapId());
        for(RenderExtension extension : renderExtensions) {
            GPUProfiler.start("RenderExtension " + extension.getClass().getSimpleName());
            extension.renderFirstPass(renderState, firstPassResult);
            GPUProfiler.end();
        }

        GPUProfiler.start("RenderExtension lightmap firstpass");
        OpenGLContext.getInstance().bindTexture(6, TEXTURE_2D, directionalLightShadowMapExtension.getShadowMapId());
        lightMapExtension.renderFirstPass(renderState, firstPassResult);
        GPUProfiler.end();

        if (Config.DIRECT_TEXTURE_OUTPUT) {
//            debugDrawProbes(camera, renderState);
            EnvironmentProbeFactory.getInstance().draw();
        }
        openGLContext.enable(CULL_FACE);

        GPUProfiler.start("Generate Mipmaps of colormap");
        openGLContext.activeTexture(0);
        TextureFactory.getInstance().generateMipMaps(Renderer.getInstance().getGBuffer().getColorReflectivenessMap());
        GPUProfiler.end();


        return firstPassResult;
    }

    public SecondPassResult drawSecondPass(Camera camera, DirectionalLight directionalLight, List<TubeLight> tubeLights, List<AreaLight> areaLights, RenderState renderState) {
        SecondPassResult secondPassResult = new SecondPassResult();
        Vector3f camPosition = camera.getPosition();
        Vector3f.add(camPosition, (Vector3f) camera.getViewDirection().scale(-camera.getNear()), camPosition);
        Vector4f camPositionV4 = new Vector4f(camPosition.x, camPosition.y, camPosition.z, 0);

        GPUProfiler.start("Directional light");
        openGLContext.depthMask(false);
        openGLContext.disable(DEPTH_TEST);
        openGLContext.enable(BLEND);
        openGLContext.blendEquation(FUNC_ADD);
        openGLContext.blendFunc(ONE, ONE);

        GBuffer gBuffer = Renderer.getInstance().getGBuffer();
        gBuffer.getLightAccumulationBuffer().use(true);
        GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL30.GL_RENDERBUFFER, gBuffer.getDepthBufferTexture());
        OpenGLContext.getInstance().clearColor(0, 0, 0, 0);
        OpenGLContext.getInstance().clearColorBuffer();

        GPUProfiler.start("Activate GBuffer textures");
        openGLContext.bindTexture(0, TEXTURE_2D, gBuffer.getPositionMap());
        openGLContext.bindTexture(1, TEXTURE_2D, gBuffer.getNormalMap());
        openGLContext.bindTexture(2, TEXTURE_2D, gBuffer.getColorReflectivenessMap());
        openGLContext.bindTexture(3, TEXTURE_2D, gBuffer.getMotionMap());
        openGLContext.bindTexture(4, TEXTURE_CUBE_MAP, TextureFactory.getInstance().getCubeMap().getTextureID());
        openGLContext.bindTexture(6, TEXTURE_2D, directionalLightShadowMapExtension.getShadowMapId());
        openGLContext.bindTexture(7, TEXTURE_2D, gBuffer.getVisibilityMap());
        openGLContext.bindTexture(8, TEXTURE_CUBE_MAP_ARRAY, EnvironmentProbeFactory.getInstance().getEnvironmentMapsArray(3).getTextureID());
        GPUProfiler.end();

        secondPassDirectionalProgram.use();
        secondPassDirectionalProgram.setUniform("eyePosition", camera.getWorldPosition());
        secondPassDirectionalProgram.setUniform("ambientOcclusionRadius", Config.AMBIENTOCCLUSION_RADIUS);
        secondPassDirectionalProgram.setUniform("ambientOcclusionTotalStrength", Config.AMBIENTOCCLUSION_TOTAL_STRENGTH);
        secondPassDirectionalProgram.setUniform("screenWidth", (float) Config.WIDTH);
        secondPassDirectionalProgram.setUniform("screenHeight", (float) Config.HEIGHT);
        FloatBuffer viewMatrix = camera.getViewMatrixAsBuffer();
        secondPassDirectionalProgram.setUniformAsMatrix4("viewMatrix", viewMatrix);
        FloatBuffer projectionMatrix = camera.getProjectionMatrixAsBuffer();
        secondPassDirectionalProgram.setUniformAsMatrix4("projectionMatrix", projectionMatrix);
        secondPassDirectionalProgram.setUniformAsMatrix4("shadowMatrix", directionalLight.getViewProjectionMatrixAsBuffer());
        secondPassDirectionalProgram.setUniform("lightDirection", directionalLight.getCamera().getViewDirection());
        secondPassDirectionalProgram.setUniform("lightDiffuse", directionalLight.getColor());
        EnvironmentProbeFactory.getInstance().bindEnvironmentProbePositions(secondPassDirectionalProgram);
        GPUProfiler.start("Draw fullscreen buffer");
        QuadVertexBuffer.getFullscreenBuffer().draw();
        GPUProfiler.end();

        GPUProfiler.end();

        doTubeLights(tubeLights, camPositionV4, viewMatrix, projectionMatrix);

        doAreaLights(areaLights, viewMatrix, projectionMatrix);

        doPointLights(renderState, viewMatrix, projectionMatrix);

        GPUProfiler.start("Extensions");
        openGLContext.bindTexture(6, TEXTURE_2D, directionalLightShadowMapExtension.getShadowMapId());
        for(RenderExtension extension : renderExtensions) {
            extension.renderSecondPassFullScreen(renderState, secondPassResult);
        }
        GPUProfiler.end();

        lightMapExtension.renderSecondPassFullScreen(renderState, secondPassResult);

        OpenGLContext.getInstance().disable(BLEND);
        gBuffer.getLightAccumulationBuffer().unuse();

        renderAOAndScattering(renderState);

        GPUProfiler.start("MipMap generation AO and light buffer");
        OpenGLContext.getInstance().activeTexture(0);
        TextureFactory.getInstance().generateMipMaps(gBuffer.getLightAccumulationMapOneId());
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
            extension.renderSecondPassHalfScreen(renderState, secondPassResult);
        }

        GPUProfiler.start("Blurring");
//        GPUProfiler.start("LightAccumulationMap");
//        TextureFactory.getInstance().blurHorinzontal2DTextureRGBA16F(gBuffer.getLightAccumulationMapOneId(), Config.WIDTH, Config.HEIGHT, 7, 8);
//        GPUProfiler.end();
        GPUProfiler.start("Scattering de.hanno.hpengine.texture");
        if(Config.SCATTERING || Config.useAmbientOcclusion) {
            TextureFactory.getInstance().blur2DTextureRGBA16F(gBuffer.getHalfScreenBuffer().getRenderedTexture(), Config.WIDTH / 2, Config.HEIGHT / 2, 0, 0);
        }
        GPUProfiler.end();
//        TextureFactory.getInstance().blur2DTextureRGBA16F(gBuffer.getHalfScreenBuffer().getRenderedTexture(), Config.WIDTH / 2, Config.HEIGHT / 2, 0, 0);
//        Renderer.getInstance().blur2DTexture(gBuffer.getHalfScreenBuffer().getRenderedTexture(), 0, Config.WIDTH / 2, Config.HEIGHT / 2, GL30.GL_RGBA16F, false, 1);
//		renderer.blur2DTexture(gBuffer.getLightAccumulationMapOneId(), 0, Config.WIDTH, Config.HEIGHT, GL30.GL_RGBA16F, false, 1);
//		renderer.blur2DTexture(getLightAccumulationMapOneId(), 0, (int)(Config.WIDTH*SECONDPASSSCALE), (int)(Config.HEIGHT*SECONDPASSSCALE), GL30.GL_RGBA16F, false, 1);
//		Renderer.getInstance().blur2DTexture(gBuffer.getAmbientOcclusionMapId(), 0, (int)(Config.WIDTH), (int)(Config.HEIGHT), GL30.GL_RGBA16F, false, 1);
//		renderer.blur2DTextureBilateral(getLightAccumulationMapOneId(), 0, (int)(renderer.WIDTH*SECONDPASSSCALE), (int)(renderer.HEIGHT*SECONDPASSSCALE), GL30.GL_RGBA16F, false, 1);

//		renderer.blur2DTexture(halfScreenTarget.getRenderedTexture(0), (int)(renderer.WIDTH*0.5), (int)(renderer.HEIGHT*0.5), GL11.GL_RGBA8, false, 1);
//		renderer.blur2DTexture(halfScreenTarget.getRenderedTexture(1), (int)(renderer.WIDTH*0.5), (int)(renderer.HEIGHT*0.5), GL11.GL_RGBA8, false, 1);

        OpenGLContext.getInstance().cullFace(BACK);
        OpenGLContext.getInstance().depthFunc(LESS);
        GPUProfiler.end();

        return secondPassResult;
    }

    private void doPointLights(RenderState renderState, FloatBuffer viewMatrix, FloatBuffer projectionMatrix) {
        if (Engine.getInstance().getScene().getPointLights().isEmpty()) {
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
        secondPassPointComputeProgram.bindShaderStorageBuffer(1, renderState.getMaterialBuffer());
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
            QuadVertexBuffer.getFullscreenBuffer().draw();
//			areaLight.getVertexBuffer().drawDebug();
        }

        GPUProfiler.end();
    }

    private void renderAOAndScattering(RenderState renderState) {
        if (!Config.useAmbientOcclusion && !Config.SCATTERING) {
            return;
        }
        GPUProfiler.start("Scattering and AO");
        GBuffer gBuffer = Renderer.getInstance().getGBuffer();
        gBuffer.getHalfScreenBuffer().use(true);
        OpenGLContext.getInstance().disable(DEPTH_TEST);
        OpenGLContext.getInstance().bindTexture(0, TEXTURE_2D, gBuffer.getPositionMap());
        OpenGLContext.getInstance().bindTexture(1, TEXTURE_2D, gBuffer.getNormalMap());
        OpenGLContext.getInstance().bindTexture(2, TEXTURE_2D, gBuffer.getColorReflectivenessMap());
        OpenGLContext.getInstance().bindTexture(3, TEXTURE_2D, gBuffer.getMotionMap());
        OpenGLContext.getInstance().bindTexture(6, TEXTURE_2D, directionalLightShadowMapExtension.getShadowMapId());
        EnvironmentProbeFactory.getInstance().getEnvironmentMapsArray(3).bind(8);

//		halfScreenBuffer.setTargetTexture(halfScreenBuffer.getRenderedTexture(), 0);
        aoScatteringProgram.use();
        aoScatteringProgram.setUniform("eyePosition", renderState.camera.getPosition());
        aoScatteringProgram.setUniform("useAmbientOcclusion", Config.useAmbientOcclusion);
        aoScatteringProgram.setUniform("ambientOcclusionRadius", Config.AMBIENTOCCLUSION_RADIUS);
        aoScatteringProgram.setUniform("ambientOcclusionTotalStrength", Config.AMBIENTOCCLUSION_TOTAL_STRENGTH);
        aoScatteringProgram.setUniform("screenWidth", (float) Config.WIDTH / 2);
        aoScatteringProgram.setUniform("screenHeight", (float) Config.HEIGHT / 2);
        aoScatteringProgram.setUniformAsMatrix4("viewMatrix", renderState.camera.getViewMatrixAsBuffer());
        aoScatteringProgram.setUniformAsMatrix4("projectionMatrix", renderState.camera.getProjectionMatrixAsBuffer());
        aoScatteringProgram.setUniformAsMatrix4("shadowMatrix", renderState.directionalLight.getViewProjectionMatrixAsBuffer());
        aoScatteringProgram.setUniform("lightDirection", renderState.directionalLight.getViewDirection());
        aoScatteringProgram.setUniform("lightDiffuse", renderState.directionalLight.getColor());
        aoScatteringProgram.setUniform("scatterFactor", renderState.directionalLight.getScatterFactor());

        EnvironmentProbeFactory.getInstance().bindEnvironmentProbePositions(aoScatteringProgram);
        QuadVertexBuffer.getFullscreenBuffer().draw();
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
        TextureFactory.getInstance().getCubeMap().bind(6);
//        GL13.glActiveTexture(GL13.GL_TEXTURE0 + 7);
//        reflectionBuffer.getRenderedTexture(0);
        EnvironmentProbeFactory.getInstance().getEnvironmentMapsArray(3).bind(8);
        TextureFactory.getInstance().getCubeMap().bind(9);
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
            QuadVertexBuffer.getFullscreenBuffer().draw();
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


    public void combinePass(RenderTarget target, RenderState renderState) {
        GBuffer gBuffer = Renderer.getInstance().getGBuffer();
        RenderTarget finalBuffer = gBuffer.getFinalBuffer();
        TextureFactory.getInstance().generateMipMaps(finalBuffer.getRenderedTexture(0));

        combineProgram.use();
        combineProgram.setUniformAsMatrix4("projectionMatrix", renderState.camera.getProjectionMatrixAsBuffer());
        combineProgram.setUniformAsMatrix4("viewMatrix", renderState.camera.getViewMatrixAsBuffer());
        combineProgram.setUniform("screenWidth", (float) Config.WIDTH);
        combineProgram.setUniform("screenHeight", (float) Config.HEIGHT);
        combineProgram.setUniform("camPosition", renderState.camera.getPosition());
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
        TextureFactory.getInstance().getCubeMap().bind(6);
        EnvironmentProbeFactory.getInstance().getEnvironmentMapsArray().bind(7);
        OpenGLContext.getInstance().bindTexture(8, TEXTURE_2D, gBuffer.getReflectionMap());
        OpenGLContext.getInstance().bindTexture(9, TEXTURE_2D, gBuffer.getRefractedMap());
        OpenGLContext.getInstance().bindTexture(11, TEXTURE_2D, gBuffer.getAmbientOcclusionScatteringMap());
        OpenGLContext.getInstance().bindTexture(12, TEXTURE_CUBE_MAP_ARRAY, LightFactory.getInstance().getPointLightDepthMapsArrayCube());

        QuadVertexBuffer.getFullscreenBuffer().draw();

        if (target == null) {
            RenderTarget.getFrontBuffer().use(true);
        } else {
            target.use(true);
        }
        GPUProfiler.start("Post processing");
        postProcessProgram.use();
        OpenGLContext.getInstance().bindTexture(0, TEXTURE_2D, finalBuffer.getRenderedTexture(0));
        postProcessProgram.setUniform("screenWidth", (float) Config.WIDTH);
        postProcessProgram.setUniform("screenHeight", (float) Config.HEIGHT);
        postProcessProgram.setUniform("worldExposure", Config.EXPOSURE);
        postProcessProgram.setUniform("AUTO_EXPOSURE_ENABLED", Config.AUTO_EXPOSURE_ENABLED);
        postProcessProgram.setUniform("usePostProcessing", Config.ENABLE_POSTPROCESSING);
        try {
            postProcessProgram.setUniform("cameraRightDirection", renderState.camera.getTransform().getRightDirection());
            postProcessProgram.setUniform("cameraViewDirection", renderState.camera.getTransform().getViewDirection());
        } catch (IllegalStateException e) {
            // Normalizing zero length vector
        }
        postProcessProgram.setUniform("seconds", (float)Renderer.getInstance().getDeltaInS());
        postProcessProgram.bindShaderStorageBuffer(0, gBuffer.getStorageBuffer());
//        postProcessProgram.bindShaderStorageBuffer(1, Engine.getInstance().getRenderer().getMaterialFactory().getMaterialBuffer());
        OpenGLContext.getInstance().bindTexture(1, TEXTURE_2D, gBuffer.getNormalMap());
        OpenGLContext.getInstance().bindTexture(2, TEXTURE_2D, gBuffer.getMotionMap());
        OpenGLContext.getInstance().bindTexture(3, TEXTURE_2D, gBuffer.getLightAccumulationMapOneId());
        OpenGLContext.getInstance().bindTexture(4, TEXTURE_2D, TextureFactory.getInstance().getLensFlareTexture().getTextureID());
        QuadVertexBuffer.getFullscreenBuffer().draw();

        GPUProfiler.end();
    }

//    TODO: Reimplement this functionality
//    private void debugDrawProbes(Camera de.hanno.hpengine.camera, RenderState extract) {
//        Entity probeBoxEntity = Renderer.getInstance().getGBuffer().getProbeBoxEntity();
//
//        probeFirstpassProgram.use();
//        EnvironmentProbeFactory.getInstance().bindEnvironmentProbePositions(probeFirstpassProgram);
//        OpenGLContext.getInstance().activeTexture(8);
//        EnvironmentProbeFactory.getInstance().getEnvironmentMapsArray(3).bind();
//        probeFirstpassProgram.setUniform("showContent", Config.DEBUGDRAW_PROBES_WITH_CONTENT);
//
//        Vector3f oldMaterialColor = new Vector3f(probeBoxEntity.getComponent(ModelComponent.class).getMaterial().getDiffuse());
//
//        for (EnvironmentProbe probe : EnvironmentProbeFactory.getInstance().getProbes()) {
//            Transform transform = new Transform();
//            transform.setPosition(probe.getCenter());
//            transform.setScale(probe.getSize());
//            Vector3f colorHelper = probe.getDebugColor();
//            probeBoxEntity.getComponent(ModelComponent.class).getMaterial().setDiffuse(colorHelper);
//            probeBoxEntity.setTransform(transform);
//            probeBoxEntity.update(0);
//            probeFirstpassProgram.setUniform("probeCenter", probe.getCenter());
//            probeFirstpassProgram.setUniform("probeIndex", probe.getIndex());
//            probeBoxEntity.getComponent(ModelComponent.class).draw(extract, de.hanno.hpengine.camera, probeBoxEntity.getModelMatrixAsBuffer(), -1);
//        }
//
//        probeBoxEntity.getComponent(ModelComponent.class).getMaterial().getDiffuse().x = oldMaterialColor.x;
//        probeBoxEntity.getComponent(ModelComponent.class).getMaterial().getDiffuse().y = oldMaterialColor.y;
//        probeBoxEntity.getComponent(ModelComponent.class).getMaterial().getDiffuse().z = oldMaterialColor.z;
//
//    }

    public void registerRenderExtension(RenderExtension extension) {
        renderExtensions.add(extension);
    }

    public DirectionalLightShadowMapExtension getDirectionalLightExtension() {
        return directionalLightShadowMapExtension;
    }

    public DrawLightMapExtension getLightMapExtension() {
        return lightMapExtension;
    }
}
