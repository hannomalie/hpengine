package de.hanno.hpengine.engine.graphics.renderer.drawstrategy;

import de.hanno.hpengine.engine.camera.Camera;
import de.hanno.hpengine.engine.component.ModelComponent;
import de.hanno.hpengine.engine.config.Config;
import de.hanno.hpengine.engine.container.EntitiesContainer;
import de.hanno.hpengine.engine.DirectoryManager;
import de.hanno.hpengine.engine.graphics.renderer.*;
import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.graphics.renderer.pipelines.GPUCulledMainPipeline;
import de.hanno.hpengine.engine.graphics.renderer.pipelines.Pipeline;
import de.hanno.hpengine.engine.graphics.shader.define.Defines;
import de.hanno.hpengine.engine.graphics.state.multithreading.TripleBuffer;
import de.hanno.hpengine.engine.model.*;
import de.hanno.hpengine.engine.graphics.renderer.constants.GlCap;
import de.hanno.hpengine.engine.graphics.renderer.constants.GlTextureTarget;
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.extensions.*;
import de.hanno.hpengine.engine.graphics.light.AreaLight;
import de.hanno.hpengine.engine.graphics.light.LightFactory;
import de.hanno.hpengine.engine.graphics.light.TubeLight;
import de.hanno.hpengine.engine.model.material.MaterialFactory;
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.RenderTarget;
import de.hanno.hpengine.engine.graphics.state.RenderState;
import de.hanno.hpengine.engine.scene.AABB;
import de.hanno.hpengine.engine.scene.EnvironmentProbeFactory;
import de.hanno.hpengine.engine.scene.VertexIndexBuffer;
import de.hanno.hpengine.engine.scene.VertexIndexBuffer.VertexIndexOffsets;
import de.hanno.hpengine.engine.graphics.shader.ComputeShaderProgram;
import de.hanno.hpengine.engine.graphics.shader.Program;
import de.hanno.hpengine.engine.graphics.shader.ProgramFactory;
import de.hanno.hpengine.engine.graphics.shader.Shader;
import de.hanno.hpengine.engine.model.texture.TextureFactory;
import de.hanno.hpengine.util.Util;
import de.hanno.hpengine.util.stopwatch.GPUProfiler;
import org.lwjgl.opengl.*;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.io.File;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

import static de.hanno.hpengine.engine.graphics.shader.Shader.ShaderSourceFactory.getShaderSource;
import static de.hanno.hpengine.engine.model.Update.DYNAMIC;
import static de.hanno.hpengine.engine.graphics.renderer.constants.BlendMode.FUNC_ADD;
import static de.hanno.hpengine.engine.graphics.renderer.constants.BlendMode.Factor.ONE;
import static de.hanno.hpengine.engine.graphics.renderer.constants.CullMode.BACK;
import static de.hanno.hpengine.engine.graphics.renderer.constants.GlCap.*;
import static de.hanno.hpengine.engine.graphics.renderer.constants.GlDepthFunc.LESS;
import static de.hanno.hpengine.engine.graphics.renderer.constants.GlTextureTarget.*;
import static org.lwjgl.opengl.GL42.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT;
import static org.lwjgl.opengl.GL42.glMemoryBarrier;

public class SimpleDrawStrategy extends BaseDrawStrategy {
    public static volatile boolean USE_COMPUTESHADER_FOR_REFLECTIONS = false;
    public static volatile int IMPORTANCE_SAMPLE_COUNT = 8;
    private final Entity skyBoxEntity;
    private final VertexIndexBuffer skyboxVertexIndexBuffer;

    private Program depthPrePassProgram;
    private Program secondPassDirectionalProgram;
    private Program secondPassPointProgram;
    private Program secondPassTubeProgram;
    private Program secondPassAreaProgram;
    private Program combineProgram;
    private Program postProcessProgram;
    private Program instantRadiosityProgram;
    private Program aoScatteringProgram;
    private Program reflectionProgram;
    private Program linesProgram;
    private Program skyBoxProgram;
    private Program skyBoxDepthProgram;
    private Program probeFirstpassProgram;
    private ComputeShaderProgram secondPassPointComputeProgram;
    private ComputeShaderProgram tiledProbeLightingProgram;
    private ComputeShaderProgram tiledDirectLightingProgram;

    GraphicsContext graphicsContext;
    private final List<RenderExtension> renderExtensions = new ArrayList<>();
    private final DirectionalLightShadowMapExtension directionalLightShadowMapExtension;
    private TripleBuffer.PipelineRef<GPUCulledMainPipeline> mainPipelineRef;

    private final RenderBatch skyBoxRenderBatch;

    public SimpleDrawStrategy() throws Exception {
        super();
        ProgramFactory programFactory = ProgramFactory.getInstance();
        secondPassPointProgram = programFactory.getProgram(getShaderSource(new File(Shader.getDirectory() + "second_pass_point_vertex.glsl")), getShaderSource(new File(Shader.getDirectory() + "second_pass_point_fragment.glsl")), new Defines());
        secondPassTubeProgram = programFactory.getProgram(getShaderSource(new File(Shader.getDirectory() + "second_pass_point_vertex.glsl")), getShaderSource(new File(Shader.getDirectory() + "second_pass_tube_fragment.glsl")), new Defines());
        secondPassAreaProgram = programFactory.getProgram(getShaderSource(new File(Shader.getDirectory() + "second_pass_area_vertex.glsl")), getShaderSource(new File(Shader.getDirectory() + "second_pass_area_fragment.glsl")), new Defines());
        secondPassDirectionalProgram = programFactory.getProgram(getShaderSource(new File(Shader.getDirectory() + "second_pass_directional_vertex.glsl")), getShaderSource(new File(Shader.getDirectory() + "second_pass_directional_fragment.glsl")), new Defines());
        instantRadiosityProgram = programFactory.getProgram(getShaderSource(new File(Shader.getDirectory() + "second_pass_area_vertex.glsl")), getShaderSource(new File(Shader.getDirectory() + "second_pass_instant_radiosity_fragment.glsl")), new Defines());

        secondPassPointComputeProgram = programFactory.getComputeProgram("second_pass_point_compute.glsl");

        combineProgram = programFactory.getProgram(getShaderSource(new File(Shader.getDirectory() + "combine_pass_vertex.glsl")), getShaderSource(new File(Shader.getDirectory() + "combine_pass_fragment.glsl")), new Defines());
        postProcessProgram = programFactory.getProgram(getShaderSource(new File(Shader.getDirectory() + "passthrough_vertex.glsl")), getShaderSource(new File(Shader.getDirectory() + "postprocess_fragment.glsl")), new Defines());

        aoScatteringProgram = ProgramFactory.getInstance().getProgramFromFileNames("passthrough_vertex.glsl", "scattering_ao_fragment.glsl", new Defines());
        reflectionProgram = ProgramFactory.getInstance().getProgramFromFileNames("passthrough_vertex.glsl", "reflections_fragment.glsl", new Defines());
        linesProgram = ProgramFactory.getInstance().getProgramFromFileNames("mvp_vertex.glsl", "simple_color_fragment.glsl", new Defines());
        skyBoxProgram = ProgramFactory.getInstance().getProgramFromFileNames("mvp_vertex.glsl", "skybox.glsl", new Defines());
        skyBoxDepthProgram = ProgramFactory.getInstance().getProgramFromFileNames("mvp_vertex.glsl", "skybox_depth.glsl", new Defines());
        probeFirstpassProgram = ProgramFactory.getInstance().getProgramFromFileNames("first_pass_vertex.glsl", "probe_first_pass_fragment.glsl", new Defines());
        depthPrePassProgram = ProgramFactory.getInstance().getProgramFromFileNames("first_pass_vertex.glsl", "depth_prepass_fragment.glsl", new Defines());
        tiledDirectLightingProgram = ProgramFactory.getInstance().getComputeProgram("tiled_direct_lighting_compute.glsl");
        tiledProbeLightingProgram = ProgramFactory.getInstance().getComputeProgram("tiled_probe_lighting_compute.glsl");

        graphicsContext = GraphicsContext.getInstance();


        StaticModel skyBox = new OBJLoader().loadTexturedModel(new File(DirectoryManager.WORKDIR_NAME + "/assets/models/skybox.obj"));
        skyBoxEntity = EntityFactory.getInstance().getEntity(new Vector3f(), "skybox", skyBox);
        skyboxVertexIndexBuffer = new VertexIndexBuffer(10, 10, ModelComponent.DEFAULTCHANNELS);
        VertexIndexOffsets vertexIndexOffsets = skyBoxEntity.getComponent(ModelComponent.class, ModelComponent.COMPONENT_KEY).putToBuffer(skyboxVertexIndexBuffer, ModelComponent.DEFAULTCHANNELS);
        skyBoxRenderBatch = new RenderBatch().init(skyBoxProgram, 0, true, false, false, new Vector3f(0,0,0), true, 1, true, DYNAMIC, new Vector3f(0,0,0), new Vector3f(0,0,0), new Vector3f(), 1000, skyBox.getIndices().length, vertexIndexOffsets.indexOffset, vertexIndexOffsets.vertexOffset, false, skyBoxEntity.getInstanceMinMaxWorlds());

        directionalLightShadowMapExtension = new DirectionalLightShadowMapExtension();

        registerRenderExtension(new DrawLinesExtension());
//        registerRenderExtension(new VoxelConeTracingExtension());
        registerRenderExtension(new PixelPerfectPickingExtension());
    }

    @Override
    public void draw(DrawResult result, RenderTarget target, RenderState renderState) {
        EntitiesContainer entitiesContainer = Engine.getInstance().getSceneManager().getScene().getEntitiesContainer();

        GPUProfiler.start("First pass");
        drawFirstPass(result.getFirstPassResult(), renderState);
        GPUProfiler.end();

        EnvironmentProbeFactory.getInstance().drawAlternating(renderState.camera);
        Renderer.getInstance().executeRenderProbeCommands(renderState);

        if (!Config.getInstance().isUseDirectTextureOutput()) {
            GPUProfiler.start("Shadowmap pass");
            directionalLightShadowMapExtension.renderFirstPass(result.getFirstPassResult(), renderState);
            LightFactory.getInstance().renderAreaLightShadowMaps(renderState, entitiesContainer);
            if (Config.getInstance().isUseDpsm()) {
                LightFactory.getInstance().renderPointLightShadowMaps_dpsm(renderState, entitiesContainer);
            } else {
                LightFactory.getInstance().renderPointLightShadowMaps(renderState);
            }
            GPUProfiler.end();

            GPUProfiler.start("Second pass");
            drawSecondPass(result.getSecondPassResult(), renderState.camera, Engine.getInstance().getSceneManager().getScene().getTubeLights(), Engine.getInstance().getSceneManager().getScene().getAreaLights(), renderState);
            GPUProfiler.end();
            GPUProfiler.start("Combine pass");
            combinePass(target, renderState);
            GPUProfiler.end();
        } else {
            GraphicsContext.getInstance().disable(DEPTH_TEST);
            RenderTarget.getFrontBuffer().use(true);
            Renderer.getInstance().drawToQuad(Config.getInstance().getDirectTextureOutputTextureIndex());
        }
    }

    public static void renderHighZMap(int baseDepthTexture, int baseWidth, int baseHeight, int highZTexture, ComputeShaderProgram highZProgram) {
        GPUProfiler.start("HighZ map calculation");
        highZProgram.use();
        int lastWidth = baseWidth;
        int lastHeight = baseHeight;
        int currentWidth = lastWidth /2;
        int currentHeight = lastHeight/2;
        int mipMapCount = Util.calculateMipMapCount(currentWidth, currentHeight);
        for(int mipmapTarget = 0; mipmapTarget < mipMapCount; mipmapTarget++ ) {
            highZProgram.setUniform("width", currentWidth);
            highZProgram.setUniform("height", currentHeight);
            highZProgram.setUniform("lastWidth", lastWidth);
            highZProgram.setUniform("lastHeight", lastHeight);
            highZProgram.setUniform("mipmapTarget", mipmapTarget);
            if(mipmapTarget == 0) {
                GraphicsContext.getInstance().bindTexture(0, TEXTURE_2D, baseDepthTexture);
            } else {
                GraphicsContext.getInstance().bindTexture(0, TEXTURE_2D, highZTexture);
            }
            GraphicsContext.getInstance().bindImageTexture(1, highZTexture, mipmapTarget, false, 0, GL15.GL_READ_WRITE, Pipeline.Companion.getHIGHZ_FORMAT());
            int num_groups_x = Math.max(1, (currentWidth + 7) / 8);
            int num_groups_y = Math.max(1, (currentHeight + 7) / 8);
            highZProgram.dispatchCompute(num_groups_x, num_groups_y, 1);
            lastWidth = currentWidth;
            lastHeight = currentHeight;
            currentWidth /= 2;
            currentHeight /= 2;
            glMemoryBarrier(GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);
//            glMemoryBarrier(GL_ALL_BARRIER_BITS);
        }
        GPUProfiler.end();
    }

    public FirstPassResult drawFirstPass(FirstPassResult firstPassResult, RenderState renderState) {

        GPUCulledMainPipeline pipeline = renderState.get(mainPipelineRef);

        Camera camera = renderState.camera;

        graphicsContext.depthMask(true);
        Renderer.getInstance().getGBuffer().use(true);

        renderSkyBox(renderState, camera, false, skyBoxProgram);

        GPUProfiler.start("Set GPU state");
        graphicsContext.enable(CULL_FACE);
        graphicsContext.depthMask(true);
        graphicsContext.enable(DEPTH_TEST);
        graphicsContext.depthFunc(LESS);
        graphicsContext.disable(GlCap.BLEND);
        GPUProfiler.end();

        GPUProfiler.start("Draw entities");

        if (Config.getInstance().isDrawScene()) {
            Program firstpassProgram = ProgramFactory.getInstance().getFirstpassDefaultProgram();
            Program firstpassProgramAnimated = ProgramFactory.getInstance().getFirstpassAnimatedDefaultProgram();
            pipeline.draw(renderState, firstpassProgram, firstpassProgramAnimated, firstPassResult);
        }
        GPUProfiler.end();

        if(!Config.getInstance().isUseDirectTextureOutput()) {
            GraphicsContext.getInstance().bindTexture(6, TEXTURE_2D, directionalLightShadowMapExtension.getShadowMapId());
            for(RenderExtension extension : renderExtensions) {
                GPUProfiler.start("RenderExtension " + extension.getClass().getSimpleName());
                extension.renderFirstPass(firstPassResult, renderState);
                GPUProfiler.end();
            }
        }

        graphicsContext.enable(CULL_FACE);

        GPUProfiler.start("Generate Mipmaps of colormap");
        TextureFactory.getInstance().generateMipMaps(Renderer.getInstance().getGBuffer().getColorReflectivenessMap());
        GPUProfiler.end();

        return firstPassResult;
    }

    public void renderSkyBox(RenderState renderState, Camera camera, boolean depthMask, Program program) {
        graphicsContext.disable(CULL_FACE);
        graphicsContext.depthMask(depthMask);
        graphicsContext.disable(GlCap.BLEND);
        skyBoxEntity.identity().scale(10);
        skyBoxEntity.setTranslation(camera.getPosition());
        program.use();
        program.setUniform("eyeVec", camera.getViewDirection());
        program.setUniform("directionalLightColor", renderState.directionalLightState.directionalLightColor);
        Vector3f translation = new Vector3f();
        program.setUniform("eyePos_world", camera.getTranslation(translation));
        program.setUniform("materialIndex", MaterialFactory.getInstance().indexOf(MaterialFactory.getInstance().getSkyboxMaterial()));
        program.setUniformAsMatrix4("modelMatrix", skyBoxEntity.getTransformationBuffer());
        program.setUniformAsMatrix4("viewMatrix", camera.getViewMatrixAsBuffer());
        program.setUniformAsMatrix4("projectionMatrix", camera.getProjectionMatrixAsBuffer());
        DrawStrategy.draw(skyboxVertexIndexBuffer.getVertexBuffer(), skyboxVertexIndexBuffer.getIndexBuffer(), skyBoxRenderBatch, program, false);
    }

    public SecondPassResult drawSecondPass(SecondPassResult secondPassResult, Camera camera, List<TubeLight> tubeLights, List<AreaLight> areaLights, RenderState renderState) {
        Vector3f camPosition = new Vector3f(camera.getPosition());
        camPosition.add(camera.getViewDirection().mul(-camera.getNear()));
        Vector4f camPositionV4 = new Vector4f(camPosition.x, camPosition.y, camPosition.z, 0);

        FloatBuffer viewMatrix = camera.getViewMatrixAsBuffer();
        FloatBuffer projectionMatrix = camera.getProjectionMatrixAsBuffer();

        GPUProfiler.start("Directional light");
        graphicsContext.depthMask(false);
        graphicsContext.disable(DEPTH_TEST);
        graphicsContext.enable(BLEND);
        graphicsContext.blendEquation(FUNC_ADD);
        graphicsContext.blendFunc(ONE, ONE);

        GBuffer gBuffer = Renderer.getInstance().getGBuffer();
        gBuffer.getLightAccumulationBuffer().use(true);
        GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL30.GL_RENDERBUFFER, gBuffer.getDepthBufferTexture());
        GraphicsContext.getInstance().clearColor(0, 0, 0, 0);
        GraphicsContext.getInstance().clearColorBuffer();

        GPUProfiler.start("Activate GBuffer textures");
        graphicsContext.bindTexture(0, TEXTURE_2D, gBuffer.getPositionMap());
        graphicsContext.bindTexture(1, TEXTURE_2D, gBuffer.getNormalMap());
        graphicsContext.bindTexture(2, TEXTURE_2D, gBuffer.getColorReflectivenessMap());
        graphicsContext.bindTexture(3, TEXTURE_2D, gBuffer.getMotionMap());
        graphicsContext.bindTexture(4, TEXTURE_CUBE_MAP, TextureFactory.getInstance().getCubeMap().getTextureID());
        graphicsContext.bindTexture(6, TEXTURE_2D, directionalLightShadowMapExtension.getShadowMapId());
        graphicsContext.bindTexture(7, TEXTURE_2D, gBuffer.getVisibilityMap());
        graphicsContext.bindTexture(8, TEXTURE_CUBE_MAP_ARRAY, EnvironmentProbeFactory.getInstance().getEnvironmentMapsArray(3).getTextureID());
        GPUProfiler.end();

        secondPassDirectionalProgram.use();
        Vector3f camTranslation = new Vector3f();
        secondPassDirectionalProgram.setUniform("eyePosition", camera.getTranslation(camTranslation));
        secondPassDirectionalProgram.setUniform("ambientOcclusionRadius", Config.getInstance().getAmbientocclusionRadius());
        secondPassDirectionalProgram.setUniform("ambientOcclusionTotalStrength", Config.getInstance().getAmbientocclusionTotalStrength());
        secondPassDirectionalProgram.setUniform("screenWidth", (float) Config.getInstance().getWidth());
        secondPassDirectionalProgram.setUniform("screenHeight", (float) Config.getInstance().getHeight());
        secondPassDirectionalProgram.setUniformAsMatrix4("viewMatrix", viewMatrix);
        secondPassDirectionalProgram.setUniformAsMatrix4("projectionMatrix", projectionMatrix);
        secondPassDirectionalProgram.setUniformAsMatrix4("shadowMatrix", renderState.getDirectionalLightViewProjectionMatrixAsBuffer());
        secondPassDirectionalProgram.setUniform("lightDirection", renderState.directionalLightState.directionalLightDirection);
        secondPassDirectionalProgram.setUniform("lightDiffuse", renderState.directionalLightState.directionalLightColor);
        EnvironmentProbeFactory.getInstance().bindEnvironmentProbePositions(secondPassDirectionalProgram);
        GPUProfiler.start("Draw fullscreen buffer");
        QuadVertexBuffer.getFullscreenBuffer().draw();
        GPUProfiler.end();

        GPUProfiler.end();

        doTubeLights(tubeLights, camPositionV4, viewMatrix, projectionMatrix);

        doAreaLights(areaLights, viewMatrix, projectionMatrix);

        doPointLights(renderState, viewMatrix, projectionMatrix);

        if(!Config.getInstance().isUseDirectTextureOutput()) {
            GPUProfiler.start("Extensions");
            graphicsContext.bindTexture(6, TEXTURE_2D, directionalLightShadowMapExtension.getShadowMapId());
            for(RenderExtension extension : renderExtensions) {
                extension.renderSecondPassFullScreen(renderState, secondPassResult);
            }
            GPUProfiler.end();
        }

        GraphicsContext.getInstance().disable(BLEND);
        gBuffer.getLightAccumulationBuffer().unuse();

        renderAOAndScattering(renderState);

        GPUProfiler.start("MipMap generation AO and light buffer");
        GraphicsContext.getInstance().activeTexture(0);
        TextureFactory.getInstance().generateMipMaps(gBuffer.getLightAccumulationMapOneId());
        GPUProfiler.end();

        if (Config.getInstance().isUseGi()) {
            GL11.glDepthMask(false);
            GraphicsContext.getInstance().disable(DEPTH_TEST);
            GraphicsContext.getInstance().disable(BLEND);
            GraphicsContext.getInstance().cullFace(BACK);
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
//        TextureFactory.getInstance().blurHorinzontal2DTextureRGBA16F(gBuffer.getLightAccumulationMapOneId(), Config.getInstance().WIDTH, Config.getInstance().HEIGHT, 7, 8);
//        GPUProfiler.end();
        GPUProfiler.start("Scattering texture");
        if(Config.getInstance().isScattering() || Config.getInstance().isUseAmbientOcclusion()) {
            TextureFactory.getInstance().blur2DTextureRGBA16F(gBuffer.getHalfScreenBuffer().getRenderedTexture(), Config.getInstance().getWidth() / 2, Config.getInstance().getHeight() / 2, 0, 0);
        }
        GPUProfiler.end();
//        TextureFactory.getInstance().blur2DTextureRGBA16F(gBuffer.getHalfScreenBuffer().getRenderedTexture(), Config.getInstance().WIDTH / 2, Config.getInstance().HEIGHT / 2, 0, 0);
//        Renderer.getInstance().blur2DTexture(gBuffer.getHalfScreenBuffer().getRenderedTexture(), 0, Config.getInstance().WIDTH / 2, Config.getInstance().HEIGHT / 2, GL30.GL_RGBA16F, false, 1);
//		renderer.blur2DTexture(gBuffer.getLightAccumulationMapOneId(), 0, Config.getInstance().WIDTH, Config.getInstance().HEIGHT, GL30.GL_RGBA16F, false, 1);
//		renderer.blur2DTexture(getLightAccumulationMapOneId(), 0, (int)(Config.getInstance().WIDTH*SECONDPASSSCALE), (int)(Config.getInstance().HEIGHT*SECONDPASSSCALE), GL30.GL_RGBA16F, false, 1);
//		Renderer.getInstance().blur2DTexture(gBuffer.getAmbientOcclusionMapId(), 0, (int)(Config.getInstance().WIDTH), (int)(Config.getInstance().HEIGHT), GL30.GL_RGBA16F, false, 1);
//		renderer.blur2DTextureBilateral(getLightAccumulationMapOneId(), 0, (int)(renderer.WIDTH*SECONDPASSSCALE), (int)(renderer.HEIGHT*SECONDPASSSCALE), GL30.GL_RGBA16F, false, 1);

//		renderer.blur2DTexture(halfScreenTarget.getRenderedTexture(0), (int)(renderer.WIDTH*0.5), (int)(renderer.HEIGHT*0.5), GL11.GL_RGBA8, false, 1);
//		renderer.blur2DTexture(halfScreenTarget.getRenderedTexture(1), (int)(renderer.WIDTH*0.5), (int)(renderer.HEIGHT*0.5), GL11.GL_RGBA8, false, 1);

        GraphicsContext.getInstance().cullFace(BACK);
        GraphicsContext.getInstance().depthFunc(LESS);
        GPUProfiler.end();

        return secondPassResult;
    }

    private void doPointLights(RenderState renderState, FloatBuffer viewMatrix, FloatBuffer projectionMatrix) {
        if (Engine.getInstance().getSceneManager().getScene().getPointLights().isEmpty()) {
            return;
        }
        GPUProfiler.start("Seconds pass PointLights");
        graphicsContext.bindTexture(0, TEXTURE_2D, Renderer.getInstance().getGBuffer().getPositionMap());
        graphicsContext.bindTexture(1, TEXTURE_2D, Renderer.getInstance().getGBuffer().getNormalMap());
        graphicsContext.bindTexture(2, TEXTURE_2D, Renderer.getInstance().getGBuffer().getColorReflectivenessMap());
        graphicsContext.bindTexture(3, TEXTURE_2D, Renderer.getInstance().getGBuffer().getMotionMap());
        graphicsContext.bindTexture(4, TEXTURE_2D, Renderer.getInstance().getGBuffer().getLightAccumulationMapOneId());
        graphicsContext.bindTexture(5, TEXTURE_2D, Renderer.getInstance().getGBuffer().getVisibilityMap());
        if (Config.getInstance().isUseDpsm()) {
            graphicsContext.bindTexture(6, TEXTURE_2D_ARRAY, LightFactory.getInstance().getPointLightDepthMapsArrayFront());
            graphicsContext.bindTexture(7, TEXTURE_2D_ARRAY, LightFactory.getInstance().getPointLightDepthMapsArrayBack());
        } else {
            graphicsContext.bindTexture(8, TEXTURE_CUBE_MAP_ARRAY, LightFactory.getInstance().getPointLightDepthMapsArrayCube());
        }
        // TODO: Add glbindimagetexture to openglcontext class
        GL42.glBindImageTexture(4, Renderer.getInstance().getGBuffer().getLightAccumulationMapOneId(), 0, false, 0, GL15.GL_READ_WRITE, GL30.GL_RGBA16F);
        secondPassPointComputeProgram.use();
        secondPassPointComputeProgram.setUniform("pointLightCount", Engine.getInstance().getSceneManager().getScene().getPointLights().size());
        secondPassPointComputeProgram.setUniform("screenWidth", (float) Config.getInstance().getWidth());
        secondPassPointComputeProgram.setUniform("screenHeight", (float) Config.getInstance().getHeight());
        secondPassPointComputeProgram.setUniformAsMatrix4("viewMatrix", viewMatrix);
        secondPassPointComputeProgram.setUniformAsMatrix4("projectionMatrix", projectionMatrix);
        secondPassPointComputeProgram.setUniform("maxPointLightShadowmaps", LightFactory.MAX_POINTLIGHT_SHADOWMAPS);
        secondPassPointComputeProgram.bindShaderStorageBuffer(1, renderState.getMaterialBuffer());
        secondPassPointComputeProgram.bindShaderStorageBuffer(2, LightFactory.getInstance().getLightBuffer());
        secondPassPointComputeProgram.dispatchCompute(Config.getInstance().getWidth() / 16, Config.getInstance().getHeight() / 16, 1);
        GPUProfiler.end();
    }

    private void doTubeLights(List<TubeLight> tubeLights,
                              Vector4f camPositionV4, FloatBuffer viewMatrix,
                              FloatBuffer projectionMatrix) {


        if (tubeLights.isEmpty()) {
            return;
        }

        secondPassTubeProgram.use();
        secondPassTubeProgram.setUniform("screenWidth", (float) Config.getInstance().getWidth());
        secondPassTubeProgram.setUniform("screenHeight", (float) Config.getInstance().getHeight());
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

        GraphicsContext.getInstance().disable(CULL_FACE);
        GraphicsContext.getInstance().disable(DEPTH_TEST);
        if (areaLights.isEmpty()) {
            return;
        }

        GPUProfiler.start("Area light: " + areaLights.size());

        secondPassAreaProgram.use();
        secondPassAreaProgram.setUniform("screenWidth", (float) Config.getInstance().getWidth());
        secondPassAreaProgram.setUniform("screenHeight", (float) Config.getInstance().getHeight());
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
//				Texture lightTexture = renderer.getTextureFactory().getDiffuseTexture("brick.hptexture");
//				GL11.glBindTexture(GL11.GL_TEXTURE_2D, lightTexture.getTextureID());
//			} catch (IOException e) {
//				e.printStackTrace();
//			}
            GraphicsContext.getInstance().bindTexture(9, GlTextureTarget.TEXTURE_2D, LightFactory.getInstance().getDepthMapForAreaLight(areaLight));
            QuadVertexBuffer.getFullscreenBuffer().draw();
//			areaLight.getVertexBuffer().drawDebug();
        }

        GPUProfiler.end();
    }

    private void renderAOAndScattering(RenderState renderState) {
        if (!Config.getInstance().isUseAmbientOcclusion() && !Config.getInstance().isScattering()) {
            return;
        }
        GPUProfiler.start("Scattering and AO");
        GBuffer gBuffer = Renderer.getInstance().getGBuffer();
        gBuffer.getHalfScreenBuffer().use(true);
        GraphicsContext.getInstance().disable(DEPTH_TEST);
        GraphicsContext.getInstance().bindTexture(0, TEXTURE_2D, gBuffer.getPositionMap());
        GraphicsContext.getInstance().bindTexture(1, TEXTURE_2D, gBuffer.getNormalMap());
        GraphicsContext.getInstance().bindTexture(2, TEXTURE_2D, gBuffer.getColorReflectivenessMap());
        GraphicsContext.getInstance().bindTexture(3, TEXTURE_2D, gBuffer.getMotionMap());
        GraphicsContext.getInstance().bindTexture(6, TEXTURE_2D, directionalLightShadowMapExtension.getShadowMapId());
        EnvironmentProbeFactory.getInstance().getEnvironmentMapsArray(3).bind(8);

//		halfScreenBuffer.setTargetTexture(halfScreenBuffer.getRenderedTexture(), 0);
        aoScatteringProgram.use();
        aoScatteringProgram.setUniform("eyePosition", renderState.camera.getPosition());
        aoScatteringProgram.setUniform("useAmbientOcclusion", Config.getInstance().isUseAmbientOcclusion());
        aoScatteringProgram.setUniform("ambientOcclusionRadius", Config.getInstance().getAmbientocclusionRadius());
        aoScatteringProgram.setUniform("ambientOcclusionTotalStrength", Config.getInstance().getAmbientocclusionTotalStrength());
        aoScatteringProgram.setUniform("screenWidth", (float) Config.getInstance().getWidth() / 2);
        aoScatteringProgram.setUniform("screenHeight", (float) Config.getInstance().getHeight() / 2);
        aoScatteringProgram.setUniformAsMatrix4("viewMatrix", renderState.camera.getViewMatrixAsBuffer());
        aoScatteringProgram.setUniformAsMatrix4("projectionMatrix", renderState.camera.getProjectionMatrixAsBuffer());
        aoScatteringProgram.setUniformAsMatrix4("shadowMatrix", renderState.getDirectionalLightViewProjectionMatrixAsBuffer());
        aoScatteringProgram.setUniform("lightDirection", renderState.directionalLightState.directionalLightDirection);
        aoScatteringProgram.setUniform("lightDiffuse", renderState.directionalLightState.directionalLightColor);
        aoScatteringProgram.setUniform("scatterFactor", renderState.directionalLightState.directionalLightScatterFactor);

        EnvironmentProbeFactory.getInstance().bindEnvironmentProbePositions(aoScatteringProgram);
        QuadVertexBuffer.getFullscreenBuffer().draw();
        GraphicsContext.getInstance().enable(DEPTH_TEST);
        TextureFactory.getInstance().generateMipMaps(gBuffer.getHalfScreenBuffer().getRenderedTexture());
        GPUProfiler.end();
    }

    private void renderReflections(FloatBuffer viewMatrix, FloatBuffer projectionMatrix) {
        GPUProfiler.start("Reflections and AO");
        GBuffer gBuffer = Renderer.getInstance().getGBuffer();
        RenderTarget reflectionBuffer = gBuffer.getReflectionBuffer();

        GraphicsContext.getInstance().bindTexture(0, TEXTURE_2D, gBuffer.getPositionMap());
        GraphicsContext.getInstance().bindTexture(1, TEXTURE_2D, gBuffer.getNormalMap());
        GraphicsContext.getInstance().bindTexture(2, TEXTURE_2D, gBuffer.getColorReflectivenessMap());
        GraphicsContext.getInstance().bindTexture(3, TEXTURE_2D, gBuffer.getMotionMap());
        GraphicsContext.getInstance().bindTexture(4, TEXTURE_2D, gBuffer.getLightAccumulationMapOneId());
        GraphicsContext.getInstance().bindTexture(5, TEXTURE_2D, gBuffer.getFinalMap());
        TextureFactory.getInstance().getCubeMap().bind(6);
//        GL13.glActiveTexture(GL13.GL_TEXTURE0 + 7);
//        reflectionBuffer.getRenderedTexture(0);
        EnvironmentProbeFactory.getInstance().getEnvironmentMapsArray(3).bind(8);
        TextureFactory.getInstance().getCubeMap().bind(9);
        EnvironmentProbeFactory.getInstance().getEnvironmentMapsArray(0).bind(10);
        GraphicsContext.getInstance().bindTexture(11, TEXTURE_2D, reflectionBuffer.getRenderedTexture());

        int copyTextureId = GL11.glGenTextures();
        GraphicsContext.getInstance().bindTexture(11, TEXTURE_2D, copyTextureId);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RGBA16F, reflectionBuffer.getWidth(), reflectionBuffer.getHeight(), 0, GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE, (FloatBuffer) null);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL43.glCopyImageSubData(reflectionBuffer.getRenderedTexture(), GL11.GL_TEXTURE_2D, 0, 0, 0, 0,
                copyTextureId, GL11.GL_TEXTURE_2D, 0, 0, 0, 0,
                reflectionBuffer.getWidth(), reflectionBuffer.getHeight(), 1);
        GraphicsContext.getInstance().bindTexture(11, TEXTURE_2D, copyTextureId);

        if (!USE_COMPUTESHADER_FOR_REFLECTIONS) {
            reflectionBuffer.use(true);
            reflectionProgram.use();
            reflectionProgram.setUniform("N", IMPORTANCE_SAMPLE_COUNT);
            reflectionProgram.setUniform("useAmbientOcclusion", Config.getInstance().isUseAmbientOcclusion());
            reflectionProgram.setUniform("useSSR", Config.getInstance().isUseSSR());
            reflectionProgram.setUniform("screenWidth", (float) Config.getInstance().getWidth());
            reflectionProgram.setUniform("screenHeight", (float) Config.getInstance().getHeight());
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
            tiledProbeLightingProgram.setUniform("useAmbientOcclusion", Config.getInstance().isUseAmbientOcclusion());
            tiledProbeLightingProgram.setUniform("useSSR", Config.getInstance().isUseSSR());
            tiledProbeLightingProgram.setUniform("screenWidth", (float) Config.getInstance().getWidth());
            tiledProbeLightingProgram.setUniform("screenHeight", (float) Config.getInstance().getHeight());
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
        combineProgram.setUniform("screenWidth", (float) Config.getInstance().getWidth());
        combineProgram.setUniform("screenHeight", (float) Config.getInstance().getHeight());
        combineProgram.setUniform("camPosition", renderState.camera.getPosition());
        combineProgram.setUniform("ambientColor", Config.getInstance().getAmbientLight());
        combineProgram.setUniform("useAmbientOcclusion", Config.getInstance().isUseAmbientOcclusion());
        combineProgram.setUniform("worldExposure", Config.getInstance().getExposure());
        combineProgram.setUniform("AUTO_EXPOSURE_ENABLED", Config.getInstance().isAutoExposureEnabled());
        combineProgram.setUniform("fullScreenMipmapCount", gBuffer.getFullScreenMipmapCount());
        combineProgram.setUniform("activeProbeCount", EnvironmentProbeFactory.getInstance().getProbes().size());
        combineProgram.bindShaderStorageBuffer(0, gBuffer.getStorageBuffer());

        finalBuffer.use(true);
        GraphicsContext.getInstance().disable(DEPTH_TEST);

        GraphicsContext.getInstance().bindTexture(0, TEXTURE_2D, gBuffer.getColorReflectivenessMap());
        GraphicsContext.getInstance().bindTexture(1, TEXTURE_2D, gBuffer.getLightAccumulationMapOneId());
        GraphicsContext.getInstance().bindTexture(2, TEXTURE_2D, gBuffer.getLightAccumulationBuffer().getRenderedTexture(1));
        GraphicsContext.getInstance().bindTexture(3, TEXTURE_2D, gBuffer.getMotionMap());
        GraphicsContext.getInstance().bindTexture(4, TEXTURE_2D, gBuffer.getPositionMap());
        GraphicsContext.getInstance().bindTexture(5, TEXTURE_2D, gBuffer.getNormalMap());
        TextureFactory.getInstance().getCubeMap().bind(6);
        EnvironmentProbeFactory.getInstance().getEnvironmentMapsArray().bind(7);
        GraphicsContext.getInstance().bindTexture(8, TEXTURE_2D, gBuffer.getReflectionMap());
        GraphicsContext.getInstance().bindTexture(9, TEXTURE_2D, gBuffer.getRefractedMap());
        GraphicsContext.getInstance().bindTexture(11, TEXTURE_2D, gBuffer.getAmbientOcclusionScatteringMap());
        GraphicsContext.getInstance().bindTexture(12, TEXTURE_CUBE_MAP_ARRAY, LightFactory.getInstance().getPointLightDepthMapsArrayCube());

        QuadVertexBuffer.getFullscreenBuffer().draw();

        if (target == null) {
            RenderTarget.getFrontBuffer().use(true);
        } else {
            target.use(true);
        }
        GPUProfiler.start("Post processing");
        postProcessProgram.use();
        GraphicsContext.getInstance().bindTexture(0, TEXTURE_2D, finalBuffer.getRenderedTexture(0));
        postProcessProgram.setUniform("screenWidth", (float) Config.getInstance().getWidth());
        postProcessProgram.setUniform("screenHeight", (float) Config.getInstance().getHeight());
        postProcessProgram.setUniform("worldExposure", Config.getInstance().getExposure());
        postProcessProgram.setUniform("AUTO_EXPOSURE_ENABLED", Config.getInstance().isAutoExposureEnabled());
        postProcessProgram.setUniform("usePostProcessing", Config.getInstance().isEnablePostprocessing());
        try {
            postProcessProgram.setUniform("cameraRightDirection", renderState.camera.getRightDirection());
            postProcessProgram.setUniform("cameraViewDirection", renderState.camera.getViewDirection());
        } catch (IllegalStateException e) {
            // Normalizing zero length vector
        }
        postProcessProgram.setUniform("seconds", (float)Renderer.getInstance().getDeltaInS());
        postProcessProgram.bindShaderStorageBuffer(0, gBuffer.getStorageBuffer());
//        postProcessProgram.bindShaderStorageBuffer(1, Engine.getInstance().getRenderer().getMaterialFactory().getMaterialBuffer());
        GraphicsContext.getInstance().bindTexture(1, TEXTURE_2D, gBuffer.getNormalMap());
        GraphicsContext.getInstance().bindTexture(2, TEXTURE_2D, gBuffer.getMotionMap());
        GraphicsContext.getInstance().bindTexture(3, TEXTURE_2D, gBuffer.getLightAccumulationMapOneId());
        GraphicsContext.getInstance().bindTexture(4, TEXTURE_2D, TextureFactory.getInstance().getLensFlareTexture().getTextureID());
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
//        probeFirstpassProgram.setUniform("showContent", Config.getInstance().DEBUGDRAW_PROBES_WITH_CONTENT);
//
//        Vector3f oldMaterialColor = new Vector3f(probeBoxEntity.getComponent(ModelComponent.class).getMaterials().getDiffuse());
//
//        for (EnvironmentProbe probe : EnvironmentProbeFactory.getInstance().getProbes()) {
//            Transform transform = new Transform();
//            transform.setPosition(probe.getCenter());
//            transform.setScale(probe.getSize());
//            Vector3f colorHelper = probe.getDebugColor();
//            probeBoxEntity.getComponent(ModelComponent.class).getMaterials().setDiffuse(colorHelper);
//            probeBoxEntity.setTransform(transform);
//            probeBoxEntity.update(0);
//            probeFirstpassProgram.setUniform("probeCenter", probe.getCenter());
//            probeFirstpassProgram.setUniform("probeIndex", probe.getIndex());
//            probeBoxEntity.getComponent(ModelComponent.class).draw(extract, de.hanno.hpengine.camera, probeBoxEntity.getModelMatrixAsBuffer(), -1);
//        }
//
//        probeBoxEntity.getComponent(ModelComponent.class).getMaterials().getDiffuse().x = oldMaterialColor.x;
//        probeBoxEntity.getComponent(ModelComponent.class).getMaterials().getDiffuse().y = oldMaterialColor.y;
//        probeBoxEntity.getComponent(ModelComponent.class).getMaterials().getDiffuse().z = oldMaterialColor.z;
//
//    }

    public void registerRenderExtension(RenderExtension extension) {
        renderExtensions.add(extension);
    }

    public DirectionalLightShadowMapExtension getDirectionalLightExtension() {
        return directionalLightShadowMapExtension;
    }

    public void setMainPipelineRef(TripleBuffer.PipelineRef<GPUCulledMainPipeline> mainPipelineRef) {
        this.mainPipelineRef = mainPipelineRef;
    }

    public TripleBuffer.PipelineRef getMainPipelineRef() {
        return mainPipelineRef;
    }

}
