package de.hanno.hpengine.engine.graphics.renderer.environmentsampler;

import com.google.common.eventbus.Subscribe;
import de.hanno.hpengine.engine.camera.Camera;
import de.hanno.hpengine.engine.config.Config;
import de.hanno.hpengine.engine.entity.Entity;
import de.hanno.hpengine.engine.event.MaterialChangedEvent;
import de.hanno.hpengine.engine.graphics.GpuContext;
import de.hanno.hpengine.engine.graphics.light.area.AreaLight;
import de.hanno.hpengine.engine.graphics.light.directional.DirectionalLight;
import de.hanno.hpengine.engine.graphics.light.directional.DirectionalLightSystem;
import de.hanno.hpengine.engine.graphics.light.point.PointLight;
import de.hanno.hpengine.engine.graphics.light.tube.TubeLight;
import de.hanno.hpengine.engine.graphics.renderer.RenderBatch;
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DeferredRenderingBuffer;
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.ColorAttachmentDefinition;
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.CubeMapArrayRenderTarget;
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.DepthBuffer;
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.FrameBuffer;
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.RenderTarget;
import de.hanno.hpengine.engine.graphics.shader.ComputeShaderProgram;
import de.hanno.hpengine.engine.graphics.shader.Program;
import de.hanno.hpengine.engine.graphics.shader.ProgramManager;
import de.hanno.hpengine.engine.graphics.shader.Shader;
import de.hanno.hpengine.engine.graphics.state.RenderState;
import de.hanno.hpengine.engine.model.texture.CubeMapArray;
import de.hanno.hpengine.engine.model.texture.Texture2D;
import de.hanno.hpengine.engine.model.texture.TextureManager;
import de.hanno.hpengine.engine.scene.EnvironmentProbe;
import de.hanno.hpengine.engine.scene.EnvironmentProbeManager;
import de.hanno.hpengine.engine.scene.Scene;
import de.hanno.hpengine.engine.transform.AABB;
import de.hanno.hpengine.engine.transform.Spatial;
import de.hanno.hpengine.engine.vertexbuffer.QuadVertexBuffer;
import de.hanno.hpengine.engine.vertexbuffer.VertexBuffer;
import de.hanno.hpengine.engine.vertexbuffer.VertexBufferExtensionsKt;
import de.hanno.hpengine.util.TypedTuple;
import de.hanno.hpengine.util.Util;
import de.hanno.hpengine.util.ressources.FileBasedCodeSource;
import org.jetbrains.annotations.NotNull;
import org.joml.AxisAngle4f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL42;
import org.lwjgl.opengl.GL43;

import java.io.File;
import java.nio.FloatBuffer;
import java.util.Arrays;
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
import static de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DrawUtilsKt.draw;
import static de.hanno.hpengine.engine.graphics.renderer.rendertarget.RenderTargetKt.toTextures;
import static de.hanno.hpengine.engine.scene.EnvironmentProbeManager.RESOLUTION;
import static de.hanno.hpengine.engine.transform.AABBKt.containsOrIntersectsSphere;

public class EnvironmentSampler extends Entity {
	public static volatile boolean deferredRenderingForProbes = false;
	@NotNull
	private final EnvironmentProbeManager environmentProbeManager;
	private Program cubeMapProgram;
	private Program cubeMapLightingProgram;
	private Program depthPrePassProgram;
	private ComputeShaderProgram tiledProbeLightingProgram;
	private ComputeShaderProgram cubemapRadianceProgram;
	private Program cubemapRadianceFragmentProgram;
	private FloatBuffer entityBuffer = BufferUtils.createFloatBuffer(16);
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
	private Program firstPassDefaultProgram;
	
	private RenderTarget<Texture2D> renderTarget;
	private Camera camera;

//	 TODO: Populate this somehow
	private Scene scene = null;

	GpuContext gpuContext;
	private final Config config;
	private TextureManager textureManager;

	public EnvironmentSampler(Entity entity,
							  EnvironmentProbe probe,
							  Vector3f position,
							  int width, int height, int probeIndex,
							  EnvironmentProbeManager environmentProbeManager,
							  ProgramManager programManager, Config config,
							  TextureManager textureManager) throws Exception {
		this.environmentProbeManager = environmentProbeManager;
		this.gpuContext = programManager.getGpuContext();
		this.config = config;
		this.textureManager = textureManager;
		Camera camera = new Camera(entity, 0.1f, 5000f, 90f, 1f);
		entity.addComponent(camera);
        camera.setWidth(width);
		camera.setWidth(height);
		this.camera = camera;
        translate(position);
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

		cubeMapProgram = programManager.getProgramFromFileNames("first_pass_vertex.glsl", "cubemap_fragment.glsl");
		depthPrePassProgram = programManager.getProgramFromFileNames("first_pass_vertex.glsl", "cubemap_fragment.glsl");
		cubeMapLightingProgram = programManager.getProgramFromFileNames("first_pass_vertex.glsl", "cubemap_lighting_fragment.glsl");
		tiledProbeLightingProgram = programManager.getComputeProgram("tiled_probe_lighting_probe_rendering_compute.glsl");
		cubemapRadianceProgram = programManager.getComputeProgram("cubemap_radiance_compute.glsl");
		cubemapRadianceFragmentProgram = programManager.getProgramFromFileNames("passthrough_vertex.glsl", "cubemap_radiance_fragment.glsl");
		secondPassPointProgram = programManager.getProgram(new FileBasedCodeSource(new File(Shader.directory + "second_pass_point_vertex.glsl")), new FileBasedCodeSource(new File(Shader.directory + "second_pass_point_fragment.glsl")));
		secondPassTubeProgram = programManager.getProgram(new FileBasedCodeSource(new File(Shader.directory + "second_pass_point_vertex.glsl")), new FileBasedCodeSource(new File(Shader.directory + "second_pass_tube_fragment.glsl")));
		secondPassAreaProgram = programManager.getProgram(new FileBasedCodeSource(new File(Shader.directory + "second_pass_area_vertex.glsl")), new FileBasedCodeSource(new File(Shader.directory + "second_pass_area_fragment.glsl")));
		secondPassDirectionalProgram = programManager.getProgram(new FileBasedCodeSource(new File(Shader.directory + "second_pass_directional_vertex.glsl")), new FileBasedCodeSource(new File(Shader.directory + "second_pass_directional_fragment.glsl")));
		firstPassDefaultProgram = programManager.getProgram(new FileBasedCodeSource(new File(Shader.directory + "first_pass_vertex.glsl")), new FileBasedCodeSource(new File(Shader.directory + "first_pass_fragment.glsl")));

        CubeMapArrayRenderTarget cubeMapArrayRenderTarget = environmentProbeManager.getCubeMapArrayRenderTarget();
		cubeMapView = GL11.glGenTextures();
		cubeMapView1 = GL11.glGenTextures();
		cubeMapView2 = GL11.glGenTextures();
		GpuContext.exitOnGLError("EnvironmentSampler before view creation");
		int diffuseInternalFormat = cubeMapArrayRenderTarget.getCubeMapArray(3).getInternalFormat();
		for (int z = 0; z < 6; z++) {
			cubeMapFaceViews[0][z] = GL11.glGenTextures();
			cubeMapFaceViews[1][z] = GL11.glGenTextures();
            cubeMapFaceViews[2][z] = GL11.glGenTextures();
            cubeMapFaceViews[3][z] = GL11.glGenTextures();
			//GL43.glTextureView(cubeMapFaceViews[i][z], GL11.GL_TEXTURE_2D, cubeMapArrayRenderTarget.getCubeMapArray(i).getTextureId(), cubeMapArrayRenderTarget.getCubeMapArray(i).getInternalFormat(), 0, 1, 6 * probe.getIndex() + z, 1);
			GL43.glTextureView(cubeMapFaceViews[0][z], GL11.GL_TEXTURE_2D, cubeMapArrayRenderTarget.getCubeMapArray(0).getId(), cubeMapArrayRenderTarget.getCubeMapArray(0).getInternalFormat(), 0, 1, 6 * probeIndex + z, 1);
			GL43.glTextureView(cubeMapFaceViews[1][z], GL11.GL_TEXTURE_2D, cubeMapArrayRenderTarget.getCubeMapArray(1).getId(), cubeMapArrayRenderTarget.getCubeMapArray(1).getInternalFormat(), 0, 1, 6 * probeIndex + z, 1);
            GL43.glTextureView(cubeMapFaceViews[2][z], GL11.GL_TEXTURE_2D, cubeMapArrayRenderTarget.getCubeMapArray(2).getId(), cubeMapArrayRenderTarget.getCubeMapArray(2).getInternalFormat(), 0, 1, 6 * probeIndex + z, 1);
            GL43.glTextureView(cubeMapFaceViews[3][z], GL11.GL_TEXTURE_2D, cubeMapArrayRenderTarget.getCubeMapArray(3).getId(), diffuseInternalFormat, 0, 1, 6 * probeIndex + z, 1);
		}
        GL43.glTextureView(cubeMapView, GL13.GL_TEXTURE_CUBE_MAP, cubeMapArrayRenderTarget.getCubeMapArray(0).getId(), cubeMapArrayRenderTarget.getCubeMapArray(0).getInternalFormat(), 0, environmentProbeManager.CUBEMAP_MIPMAP_COUNT, 6*probeIndex, 6);
        GL43.glTextureView(cubeMapView1, GL13.GL_TEXTURE_CUBE_MAP, cubeMapArrayRenderTarget.getCubeMapArray(1).getId(), cubeMapArrayRenderTarget.getCubeMapArray(1).getInternalFormat(), 0, environmentProbeManager.CUBEMAP_MIPMAP_COUNT, 6*probeIndex, 6);
        GL43.glTextureView(cubeMapView2, GL13.GL_TEXTURE_CUBE_MAP, cubeMapArrayRenderTarget.getCubeMapArray(2).getId(), cubeMapArrayRenderTarget.getCubeMapArray(2).getInternalFormat(), 0, environmentProbeManager.CUBEMAP_MIPMAP_COUNT, 6*probeIndex, 6);

		renderTarget = RenderTarget.Companion.create2D(
				gpuContext,
				FrameBuffer.Companion.invoke(gpuContext, DepthBuffer.Companion.invoke(gpuContext, RESOLUTION, RESOLUTION)),
				RESOLUTION, RESOLUTION,
				toTextures(Arrays.asList(new ColorAttachmentDefinition("Environment Diffuse", diffuseInternalFormat)), gpuContext, RESOLUTION, RESOLUTION),
				"Environment Sampler"
		);

		fullscreenBuffer = new QuadVertexBuffer(gpuContext, true);
		fullscreenBuffer.upload();
		GpuContext.exitOnGLError("EnvironmentSampler constructor");
	}

	public void drawCubeMap(boolean urgent, RenderState extract) {
		drawCubeMapSides(urgent, extract);
	}

	private void drawCubeMapSides(boolean urgent, RenderState renderState) {

		Quaternionf initialOrientation = getRotation();
		Vector3f initialPosition = getPosition();

		DirectionalLight light = scene.getEntitySystems().get(DirectionalLightSystem.class).getDirectionalLight();
		gpuContext.bindTexture(8, environmentProbeManager.getEnvironmentMapsArray());
		gpuContext.bindTexture(10, environmentProbeManager.getEnvironmentMapsArray(0));

		gpuContext.disable(DEPTH_TEST);
		gpuContext.setDepthFunc(LEQUAL);

		renderTarget.use(gpuContext, false);
		Program cubeMapProgram = this.cubeMapProgram;
		bindProgramSpecificsPerCubeMap(cubeMapProgram, renderState);

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
					.filter(e -> containsOrIntersectsSphere(probe.getBox(), e.getEntity().getPosition(), e.getRadius()))
					.filter(e -> e.getEntity().hasMoved()).collect(Collectors.toList()).isEmpty();
			boolean areaLightHasMoved = !scene.getAreaLightSystem().getAreaLights().stream().filter(e -> e.getEntity().hasMoved()).collect(Collectors.toList()).isEmpty();
			boolean reRenderLightingRequired = light.getEntity().hasMoved() || aPointLightHasMoved || areaLightHasMoved;
			boolean noNeedToRedraw = !urgent && !fullReRenderRequired && !reRenderLightingRequired;

			if (noNeedToRedraw) {  // early exit if only static objects visible and lights didn't change
//				continue;
			} else if (reRenderLightingRequired) {
//				cubeMapLightingProgram.use();
			} else if (fullReRenderRequired) {
//				cubeMapProgram.use();
			}
			filteringRequired = true;

			if (deferredRenderingForProbes) {
				if (!sidesDrawn.contains(i)) {
					environmentProbeManager.getCubeMapArrayRenderTarget().setCubeMapFace(0, probe.getIndex(), i);
					environmentProbeManager.getCubeMapArrayRenderTarget().setCubeMapFace(1, probe.getIndex(), i);
					environmentProbeManager.getCubeMapArrayRenderTarget().setCubeMapFace(2, probe.getIndex(), i);
					gpuContext.clearDepthAndColorBuffer();

					drawFirstPass(i, this.getCamera(), renderState);
					environmentProbeManager.getCubeMapArrayRenderTarget().resetAttachments();

				}

				environmentProbeManager.getCubeMapArrayRenderTarget().setCubeMapFace(3, 0, probe.getIndex(), i);
				gpuContext.clearDepthAndColorBuffer();
				drawSecondPass(i, light, scene.getPointLights(), scene.getTubeLights(), scene.getAreaLights());
				registerSideAsDrawn(i);
			} else {
				gpuContext.setDepthMask(true);
				gpuContext.enable(DEPTH_TEST);
				gpuContext.setDepthFunc(LEQUAL);
				environmentProbeManager.getCubeMapArrayRenderTarget().setCubeMapFace(3, 0, probe.getIndex(), i);
				gpuContext.clearDepthAndColorBuffer();
				drawEntities(renderState, cubeMapProgram, viewMatrixBuffer, projectionMatrixBuffer, viewProjectionMatrixBuffer);
			}
		}
		if (filteringRequired) {
			generateCubeMapMipMaps();
		}
		translation(initialPosition);
		rotation(initialOrientation);
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
		scene.getEnvironmentProbeManager().addRenderProbeCommand(probe, true);
		scene.getEnvironmentProbeManager().addRenderProbeCommand(probe, true);
		scene.getEnvironmentProbeManager().addRenderProbeCommand(probe, true);
	}

	private void bindProgramSpecificsPerCubeMap(Program program, RenderState renderState) {
		program.use();
		program.setUniform("firstBounceForProbe", DeferredRenderingBuffer.RENDER_PROBES_WITH_FIRST_BOUNCE);
		program.setUniform("probePosition", probe.getEntity().getCenter());
		program.setUniform("probeSize", probe.getSize());
		program.setUniform("activePointLightCount", scene.getPointLights().size());
		program.bindShaderStorageBuffer(3, renderState.getEntitiesBuffer());
		program.bindShaderStorageBuffer(5, renderState.getLightState().getPointLightBuffer());

		program.setUniform("activeAreaLightCount", scene.getAreaLights().size());
		program.bindShaderStorageBuffer(6, scene.getAreaLightSystem().getLightBuffer());

		for(int i = 0; i < Math.min(scene.getAreaLights().size(), MAX_AREALIGHT_SHADOWMAPS); i++) {
			AreaLight areaLight = scene.getAreaLights().get(i);
            gpuContext.bindTexture(9 + i, TEXTURE_2D, scene.getAreaLightSystem().getDepthMapForAreaLight(areaLight));
            program.setUniformAsMatrix4("areaLightShadowMatrices[" + i + "]", scene.getAreaLightSystem().getShadowMatrixForAreaLight(areaLight));
		}
		
		program.setUniform("probeIndex", probe.getIndex());
        environmentProbeManager.bindEnvironmentProbePositions(cubeMapProgram);
	}

	private void drawEntities(RenderState renderState, Program program, FloatBuffer viewMatrixAsBuffer, FloatBuffer projectionMatrixAsBuffer, FloatBuffer viewProjectionMatrixAsBuffer) {
		program.use();
		bindShaderSpecificsPerCubeMapSide(viewMatrixAsBuffer, projectionMatrixAsBuffer, viewProjectionMatrixAsBuffer, program);

		for (RenderBatch e : renderState.getRenderBatchesStatic()) {
			if (!Spatial.Companion.isInFrustum(getCamera(), e.getCenterWorld(), e.getEntityMinWorld(), e.getEntityMaxWorld())) {
//				continue;
			}
			draw(renderState.getVertexIndexBufferStatic().getVertexBuffer(), renderState.getVertexIndexBufferStatic().getIndexBuffer(), e, program, false, true);
		}
	}
	
	void drawFirstPass(int sideIndex, Camera camera, RenderState extract) {
//		GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, EnvironmentProbeManager.getInstance().getCubeMapArrayRenderTarget().getFrameBufferLocation());
//		EnvironmentProbeManager.getInstance().getCubeMapArrayRenderTarget().resetAttachments();
        environmentProbeManager.getCubeMapArrayRenderTarget().setCubeMapFace(0, probe.getIndex(), sideIndex);
        environmentProbeManager.getCubeMapArrayRenderTarget().setCubeMapFace(1, probe.getIndex(), sideIndex);
        environmentProbeManager.getCubeMapArrayRenderTarget().setCubeMapFace(2, probe.getIndex(), sideIndex);

        gpuContext.clearDepthAndColorBuffer();
        gpuContext.enable(CULL_FACE);
        gpuContext.setDepthMask(true);
        gpuContext.enable(DEPTH_TEST);
        gpuContext.setDepthFunc(LEQUAL);
        gpuContext.disable(BLEND);

        firstPassDefaultProgram.use();
        firstPassDefaultProgram.bindShaderStorageBuffer(1, extract.getMaterialBuffer());
        firstPassDefaultProgram.setUniform("useRainEffect", config.getEffects().getRainEffect() != 0.0);
        firstPassDefaultProgram.setUniform("rainEffect", config.getEffects().getRainEffect());
        firstPassDefaultProgram.setUniformAsMatrix4("viewMatrix", camera.getViewMatrixAsBuffer());
        firstPassDefaultProgram.setUniformAsMatrix4("lastViewMatrix", camera.getLastViewMatrixAsBuffer());
        firstPassDefaultProgram.setUniformAsMatrix4("projectionMatrix", camera.getProjectionMatrixAsBuffer());
        firstPassDefaultProgram.setUniform("eyePosition", camera.getEntity().getPosition());
        firstPassDefaultProgram.setUniform("lightDirection", scene.getEntitySystems().get(DirectionalLightSystem.class).getDirectionalLight().getViewDirection());
        firstPassDefaultProgram.setUniform("near", camera.getNear());
        firstPassDefaultProgram.setUniform("far", camera.getFar());
        firstPassDefaultProgram.setUniform("timeGpu", (int)System.currentTimeMillis());

		for (RenderBatch entity : extract.getRenderBatchesStatic()) {
            draw(extract.getVertexIndexBufferStatic().getVertexBuffer(), extract.getVertexIndexBufferStatic().getIndexBuffer(), entity, firstPassDefaultProgram, !entity.isVisibleForCamera(), true);
        }
		for (RenderBatch entity : extract.getRenderBatchesAnimated()) {
//			TODO: program usage is wrong for animated things..
            draw(extract.getVertexIndexBufferStatic().getVertexBuffer(), extract.getVertexIndexBufferStatic().getIndexBuffer(), entity, firstPassDefaultProgram, !entity.isVisibleForCamera(), true);
        }
        gpuContext.enable(CULL_FACE);
	}

	void drawSecondPass(int sideIndex, DirectionalLight directionalLight, List<PointLight> pointLights, List<TubeLight> tubeLights, List<AreaLight> areaLights) {
        CubeMapArrayRenderTarget cubeMapArrayRenderTarget = environmentProbeManager.getCubeMapArrayRenderTarget();
		Vector3f camPosition = getPosition();//.negate(null);
		camPosition.add(getViewDirection().mul(getCamera().getNear()));
		Vector4f camPositionV4 = new Vector4f(camPosition.x, camPosition.y, camPosition.z, 0);
		
        gpuContext.setDepthMask(true);
        gpuContext.enable(DEPTH_TEST);
        gpuContext.setDepthFunc(LESS);
        gpuContext.enable(BLEND);
        gpuContext.setBlendEquation(FUNC_ADD);
        gpuContext.blendFunc(ONE, ONE);

        gpuContext.bindTexture(0, TEXTURE_2D, cubeMapFaceViews[0][sideIndex]);
        gpuContext.bindTexture(1, TEXTURE_2D, cubeMapFaceViews[1][sideIndex]);
        gpuContext.bindTexture(2, TEXTURE_2D, cubeMapFaceViews[2][sideIndex]);
        gpuContext.bindTexture(4, textureManager.getCubeMap());

		secondPassDirectionalProgram.use();
		secondPassDirectionalProgram.setUniform("eyePosition", getPosition());
		secondPassDirectionalProgram.setUniform("ambientOcclusionRadius", config.getEffects().getAmbientocclusionRadius());
		secondPassDirectionalProgram.setUniform("ambientOcclusionTotalStrength", config.getEffects().getAmbientocclusionTotalStrength());
		secondPassDirectionalProgram.setUniform("screenWidth", (float) RESOLUTION);
		secondPassDirectionalProgram.setUniform("screenHeight", (float) RESOLUTION);
		FloatBuffer viewMatrix = getCamera().getViewMatrixAsBuffer();
		secondPassDirectionalProgram.setUniformAsMatrix4("viewMatrix", viewMatrix);
		FloatBuffer projectionMatrix = getCamera().getProjectionMatrixAsBuffer();
		secondPassDirectionalProgram.setUniformAsMatrix4("projectionMatrix", projectionMatrix);
		secondPassDirectionalProgram.setUniformAsMatrix4("shadowMatrix", directionalLight.getViewProjectionMatrixAsBuffer());
		secondPassDirectionalProgram.setUniform("lightDirection", directionalLight.getEntity().getViewDirection());
		secondPassDirectionalProgram.setUniform("lightDiffuse", directionalLight.getColor());
		secondPassDirectionalProgram.setUniform("currentProbe", probe.getIndex());
        secondPassDirectionalProgram.setUniform("activeProbeCount", environmentProbeManager.getProbes().size());
        environmentProbeManager.bindEnvironmentProbePositions(secondPassDirectionalProgram);
//		LOGGER.de.hanno.hpengine.log(Level.INFO, String.format("DIR LIGHT: %f %f %f", directionalLight.getOrientation().x, directionalLight.getOrientation().y, directionalLight.getOrientation().z));
		VertexBufferExtensionsKt.draw(fullscreenBuffer);


        gpuContext.enable(CULL_FACE);
		doPointLights(this.getCamera(), pointLights, camPosition, viewMatrix, projectionMatrix);
        gpuContext.disable(CULL_FACE);

		doTubeLights(tubeLights, camPositionV4, viewMatrix, projectionMatrix);

		doAreaLights(areaLights, viewMatrix, projectionMatrix);

        gpuContext.disable(BLEND);

		if (DeferredRenderingBuffer.RENDER_PROBES_WITH_FIRST_BOUNCE) {
			renderReflectionsSecondBounce(viewMatrix, projectionMatrix,
					cubeMapFaceViews[0][sideIndex],
					cubeMapFaceViews[1][sideIndex],
					cubeMapFaceViews[2][sideIndex], sideIndex);
		}
        gpuContext.enable(CULL_FACE);
        gpuContext.setCullFace(true);
        gpuContext.setCullMode(BACK);
        gpuContext.setDepthFunc(LESS);

//		GL11.glDeleteTextures(cubeMapFaceView);
//		GL11.glDeleteTextures(cubeMapFaceView1);
//		GL11.glDeleteTextures(cubeMapFaceView2);
	}
	
	private void renderReflectionsSecondBounce(FloatBuffer viewMatrix, FloatBuffer projectionMatrix, int positionMap, int normalMap, int colorMap, int sideIndex) {
        gpuContext.bindTexture(0, TEXTURE_2D, positionMap);
        gpuContext.bindTexture(1, TEXTURE_2D, normalMap);
        gpuContext.bindTexture(2, TEXTURE_2D, colorMap);
        gpuContext.bindTexture(8, TEXTURE_2D, colorMap);
		gpuContext.bindTexture(8, environmentProbeManager.getEnvironmentMapsArray(3));
        gpuContext.bindTexture(9, textureManager.getCubeMap());

        gpuContext.bindImageTexture(6, environmentProbeManager.getCubeMapArrayRenderTarget().getCubeMapArray(3).getId(), 0, false, 6 * probe.getIndex() + sideIndex, GL15.GL_WRITE_ONLY, GL30.GL_RGBA16F);
		tiledProbeLightingProgram.use();
		tiledProbeLightingProgram.setUniform("secondBounce", DeferredRenderingBuffer.RENDER_PROBES_WITH_SECOND_BOUNCE);
		tiledProbeLightingProgram.setUniform("screenWidth", (float) RESOLUTION);
		tiledProbeLightingProgram.setUniform("screenHeight", (float) RESOLUTION);
		tiledProbeLightingProgram.setUniform("currentProbe", probe.getIndex());
		tiledProbeLightingProgram.setUniformAsMatrix4("viewMatrix", viewMatrix);
		tiledProbeLightingProgram.setUniformAsMatrix4("projectionMatrix", projectionMatrix);
        tiledProbeLightingProgram.setUniform("activeProbeCount", environmentProbeManager.getProbes().size());
        environmentProbeManager.bindEnvironmentProbePositions(tiledProbeLightingProgram);
		tiledProbeLightingProgram.dispatchCompute(RESOLUTION/16, RESOLUTION/16+1, 1);
//		GL42.glMemoryBarrier(GL42.GL_ALL_BARRIER_BITS);
	}
	
	private void bindShaderSpecificsPerCubeMapSide(FloatBuffer viewMatrixAsBuffer, FloatBuffer projectionMatrixAsBuffer, FloatBuffer viewProjectionMatrixAsBuffer, Program program) {
		DirectionalLight light = scene.getEntitySystems().get(DirectionalLightSystem.class).getDirectionalLight();
		program.setUniform("lightDirection", light.getEntity().getViewDirection());
		program.setUniform("lightDiffuse", light.getColor());
		program.setUniform("lightAmbient", config.getEffects().getAmbientLight());
		program.setUniformAsMatrix4("viewMatrix", viewMatrixAsBuffer);
		program.setUniformAsMatrix4("projectionMatrix", projectionMatrixAsBuffer);
		program.setUniformAsMatrix4("viewProjectionMatrix", viewProjectionMatrixAsBuffer);
		program.setUniformAsMatrix4("shadowMatrix", light.getViewProjectionMatrixAsBuffer());
	}

	private void generateCubeMapMipMaps() {
		if(config.getQuality().isUsePrecomputedRadiance()) {
			
			_generateCubeMapMipMaps();
			
			if (config.getQuality().isCalculateActualRadiance()) {

                CubeMapArray cubeMapArray = environmentProbeManager.getEnvironmentMapsArray(3);
				int internalFormat = cubeMapArray.getInternalFormat();
				int cubemapArrayColorTextureId = cubeMapArray.getId();
				int cubeMapView = GL11.glGenTextures();

                GL43.glTextureView(cubeMapView, GL13.GL_TEXTURE_CUBE_MAP, cubeMapArray.getId(),
						cubeMapArray.getInternalFormat(), 0, environmentProbeManager.CUBEMAP_MIPMAP_COUNT,
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
			}
			
		} else {
			_generateCubeMapMipMaps();
		}
	}

	private void calculateRadianceFragment(int internalFormat,
			int cubemapArrayColorTextureId, int cubeMapView, int cubemapCopy) {
		
		cubemapRadianceFragmentProgram.use();
        CubeMapArrayRenderTarget cubeMapArrayRenderTarget = environmentProbeManager.getCubeMapArrayRenderTarget();
		renderTarget.use(gpuContext, false);

        gpuContext.bindTexture(8, TEXTURE_CUBE_MAP, cubeMapView);
		//GL11.glBindTexture(GL13.GL_TEXTURE_CUBE_MAP, renderer.getEnvironmentMap().getTextureId());
		
		for (int i = 0; i < 6; i++) {

			int width = RESOLUTION;
			int height = RESOLUTION;
			
			int indexOfFace = 6 * probe.getIndex() + i;

			for (int z = 0; z < EnvironmentProbeManager.CUBEMAP_MIPMAP_COUNT; z++) {
				//cubeMapArrayRenderTarget.setCubeMapFace(0, probe.getIndex(), indexOfFace);
				renderTarget.setCubeMapFace(0, cubeMapView, i, z);
				width /= 2;
				height /= 2;

                gpuContext.clearColorBuffer();
                gpuContext.clearColor(0, 0, 0, 0);

				cubemapRadianceFragmentProgram.setUniform("currentCubemapSide", i);
				cubemapRadianceFragmentProgram.setUniform("currentProbe", probe.getIndex());
				cubemapRadianceFragmentProgram.setUniform("screenWidth", (float) width);
				cubemapRadianceFragmentProgram.setUniform("screenHeight", (float) height);
                environmentProbeManager.bindEnvironmentProbePositions(cubemapRadianceFragmentProgram);
                gpuContext.viewPort(0, 0, width, height);
				VertexBufferExtensionsKt.draw(fullscreenBuffer);
			}
		}
	}
	private void calculateRadianceCompute(int internalFormat,
			int cubemapArrayColorTextureId, int cubeMapView, int cubemapCopy) {
		cubemapRadianceProgram.use();
		int width = RESOLUTION / 2;
		int height = RESOLUTION / 2;
		
		for (int i = 0; i < 6; i++) {

			int indexOfFace = 6 * probe.getIndex() + i;

			for (int z = 0; z < EnvironmentProbeManager.CUBEMAP_MIPMAP_COUNT; z++) {
				GL42.glBindImageTexture(z, cubemapArrayColorTextureId, z + 1, false, indexOfFace, GL15.GL_WRITE_ONLY, internalFormat);
			}

            gpuContext.bindTexture(8, TEXTURE_CUBE_MAP, cubemapCopy);

			cubemapRadianceProgram.setUniform("currentCubemapSide", i);
			cubemapRadianceProgram.setUniform("currentProbe", probe.getIndex());
			cubemapRadianceProgram.setUniform("screenWidth", (float) width);
			cubemapRadianceProgram.setUniform("screenHeight", (float) height);
            environmentProbeManager.bindEnvironmentProbePositions(cubemapRadianceProgram);
			cubemapRadianceProgram.dispatchCompute((RESOLUTION / 2) / 32, (RESOLUTION / 2) / 32, 1);
		}
	}
	private void _generateCubeMapMipMaps() {


		boolean use2DMipMapping = false;
        CubeMapArray cubeMapArray = environmentProbeManager.getEnvironmentMapsArray(3);
		if (use2DMipMapping ) {
			for (int i = 0; i < 6; i++) {
				int cubeMapFaceView = GL11.glGenTextures();
                GL43.glTextureView(cubeMapFaceView, GL11.GL_TEXTURE_2D, cubeMapArray.getId(), cubeMapArray.getInternalFormat(), 0, environmentProbeManager.CUBEMAP_MIPMAP_COUNT, 6 * probe.getIndex() + i, 1);
                textureManager.generateMipMaps(TEXTURE_2D, cubeMapFaceView);
				GL11.glDeleteTextures(cubeMapFaceView);
			}
			
		} else {
			int cubeMapView = GL11.glGenTextures();
            GL43.glTextureView(cubeMapView, GL13.GL_TEXTURE_CUBE_MAP, cubeMapArray.getId(), cubeMapArray.getInternalFormat(), 0, environmentProbeManager.CUBEMAP_MIPMAP_COUNT, 6*probe.getIndex(), 6);
            textureManager.generateMipMaps(TEXTURE_CUBE_MAP, cubeMapView);
			GL11.glDeleteTextures(cubeMapView);
		}
	}

	private void doPointLights(Camera camera, List<PointLight> pointLights,
			Vector3f camPosition, FloatBuffer viewMatrix,
			FloatBuffer projectionMatrix) {
		
		if(pointLights.isEmpty()) { return; }

		secondPassPointProgram.use();

//		secondPassPointProgram.setUniform("lightCount", pointLights.size());
//		secondPassPointProgram.setUniformAsBlock("pointlights", PointLight.convert(pointLights));
		secondPassPointProgram.setUniform("screenWidth", (float) RESOLUTION);
		secondPassPointProgram.setUniform("screenHeight", (float) RESOLUTION);
		secondPassPointProgram.setUniformAsMatrix4("viewMatrix", viewMatrix);
		secondPassPointProgram.setUniformAsMatrix4("projectionMatrix", projectionMatrix);

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

			VertexBufferExtensionsKt.draw(fullscreenBuffer);
			light.draw(secondPassPointProgram);
			
			if(firstLightDrawn) {
				light.drawAgain(null, secondPassPointProgram);
			} else {
				light.draw(secondPassPointProgram);
			}
			firstLightDrawn = true;
		}
	}
	private void doTubeLights(List<TubeLight> tubeLights,
			Vector4f camPositionV4, FloatBuffer viewMatrix,
			FloatBuffer projectionMatrix) {

		
		if(tubeLights.isEmpty()) { return; }
		
		secondPassTubeProgram.use();
		secondPassTubeProgram.setUniform("screenWidth", (float) RESOLUTION);
		secondPassTubeProgram.setUniform("screenHeight", (float) RESOLUTION);
		secondPassTubeProgram.setUniformAsMatrix4("viewMatrix", viewMatrix);
		secondPassTubeProgram.setUniformAsMatrix4("projectionMatrix", projectionMatrix);
		for (TubeLight tubeLight : tubeLights) {
			boolean camInsideLightVolume = new AABB(tubeLight.getEntity().getPosition(), new Vector3f(tubeLight.getLength(), tubeLight.getRadius(), tubeLight.getRadius())).contains(camPositionV4);
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
		

		secondPassAreaProgram.use();
		secondPassAreaProgram.setUniform("screenWidth", (float) RESOLUTION);
		secondPassAreaProgram.setUniform("screenHeight", (float) RESOLUTION);
		secondPassAreaProgram.setUniformAsMatrix4("viewMatrix", viewMatrix);
		secondPassAreaProgram.setUniformAsMatrix4("projectionMatrix", projectionMatrix);
        gpuContext.disable(CULL_FACE);
        gpuContext.disable(DEPTH_TEST);
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
            secondPassAreaProgram.setUniformAsMatrix4("shadowMatrix", scene.getAreaLightSystem().getShadowMatrixForAreaLight(areaLight));

			// TODO: Add textures to arealights
//			try {
//				GL13.glActiveTexture(GL13.GL_TEXTURE0 + 8);
//				Texture lightTexture = TextureManager.getInstance().getDiffuseTexture("brick.hptexture");
//				GL11.glBindTexture(GL11.GL_TEXTURE_2D, lightTexture.getTextureId());
//			} catch (IOException e) {
//				e.printStackTrace();
//			}

            gpuContext.bindTexture(9, TEXTURE_2D, scene.getAreaLightSystem().getDepthMapForAreaLight(areaLight));
			VertexBufferExtensionsKt.draw(fullscreenBuffer);
//			areaLight.getVertexBuffer().drawDebug();
		}
		
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
