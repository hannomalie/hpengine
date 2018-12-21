package de.hanno.hpengine.engine.graphics.renderer;

import de.hanno.hpengine.engine.DirectoryManager;
import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.camera.Camera;
import de.hanno.hpengine.engine.component.ModelComponent;
import de.hanno.hpengine.engine.config.Config;
import de.hanno.hpengine.engine.entity.Entity;
import de.hanno.hpengine.engine.graphics.GpuContext;
import de.hanno.hpengine.engine.graphics.OpenGLContext;
import de.hanno.hpengine.engine.graphics.light.area.AreaLight;
import de.hanno.hpengine.engine.graphics.light.tube.TubeLight;
import de.hanno.hpengine.engine.graphics.renderer.constants.GlCap;
import de.hanno.hpengine.engine.graphics.renderer.constants.GlTextureTarget;
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.*;
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.extensions.*;
import de.hanno.hpengine.engine.graphics.renderer.pipelines.GPUCulledMainPipeline;
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.RenderTarget;
import de.hanno.hpengine.engine.graphics.shader.ComputeShaderProgram;
import de.hanno.hpengine.engine.graphics.shader.Program;
import de.hanno.hpengine.engine.graphics.shader.ProgramManager;
import de.hanno.hpengine.engine.graphics.shader.Shader;
import de.hanno.hpengine.engine.graphics.shader.define.Defines;
import de.hanno.hpengine.engine.graphics.state.RenderState;
import de.hanno.hpengine.engine.graphics.state.StateRef;
import de.hanno.hpengine.engine.graphics.state.multithreading.TripleBuffer;
import de.hanno.hpengine.engine.model.*;
import de.hanno.hpengine.engine.model.texture.Texture;
import de.hanno.hpengine.engine.scene.VertexIndexBuffer;
import de.hanno.hpengine.util.stopwatch.GPUProfiler;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

import javax.vecmath.Vector2f;
import java.io.File;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.logging.Logger;

import static de.hanno.hpengine.engine.graphics.light.point.PointLightSystem.MAX_POINTLIGHT_SHADOWMAPS;
import static de.hanno.hpengine.engine.graphics.renderer.constants.BlendMode.FUNC_ADD;
import static de.hanno.hpengine.engine.graphics.renderer.constants.BlendMode.Factor.ONE;
import static de.hanno.hpengine.engine.graphics.renderer.constants.CullMode.BACK;
import static de.hanno.hpengine.engine.graphics.renderer.constants.GlCap.*;
import static de.hanno.hpengine.engine.graphics.renderer.constants.GlDepthFunc.LESS;
import static de.hanno.hpengine.engine.graphics.renderer.constants.GlTextureTarget.*;
import static de.hanno.hpengine.engine.graphics.shader.Shader.ShaderSourceFactory.getShaderSource;
import static de.hanno.hpengine.engine.model.Update.DYNAMIC;
import static de.hanno.hpengine.log.ConsoleLogger.getLogger;
import static org.lwjgl.glfw.GLFW.glfwPollEvents;
import static org.lwjgl.glfw.GLFW.glfwSwapBuffers;
import static org.lwjgl.opengl.GL11.glFinish;

public class DeferredRenderer implements Renderer {
	private static Logger LOGGER = getLogger();

	private ArrayList<VertexBuffer> sixDebugBuffers;

	private DeferredRenderingBuffer gBuffer;

    private VertexBuffer buffer;

	private Engine engine;

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

	GpuContext gpuContext;
	private final List<RenderExtension> renderExtensions = new ArrayList<>();
	private final DirectionalLightShadowMapExtension directionalLightShadowMapExtension;
	private StateRef<GPUCulledMainPipeline> mainPipelineRef;

	private final RenderBatch skyBoxRenderBatch;

	private FloatBuffer modelMatrixBuffer = BufferUtils.createFloatBuffer(16);
	public DeferredRenderer(Engine engine) throws Exception {
		if(!(engine.getGpuContext() instanceof OpenGLContext)) {
			throw new IllegalStateException("Cannot use this DeferredRenderer with a non-OpenGlContext!");
		}
		this.engine = engine;

		setupBuffers();
		setUpGBuffer();

		this.engine = engine;
		ProgramManager programManager = this.engine.getProgramManager();
		secondPassPointProgram = programManager.getProgram(getShaderSource(new File(Shader.getDirectory() + "second_pass_point_vertex.glsl")), getShaderSource(new File(Shader.getDirectory() + "second_pass_point_fragment.glsl")), new Defines());
		secondPassTubeProgram = programManager.getProgram(getShaderSource(new File(Shader.getDirectory() + "second_pass_point_vertex.glsl")), getShaderSource(new File(Shader.getDirectory() + "second_pass_tube_fragment.glsl")), new Defines());
		secondPassAreaProgram = programManager.getProgram(getShaderSource(new File(Shader.getDirectory() + "second_pass_area_vertex.glsl")), getShaderSource(new File(Shader.getDirectory() + "second_pass_area_fragment.glsl")), new Defines());
		secondPassDirectionalProgram = programManager.getProgram(getShaderSource(new File(Shader.getDirectory() + "second_pass_directional_vertex.glsl")), getShaderSource(new File(Shader.getDirectory() + "second_pass_directional_fragment.glsl")), new Defines());
		instantRadiosityProgram = programManager.getProgram(getShaderSource(new File(Shader.getDirectory() + "second_pass_area_vertex.glsl")), getShaderSource(new File(Shader.getDirectory() + "second_pass_instant_radiosity_fragment.glsl")), new Defines());

		secondPassPointComputeProgram = programManager.getComputeProgram("second_pass_point_compute.glsl");

		combineProgram = programManager.getProgram(getShaderSource(new File(Shader.getDirectory() + "combine_pass_vertex.glsl")), getShaderSource(new File(Shader.getDirectory() + "combine_pass_fragment.glsl")), new Defines());
		postProcessProgram = programManager.getProgram(getShaderSource(new File(Shader.getDirectory() + "passthrough_vertex.glsl")), getShaderSource(new File(Shader.getDirectory() + "postprocess_fragment.glsl")), new Defines());

		aoScatteringProgram = this.engine.getProgramManager().getProgramFromFileNames("passthrough_vertex.glsl", "scattering_ao_fragment.glsl", new Defines());
		reflectionProgram = this.engine.getProgramManager().getProgramFromFileNames("passthrough_vertex.glsl", "reflections_fragment.glsl", new Defines());
		linesProgram = this.engine.getProgramManager().getProgramFromFileNames("mvp_vertex.glsl", "simple_color_fragment.glsl", new Defines());
		skyBoxProgram = this.engine.getProgramManager().getProgramFromFileNames("mvp_vertex.glsl", "skybox.glsl", new Defines());
		skyBoxDepthProgram = this.engine.getProgramManager().getProgramFromFileNames("mvp_vertex.glsl", "skybox_depth.glsl", new Defines());
		probeFirstpassProgram = this.engine.getProgramManager().getProgramFromFileNames("first_pass_vertex.glsl", "probe_first_pass_fragment.glsl", new Defines());
		depthPrePassProgram = this.engine.getProgramManager().getProgramFromFileNames("first_pass_vertex.glsl", "depth_prepass_fragment.glsl", new Defines());
		tiledDirectLightingProgram = this.engine.getProgramManager().getComputeProgram("tiled_direct_lighting_compute.glsl");
		tiledProbeLightingProgram = this.engine.getProgramManager().getComputeProgram("tiled_probe_lighting_compute.glsl");

		gpuContext = this.engine.getGpuContext();

		StaticModel skyBox = new OBJLoader().loadTexturedModel(engine.getScene().getMaterialManager(), new File(DirectoryManager.WORKDIR_NAME + "/assets/models/skybox.obj"));
		skyBoxEntity = this.engine.getSceneManager().getScene().getEntityManager().create();
		skyBoxEntity.addComponent(new ModelComponent(skyBoxEntity, skyBox));
		skyBoxEntity.init(engine);
		skyboxVertexIndexBuffer = new VertexIndexBuffer(gpuContext, 10, 10, ModelComponent.DEFAULTCHANNELS);
		VertexIndexBuffer.VertexIndexOffsets vertexIndexOffsets = skyBoxEntity.getComponent(ModelComponent.class).putToBuffer(engine.getGpuContext(), skyboxVertexIndexBuffer, ModelComponent.DEFAULTCHANNELS);
		skyBoxRenderBatch = new RenderBatch().init(skyBoxProgram, 0, true, false, false, new Vector3f(0,0,0), true, 1, true, DYNAMIC, new Vector3f(0,0,0), new Vector3f(0,0,0), new Vector3f(), 1000, skyBox.getIndices().length, vertexIndexOffsets.indexOffset, vertexIndexOffsets.vertexOffset, false, skyBoxEntity.getInstanceMinMaxWorlds());

		directionalLightShadowMapExtension = new DirectionalLightShadowMapExtension(engine);

		registerRenderExtension(new DrawLinesExtension(engine));
		registerRenderExtension(new VoxelConeTracingExtension(engine, directionalLightShadowMapExtension));
//        registerRenderExtension(new EvaluateProbeRenderExtension(engine));
		registerRenderExtension(new PixelPerfectPickingExtension());

		float[] points = {0f, 0f, 0f, 0f};
		buffer = new VertexBuffer(engine.getGpuContext(), points, EnumSet.of(DataChannels.POSITION3));
		buffer.upload();

		TripleBuffer<RenderState> renderState = engine.getRenderManager().getRenderState();
		mainPipelineRef = renderState.registerState(() -> new GPUCulledMainPipeline(engine, this));
	}

	private void registerRenderExtension(RenderExtension extension) {
		renderExtensions.add(extension);
	}

	private void setupBuffers() {

		sixDebugBuffers = new ArrayList<VertexBuffer>() {{
			float height = -2f/3f;
			float width = 2f;
			float widthDiv = width/6f;
			for (int i = 0; i < 6; i++) {
				QuadVertexBuffer quadVertexBuffer = new QuadVertexBuffer(engine.getGpuContext(), new Vector2f(-1f + i * widthDiv, -1f), new Vector2f(-1 + (i + 1) * widthDiv, height));
				add(quadVertexBuffer);
				quadVertexBuffer.upload();
			}
		}};

		GpuContext.exitOnGLError("setupBuffers");
	}

    private void setUpGBuffer() {
		GpuContext.exitOnGLError("Before setupGBuffer");

        gBuffer = engine.getGpuContext().calculate(() -> new DeferredRenderingBuffer(engine.getGpuContext()));

        engine.getGpuContext().execute(() -> {
            engine.getGpuContext().enable(GlCap.TEXTURE_CUBE_MAP_SEAMLESS);

			GpuContext.exitOnGLError("setupGBuffer");
		});
	}

	public void update(Engine engine, float seconds) {
	}


    @Override
	public void render(DrawResult result, RenderState renderState) {
		GPUProfiler.start("Frame");

		engine.getEnvironmentProbeManager().drawAlternating(renderState.getCamera().getEntity());
		GPUProfiler.start("First pass");
		FirstPassResult firstPassResult = result.getFirstPassResult();

		GPUCulledMainPipeline pipeline = renderState.getState(mainPipelineRef);

		Camera camera = renderState.getCamera();

		gpuContext.depthMask(true);
		engine.getRenderer().getGBuffer().use(true);

		gpuContext.disable(CULL_FACE);
		gpuContext.depthMask(false);
		gpuContext.disable(GlCap.BLEND);
		skyBoxEntity.identity().scale(10);
		skyBoxEntity.setTranslation(camera.getPosition());
		skyBoxProgram.use();
		skyBoxProgram.setUniform("eyeVec", camera.getViewDirection());
		skyBoxProgram.setUniform("directionalLightColor", renderState.getDirectionalLightState().directionalLightColor);
		Vector3f translation = new Vector3f();
		skyBoxProgram.setUniform("eyePos_world", camera.getTranslation(translation));
		skyBoxProgram.setUniform("materialIndex", engine.getScene().getMaterialManager().getSkyboxMaterial().getMaterialIndex());
		skyBoxProgram.setUniformAsMatrix4("modelMatrix", skyBoxEntity.getTransformation().get(modelMatrixBuffer));
		skyBoxProgram.setUniformAsMatrix4("viewMatrix", camera.getViewMatrixAsBuffer());
		skyBoxProgram.setUniformAsMatrix4("projectionMatrix", camera.getProjectionMatrixAsBuffer());
		DrawUtils.draw(gpuContext, skyboxVertexIndexBuffer.getVertexBuffer(), skyboxVertexIndexBuffer.getIndexBuffer(), skyBoxRenderBatch, skyBoxProgram, false, false);

		GPUProfiler.start("Set GPU state");
		gpuContext.enable(CULL_FACE);
		gpuContext.depthMask(true);
		gpuContext.enable(DEPTH_TEST);
		gpuContext.depthFunc(LESS);
		gpuContext.disable(GlCap.BLEND);
		GPUProfiler.end();

		GPUProfiler.start("Draw entities");

		if (Config.getInstance().isDrawScene()) {
			Program firstpassProgram = engine.getProgramManager().getFirstpassDefaultProgram();
			Program firstpassProgramAnimated = engine.getProgramManager().getFirstpassAnimatedDefaultProgram();
			pipeline.draw(renderState, firstpassProgram, firstpassProgramAnimated, firstPassResult);
		}
		GPUProfiler.end();

		if(!Config.getInstance().isUseDirectTextureOutput()) {
			engine.getGpuContext().bindTexture(6, TEXTURE_2D, directionalLightShadowMapExtension.getShadowMapId());
			for(RenderExtension extension : renderExtensions) {
				GPUProfiler.start("RenderExtension " + extension.getClass().getSimpleName());
				extension.renderFirstPass(engine, gpuContext, firstPassResult, renderState);
				GPUProfiler.end();
			}
		}

		gpuContext.enable(CULL_FACE);

		GPUProfiler.start("Generate Mipmaps of colormap");
		engine.getTextureManager().generateMipMaps(TEXTURE_2D, engine.getRenderer().getGBuffer().getColorReflectivenessMap());
		GPUProfiler.end();

		GPUProfiler.end();

		if (!Config.getInstance().isUseDirectTextureOutput()) {
			GPUProfiler.start("Shadowmap pass");
			directionalLightShadowMapExtension.renderFirstPass(engine, gpuContext, result.getFirstPassResult(), renderState);
//            engine.getSimpleScene().getAreaLightSystem().renderAreaLightShadowMaps(renderState);
//            engine.getSimpleScene().getPointLightSystem().getShadowMapStrategy().renderPointLightShadowMaps(renderState);
			GPUProfiler.end();

			GPUProfiler.start("Second pass");
			SecondPassResult secondPassResult = result.getSecondPassResult();
			Camera camera1 = renderState.getCamera();
			Vector3f camPosition = new Vector3f(camera1.getPosition());
			camPosition.add(camera1.getViewDirection().mul(-camera1.getNear()));
			Vector4f camPositionV4 = new Vector4f(camPosition.x, camPosition.y, camPosition.z, 0);

			FloatBuffer viewMatrix = camera1.getViewMatrixAsBuffer();
			FloatBuffer projectionMatrix = camera1.getProjectionMatrixAsBuffer();

			GPUProfiler.start("Directional light");
			gpuContext.depthMask(false);
			gpuContext.disable(DEPTH_TEST);
			gpuContext.enable(BLEND);
			gpuContext.blendEquation(FUNC_ADD);
			gpuContext.blendFunc(ONE, ONE);

			DeferredRenderingBuffer gBuffer1 = engine.getRenderer().getGBuffer();
			gBuffer1.getLightAccumulationBuffer().use(true);
			GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL30.GL_RENDERBUFFER, gBuffer1.getDepthBufferTexture());
			engine.getGpuContext().clearColor(0, 0, 0, 0);
			engine.getGpuContext().clearColorBuffer();

			GPUProfiler.start("Activate DeferredRenderingBuffer textures");
			gpuContext.bindTexture(0, TEXTURE_2D, gBuffer1.getPositionMap());
			gpuContext.bindTexture(1, TEXTURE_2D, gBuffer1.getNormalMap());
			gpuContext.bindTexture(2, TEXTURE_2D, gBuffer1.getColorReflectivenessMap());
			gpuContext.bindTexture(3, TEXTURE_2D, gBuffer1.getMotionMap());
			gpuContext.bindTexture(4, TEXTURE_CUBE_MAP, engine.getTextureManager().getCubeMap().getTextureId());
			gpuContext.bindTexture(6, TEXTURE_2D, directionalLightShadowMapExtension.getShadowMapId());
			gpuContext.bindTexture(7, TEXTURE_2D, gBuffer1.getVisibilityMap());
			gpuContext.bindTexture(8, TEXTURE_CUBE_MAP_ARRAY, engine.getSceneManager().getScene().getEnvironmentProbeManager().getEnvironmentMapsArray(3).getTextureID());
			GPUProfiler.end();

			secondPassDirectionalProgram.use();
			Vector3f camTranslation = new Vector3f();
			secondPassDirectionalProgram.setUniform("eyePosition", camera1.getTranslation(camTranslation));
			secondPassDirectionalProgram.setUniform("ambientOcclusionRadius", Config.getInstance().getAmbientocclusionRadius());
			secondPassDirectionalProgram.setUniform("ambientOcclusionTotalStrength", Config.getInstance().getAmbientocclusionTotalStrength());
			secondPassDirectionalProgram.setUniform("screenWidth", (float) Config.getInstance().getWidth());
			secondPassDirectionalProgram.setUniform("screenHeight", (float) Config.getInstance().getHeight());
			secondPassDirectionalProgram.setUniformAsMatrix4("viewMatrix", viewMatrix);
			secondPassDirectionalProgram.setUniformAsMatrix4("projectionMatrix", projectionMatrix);
			secondPassDirectionalProgram.setUniformAsMatrix4("shadowMatrix", renderState.getDirectionalLightViewProjectionMatrixAsBuffer());
			secondPassDirectionalProgram.setUniform("lightDirection", renderState.getDirectionalLightState().directionalLightDirection);
			secondPassDirectionalProgram.setUniform("lightDiffuse", renderState.getDirectionalLightState().directionalLightColor);
			engine.getSceneManager().getScene().getEnvironmentProbeManager().bindEnvironmentProbePositions(secondPassDirectionalProgram);
			GPUProfiler.start("Draw fullscreen buffer");
			engine.getGpuContext().getFullscreenBuffer().draw();
			GPUProfiler.end();

			GPUProfiler.end();

			doTubeLights(engine.getSceneManager().getScene().getTubeLights(), camPositionV4, viewMatrix, projectionMatrix);

			doAreaLights(engine.getSceneManager().getScene().getAreaLights(), viewMatrix, projectionMatrix);

			doPointLights(renderState, viewMatrix, projectionMatrix);

			if(!Config.getInstance().isUseDirectTextureOutput()) {
				GPUProfiler.start("Extensions");
				gpuContext.bindTexture(6, TEXTURE_2D, directionalLightShadowMapExtension.getShadowMapId());
				for(RenderExtension extension : renderExtensions) {
					extension.renderSecondPassFullScreen(renderState, secondPassResult);
				}
				GPUProfiler.end();
			}

			engine.getGpuContext().disable(BLEND);
			gBuffer1.getLightAccumulationBuffer().unuse();

			renderAOAndScattering(renderState);

			GPUProfiler.start("MipMap generation AO and light buffer");
			engine.getGpuContext().activeTexture(0);
			engine.getTextureManager().generateMipMaps(TEXTURE_2D, gBuffer1.getLightAccumulationMapOneId());
			GPUProfiler.end();

			if (Config.getInstance().isUseGi()) {
				GL11.glDepthMask(false);
				engine.getGpuContext().disable(DEPTH_TEST);
				engine.getGpuContext().disable(BLEND);
				engine.getGpuContext().cullFace(BACK);
				renderReflections(viewMatrix, projectionMatrix);
			} else {
				gBuffer1.getReflectionBuffer().use(true);
				gBuffer1.getReflectionBuffer().unuse();
			}

			for(RenderExtension extension: renderExtensions) {
				extension.renderSecondPassHalfScreen(renderState, secondPassResult);
			}

			GPUProfiler.start("Blurring");
//        GPUProfiler.start("LightAccumulationMap");
//        TextureManager.getInstance().blurHorinzontal2DTextureRGBA16F(gBuffer.getLightAccumulationMapOneId(), Config.getInstance().WIDTH, Config.getInstance().HEIGHT, 7, 8);
//        GPUProfiler.end();
			GPUProfiler.start("Scattering texture");
			if(Config.getInstance().isScattering() || Config.getInstance().isUseAmbientOcclusion()) {
				engine.getTextureManager().blur2DTextureRGBA16F(gBuffer1.getHalfScreenBuffer().getRenderedTexture(), Config.getInstance().getWidth() / 2, Config.getInstance().getHeight() / 2, 0, 0);
			}
			GPUProfiler.end();
//        TextureManager.getInstance().blur2DTextureRGBA16F(gBuffer.getHalfScreenBuffer().getRenderedTexture(), Config.getInstance().WIDTH / 2, Config.getInstance().HEIGHT / 2, 0, 0);
//        Renderer.getInstance().blur2DTexture(gBuffer.getHalfScreenBuffer().getRenderedTexture(), 0, Config.getInstance().WIDTH / 2, Config.getInstance().HEIGHT / 2, GL30.GL_RGBA16F, false, 1);
//		renderer.blur2DTexture(gBuffer.getLightAccumulationMapOneId(), 0, Config.getInstance().WIDTH, Config.getInstance().HEIGHT, GL30.GL_RGBA16F, false, 1);
//		renderer.blur2DTexture(getLightAccumulationMapOneId(), 0, (int)(Config.getInstance().WIDTH*SECONDPASSSCALE), (int)(Config.getInstance().HEIGHT*SECONDPASSSCALE), GL30.GL_RGBA16F, false, 1);
//		Renderer.getInstance().blur2DTexture(gBuffer.getAmbientOcclusionMapId(), 0, (int)(Config.getInstance().WIDTH), (int)(Config.getInstance().HEIGHT), GL30.GL_RGBA16F, false, 1);
//		renderer.blur2DTextureBilateral(getLightAccumulationMapOneId(), 0, (int)(renderer.WIDTH*SECONDPASSSCALE), (int)(renderer.HEIGHT*SECONDPASSSCALE), GL30.GL_RGBA16F, false, 1);

//		renderer.blur2DTexture(halfScreenTarget.getRenderedTexture(0), (int)(renderer.WIDTH*0.5), (int)(renderer.HEIGHT*0.5), GL11.GL_RGBA8, false, 1);
//		renderer.blur2DTexture(halfScreenTarget.getRenderedTexture(1), (int)(renderer.WIDTH*0.5), (int)(renderer.HEIGHT*0.5), GL11.GL_RGBA8, false, 1);

			engine.getGpuContext().cullFace(BACK);
			engine.getGpuContext().depthFunc(LESS);
			GPUProfiler.end();

			GPUProfiler.end();
			GPUProfiler.start("Combine pass");
			DeferredRenderingBuffer gBuffer2 = engine.getRenderer().getGBuffer();
			RenderTarget finalBuffer = gBuffer2.getFinalBuffer();
			engine.getTextureManager().generateMipMaps(TEXTURE_2D, finalBuffer.getRenderedTexture(0));

			combineProgram.use();
			combineProgram.setUniformAsMatrix4("projectionMatrix", renderState.getCamera().getProjectionMatrixAsBuffer());
			combineProgram.setUniformAsMatrix4("viewMatrix", renderState.getCamera().getViewMatrixAsBuffer());
			combineProgram.setUniform("screenWidth", (float) Config.getInstance().getWidth());
			combineProgram.setUniform("screenHeight", (float) Config.getInstance().getHeight());
			combineProgram.setUniform("camPosition", renderState.getCamera().getPosition());
			combineProgram.setUniform("ambientColor", Config.getInstance().getAmbientLight());
			combineProgram.setUniform("useAmbientOcclusion", Config.getInstance().isUseAmbientOcclusion());
			combineProgram.setUniform("worldExposure", Config.getInstance().getExposure());
			combineProgram.setUniform("AUTO_EXPOSURE_ENABLED", Config.getInstance().isAutoExposureEnabled());
			combineProgram.setUniform("fullScreenMipmapCount", gBuffer2.getFullScreenMipmapCount());
			combineProgram.setUniform("activeProbeCount", engine.getSceneManager().getScene().getEnvironmentProbeManager().getProbes().size());
			combineProgram.bindShaderStorageBuffer(0, gBuffer2.getStorageBuffer());

			finalBuffer.use(true);
			engine.getGpuContext().disable(DEPTH_TEST);

			engine.getGpuContext().bindTexture(0, TEXTURE_2D, gBuffer2.getColorReflectivenessMap());
			engine.getGpuContext().bindTexture(1, TEXTURE_2D, gBuffer2.getLightAccumulationMapOneId());
			engine.getGpuContext().bindTexture(2, TEXTURE_2D, gBuffer2.getLightAccumulationBuffer().getRenderedTexture(1));
			engine.getGpuContext().bindTexture(3, TEXTURE_2D, gBuffer2.getMotionMap());
			engine.getGpuContext().bindTexture(4, TEXTURE_2D, gBuffer2.getPositionMap());
			engine.getGpuContext().bindTexture(5, TEXTURE_2D, gBuffer2.getNormalMap());
			engine.getGpuContext().bindTexture(6, engine.getTextureManager().getCubeMap());
			engine.getSceneManager().getScene().getEnvironmentProbeManager().getEnvironmentMapsArray().bind(engine.getGpuContext(), 7);
			engine.getGpuContext().bindTexture(8, TEXTURE_2D, gBuffer2.getReflectionMap());
			engine.getGpuContext().bindTexture(9, TEXTURE_2D, gBuffer2.getRefractedMap());
			engine.getGpuContext().bindTexture(11, TEXTURE_2D, gBuffer2.getAmbientOcclusionScatteringMap());

			gpuContext.getFullscreenBuffer().draw();

			if (null == null) {
				engine.getGpuContext().getFrontBuffer().use(true);
			} else {
				((RenderTarget) null).use(true);
			}
			GPUProfiler.start("Post processing");
			postProcessProgram.use();
			engine.getGpuContext().bindTexture(0, TEXTURE_2D, finalBuffer.getRenderedTexture(0));
			postProcessProgram.setUniform("screenWidth", (float) Config.getInstance().getWidth());
			postProcessProgram.setUniform("screenHeight", (float) Config.getInstance().getHeight());
			postProcessProgram.setUniform("worldExposure", Config.getInstance().getExposure());
			postProcessProgram.setUniform("AUTO_EXPOSURE_ENABLED", Config.getInstance().isAutoExposureEnabled());
			postProcessProgram.setUniform("usePostProcessing", Config.getInstance().isEnablePostprocessing());
			try {
				postProcessProgram.setUniform("cameraRightDirection", renderState.getCamera().getRightDirection());
				postProcessProgram.setUniform("cameraViewDirection", renderState.getCamera().getViewDirection());
			} catch (IllegalStateException e) {
				// Normalizing zero length vector
			}
			postProcessProgram.setUniform("seconds", (float) engine.getRenderManager().getDeltaInS());
			postProcessProgram.bindShaderStorageBuffer(0, gBuffer2.getStorageBuffer());
//        postProcessProgram.bindShaderStorageBuffer(1, engine.getRenderer().getMaterialManager().getMaterialBuffer());
			engine.getGpuContext().bindTexture(1, TEXTURE_2D, gBuffer2.getNormalMap());
			engine.getGpuContext().bindTexture(2, TEXTURE_2D, gBuffer2.getMotionMap());
			engine.getGpuContext().bindTexture(3, TEXTURE_2D, gBuffer2.getLightAccumulationMapOneId());
			engine.getGpuContext().bindTexture(4, TEXTURE_2D, engine.getTextureManager().getLensFlareTexture().getTextureId());
			gpuContext.getFullscreenBuffer().draw();

			GPUProfiler.end();
			GPUProfiler.end();
		} else {
			engine.getGpuContext().disable(DEPTH_TEST);
			engine.getGpuContext().getFrontBuffer().use(true);
			engine.getRenderer().drawToQuad(Config.getInstance().getDirectTextureOutputTextureIndex());
		}
		if (Config.getInstance().isDebugframeEnabled()) {
			ArrayList<Texture> textures = new ArrayList<>(engine.getTextureManager().getTextures().values());
			drawToQuad(engine.getTextureManager().getTexture("hp/assets/models/textures/gi_flag.png", true).getTextureId(), engine.getGpuContext().getDebugBuffer(), engine.getProgramManager().getDebugFrameProgram());
//			drawToQuad(engine.getSimpleScene().getAreaLightSystem().getDepthMapForAreaLight(engine.getSimpleScene().getAreaLightSystem().getAreaLights().get(0)), engine.getGpuContext().getDebugBuffer(), engine.getProgramManager().getDebugFrameProgram());

//			for(int i = 0; i < 6; i++) {
//				drawToQuad(engine.getEnvironmentProbeManager().getProbes().get(0).getSampler().getCubeMapFaceViews()[3][i], sixDebugBuffers.get(i));
//			}


//			DEBUG POINT LIGHT SHADOWS

//			int faceView = OpenGLContext.getInstance().genTextures();
//			GL43.glTextureView(faceView, GlTextureTarget.TEXTURE_2D.glTarget, directionalLightSystem.getPointLightDepthMapsArrayBack(),
//					GL30.GL_RGBA16F, 0, 1, 0, 1);
//			drawToQuad(faceView, sixDebugBuffers.get(0));
//			faceView = OpenGLContext.getInstance().genTextures();
//			GL43.glTextureView(faceView, GlTextureTarget.TEXTURE_2D.glTarget, directionalLightSystem.getPointLightDepthMapsArrayFront(),
//					GL30.GL_RGBA16F, 0, 1, 0, 1);
//			drawToQuad(faceView, sixDebugBuffers.get(1));
//			GL11.glDeleteTextures(faceView);


//			DEBUG PROBES

//            int[] faceViews = new int[6];
//			int index = 0;
//            for(int i = 0; i < 6; i++) {
//                faceViews[i] = engine.getGpuContext().genTextures();
//				int cubeMapArray = engine.getScene().getProbeSystem().getStrategy().getCubemapArrayRenderTarget().getCubeMapArray().getTextureID();
//				GL43.glTextureView(faceViews[i], GlTextureTarget.TEXTURE_2D.glTarget, cubeMapArray, GL_RGBA16F, 0, 10, (6*index)+i, 1);
//				drawToQuad(faceViews[i], sixDebugBuffers.get(i), engine.getProgramManager().getDebugFrameProgram());
//			}
//            for(int i = 0; i < 6; i++) {
//                GL11.glDeleteTextures(faceViews[i]);
//            }

		}

		GPUProfiler.start("Create new fence");
        engine.getGpuContext().createNewGPUFenceForReadState(renderState);
		GPUProfiler.end();
		GPUProfiler.start("Waiting for driver");
		GPUProfiler.start("Poll events");
		glfwPollEvents();
		GPUProfiler.end();
		GPUProfiler.start("Swap buffers");
        glfwSwapBuffers(engine.getGpuContext().getWindowHandle());
		GPUProfiler.end();
		GPUProfiler.end();
		GPUProfiler.end();

	}

	@Override
	public void drawToQuad(int texture) {
        drawToQuad(texture, engine.getGpuContext().getFullscreenBuffer(), engine.getProgramManager().getRenderToQuadProgram());
	}

	public void drawToQuad(int texture, VertexBuffer buffer) {
        drawToQuad(texture, buffer, engine.getProgramManager().getRenderToQuadProgram());
	}
	
	private void drawToQuad(int texture, VertexBuffer buffer, Program program) {
		program.use();
        engine.getGpuContext().disable(GlCap.DEPTH_TEST);

        engine.getGpuContext().bindTexture(0, GlTextureTarget.TEXTURE_2D, texture);
        engine.getGpuContext().bindTexture(1, GlTextureTarget.TEXTURE_2D, gBuffer.getNormalMap());

		buffer.draw();
	}

	public int drawLines(Program program) {
		float[] points = new float[linePoints.size() * 3];
		for (int i = 0; i < linePoints.size(); i++) {
			Vector3f point = linePoints.get(i);
			points[3 * i] = point.x;
			points[3*i + 1] = point.y;
			points[3*i + 2] = point.z;
		}
		buffer.putValues(points);
		buffer.upload().join();
		buffer.drawDebugLines();
		glFinish();
		linePoints.clear();
        return points.length / 3 / 2;
	}

	@Override
	public void batchLine(Vector3f from, Vector3f to) {
		linePoints.add(from);
		linePoints.add(to);
	}

	private List<Vector3f> linePoints = new ArrayList<>();

    @Override
	public DeferredRenderingBuffer getGBuffer() {
		return gBuffer;
	}

	@Override
	public List<RenderExtension> getRenderExtensions() {
		return renderExtensions;
	}

	private void doPointLights(RenderState renderState, FloatBuffer viewMatrix, FloatBuffer projectionMatrix) {
		if (engine.getSceneManager().getScene().getPointLights().isEmpty()) {
			return;
		}
		GPUProfiler.start("Seconds pass PointLights");
		gpuContext.bindTexture(0, TEXTURE_2D, engine.getRenderer().getGBuffer().getPositionMap());
		gpuContext.bindTexture(1, TEXTURE_2D, engine.getRenderer().getGBuffer().getNormalMap());
		gpuContext.bindTexture(2, TEXTURE_2D, engine.getRenderer().getGBuffer().getColorReflectivenessMap());
		gpuContext.bindTexture(3, TEXTURE_2D, engine.getRenderer().getGBuffer().getMotionMap());
		gpuContext.bindTexture(4, TEXTURE_2D, engine.getRenderer().getGBuffer().getLightAccumulationMapOneId());
		gpuContext.bindTexture(5, TEXTURE_2D, engine.getRenderer().getGBuffer().getVisibilityMap());
		engine.getScene().getPointLightSystem().getShadowMapStrategy().bindTextures();
		// TODO: Add glbindimagetexture to openglcontext class
		GL42.glBindImageTexture(4, engine.getRenderer().getGBuffer().getLightAccumulationMapOneId(), 0, false, 0, GL15.GL_READ_WRITE, GL30.GL_RGBA16F);
		secondPassPointComputeProgram.use();
		secondPassPointComputeProgram.setUniform("pointLightCount", engine.getSceneManager().getScene().getPointLights().size());
		secondPassPointComputeProgram.setUniform("screenWidth", (float) Config.getInstance().getWidth());
		secondPassPointComputeProgram.setUniform("screenHeight", (float) Config.getInstance().getHeight());
		secondPassPointComputeProgram.setUniformAsMatrix4("viewMatrix", viewMatrix);
		secondPassPointComputeProgram.setUniformAsMatrix4("projectionMatrix", projectionMatrix);
		secondPassPointComputeProgram.setUniform("maxPointLightShadowmaps", MAX_POINTLIGHT_SHADOWMAPS);
		secondPassPointComputeProgram.bindShaderStorageBuffer(1, renderState.getMaterialBuffer());
		secondPassPointComputeProgram.bindShaderStorageBuffer(2, engine.getScene().getPointLightSystem().getLightBuffer());
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
			boolean camInsideLightVolume = tubeLight.getMinMaxWorld().contains(camPositionV4);
			if (camInsideLightVolume) {
				GL11.glCullFace(GL11.GL_FRONT);
				GL11.glDepthFunc(GL11.GL_GEQUAL);
			} else {
				GL11.glCullFace(GL11.GL_BACK);
				GL11.glDepthFunc(GL11.GL_LEQUAL);
			}
			secondPassTubeProgram.setUniform("lightPosition", tubeLight.getEntity().getPosition());
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

		engine.getGpuContext().disable(CULL_FACE);
		engine.getGpuContext().disable(DEPTH_TEST);
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
//            boolean camInsideLightVolume = new AABB(areaLight.getPosition(), 2*areaLight.getScale().x, 2*areaLight.getScale().y, 2*areaLight.getScale().z).contains(camPositionV4);
//            if (camInsideLightVolume) {
//                GL11.glCullFace(GL11.GL_FRONT);
//                GL11.glDepthFunc(GL11.GL_GEQUAL);
//            } else {
//                GL11.glCullFace(GL11.GL_BACK);
//                GL11.glDepthFunc(GL11.GL_LEQUAL);
//            }
			secondPassAreaProgram.setUniform("lightPosition", areaLight.getEntity().getPosition());
			secondPassAreaProgram.setUniform("lightRightDirection", areaLight.getEntity().getRightDirection());
			secondPassAreaProgram.setUniform("lightViewDirection", areaLight.getEntity().getViewDirection());
			secondPassAreaProgram.setUniform("lightUpDirection", areaLight.getEntity().getUpDirection());
			secondPassAreaProgram.setUniform("lightWidth", areaLight.getWidth());
			secondPassAreaProgram.setUniform("lightHeight", areaLight.getHeight());
			secondPassAreaProgram.setUniform("lightRange", areaLight.getRange());
			secondPassAreaProgram.setUniform("lightDiffuse", areaLight.getColor());
			secondPassAreaProgram.setUniformAsMatrix4("shadowMatrix", areaLight.getViewProjectionMatrixAsBuffer());

			// TODO: Add textures to arealights
//            try {
//                GL13.glActiveTexture(GL13.GL_TEXTURE0 + 8);
//                Texture lightTexture = renderer.getTextureManager().getDiffuseTexture("brick.hptexture");
//                GL11.glBindTexture(GL11.GL_TEXTURE_2D, lightTexture.getTextureId());
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
			engine.getGpuContext().bindTexture(9, GlTextureTarget.TEXTURE_2D, engine.getScene().getAreaLightSystem().getDepthMapForAreaLight(areaLight));
			gpuContext.getFullscreenBuffer().draw();
//            areaLight.getVertexBuffer().drawDebug();
		}

		GPUProfiler.end();
	}

	private void renderAOAndScattering(RenderState renderState) {
		if (!Config.getInstance().isUseAmbientOcclusion() && !Config.getInstance().isScattering()) {
			return;
		}
		GPUProfiler.start("Scattering and AO");
		DeferredRenderingBuffer gBuffer = engine.getRenderer().getGBuffer();
		gBuffer.getHalfScreenBuffer().use(true);
		engine.getGpuContext().disable(DEPTH_TEST);
		engine.getGpuContext().bindTexture(0, TEXTURE_2D, gBuffer.getPositionMap());
		engine.getGpuContext().bindTexture(1, TEXTURE_2D, gBuffer.getNormalMap());
		engine.getGpuContext().bindTexture(2, TEXTURE_2D, gBuffer.getColorReflectivenessMap());
		engine.getGpuContext().bindTexture(3, TEXTURE_2D, gBuffer.getMotionMap());
		engine.getGpuContext().bindTexture(6, TEXTURE_2D, directionalLightShadowMapExtension.getShadowMapId());
		engine.getScene().getPointLightSystem().getShadowMapStrategy().bindTextures();
		engine.getSceneManager().getScene().getEnvironmentProbeManager().getEnvironmentMapsArray(3).bind(engine.getGpuContext(), 8);

		if(directionalLightShadowMapExtension.getVoxelConeTracingExtension() != null) {
			gpuContext.bindTexture(13, TEXTURE_3D, directionalLightShadowMapExtension.getVoxelConeTracingExtension().getVoxelGrids().get(0).getCurrentVoxelSource());
		}

//		halfScreenBuffer.setTargetTexture(halfScreenBuffer.getRenderedTexture(), 0);
		aoScatteringProgram.use();
		aoScatteringProgram.setUniform("eyePosition", renderState.getCamera().getPosition());
		aoScatteringProgram.setUniform("useAmbientOcclusion", Config.getInstance().isUseAmbientOcclusion());
		aoScatteringProgram.setUniform("ambientOcclusionRadius", Config.getInstance().getAmbientocclusionRadius());
		aoScatteringProgram.setUniform("ambientOcclusionTotalStrength", Config.getInstance().getAmbientocclusionTotalStrength());
		aoScatteringProgram.setUniform("screenWidth", (float) Config.getInstance().getWidth() / 2);
		aoScatteringProgram.setUniform("screenHeight", (float) Config.getInstance().getHeight() / 2);
		aoScatteringProgram.setUniformAsMatrix4("viewMatrix", renderState.getCamera().getViewMatrixAsBuffer());
		aoScatteringProgram.setUniformAsMatrix4("projectionMatrix", renderState.getCamera().getProjectionMatrixAsBuffer());
		aoScatteringProgram.setUniformAsMatrix4("shadowMatrix", renderState.getDirectionalLightViewProjectionMatrixAsBuffer());
		aoScatteringProgram.setUniform("lightDirection", renderState.getDirectionalLightState().directionalLightDirection);
		aoScatteringProgram.setUniform("lightDiffuse", renderState.getDirectionalLightState().directionalLightColor);
		aoScatteringProgram.setUniform("scatterFactor", renderState.getDirectionalLightState().directionalLightScatterFactor);
		aoScatteringProgram.setUniform("time", (int) System.currentTimeMillis());
		aoScatteringProgram.setUniform("useVoxelGrid", directionalLightShadowMapExtension.getVoxelConeTracingExtension() != null);
		if(directionalLightShadowMapExtension.getVoxelConeTracingExtension() != null) {
			aoScatteringProgram.bindShaderStorageBuffer(5, renderState.getState(directionalLightShadowMapExtension.getVoxelConeTracingExtension().getVoxelGridBufferRef()).getVoxelGridBuffer());
		}

		aoScatteringProgram.setUniform("maxPointLightShadowmaps", MAX_POINTLIGHT_SHADOWMAPS);
		aoScatteringProgram.setUniform("pointLightCount", engine.getSceneManager().getScene().getPointLights().size());
		aoScatteringProgram.bindShaderStorageBuffer(2, engine.getScene().getPointLightSystem().getLightBuffer());

		engine.getSceneManager().getScene().getEnvironmentProbeManager().bindEnvironmentProbePositions(aoScatteringProgram);
		gpuContext.getFullscreenBuffer().draw();
		engine.getGpuContext().enable(DEPTH_TEST);
		engine.getTextureManager().generateMipMaps(TEXTURE_2D, gBuffer.getHalfScreenBuffer().getRenderedTexture());
		GPUProfiler.end();
	}

	private void renderReflections(FloatBuffer viewMatrix, FloatBuffer projectionMatrix) {
		GPUProfiler.start("Reflections and AO");
		DeferredRenderingBuffer gBuffer = engine.getRenderer().getGBuffer();
		RenderTarget reflectionBuffer = gBuffer.getReflectionBuffer();

		engine.getGpuContext().bindTexture(0, TEXTURE_2D, gBuffer.getPositionMap());
		engine.getGpuContext().bindTexture(1, TEXTURE_2D, gBuffer.getNormalMap());
		engine.getGpuContext().bindTexture(2, TEXTURE_2D, gBuffer.getColorReflectivenessMap());
		engine.getGpuContext().bindTexture(3, TEXTURE_2D, gBuffer.getMotionMap());
		engine.getGpuContext().bindTexture(4, TEXTURE_2D, gBuffer.getLightAccumulationMapOneId());
		engine.getGpuContext().bindTexture(5, TEXTURE_2D, gBuffer.getFinalMap());
		engine.getGpuContext().bindTexture(6, engine.getTextureManager().getCubeMap());
//        GL13.glActiveTexture(GL13.GL_TEXTURE0 + 7);
//        reflectionBuffer.getRenderedTexture(0);
		engine.getSceneManager().getScene().getEnvironmentProbeManager().getEnvironmentMapsArray(3).bind(engine.getGpuContext(), 8);
		engine.getGpuContext().bindTexture(9, engine.getTextureManager().getCubeMap());
		engine.getSceneManager().getScene().getEnvironmentProbeManager().getEnvironmentMapsArray(0).bind(engine.getGpuContext(), 10);
		engine.getGpuContext().bindTexture(11, TEXTURE_2D, reflectionBuffer.getRenderedTexture());

		int copyTextureId = GL11.glGenTextures();
		engine.getGpuContext().bindTexture(11, TEXTURE_2D, copyTextureId);
		GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RGBA16F, reflectionBuffer.getWidth(), reflectionBuffer.getHeight(), 0, GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE, (FloatBuffer) null);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
		GL43.glCopyImageSubData(reflectionBuffer.getRenderedTexture(), GL11.GL_TEXTURE_2D, 0, 0, 0, 0,
				copyTextureId, GL11.GL_TEXTURE_2D, 0, 0, 0, 0,
				reflectionBuffer.getWidth(), reflectionBuffer.getHeight(), 1);
		engine.getGpuContext().bindTexture(11, TEXTURE_2D, copyTextureId);

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
			engine.getSceneManager().getScene().getEnvironmentProbeManager().bindEnvironmentProbePositions(reflectionProgram);
			reflectionProgram.setUniform("activeProbeCount", engine.getSceneManager().getScene().getEnvironmentProbeManager().getProbes().size());
			reflectionProgram.bindShaderStorageBuffer(0, gBuffer.getStorageBuffer());
			gpuContext.getFullscreenBuffer().draw();
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
			tiledProbeLightingProgram.setUniform("activeProbeCount", engine.getSceneManager().getScene().getEnvironmentProbeManager().getProbes().size());
			engine.getSceneManager().getScene().getEnvironmentProbeManager().bindEnvironmentProbePositions(tiledProbeLightingProgram);
			tiledProbeLightingProgram.dispatchCompute(reflectionBuffer.getWidth() / 16, reflectionBuffer.getHeight() / 16, 1); //16+1
			//		GL42.glMemoryBarrier(GL42.GL_ALL_BARRIER_BITS);
		}

		GL11.glDeleteTextures(copyTextureId);
		GPUProfiler.end();
	}


}
