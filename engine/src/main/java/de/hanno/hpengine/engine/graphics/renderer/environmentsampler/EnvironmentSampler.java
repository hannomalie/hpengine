package de.hanno.hpengine.engine.graphics.renderer.environmentsampler;

import com.google.common.eventbus.Subscribe;
import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.camera.Camera;
import de.hanno.hpengine.engine.config.Config;
import de.hanno.hpengine.engine.entity.Entity;
import de.hanno.hpengine.engine.event.MaterialChangedEvent;
import de.hanno.hpengine.engine.graphics.GpuContext;
import de.hanno.hpengine.engine.graphics.light.directional.DirectionalLight;
import de.hanno.hpengine.engine.graphics.light.directional.DirectionalLightSystem;
import de.hanno.hpengine.engine.graphics.light.tube.TubeLight;
import de.hanno.hpengine.engine.graphics.light.area.AreaLight;
import de.hanno.hpengine.engine.graphics.light.point.PointLight;
import de.hanno.hpengine.engine.graphics.renderer.RenderBatch;
import de.hanno.hpengine.engine.graphics.renderer.Renderer;
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DrawStrategy;
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.GBuffer;
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.ColorAttachmentDefinition;
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.CubeMapArrayRenderTarget;
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.RenderTarget;
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.RenderTargetBuilder;
import de.hanno.hpengine.engine.graphics.shader.ComputeShaderProgram;
import de.hanno.hpengine.engine.graphics.shader.Program;
import de.hanno.hpengine.engine.graphics.shader.ProgramManager;
import de.hanno.hpengine.engine.graphics.shader.Shader;
import de.hanno.hpengine.engine.graphics.shader.define.Defines;
import de.hanno.hpengine.engine.graphics.state.RenderState;
import de.hanno.hpengine.engine.model.QuadVertexBuffer;
import de.hanno.hpengine.engine.model.VertexBuffer;
import de.hanno.hpengine.engine.model.texture.CubeMapArray;
import de.hanno.hpengine.engine.scene.*;
import de.hanno.hpengine.engine.transform.Spatial;
import de.hanno.hpengine.util.TypedTuple;
import de.hanno.hpengine.util.Util;
import de.hanno.hpengine.util.stopwatch.GPUProfiler;
import org.joml.*;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

import java.io.File;
import java.lang.Math;
import java.nio.FloatBuffer;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static de.hanno.hpengine.engine.graphics.light.area.AreaLightSystem.MAX_AREALIGHT_SHADOWMAPS;
import static de.hanno.hpengine.engine.graphics.renderer.constants.BlendMode.FUNC_ADD;
import static de.hanno.hpengine.engine.graphics.renderer.constants.BlendMode.Factor.ONE;
import static de.hanno.hpengine.engine.graphics.renderer.constants.CullMode.BACK;
import static de.hanno.hpengine.engine.graphics.renderer.constants.GlCap.*;
import static de.hanno.hpengine.engine.graphics.renderer.constants.GlDepthFunc.LEQUAL;
import static de.hanno.hpengine.engine.graphics.renderer.constants.GlDepthFunc.LESS;
import static de.hanno.hpengine.engine.graphics.renderer.constants.GlTextureTarget.TEXTURE_2D;
import static de.hanno.hpengine.engine.graphics.renderer.constants.GlTextureTarget.TEXTURE_CUBE_MAP;

public class EnvironmentSampler extends Entity {
	public static volatile boolean deferredRenderingForProbes = false;
	private final Engine engine;
	private Program cubeMapProgram;
	private Program cubeMapLightingProgram;
	private Program depthPrePassProgram;
	private ComputeShaderProgram tiledProbeLightingProgram;
	private ComputeShaderProgram cubemapRadianceProgram;
	private Program cubemapRadianceFragmentProgram;
	private FloatBuffer entityBuffer = BufferUtils.createFloatBuffer(16);
	private Renderer renderer;
	transient private boolean drawnOnce = false;
	transient Set<Integer> sidesDrawn = new HashSet<>();
	transient private EnvironmentProbe probe;
	private VertexBuffer fullscreenBuffer;
	private int cubeMapView;
	private int cubeMapView1;
	private int cubeMapView2;

	private int cubeMapFaceViews[][] = new int[4][6];
	private Program secondPassPointProgram;
	private Program secondPassTubeProgram;
	private Program secondPassAreaProgram;
	private Program secondPassDirectionalProgram;
	
	private RenderTarget renderTarget;
	private Camera camera;

	public EnvironmentSampler(Entity entity, Engine engine, EnvironmentProbe probe, Vector3f position, int width, int height, int probeIndex) throws Exception {
		Camera camera = new Camera(entity, 0.1f, 5000f, 90f, 1f);
		entity.addComponent(camera);
        camera.setWidth(width);
		camera.setWidth(height);
		this.camera = camera;
        translate(position);
        this.engine = engine;
        this.renderer = engine.getRenderer();
		this.probe = probe;
		float far = 5000f;
		float near = 0.1f;
		float fov = 90f;
		camera.setFar(far);
		camera.setNear(near);
		camera.setFov(fov);
		camera.setRatio(1f);
		setParent(probe.getEntity());
		Quaternionf cubeMapCamInitialOrientation = new Quaternionf().identity();
		rotate(cubeMapCamInitialOrientation);

        ProgramManager programManager = engine.getProgramManager();
		cubeMapProgram = programManager.getProgramFromFileNames("first_pass_vertex.glsl", "cubemap_fragment.glsl", new Defines());
		depthPrePassProgram = programManager.getProgramFromFileNames("first_pass_vertex.glsl", "cubemap_fragment.glsl", new Defines());
		cubeMapLightingProgram = programManager.getProgramFromFileNames("first_pass_vertex.glsl", "cubemap_lighting_fragment.glsl", new Defines());
		tiledProbeLightingProgram = programManager.getComputeProgram("tiled_probe_lighting_probe_rendering_compute.glsl");
		cubemapRadianceProgram = programManager.getComputeProgram("cubemap_radiance_compute.glsl");
		cubemapRadianceFragmentProgram = programManager.getProgramFromFileNames("passthrough_vertex.glsl", "cubemap_radiance_fragment.glsl", new Defines());
		secondPassPointProgram = programManager.getProgram(Shader.ShaderSourceFactory.getShaderSource(new File(Shader.getDirectory() + "second_pass_point_vertex.glsl")), Shader.ShaderSourceFactory.getShaderSource(new File(Shader.getDirectory() + "second_pass_point_fragment.glsl")), new Defines());
		secondPassTubeProgram = programManager.getProgram(Shader.ShaderSourceFactory.getShaderSource(new File(Shader.getDirectory() + "second_pass_point_vertex.glsl")), Shader.ShaderSourceFactory.getShaderSource(new File(Shader.getDirectory() + "second_pass_tube_fragment.glsl")), new Defines());
		secondPassAreaProgram = programManager.getProgram(Shader.ShaderSourceFactory.getShaderSource(new File(Shader.getDirectory() + "second_pass_area_vertex.glsl")), Shader.ShaderSourceFactory.getShaderSource(new File(Shader.getDirectory() + "second_pass_area_fragment.glsl")), new Defines());
		secondPassDirectionalProgram = programManager.getProgram(Shader.ShaderSourceFactory.getShaderSource(new File(Shader.getDirectory() + "second_pass_directional_vertex.glsl")), Shader.ShaderSourceFactory.getShaderSource(new File(Shader.getDirectory() + "second_pass_directional_fragment.glsl")), new Defines());

        CubeMapArrayRenderTarget cubeMapArrayRenderTarget = engine.getSceneManager().getScene().getEnvironmentProbeManager().getCubeMapArrayRenderTarget();
		cubeMapView = GL11.glGenTextures();
		cubeMapView1 = GL11.glGenTextures();
		cubeMapView2 = GL11.glGenTextures();
		GpuContext.exitOnGLError("EnvironmentSampler before view creation");
		for (int z = 0; z < 6; z++) {
			cubeMapFaceViews[0][z] = GL11.glGenTextures();
			cubeMapFaceViews[1][z] = GL11.glGenTextures();
            cubeMapFaceViews[2][z] = GL11.glGenTextures();
            cubeMapFaceViews[3][z] = GL11.glGenTextures();
			//GL43.glTextureView(cubeMapFaceViews[i][z], GL11.GL_TEXTURE_2D, cubeMapArrayRenderTarget.getCubeMapArray(i).getTextureID(), cubeMapArrayRenderTarget.getCubeMapArray(i).getInternalFormat(), 0, 1, 6 * probe.getIndex() + z, 1);
			GL43.glTextureView(cubeMapFaceViews[0][z], GL11.GL_TEXTURE_2D, cubeMapArrayRenderTarget.getCubeMapArray(0).getTextureID(), cubeMapArrayRenderTarget.getCubeMapArray(0).getInternalFormat(), 0, 1, 6 * probeIndex + z, 1);
			GL43.glTextureView(cubeMapFaceViews[1][z], GL11.GL_TEXTURE_2D, cubeMapArrayRenderTarget.getCubeMapArray(1).getTextureID(), cubeMapArrayRenderTarget.getCubeMapArray(1).getInternalFormat(), 0, 1, 6 * probeIndex + z, 1);
            GL43.glTextureView(cubeMapFaceViews[2][z], GL11.GL_TEXTURE_2D, cubeMapArrayRenderTarget.getCubeMapArray(2).getTextureID(), cubeMapArrayRenderTarget.getCubeMapArray(2).getInternalFormat(), 0, 1, 6 * probeIndex + z, 1);
            GL43.glTextureView(cubeMapFaceViews[3][z], GL11.GL_TEXTURE_2D, cubeMapArrayRenderTarget.getCubeMapArray(3).getTextureID(), cubeMapArrayRenderTarget.getCubeMapArray(3).getInternalFormat(), 0, 1, 6 * probeIndex + z, 1);
		}
        GL43.glTextureView(cubeMapView, GL13.GL_TEXTURE_CUBE_MAP, cubeMapArrayRenderTarget.getCubeMapArray(0).getTextureID(), cubeMapArrayRenderTarget.getCubeMapArray(0).getInternalFormat(), 0, engine.getSceneManager().getScene().getEnvironmentProbeManager().CUBEMAP_MIPMAP_COUNT, 6*probeIndex, 6);
        GL43.glTextureView(cubeMapView1, GL13.GL_TEXTURE_CUBE_MAP, cubeMapArrayRenderTarget.getCubeMapArray(1).getTextureID(), cubeMapArrayRenderTarget.getCubeMapArray(1).getInternalFormat(), 0, engine.getSceneManager().getScene().getEnvironmentProbeManager().CUBEMAP_MIPMAP_COUNT, 6*probeIndex, 6);
        GL43.glTextureView(cubeMapView2, GL13.GL_TEXTURE_CUBE_MAP, cubeMapArrayRenderTarget.getCubeMapArray(2).getTextureID(), cubeMapArrayRenderTarget.getCubeMapArray(2).getInternalFormat(), 0, engine.getSceneManager().getScene().getEnvironmentProbeManager().CUBEMAP_MIPMAP_COUNT, 6*probeIndex, 6);


		renderTarget = new RenderTargetBuilder<RenderTargetBuilder, RenderTarget>(engine.getGpuContext()).setWidth(EnvironmentProbeManager.RESOLUTION )
								.setHeight(EnvironmentProbeManager.RESOLUTION )
								.add(new ColorAttachmentDefinition()
										.setInternalFormat(cubeMapArrayRenderTarget.getCubeMapArray(3).getInternalFormat()))
								.build();

		fullscreenBuffer = new QuadVertexBuffer(engine.getGpuContext(), true);
		fullscreenBuffer.upload();
		engine.getEventBus().register(this);
		GpuContext.exitOnGLError("EnvironmentSampler constructor");
	}

	public void drawCubeMap(boolean urgent, RenderState extract) {
		drawCubeMapSides(urgent, extract);
	}

	private void drawCubeMapSides(boolean urgent, RenderState extract) {
		Scene scene = engine.getSceneManager().getScene();

		GPUProfiler.start("Cubemap render 6 sides");
		Quaternionf initialOrientation = getRotation();
		Vector3f initialPosition = getPosition();

		DirectionalLight light = engine.getScene().getEntitySystems().get(DirectionalLightSystem.class).getDirectionalLight();
		engine.getSceneManager().getScene().getEnvironmentProbeManager().getEnvironmentMapsArray().bind(engine.getGpuContext(), 8);
		engine.getSceneManager().getScene().getEnvironmentProbeManager().getEnvironmentMapsArray(0).bind(engine.getGpuContext(), 10);

		engine.getGpuContext().disable(DEPTH_TEST);
		engine.getGpuContext().depthFunc(LEQUAL);

		renderTarget.use(false);
		Program cubeMapProgram = this.cubeMapProgram;
		bindProgramSpecificsPerCubeMap(cubeMapProgram);

		boolean filteringRequired = false;
		TypedTuple<Matrix4f[], Matrix4f[]> viewProjectionMatrices = Util.getCubeViewProjectionMatricesForPosition(getPosition());
		FloatBuffer viewMatrixBuffer = BufferUtils.createFloatBuffer(16);
		FloatBuffer projectionMatrixBuffer = BufferUtils.createFloatBuffer(16);
		FloatBuffer viewProjectionMatrixBuffer = BufferUtils.createFloatBuffer(16);

		for (int i = 0; i < 6; i++) {
			viewProjectionMatrices.getLeft()[i].get(viewMatrixBuffer);
			viewProjectionMatrices.getRight()[i].get(projectionMatrixBuffer);
			new Matrix4f().set(viewProjectionMatrices.getRight()[i]).mul(viewProjectionMatrices.getLeft()[i]).get(viewProjectionMatrixBuffer);

			rotateForIndex(i, this);
			boolean fullReRenderRequired = urgent || !drawnOnce;
			boolean aPointLightHasMoved = !scene.getPointLights().stream()
					.filter(e -> probe.getBox().containsOrIntersectsSphere(e.getEntity().getPosition(), e.getRadius()))
					.filter(e -> e.getEntity().hasMoved()).collect(Collectors.toList()).isEmpty();
			boolean areaLightHasMoved = !engine.getScene().getAreaLightSystem().getAreaLights().stream().filter(e -> e.getEntity().hasMoved()).collect(Collectors.toList()).isEmpty();
			boolean reRenderLightingRequired = light.entity.hasMoved() || aPointLightHasMoved || areaLightHasMoved;
			boolean noNeedToRedraw = !urgent && !fullReRenderRequired && !reRenderLightingRequired;

			if (noNeedToRedraw) {  // early exit if only static objects visible and lights didn't change
//				continue;
			} else if (reRenderLightingRequired) {
//				cubeMapLightingProgram.use();
			} else if (fullReRenderRequired) {
//				cubeMapProgram.use();
			}
			filteringRequired = true;

			GPUProfiler.start("side " + i);
			if (deferredRenderingForProbes) {
				if (!sidesDrawn.contains(i)) {
					GPUProfiler.start("Switch attachment");
					engine.getSceneManager().getScene().getEnvironmentProbeManager().getCubeMapArrayRenderTarget().setCubeMapFace(0, probe.getIndex(), i);
					engine.getSceneManager().getScene().getEnvironmentProbeManager().getCubeMapArrayRenderTarget().setCubeMapFace(1, probe.getIndex(), i);
					engine.getSceneManager().getScene().getEnvironmentProbeManager().getCubeMapArrayRenderTarget().setCubeMapFace(2, probe.getIndex(), i);
					engine.getGpuContext().clearDepthAndColorBuffer();
					GPUProfiler.end();

					GPUProfiler.start("Fill GBuffer");
					drawFirstPass(i, this.getCamera(), scene.getEntities(), extract);
					engine.getSceneManager().getScene().getEnvironmentProbeManager().getCubeMapArrayRenderTarget().resetAttachments();

					GPUProfiler.end();
				}
				GPUProfiler.start("Second pass");
				engine.getSceneManager().getScene().getEnvironmentProbeManager().getCubeMapArrayRenderTarget().setCubeMapFace(3, 0, probe.getIndex(), i);
				engine.getGpuContext().clearDepthAndColorBuffer();
				drawSecondPass(i, light, scene.getPointLights(), scene.getTubeLights(), scene.getAreaLights());
				GPUProfiler.end();
				registerSideAsDrawn(i);
			} else {
				engine.getGpuContext().depthMask(true);
				engine.getGpuContext().enable(DEPTH_TEST);
				engine.getGpuContext().depthFunc(LEQUAL);
				engine.getSceneManager().getScene().getEnvironmentProbeManager().getCubeMapArrayRenderTarget().setCubeMapFace(3, 0, probe.getIndex(), i);
				engine.getGpuContext().clearDepthAndColorBuffer();
				drawEntities(extract, cubeMapProgram, viewMatrixBuffer, projectionMatrixBuffer, viewProjectionMatrixBuffer);
			}
			GPUProfiler.end();
		}
		if (filteringRequired) {
			generateCubeMapMipMaps();
		}
		translation(initialPosition);
		rotation(initialOrientation);
		GPUProfiler.end();
	}

	private void registerSideAsDrawn(int i) {
		sidesDrawn.add(i);
		if(sidesDrawn.size() == 6) {
			drawnOnce = true;
		}
	}
	
	public void resetDrawing() {
		sidesDrawn.clear();
		drawnOnce = false;
	}
	
	@Subscribe
	public void handle(MaterialChangedEvent e) {
		resetDrawing();
		engine.getScene().getEnvironmentProbeManager().addRenderProbeCommand(probe, true);
		engine.getScene().getEnvironmentProbeManager().addRenderProbeCommand(probe, true);
		engine.getScene().getEnvironmentProbeManager().addRenderProbeCommand(probe, true);
	}

	private void bindProgramSpecificsPerCubeMap(Program program) {
		program.use();
		program.setUniform("firstBounceForProbe", GBuffer.RENDER_PROBES_WITH_FIRST_BOUNCE);
		program.setUniform("probePosition", probe.getEntity().getCenter());
		program.setUniform("probeSize", probe.getSize());
		program.setUniform("activePointLightCount", engine.getSceneManager().getScene().getPointLights().size());
		program.bindShaderStorageBuffer(3, engine.getRenderManager().getRenderState().getCurrentReadState().getEntitiesBuffer());
		program.bindShaderStorageBuffer(5, engine.getScene().getPointLightSystem().getLightBuffer());

		program.setUniform("activeAreaLightCount", engine.getSceneManager().getScene().getAreaLights().size());
		program.bindShaderStorageBuffer(6, engine.getScene().getAreaLightSystem().getLightBuffer());

		for(int i = 0; i < Math.min(engine.getSceneManager().getScene().getAreaLights().size(), MAX_AREALIGHT_SHADOWMAPS); i++) {
			AreaLight areaLight = engine.getSceneManager().getScene().getAreaLights().get(i);
            engine.getGpuContext().bindTexture(9 + i, TEXTURE_2D, engine.getScene().getAreaLightSystem().getDepthMapForAreaLight(areaLight));
            program.setUniformAsMatrix4("areaLightShadowMatrices[" + i + "]", engine.getScene().getAreaLightSystem().getShadowMatrixForAreaLight(areaLight));
		}
		
		program.setUniform("probeIndex", probe.getIndex());
        engine.getSceneManager().getScene().getEnvironmentProbeManager().bindEnvironmentProbePositions(cubeMapProgram);
	}

	private void drawEntities(RenderState renderState, Program program, FloatBuffer viewMatrixAsBuffer, FloatBuffer projectionMatrixAsBuffer, FloatBuffer viewProjectionMatrixAsBuffer) {
		program.use();
		bindShaderSpecificsPerCubeMapSide(viewMatrixAsBuffer, projectionMatrixAsBuffer, viewProjectionMatrixAsBuffer, program);

		GPUProfiler.start("Cubemapside draw entities");
		for (RenderBatch e : renderState.getRenderBatchesStatic()) {
			if (!Spatial.isInFrustum(getCamera(), e.getCenterWorld(), e.getMinWorld(), e.getMaxWorld())) {
//				continue;
			}
			DrawStrategy.draw(engine.getGpuContext(), renderState.getVertexIndexBufferStatic().getVertexBuffer(), renderState.getVertexIndexBufferStatic().getIndexBuffer(), e, program, false);
		}
		GPUProfiler.end();
	}
	
	void drawFirstPass(int sideIndex, Camera camera, List<Entity> entities, RenderState extract) {
//		GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, EnvironmentProbeManager.getInstance().getCubeMapArrayRenderTarget().getFrameBufferLocation());
//		EnvironmentProbeManager.getInstance().getCubeMapArrayRenderTarget().resetAttachments();
        engine.getSceneManager().getScene().getEnvironmentProbeManager().getCubeMapArrayRenderTarget().setCubeMapFace(0, probe.getIndex(), sideIndex);
        engine.getSceneManager().getScene().getEnvironmentProbeManager().getCubeMapArrayRenderTarget().setCubeMapFace(1, probe.getIndex(), sideIndex);
        engine.getSceneManager().getScene().getEnvironmentProbeManager().getCubeMapArrayRenderTarget().setCubeMapFace(2, probe.getIndex(), sideIndex);

        engine.getGpuContext().clearDepthAndColorBuffer();
        engine.getGpuContext().enable(CULL_FACE);
        engine.getGpuContext().depthMask(true);
        engine.getGpuContext().enable(DEPTH_TEST);
        engine.getGpuContext().depthFunc(LEQUAL);
        engine.getGpuContext().disable(BLEND);

		GPUProfiler.start("Draw entities");
        Program firstpassDefaultProgram = engine.getProgramManager().getFirstpassDefaultProgram();
        firstpassDefaultProgram.use();
        firstpassDefaultProgram.bindShaderStorageBuffer(1, extract.getMaterialBuffer());
        firstpassDefaultProgram.setUniform("useRainEffect", Config.getInstance().getRainEffect() == 0.0 ? false : true);
        firstpassDefaultProgram.setUniform("rainEffect", Config.getInstance().getRainEffect());
        firstpassDefaultProgram.setUniformAsMatrix4("viewMatrix", camera.getEntity().getViewMatrixAsBuffer());
        firstpassDefaultProgram.setUniformAsMatrix4("lastViewMatrix", camera.getLastViewMatrixAsBuffer());
        firstpassDefaultProgram.setUniformAsMatrix4("projectionMatrix", camera.getProjectionMatrixAsBuffer());
        firstpassDefaultProgram.setUniform("eyePosition", camera.getEntity().getPosition());
        firstpassDefaultProgram.setUniform("lightDirection", engine.getScene().getEntitySystems().get(DirectionalLightSystem.class).getDirectionalLight().getViewDirection());
        firstpassDefaultProgram.setUniform("near", camera.getNear());
        firstpassDefaultProgram.setUniform("far", camera.getFar());
        firstpassDefaultProgram.setUniform("time", (int)System.currentTimeMillis());

		for (RenderBatch entity : extract.getRenderBatchesStatic()) {
			DrawStrategy.draw(engine.getGpuContext(), extract, entity);
		}
		for (RenderBatch entity : extract.getRenderBatchesAnimated()) {
			DrawStrategy.draw(engine.getGpuContext(), extract, entity);
		}
		GPUProfiler.end();
        engine.getGpuContext().enable(CULL_FACE);
	}

	void drawSecondPass(int sideIndex, DirectionalLight directionalLight, List<PointLight> pointLights, List<TubeLight> tubeLights, List<AreaLight> areaLights) {
        CubeMapArrayRenderTarget cubeMapArrayRenderTarget = engine.getSceneManager().getScene().getEnvironmentProbeManager().getCubeMapArrayRenderTarget();
		Vector3f camPosition = getPosition();//.negate(null);
		camPosition.add(getViewDirection().mul(getCamera().getNear()));
		Vector4f camPositionV4 = new Vector4f(camPosition.x, camPosition.y, camPosition.z, 0);
		
		GPUProfiler.start("Directional light");
        engine.getGpuContext().depthMask(true);
        engine.getGpuContext().enable(DEPTH_TEST);
        engine.getGpuContext().depthFunc(LESS);
        engine.getGpuContext().enable(BLEND);
        engine.getGpuContext().blendEquation(FUNC_ADD);
        engine.getGpuContext().blendFunc(ONE, ONE);

		GPUProfiler.start("Activate GBuffer textures");
        engine.getGpuContext().bindTexture(0, TEXTURE_2D, cubeMapFaceViews[0][sideIndex]);
        engine.getGpuContext().bindTexture(1, TEXTURE_2D, cubeMapFaceViews[1][sideIndex]);
        engine.getGpuContext().bindTexture(2, TEXTURE_2D, cubeMapFaceViews[2][sideIndex]);
        engine.getTextureManager().getCubeMap().bind(4);
		GPUProfiler.end();

		secondPassDirectionalProgram.use();
		secondPassDirectionalProgram.setUniform("eyePosition", getPosition());
		secondPassDirectionalProgram.setUniform("ambientOcclusionRadius", Config.getInstance().getAmbientocclusionRadius());
		secondPassDirectionalProgram.setUniform("ambientOcclusionTotalStrength", Config.getInstance().getAmbientocclusionTotalStrength());
		secondPassDirectionalProgram.setUniform("screenWidth", (float) EnvironmentProbeManager.RESOLUTION);
		secondPassDirectionalProgram.setUniform("screenHeight", (float) EnvironmentProbeManager.RESOLUTION);
		FloatBuffer viewMatrix = getViewMatrixAsBuffer();
		secondPassDirectionalProgram.setUniformAsMatrix4("viewMatrix", viewMatrix);
		FloatBuffer projectionMatrix = getCamera().getProjectionMatrixAsBuffer();
		secondPassDirectionalProgram.setUniformAsMatrix4("projectionMatrix", projectionMatrix);
		secondPassDirectionalProgram.setUniformAsMatrix4("shadowMatrix", directionalLight.getViewProjectionMatrixAsBuffer());
		secondPassDirectionalProgram.setUniform("lightDirection", directionalLight.getEntity().getViewDirection());
		secondPassDirectionalProgram.setUniform("lightDiffuse", directionalLight.getColor());
		secondPassDirectionalProgram.setUniform("currentProbe", probe.getIndex());
        secondPassDirectionalProgram.setUniform("activeProbeCount", engine.getSceneManager().getScene().getEnvironmentProbeManager().getProbes().size());
        engine.getSceneManager().getScene().getEnvironmentProbeManager().bindEnvironmentProbePositions(secondPassDirectionalProgram);
//		LOGGER.de.hanno.hpengine.log(Level.INFO, String.format("DIR LIGHT: %f %f %f", directionalLight.getOrientation().x, directionalLight.getOrientation().y, directionalLight.getOrientation().z));
		GPUProfiler.start("Draw fullscreen buffer");
		fullscreenBuffer.draw();
		GPUProfiler.end();

		GPUProfiler.end();

        engine.getGpuContext().enable(CULL_FACE);
		doPointLights(this.getCamera(), pointLights, camPosition, viewMatrix, projectionMatrix);
        engine.getGpuContext().disable(CULL_FACE);

		doTubeLights(tubeLights, camPositionV4, viewMatrix, projectionMatrix);

		doAreaLights(areaLights, viewMatrix, projectionMatrix);

        engine.getGpuContext().disable(BLEND);

		if (GBuffer.RENDER_PROBES_WITH_FIRST_BOUNCE) {
			renderReflectionsSecondBounce(viewMatrix, projectionMatrix,
					cubeMapFaceViews[0][sideIndex],
					cubeMapFaceViews[1][sideIndex],
					cubeMapFaceViews[2][sideIndex], sideIndex);
		}
        engine.getGpuContext().enable(CULL_FACE);
        engine.getGpuContext().cullFace(BACK);
        engine.getGpuContext().depthFunc(LESS);

//		GL11.glDeleteTextures(cubeMapFaceView);
//		GL11.glDeleteTextures(cubeMapFaceView1);
//		GL11.glDeleteTextures(cubeMapFaceView2);
	}
	
	private void renderReflectionsSecondBounce(FloatBuffer viewMatrix, FloatBuffer projectionMatrix, int positionMap, int normalMap, int colorMap, int sideIndex) {
		GPUProfiler.start("Reflections");
        engine.getGpuContext().bindTexture(0, TEXTURE_2D, positionMap);
        engine.getGpuContext().bindTexture(1, TEXTURE_2D, normalMap);
        engine.getGpuContext().bindTexture(2, TEXTURE_2D, colorMap);
        engine.getGpuContext().bindTexture(8, TEXTURE_2D, colorMap);
        engine.getSceneManager().getScene().getEnvironmentProbeManager().getEnvironmentMapsArray(3).bind(engine.getGpuContext(), 8);
        engine.getTextureManager().getCubeMap().bind(9);

        engine.getGpuContext().bindImageTexture(6, engine.getSceneManager().getScene().getEnvironmentProbeManager().getCubeMapArrayRenderTarget().getCubeMapArray(3).getTextureID(), 0, false, 6 * probe.getIndex() + sideIndex, GL15.GL_WRITE_ONLY, GL30.GL_RGBA16F);
		tiledProbeLightingProgram.use();
		tiledProbeLightingProgram.setUniform("secondBounce", GBuffer.RENDER_PROBES_WITH_SECOND_BOUNCE);
		tiledProbeLightingProgram.setUniform("screenWidth", (float) EnvironmentProbeManager.RESOLUTION);
		tiledProbeLightingProgram.setUniform("screenHeight", (float) EnvironmentProbeManager.RESOLUTION);
		tiledProbeLightingProgram.setUniform("currentProbe", probe.getIndex());
		tiledProbeLightingProgram.setUniformAsMatrix4("viewMatrix", viewMatrix);
		tiledProbeLightingProgram.setUniformAsMatrix4("projectionMatrix", projectionMatrix);
        tiledProbeLightingProgram.setUniform("activeProbeCount", engine.getSceneManager().getScene().getEnvironmentProbeManager().getProbes().size());
        engine.getSceneManager().getScene().getEnvironmentProbeManager().bindEnvironmentProbePositions(tiledProbeLightingProgram);
		tiledProbeLightingProgram.dispatchCompute(EnvironmentProbeManager.RESOLUTION/16, EnvironmentProbeManager.RESOLUTION/16+1, 1);
//		GL42.glMemoryBarrier(GL42.GL_ALL_BARRIER_BITS);
		GPUProfiler.end();
	}
	
	private void bindShaderSpecificsPerCubeMapSide(FloatBuffer viewMatrixAsBuffer, FloatBuffer projectionMatrixAsBuffer, FloatBuffer viewProjectionMatrixAsBuffer, Program program) {
		GPUProfiler.start("Matrix uniforms");
		DirectionalLight light = engine.getScene().getEntitySystems().get(DirectionalLightSystem.class).getDirectionalLight();
		program.setUniform("lightDirection", light.getEntity().getViewDirection());
		program.setUniform("lightDiffuse", light.getColor());
		program.setUniform("lightAmbient", Config.getInstance().getAmbientLight());
		program.setUniformAsMatrix4("viewMatrix", viewMatrixAsBuffer);
		program.setUniformAsMatrix4("projectionMatrix", projectionMatrixAsBuffer);
		program.setUniformAsMatrix4("viewProjectionMatrix", viewProjectionMatrixAsBuffer);
		program.setUniformAsMatrix4("shadowMatrix", light.getViewProjectionMatrixAsBuffer());
		GPUProfiler.end();
	}

	private void generateCubeMapMipMaps() {
		if(Config.getInstance().isUsePrecomputedRadiance()) {
			
			_generateCubeMapMipMaps();
			
			if (Config.getInstance().isCalculateActualRadiance()) {
				GPUProfiler.start("Precompute radiance");

                CubeMapArray cubeMapArray = engine.getSceneManager().getScene().getEnvironmentProbeManager().getEnvironmentMapsArray(3);
				int internalFormat = cubeMapArray.getInternalFormat();
				int cubemapArrayColorTextureId = cubeMapArray.getTextureID();
				int cubeMapView = GL11.glGenTextures();

                GL43.glTextureView(cubeMapView, GL13.GL_TEXTURE_CUBE_MAP, cubeMapArray.getTextureID(),
						cubeMapArray.getInternalFormat(), 0, engine.getSceneManager().getScene().getEnvironmentProbeManager().CUBEMAP_MIPMAP_COUNT,
						6 * probe.getIndex(), 6);

				int cubemapCopy = cubeMapView;//TextureManager.copyCubeMap(cubeMapView, EnvironmentProbeManager.RESOLUTION, EnvironmentProbeManager.RESOLUTION, internalFormat);
//				renderer.getTextureManager().generateMipMapsCubeMap(cubemapCopy);
				
				boolean USE_OMPUTE_SHADER_FOR_RADIANCE = true;
				if(USE_OMPUTE_SHADER_FOR_RADIANCE) {
					calculateRadianceCompute(internalFormat, cubemapArrayColorTextureId, cubeMapView, cubemapCopy);	
				} else {
					calculateRadianceFragment(internalFormat, cubemapArrayColorTextureId, cubeMapView, cubemapCopy);	
				}
				GL11.glDeleteTextures(cubeMapView);
//				GL11.glDeleteTextures(cubemapCopy);
				GPUProfiler.end();
			}
			
		} else {
			_generateCubeMapMipMaps();
		}
	}

	private void calculateRadianceFragment(int internalFormat,
			int cubemapArrayColorTextureId, int cubeMapView, int cubemapCopy) {
		
		cubemapRadianceFragmentProgram.use();
        CubeMapArrayRenderTarget cubeMapArrayRenderTarget = engine.getSceneManager().getScene().getEnvironmentProbeManager().getCubeMapArrayRenderTarget();
		renderTarget.use(false);

        engine.getGpuContext().bindTexture(8, TEXTURE_CUBE_MAP, cubeMapView);
		//GL11.glBindTexture(GL13.GL_TEXTURE_CUBE_MAP, renderer.getEnvironmentMap().getTextureID());
		
		for (int i = 0; i < 6; i++) {

			int width = EnvironmentProbeManager.RESOLUTION;
			int height = EnvironmentProbeManager.RESOLUTION;
			
			int indexOfFace = 6 * probe.getIndex() + i;

			for (int z = 0; z < EnvironmentProbeManager.CUBEMAP_MIPMAP_COUNT; z++) {
				//cubeMapArrayRenderTarget.setCubeMapFace(0, probe.getIndex(), indexOfFace);
				renderTarget.setCubeMapFace(0, cubeMapView, i, z);
				width /= 2;
				height /= 2;

                engine.getGpuContext().clearColorBuffer();
                engine.getGpuContext().clearColor(0, 0, 0, 0);

				cubemapRadianceFragmentProgram.setUniform("currentCubemapSide", i);
				cubemapRadianceFragmentProgram.setUniform("currentProbe", probe.getIndex());
				cubemapRadianceFragmentProgram.setUniform("screenWidth", (float) width);
				cubemapRadianceFragmentProgram.setUniform("screenHeight", (float) height);
                engine.getSceneManager().getScene().getEnvironmentProbeManager().bindEnvironmentProbePositions(cubemapRadianceFragmentProgram);
                engine.getGpuContext().viewPort(0, 0, width, height);
				fullscreenBuffer.draw();
			}
		}
	}
	private void calculateRadianceCompute(int internalFormat,
			int cubemapArrayColorTextureId, int cubeMapView, int cubemapCopy) {
		cubemapRadianceProgram.use();
		int width = EnvironmentProbeManager.RESOLUTION / 2;
		int height = EnvironmentProbeManager.RESOLUTION / 2;
		
		for (int i = 0; i < 6; i++) {

			int indexOfFace = 6 * probe.getIndex() + i;

			for (int z = 0; z < EnvironmentProbeManager.CUBEMAP_MIPMAP_COUNT; z++) {
				GL42.glBindImageTexture(z, cubemapArrayColorTextureId, z + 1, false, indexOfFace, GL15.GL_WRITE_ONLY, internalFormat);
			}

            engine.getGpuContext().bindTexture(8, TEXTURE_CUBE_MAP, cubemapCopy);

			cubemapRadianceProgram.setUniform("currentCubemapSide", i);
			cubemapRadianceProgram.setUniform("currentProbe", probe.getIndex());
			cubemapRadianceProgram.setUniform("screenWidth", (float) width);
			cubemapRadianceProgram.setUniform("screenHeight", (float) height);
            engine.getSceneManager().getScene().getEnvironmentProbeManager().bindEnvironmentProbePositions(cubemapRadianceProgram);
			cubemapRadianceProgram.dispatchCompute((EnvironmentProbeManager.RESOLUTION / 2) / 32, (EnvironmentProbeManager.RESOLUTION / 2) / 32, 1);
		}
	}
	private void _generateCubeMapMipMaps() {

		GPUProfiler.start("MipMap generation");

		boolean use2DMipMapping = false;
        CubeMapArray cubeMapArray = engine.getSceneManager().getScene().getEnvironmentProbeManager().getEnvironmentMapsArray(3);
		if (use2DMipMapping ) {
			for (int i = 0; i < 6; i++) {
				int cubeMapFaceView = GL11.glGenTextures();
                GL43.glTextureView(cubeMapFaceView, GL11.GL_TEXTURE_2D, cubeMapArray.getTextureID(), cubeMapArray.getInternalFormat(), 0, engine.getSceneManager().getScene().getEnvironmentProbeManager().CUBEMAP_MIPMAP_COUNT, 6 * probe.getIndex() + i, 1);
                engine.getTextureManager().generateMipMaps(cubeMapFaceView, GL11.GL_NEAREST, GL11.GL_NEAREST);
				GL11.glDeleteTextures(cubeMapFaceView);
			}
			
		} else {
			int cubeMapView = GL11.glGenTextures();
            GL43.glTextureView(cubeMapView, GL13.GL_TEXTURE_CUBE_MAP, cubeMapArray.getTextureID(), cubeMapArray.getInternalFormat(), 0, engine.getSceneManager().getScene().getEnvironmentProbeManager().CUBEMAP_MIPMAP_COUNT, 6*probe.getIndex(), 6);
            engine.getTextureManager().generateMipMapsCubeMap(cubeMapView);
			GL11.glDeleteTextures(cubeMapView);
		}
		GPUProfiler.end();
	}

	private void doPointLights(Camera camera, List<PointLight> pointLights,
			Vector3f camPosition, FloatBuffer viewMatrix,
			FloatBuffer projectionMatrix) {
		
		if(pointLights.isEmpty()) { return; }

		GPUProfiler.start("Pointlights");
		secondPassPointProgram.use();

		GPUProfiler.start("Set shared uniforms");
//		secondPassPointProgram.setUniform("lightCount", pointLights.size());
//		secondPassPointProgram.setUniformAsBlock("pointlights", PointLight.convert(pointLights));
		secondPassPointProgram.setUniform("screenWidth", (float) EnvironmentProbeManager.RESOLUTION);
		secondPassPointProgram.setUniform("screenHeight", (float) EnvironmentProbeManager.RESOLUTION);
		secondPassPointProgram.setUniformAsMatrix4("viewMatrix", viewMatrix);
		secondPassPointProgram.setUniformAsMatrix4("projectionMatrix", projectionMatrix);
		GPUProfiler.end();

		GPUProfiler.start("Draw light");
		boolean firstLightDrawn = false;
		for (int i = 0 ; i < pointLights.size(); i++) {
			PointLight light = pointLights.get(i);
			if(!light.isInFrustum(camera)) {
				continue;
			}
			
			Vector3f distance = new Vector3f();
			light.getEntity().getPosition().sub(camPosition, distance);
			float lightRadius = light.getRadius();
			
			// de.hanno.hpengine.camera is inside lights
			if (distance.length() < lightRadius) {
				GL11.glCullFace(GL11.GL_FRONT);
				GL11.glDepthFunc(GL11.GL_GEQUAL);
			} else {
			// de.hanno.hpengine.camera is outside lights, cull back sides
				GL11.glCullFace(GL11.GL_BACK);
				GL11.glDepthFunc(GL11.GL_LEQUAL);
			}

//			secondPassPointProgram.setUniform("currentLightIndex", i);
			secondPassPointProgram.setUniform("lightPosition", light.getEntity().getPosition());
			secondPassPointProgram.setUniform("lightRadius", lightRadius);
			secondPassPointProgram.setUniform("lightDiffuse", light.getColor().x, light.getColor().y, light.getColor().z);

			fullscreenBuffer.draw();
			light.draw(secondPassPointProgram);
			
			if(firstLightDrawn) {
				light.drawAgain(null, secondPassPointProgram);
			} else {
				light.draw(secondPassPointProgram);
			}
			firstLightDrawn = true;
		}
		GPUProfiler.end();
		GPUProfiler.end();
	}
	private void doTubeLights(List<TubeLight> tubeLights,
			Vector4f camPositionV4, FloatBuffer viewMatrix,
			FloatBuffer projectionMatrix) {

		
		if(tubeLights.isEmpty()) { return; }
		
		secondPassTubeProgram.use();
		secondPassTubeProgram.setUniform("screenWidth", (float) EnvironmentProbeManager.RESOLUTION);
		secondPassTubeProgram.setUniform("screenHeight", (float) EnvironmentProbeManager.RESOLUTION);
		secondPassTubeProgram.setUniformAsMatrix4("viewMatrix", viewMatrix);
		secondPassTubeProgram.setUniformAsMatrix4("projectionMatrix", projectionMatrix);
		for (TubeLight tubeLight : tubeLights) {
			boolean camInsideLightVolume = new AABB(tubeLight.getEntity().getPosition(), tubeLight.getLength(), tubeLight.getRadius(), tubeLight.getRadius()).contains(camPositionV4);
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
		

		if(areaLights.isEmpty()) { return; }
		
		GPUProfiler.start("Area light: " + areaLights.size());
		
		secondPassAreaProgram.use();
		secondPassAreaProgram.setUniform("screenWidth", (float) EnvironmentProbeManager.RESOLUTION);
		secondPassAreaProgram.setUniform("screenHeight", (float) EnvironmentProbeManager.RESOLUTION);
		secondPassAreaProgram.setUniformAsMatrix4("viewMatrix", viewMatrix);
		secondPassAreaProgram.setUniformAsMatrix4("projectionMatrix", projectionMatrix);
        engine.getGpuContext().disable(CULL_FACE);
        engine.getGpuContext().disable(DEPTH_TEST);
		for (AreaLight areaLight : areaLights) {
//			boolean camInsideLightVolume = new AABB(areaLight.getPosition(), 2*areaLight.getScale().x, 2*areaLight.getScale().y, 2*areaLight.getScale().z).contains(camPositionV4);
//			if (camInsideLightVolume) {
//				GL11.glCullFace(GL11.GL_FRONT);
//				GL11.glDepthFunc(GL11.GL_GEQUAL);
//			} else {
//				GL11.glCullFace(GL11.GL_BACK);
//				GL11.glDepthFunc(GL11.GL_LEQUAL);
//			}
			secondPassAreaProgram.setUniform("lightPosition", areaLight.getEntity().getPosition());
			secondPassAreaProgram.setUniform("lightRightDirection", areaLight.getEntity().getRightDirection());
			secondPassAreaProgram.setUniform("lightViewDirection", areaLight.getEntity().getViewDirection());
			secondPassAreaProgram.setUniform("lightUpDirection", areaLight.getEntity().getUpDirection());
			secondPassAreaProgram.setUniform("lightWidth", areaLight.getWidth());
			secondPassAreaProgram.setUniform("lightHeight", areaLight.getHeight());
			secondPassAreaProgram.setUniform("lightRange", areaLight.getRange());
			secondPassAreaProgram.setUniform("lightDiffuse", areaLight.getColor());
            secondPassAreaProgram.setUniformAsMatrix4("shadowMatrix", engine.getScene().getAreaLightSystem().getShadowMatrixForAreaLight(areaLight));

			// TODO: Add textures to arealights
//			try {
//				GL13.glActiveTexture(GL13.GL_TEXTURE0 + 8);
//				Texture lightTexture = TextureManager.getInstance().getDiffuseTexture("brick.hptexture");
//				GL11.glBindTexture(GL11.GL_TEXTURE_2D, lightTexture.getTextureID());
//			} catch (IOException e) {
//				e.printStackTrace();
//			}

            engine.getGpuContext().bindTexture(9, TEXTURE_2D, engine.getScene().getAreaLightSystem().getDepthMapForAreaLight(areaLight));
			fullscreenBuffer.draw();
//			areaLight.getVertexBuffer().drawDebug();
		}
		
		GPUProfiler.end();
	}
	private void rotateForIndex(int i, Entity camera) {
		float deltaNear = 0.0f;
		float deltaFar = 100.0f;
		float halfSizeX = probe.getSize().x/2;
		float halfSizeY = probe.getSize().y/2;
		float halfSizeZ = probe.getSize().z/2;
		Vector3f position = camera.getPosition();//.negate(null); // TODO: AHHhhhh, kill this hack
		float width = probe.getSize().x;
		float height = probe.getSize().y;
//		Matrix4f projectionMatrix = Util.createOrthogonal(position.z-width/2, position.z+width/2, position.y+height/2, position.y-height/2, getCamera().getNear(), getCamera().getFar());
//		Transform oldTransform = de.hanno.hpengine.camera.getTransform();
//		de.hanno.hpengine.camera = new Camera(renderer, projectionMatrix, getCamera().getNear(), getCamera().getFar(), 90, 1);
//		de.hanno.hpengine.camera.setPerspective(false);
//		de.hanno.hpengine.camera.setTransform(oldTransform);
//		de.hanno.hpengine.camera.updateShadow();

		switch (i) {
		case 0:
			camera.rotation(new Quaternionf().identity());
			camera.rotate(new AxisAngle4f(0,0,1, (float) Math.toRadians(180)));
			camera.rotate(new AxisAngle4f(0,1,0, (float) Math.toRadians(-90)));
//			probe.getCamera().setNear(0 + halfSizeX*deltaNear);
			probe.getCamera().setFar((halfSizeX) * deltaFar);
			break;
		case 1:
			camera.rotation(new Quaternionf().identity());
			camera.rotate(new AxisAngle4f(0,0,1, (float) Math.toRadians(180)));
			camera.rotate(new AxisAngle4f(0, 1, 0, (float) Math.toRadians(90)));
//			probe.getCamera().setNear(0 + halfSizeX*deltaNear);
			probe.getCamera().setFar((halfSizeX) * deltaFar);
			break;
		case 2:
			camera.rotation(new Quaternionf().identity());
			camera.rotate(new AxisAngle4f(0,0,1, (float) Math.toRadians(180)));
			camera.rotate(new AxisAngle4f(1, 0, 0, (float) Math.toRadians(90)));
			camera.rotate(new AxisAngle4f(0, 1, 0, (float) Math.toRadians(180)));
//			probe.getCamera().setNear(0 + halfSizeY*deltaNear);
			probe.getCamera().setFar((halfSizeY) * deltaFar);
			break;
		case 3:
			camera.rotation(new Quaternionf().identity());
			camera.rotate(new AxisAngle4f(0,0,1, (float) Math.toRadians(180)));
			camera.rotate(new AxisAngle4f(1, 0, 0, (float) Math.toRadians(-90)));
//			probe.getCamera().setNear(0 + halfSizeY*deltaNear);
			probe.getCamera().setFar((halfSizeY) * deltaFar);
			break;
		case 4:
			camera.rotation(new Quaternionf().identity());
			camera.rotate(new AxisAngle4f(0,0,1, (float) Math.toRadians(180)));
			camera.rotate(new AxisAngle4f(0, 1, 0, (float) Math.toRadians(-180)));
//			probe.getCamera().setNear(0 + halfSizeZ*deltaNear);
			probe.getCamera().setFar((halfSizeZ) * deltaFar);
			break;
		case 5:
			camera.rotation(new Quaternionf().identity());
			camera.rotate(new AxisAngle4f(0,0,1, (float) Math.toRadians(180)));
//			de.hanno.hpengine.camera.rotateWorld(new Vector4f(0, 1, 0, 180));
//			probe.getCamera().setNear(0 + halfSizeZ*deltaNear);
			probe.getCamera().setFar((halfSizeZ) * deltaFar);
			break;
		default:
			break;
		}
	}

	public int getCubeMapView() {
		return cubeMapView;
	}

	public int getCubeMapView1() {
		return cubeMapView1;
	}

	public int getCubeMapView2() {
		return cubeMapView2;
	}

	public int[][] getCubeMapFaceViews() {
		return cubeMapFaceViews;
	}

	public Camera getCamera() {
		return camera;
	}
}
