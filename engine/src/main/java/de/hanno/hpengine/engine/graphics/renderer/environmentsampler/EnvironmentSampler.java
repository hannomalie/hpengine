package de.hanno.hpengine.engine.graphics.renderer.environmentsampler;

import com.google.common.eventbus.Subscribe;
import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.camera.Camera;
import de.hanno.hpengine.engine.config.Config;
import de.hanno.hpengine.engine.container.EntitiesContainer;
import de.hanno.hpengine.engine.event.MaterialChangedEvent;
import de.hanno.hpengine.engine.graphics.light.*;
import de.hanno.hpengine.engine.graphics.renderer.GraphicsContext;
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
import de.hanno.hpengine.engine.graphics.shader.ProgramFactory;
import de.hanno.hpengine.engine.graphics.shader.Shader;
import de.hanno.hpengine.engine.graphics.shader.define.Defines;
import de.hanno.hpengine.engine.graphics.state.RenderState;
import de.hanno.hpengine.engine.model.*;
import de.hanno.hpengine.engine.model.texture.CubeMapArray;
import de.hanno.hpengine.engine.scene.*;
import de.hanno.hpengine.engine.transform.Spatial;
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

import static de.hanno.hpengine.engine.graphics.renderer.constants.BlendMode.FUNC_ADD;
import static de.hanno.hpengine.engine.graphics.renderer.constants.BlendMode.Factor.ONE;
import static de.hanno.hpengine.engine.graphics.renderer.constants.CullMode.BACK;
import static de.hanno.hpengine.engine.graphics.renderer.constants.GlCap.*;
import static de.hanno.hpengine.engine.graphics.renderer.constants.GlDepthFunc.LEQUAL;
import static de.hanno.hpengine.engine.graphics.renderer.constants.GlDepthFunc.LESS;
import static de.hanno.hpengine.engine.graphics.renderer.constants.GlTextureTarget.TEXTURE_2D;
import static de.hanno.hpengine.engine.graphics.renderer.constants.GlTextureTarget.TEXTURE_CUBE_MAP;

public class EnvironmentSampler extends Camera {
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
	
	public EnvironmentSampler(Engine engine, EnvironmentProbe probe, Vector3f position, int width, int height, int probeIndex) throws Exception {
		super(0.1f, 5000f, 90f, 1f);
        setWidth(width);
        setWidth(height);
        translate(position);
		initialize();
		this.engine = engine;
        this.renderer = Engine.getInstance().getRenderer();
		this.probe = probe;
		float far = 5000f;
		float near = 0.1f;
		float fov = 90f;
		Matrix4f projectionMatrix = Util.createPerspective(fov, 1, near, far);
		setFar(far);
		setNear(near);
		setFov(fov);
		setRatio(1f);
//		projectionMatrix = Util.createOrthogonal(position.x-width/2, position.x+width/2, position.y+height/2, position.y-height/2, near, far);
		setParent(probe);
		Quaternionf cubeMapCamInitialOrientation = new Quaternionf().identity();
		rotate(cubeMapCamInitialOrientation);
//		rotate(new Vector4f(0, 1, 0, 90));
//		setPosition(position);

        ProgramFactory programFactory = Engine.getInstance().getProgramFactory();
		cubeMapProgram = programFactory.getProgramFromFileNames("first_pass_vertex.glsl", "cubemap_fragment.glsl", new Defines());
		depthPrePassProgram = programFactory.getProgramFromFileNames("first_pass_vertex.glsl", "cubemap_fragment.glsl", new Defines());
		cubeMapLightingProgram = programFactory.getProgramFromFileNames("first_pass_vertex.glsl", "cubemap_lighting_fragment.glsl", new Defines());
		tiledProbeLightingProgram = programFactory.getComputeProgram("tiled_probe_lighting_probe_rendering_compute.glsl");
		cubemapRadianceProgram = programFactory.getComputeProgram("cubemap_radiance_compute.glsl");
		cubemapRadianceFragmentProgram = programFactory.getProgramFromFileNames("passthrough_vertex.glsl", "cubemap_radiance_fragment.glsl", new Defines());
		secondPassPointProgram = programFactory.getProgram(Shader.ShaderSourceFactory.getShaderSource(new File(Shader.getDirectory() + "second_pass_point_vertex.glsl")), Shader.ShaderSourceFactory.getShaderSource(new File(Shader.getDirectory() + "second_pass_point_fragment.glsl")), new Defines());
		secondPassTubeProgram = programFactory.getProgram(Shader.ShaderSourceFactory.getShaderSource(new File(Shader.getDirectory() + "second_pass_point_vertex.glsl")), Shader.ShaderSourceFactory.getShaderSource(new File(Shader.getDirectory() + "second_pass_tube_fragment.glsl")), new Defines());
		secondPassAreaProgram = programFactory.getProgram(Shader.ShaderSourceFactory.getShaderSource(new File(Shader.getDirectory() + "second_pass_area_vertex.glsl")), Shader.ShaderSourceFactory.getShaderSource(new File(Shader.getDirectory() + "second_pass_area_fragment.glsl")), new Defines());
		secondPassDirectionalProgram = programFactory.getProgram(Shader.ShaderSourceFactory.getShaderSource(new File(Shader.getDirectory() + "second_pass_directional_vertex.glsl")), Shader.ShaderSourceFactory.getShaderSource(new File(Shader.getDirectory() + "second_pass_directional_fragment.glsl")), new Defines());

        CubeMapArrayRenderTarget cubeMapArrayRenderTarget = Engine.getInstance().getEnvironmentProbeFactory().getCubeMapArrayRenderTarget();
		cubeMapView = GL11.glGenTextures();
		cubeMapView1 = GL11.glGenTextures();
		cubeMapView2 = GL11.glGenTextures();
		GraphicsContext.exitOnGLError("EnvironmentSampler before view creation");
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
        GL43.glTextureView(cubeMapView, GL13.GL_TEXTURE_CUBE_MAP, cubeMapArrayRenderTarget.getCubeMapArray(0).getTextureID(), cubeMapArrayRenderTarget.getCubeMapArray(0).getInternalFormat(), 0, Engine.getInstance().getEnvironmentProbeFactory().CUBEMAPMIPMAPCOUNT, 6*probeIndex, 6);
        GL43.glTextureView(cubeMapView1, GL13.GL_TEXTURE_CUBE_MAP, cubeMapArrayRenderTarget.getCubeMapArray(1).getTextureID(), cubeMapArrayRenderTarget.getCubeMapArray(1).getInternalFormat(), 0, Engine.getInstance().getEnvironmentProbeFactory().CUBEMAPMIPMAPCOUNT, 6*probeIndex, 6);
        GL43.glTextureView(cubeMapView2, GL13.GL_TEXTURE_CUBE_MAP, cubeMapArrayRenderTarget.getCubeMapArray(2).getTextureID(), cubeMapArrayRenderTarget.getCubeMapArray(2).getInternalFormat(), 0, Engine.getInstance().getEnvironmentProbeFactory().CUBEMAPMIPMAPCOUNT, 6*probeIndex, 6);


		renderTarget = new RenderTargetBuilder().setWidth(EnvironmentProbeFactory.RESOLUTION )
								.setHeight(EnvironmentProbeFactory.RESOLUTION )
								.add(new ColorAttachmentDefinition()
										.setInternalFormat(cubeMapArrayRenderTarget.getCubeMapArray(3).getInternalFormat()))
								.build();

		fullscreenBuffer = new QuadVertexBuffer(true);
		fullscreenBuffer.upload();
		Engine.getEventBus().register(this);
		GraphicsContext.exitOnGLError("EnvironmentSampler constructor");
	}

	public void drawCubeMap(boolean urgent, RenderState extract) {
		drawCubeMapSides(urgent, extract);
	}
	
	private void drawCubeMapSides(boolean urgent, RenderState extract) {
        Scene scene = Engine.getInstance().getSceneManager().getScene();
        if(scene == null) { return; }

        EntitiesContainer octree = scene.getEntitiesContainer();
		GPUProfiler.start("Cubemap render 6 sides");
		Quaternionf initialOrientation = getRotation();
		Vector3f initialPosition = getPosition();

		DirectionalLight light = scene.getDirectionalLight();
        Engine.getInstance().getEnvironmentProbeFactory().getEnvironmentMapsArray().bind(8);
        Engine.getInstance().getEnvironmentProbeFactory().getEnvironmentMapsArray(0).bind(10);

        Engine.getInstance().getGpuContext().disable(DEPTH_TEST);
        Engine.getInstance().getGpuContext().depthFunc(LEQUAL);

        renderTarget.use(false);
		bindProgramSpecificsPerCubeMap();

		boolean filteringRequired = false;
		for(int i = 0; i < 6; i++) {
			rotateForIndex(i, this);
			boolean fullRerenderRequired = urgent || !drawnOnce;
			boolean aPointLightHasMoved = !scene.getPointLights().stream().filter(e -> { return probe.getBox().containsOrIntersectsSphere(e.getPosition(), e.getRadius()); }).filter(e -> { return e.hasMoved(); }).collect(Collectors.toList()).isEmpty();
			boolean areaLightHasMoved = !scene.getAreaLights().stream().filter(e -> { return e.hasMoved(); }).collect(Collectors.toList()).isEmpty();
			boolean rerenderLightingRequired = light.hasMoved() || aPointLightHasMoved || areaLightHasMoved;
			boolean noNeedToRedraw = !urgent && !fullRerenderRequired && !rerenderLightingRequired;

			if(noNeedToRedraw) {  // early exit if only static objects visible and lights didn't change
//				continue;
			} else if(rerenderLightingRequired) {
//				cubeMapLightingProgram.use();
			} else if(fullRerenderRequired) {
//				cubeMapProgram.use();
			}
			filteringRequired = true;

			GPUProfiler.start("side " + i);
			if (deferredRenderingForProbes) {
				if (!sidesDrawn.contains(i))
				{
					GPUProfiler.start("Switch attachment");
                    Engine.getInstance().getEnvironmentProbeFactory().getCubeMapArrayRenderTarget().setCubeMapFace(0, probe.getIndex(), i);
                    Engine.getInstance().getEnvironmentProbeFactory().getCubeMapArrayRenderTarget().setCubeMapFace(1, probe.getIndex(), i);
                    Engine.getInstance().getEnvironmentProbeFactory().getCubeMapArrayRenderTarget().setCubeMapFace(2, probe.getIndex(), i);
                    Engine.getInstance().getGpuContext().clearDepthAndColorBuffer();
					GPUProfiler.end();

					GPUProfiler.start("Fill GBuffer");
					drawFirstPass(i, this, scene.getEntities(), extract);
                    Engine.getInstance().getEnvironmentProbeFactory().getCubeMapArrayRenderTarget().resetAttachments();

					GPUProfiler.end();
				}
				GPUProfiler.start("Second pass");
                Engine.getInstance().getEnvironmentProbeFactory().getCubeMapArrayRenderTarget().setCubeMapFace(3, 0, probe.getIndex(), i);
                Engine.getInstance().getGpuContext().clearDepthAndColorBuffer();
				drawSecondPass(i, light, scene.getPointLights(), scene.getTubeLights(), scene.getAreaLights());
				GPUProfiler.end();
				registerSideAsDrawn(i);
			} else {
                Engine.getInstance().getEnvironmentProbeFactory().getCubeMapArrayRenderTarget().setCubeMapFace(3, 0, probe.getIndex(), i);
                Engine.getInstance().getGpuContext().clearDepthAndColorBuffer();
				drawEntities(extract, cubeMapProgram, getViewMatrixAsBuffer(), getProjectionMatrixAsBuffer());
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
		renderer.addRenderProbeCommand(probe, true);
		renderer.addRenderProbeCommand(probe, true);
		renderer.addRenderProbeCommand(probe, true);
	}

	private void bindProgramSpecificsPerCubeMap() {
		cubeMapProgram.use();
		cubeMapProgram.setUniform("firstBounceForProbe", GBuffer.RENDER_PROBES_WITH_FIRST_BOUNCE);
		cubeMapProgram.setUniform("probePosition", probe.getCenter());
		cubeMapProgram.setUniform("probeSize", probe.getSize());
		cubeMapProgram.setUniform("activePointLightCount", engine.getSceneManager().getScene().getPointLights().size());
        cubeMapProgram.setUniformVector3ArrayAsFloatBuffer("pointLightPositions", Engine.getInstance().getLightFactory().getPointLightPositions());
        cubeMapProgram.setUniformVector3ArrayAsFloatBuffer("pointLightColors", Engine.getInstance().getLightFactory().getPointLightColors());
        cubeMapProgram.setUniformFloatArrayAsFloatBuffer("pointLightRadiuses", Engine.getInstance().getLightFactory().getPointLightRadiuses());
		
		cubeMapProgram.setUniform("activeAreaLightCount", engine.getSceneManager().getScene().getAreaLights().size());
        cubeMapProgram.setUniformVector3ArrayAsFloatBuffer("areaLightPositions", Engine.getInstance().getLightFactory().getAreaLightPositions());
        cubeMapProgram.setUniformVector3ArrayAsFloatBuffer("areaLightColors", Engine.getInstance().getLightFactory().getAreaLightColors());
        cubeMapProgram.setUniformVector3ArrayAsFloatBuffer("areaLightWidthHeightRanges", Engine.getInstance().getLightFactory().getAreaLightWidthHeightRanges());
        cubeMapProgram.setUniformVector3ArrayAsFloatBuffer("areaLightViewDirections", Engine.getInstance().getLightFactory().getAreaLightViewDirections());
        cubeMapProgram.setUniformVector3ArrayAsFloatBuffer("areaLightUpDirections", Engine.getInstance().getLightFactory().getAreaLightUpDirections());
        cubeMapProgram.setUniformVector3ArrayAsFloatBuffer("areaLightRightDirections", Engine.getInstance().getLightFactory().getAreaLightRightDirections());
		
		for(int i = 0; i < Math.min(engine.getSceneManager().getScene().getAreaLights().size(), LightFactory.MAX_AREALIGHT_SHADOWMAPS); i++) {
			AreaLight areaLight = engine.getSceneManager().getScene().getAreaLights().get(i);
            Engine.getInstance().getGpuContext().bindTexture(9 + i, TEXTURE_2D, Engine.getInstance().getLightFactory().getDepthMapForAreaLight(areaLight));
            cubeMapProgram.setUniformAsMatrix4("areaLightShadowMatrices[" + i + "]", Engine.getInstance().getLightFactory().getShadowMatrixForAreaLight(areaLight));
		}
		
		cubeMapProgram.setUniform("probeIndex", probe.getIndex());
        Engine.getInstance().getEnvironmentProbeFactory().bindEnvironmentProbePositions(cubeMapProgram);
	}

	private void drawEntities(RenderState renderState, Program program, FloatBuffer viewMatrixAsBuffer, FloatBuffer projectionMatrixAsBuffer) {
		bindShaderSpecificsPerCubeMapSide(viewMatrixAsBuffer, projectionMatrixAsBuffer);

		GPUProfiler.start("Cubemapside draw entities");
		for (RenderBatch e : renderState.getRenderBatchesStatic()) {
			if (!Spatial.isInFrustum(this, e.getCenterWorld(), e.getMinWorld(), e.getMaxWorld())) {
				continue;
			}
			DrawStrategy.draw(renderState, e);
		}
		GPUProfiler.end();
	}
	
	void drawFirstPass(int sideIndex, Camera camera, List<Entity> entities, RenderState extract) {
		camera.update(0.1f);
//		GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, EnvironmentProbeFactory.getInstance().getCubeMapArrayRenderTarget().getFrameBufferLocation());
//		EnvironmentProbeFactory.getInstance().getCubeMapArrayRenderTarget().resetAttachments();
        Engine.getInstance().getEnvironmentProbeFactory().getCubeMapArrayRenderTarget().setCubeMapFace(0, probe.getIndex(), sideIndex);
        Engine.getInstance().getEnvironmentProbeFactory().getCubeMapArrayRenderTarget().setCubeMapFace(1, probe.getIndex(), sideIndex);
        Engine.getInstance().getEnvironmentProbeFactory().getCubeMapArrayRenderTarget().setCubeMapFace(2, probe.getIndex(), sideIndex);

        Engine.getInstance().getGpuContext().clearDepthAndColorBuffer();
        Engine.getInstance().getGpuContext().enable(CULL_FACE);
        Engine.getInstance().getGpuContext().depthMask(true);
        Engine.getInstance().getGpuContext().enable(DEPTH_TEST);
        Engine.getInstance().getGpuContext().depthFunc(LEQUAL);
        Engine.getInstance().getGpuContext().disable(BLEND);

		GPUProfiler.start("Draw entities");
        Program firstpassDefaultProgram = Engine.getInstance().getProgramFactory().getFirstpassDefaultProgram();
        firstpassDefaultProgram.use();
        firstpassDefaultProgram.bindShaderStorageBuffer(1, extract.getMaterialBuffer());
        firstpassDefaultProgram.setUniform("useRainEffect", Config.getInstance().getRainEffect() == 0.0 ? false : true);
        firstpassDefaultProgram.setUniform("rainEffect", Config.getInstance().getRainEffect());
        firstpassDefaultProgram.setUniformAsMatrix4("viewMatrix", camera.getViewMatrixAsBuffer());
        firstpassDefaultProgram.setUniformAsMatrix4("lastViewMatrix", camera.getLastViewMatrixAsBuffer());
        firstpassDefaultProgram.setUniformAsMatrix4("projectionMatrix", camera.getProjectionMatrixAsBuffer());
        firstpassDefaultProgram.setUniform("eyePosition", camera.getPosition());
        firstpassDefaultProgram.setUniform("lightDirection", engine.getSceneManager().getScene().getDirectionalLight().getViewDirection());
        firstpassDefaultProgram.setUniform("near", camera.getNear());
        firstpassDefaultProgram.setUniform("far", camera.getFar());
        firstpassDefaultProgram.setUniform("time", (int)System.currentTimeMillis());

		for (RenderBatch entity : extract.getRenderBatchesStatic()) {
			DrawStrategy.draw(extract, entity);
		}
		for (RenderBatch entity : extract.getRenderBatchesAnimated()) {
			DrawStrategy.draw(extract, entity);
		}
		GPUProfiler.end();
        Engine.getInstance().getGpuContext().enable(CULL_FACE);
	}

	void drawSecondPass(int sideIndex, DirectionalLight directionalLight, List<PointLight> pointLights, List<TubeLight> tubeLights, List<AreaLight> areaLights) {
        CubeMapArrayRenderTarget cubeMapArrayRenderTarget = Engine.getInstance().getEnvironmentProbeFactory().getCubeMapArrayRenderTarget();
		Vector3f camPosition = getPosition();//.negate(null);
		camPosition.add(getViewDirection().mul(getNear()));
		Vector4f camPositionV4 = new Vector4f(camPosition.x, camPosition.y, camPosition.z, 0);
		
		GPUProfiler.start("Directional light");
        Engine.getInstance().getGpuContext().depthMask(true);
        Engine.getInstance().getGpuContext().enable(DEPTH_TEST);
        Engine.getInstance().getGpuContext().depthFunc(LESS);
        Engine.getInstance().getGpuContext().enable(BLEND);
        Engine.getInstance().getGpuContext().blendEquation(FUNC_ADD);
        Engine.getInstance().getGpuContext().blendFunc(ONE, ONE);

		GPUProfiler.start("Activate GBuffer textures");
        Engine.getInstance().getGpuContext().bindTexture(0, TEXTURE_2D, cubeMapFaceViews[0][sideIndex]);
        Engine.getInstance().getGpuContext().bindTexture(1, TEXTURE_2D, cubeMapFaceViews[1][sideIndex]);
        Engine.getInstance().getGpuContext().bindTexture(2, TEXTURE_2D, cubeMapFaceViews[2][sideIndex]);
        Engine.getInstance().getTextureFactory().getCubeMap().bind(4);
		GPUProfiler.end();

		secondPassDirectionalProgram.use();
		secondPassDirectionalProgram.setUniform("eyePosition", getPosition());
		secondPassDirectionalProgram.setUniform("ambientOcclusionRadius", Config.getInstance().getAmbientocclusionRadius());
		secondPassDirectionalProgram.setUniform("ambientOcclusionTotalStrength", Config.getInstance().getAmbientocclusionTotalStrength());
		secondPassDirectionalProgram.setUniform("screenWidth", (float) EnvironmentProbeFactory.RESOLUTION);
		secondPassDirectionalProgram.setUniform("screenHeight", (float) EnvironmentProbeFactory.RESOLUTION);
		FloatBuffer viewMatrix = getViewMatrixAsBuffer();
		secondPassDirectionalProgram.setUniformAsMatrix4("viewMatrix", viewMatrix);
		FloatBuffer projectionMatrix = getProjectionMatrixAsBuffer();
		secondPassDirectionalProgram.setUniformAsMatrix4("projectionMatrix", projectionMatrix);
		secondPassDirectionalProgram.setUniformAsMatrix4("shadowMatrix", directionalLight.getViewProjectionMatrixAsBuffer());
		secondPassDirectionalProgram.setUniform("lightDirection", directionalLight.getViewDirection());
		secondPassDirectionalProgram.setUniform("lightDiffuse", directionalLight.getColor());
		secondPassDirectionalProgram.setUniform("currentProbe", probe.getIndex());
        secondPassDirectionalProgram.setUniform("activeProbeCount", Engine.getInstance().getEnvironmentProbeFactory().getProbes().size());
        Engine.getInstance().getEnvironmentProbeFactory().bindEnvironmentProbePositions(secondPassDirectionalProgram);
//		LOGGER.de.hanno.hpengine.log(Level.INFO, String.format("DIR LIGHT: %f %f %f", directionalLight.getOrientation().x, directionalLight.getOrientation().y, directionalLight.getOrientation().z));
		GPUProfiler.start("Draw fullscreen buffer");
		fullscreenBuffer.draw();
		GPUProfiler.end();

		GPUProfiler.end();

        Engine.getInstance().getGpuContext().enable(CULL_FACE);
		doPointLights(this, pointLights, camPosition, viewMatrix, projectionMatrix);
        Engine.getInstance().getGpuContext().disable(CULL_FACE);

		doTubeLights(tubeLights, camPositionV4, viewMatrix, projectionMatrix);

		doAreaLights(areaLights, viewMatrix, projectionMatrix);

        Engine.getInstance().getGpuContext().disable(BLEND);

		if (GBuffer.RENDER_PROBES_WITH_FIRST_BOUNCE) {
			renderReflectionsSecondBounce(viewMatrix, projectionMatrix,
					cubeMapFaceViews[0][sideIndex],
					cubeMapFaceViews[1][sideIndex],
					cubeMapFaceViews[2][sideIndex], sideIndex);
		}
        Engine.getInstance().getGpuContext().enable(CULL_FACE);
        Engine.getInstance().getGpuContext().cullFace(BACK);
        Engine.getInstance().getGpuContext().depthFunc(LESS);

//		GL11.glDeleteTextures(cubeMapFaceView);
//		GL11.glDeleteTextures(cubeMapFaceView1);
//		GL11.glDeleteTextures(cubeMapFaceView2);
	}
	
	private void renderReflectionsSecondBounce(FloatBuffer viewMatrix, FloatBuffer projectionMatrix, int positionMap, int normalMap, int colorMap, int sideIndex) {
		GPUProfiler.start("Reflections");
        Engine.getInstance().getGpuContext().bindTexture(0, TEXTURE_2D, positionMap);
        Engine.getInstance().getGpuContext().bindTexture(1, TEXTURE_2D, normalMap);
        Engine.getInstance().getGpuContext().bindTexture(2, TEXTURE_2D, colorMap);
        Engine.getInstance().getGpuContext().bindTexture(8, TEXTURE_2D, colorMap);
        Engine.getInstance().getEnvironmentProbeFactory().getEnvironmentMapsArray(3).bind(8);
        Engine.getInstance().getTextureFactory().getCubeMap().bind(9);

        Engine.getInstance().getGpuContext().bindImageTexture(6, Engine.getInstance().getEnvironmentProbeFactory().getCubeMapArrayRenderTarget().getCubeMapArray(3).getTextureID(), 0, false, 6 * probe.getIndex() + sideIndex, GL15.GL_WRITE_ONLY, GL30.GL_RGBA16F);
		tiledProbeLightingProgram.use();
		tiledProbeLightingProgram.setUniform("secondBounce", GBuffer.RENDER_PROBES_WITH_SECOND_BOUNCE);
		tiledProbeLightingProgram.setUniform("screenWidth", (float) EnvironmentProbeFactory.RESOLUTION);
		tiledProbeLightingProgram.setUniform("screenHeight", (float) EnvironmentProbeFactory.RESOLUTION);
		tiledProbeLightingProgram.setUniform("currentProbe", probe.getIndex());
		tiledProbeLightingProgram.setUniformAsMatrix4("viewMatrix", viewMatrix);
		tiledProbeLightingProgram.setUniformAsMatrix4("projectionMatrix", projectionMatrix);
        tiledProbeLightingProgram.setUniform("activeProbeCount", Engine.getInstance().getEnvironmentProbeFactory().getProbes().size());
        Engine.getInstance().getEnvironmentProbeFactory().bindEnvironmentProbePositions(tiledProbeLightingProgram);
		tiledProbeLightingProgram.dispatchCompute(EnvironmentProbeFactory.RESOLUTION/16, EnvironmentProbeFactory.RESOLUTION/16+1, 1);
//		GL42.glMemoryBarrier(GL42.GL_ALL_BARRIER_BITS);
		GPUProfiler.end();
	}
	
	private void bindShaderSpecificsPerCubeMapSide(FloatBuffer viewMatrixAsBuffer, FloatBuffer projectionMatrixAsBuffer) {
		GPUProfiler.start("Matrix uniforms");
		DirectionalLight light = engine.getSceneManager().getScene().getDirectionalLight();
		cubeMapProgram.setUniform("lightDirection", light.getViewDirection());
		cubeMapProgram.setUniform("lightDiffuse", light.getColor());
		cubeMapProgram.setUniform("lightAmbient", Config.getInstance().getAmbientLight());
		cubeMapProgram.setUniformAsMatrix4("viewMatrix", viewMatrixAsBuffer);
		cubeMapProgram.setUniformAsMatrix4("projectionMatrix", projectionMatrixAsBuffer);
		cubeMapProgram.setUniformAsMatrix4("shadowMatrix", light.getViewMatrixAsBuffer());
		GPUProfiler.end();
	}

	private void generateCubeMapMipMaps() {
		if(Config.getInstance().isUsePrecomputedRadiance()) {
			
			_generateCubeMapMipMaps();
			
			if (Config.getInstance().isCalculateActualRadiance()) {
				GPUProfiler.start("Precompute radiance");

                CubeMapArray cubeMapArray = Engine.getInstance().getEnvironmentProbeFactory().getEnvironmentMapsArray(3);
				int internalFormat = cubeMapArray.getInternalFormat();
				int cubemapArrayColorTextureId = cubeMapArray.getTextureID();
				int cubeMapView = GL11.glGenTextures();

                GL43.glTextureView(cubeMapView, GL13.GL_TEXTURE_CUBE_MAP, cubeMapArray.getTextureID(),
						cubeMapArray.getInternalFormat(), 0, Engine.getInstance().getEnvironmentProbeFactory().CUBEMAPMIPMAPCOUNT,
						6 * probe.getIndex(), 6);

				int cubemapCopy = cubeMapView;//TextureFactory.copyCubeMap(cubeMapView, EnvironmentProbeFactory.RESOLUTION, EnvironmentProbeFactory.RESOLUTION, internalFormat);
//				renderer.getTextureFactory().generateMipMapsCubeMap(cubemapCopy);
				
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
        CubeMapArrayRenderTarget cubeMapArrayRenderTarget = Engine.getInstance().getEnvironmentProbeFactory().getCubeMapArrayRenderTarget();
		renderTarget.use(false);

        Engine.getInstance().getGpuContext().bindTexture(8, TEXTURE_CUBE_MAP, cubeMapView);
		//GL11.glBindTexture(GL13.GL_TEXTURE_CUBE_MAP, renderer.getEnvironmentMap().getTextureID());
		
		for (int i = 0; i < 6; i++) {

			int width = EnvironmentProbeFactory.RESOLUTION;
			int height = EnvironmentProbeFactory.RESOLUTION;
			
			int indexOfFace = 6 * probe.getIndex() + i;

			for (int z = 0; z < EnvironmentProbeFactory.CUBEMAPMIPMAPCOUNT; z++) {
				//cubeMapArrayRenderTarget.setCubeMapFace(0, probe.getIndex(), indexOfFace);
				renderTarget.setCubeMapFace(0, cubeMapView, i, z);
				width /= 2;
				height /= 2;

                Engine.getInstance().getGpuContext().clearColorBuffer();
                Engine.getInstance().getGpuContext().clearColor(0, 0, 0, 0);

				cubemapRadianceFragmentProgram.setUniform("currentCubemapSide", i);
				cubemapRadianceFragmentProgram.setUniform("currentProbe", probe.getIndex());
				cubemapRadianceFragmentProgram.setUniform("screenWidth", (float) width);
				cubemapRadianceFragmentProgram.setUniform("screenHeight", (float) height);
                Engine.getInstance().getEnvironmentProbeFactory().bindEnvironmentProbePositions(cubemapRadianceFragmentProgram);
                Engine.getInstance().getGpuContext().viewPort(0, 0, width, height);
				fullscreenBuffer.draw();
			}
		}
	}
	private void calculateRadianceCompute(int internalFormat,
			int cubemapArrayColorTextureId, int cubeMapView, int cubemapCopy) {
		cubemapRadianceProgram.use();
		int width = EnvironmentProbeFactory.RESOLUTION / 2;
		int height = EnvironmentProbeFactory.RESOLUTION / 2;
		
		for (int i = 0; i < 6; i++) {

			int indexOfFace = 6 * probe.getIndex() + i;

			for (int z = 0; z < EnvironmentProbeFactory.CUBEMAPMIPMAPCOUNT; z++) {
				GL42.glBindImageTexture(z, cubemapArrayColorTextureId, z + 1, false, indexOfFace, GL15.GL_WRITE_ONLY, internalFormat);
			}

            Engine.getInstance().getGpuContext().bindTexture(8, TEXTURE_CUBE_MAP, cubemapCopy);

			cubemapRadianceProgram.setUniform("currentCubemapSide", i);
			cubemapRadianceProgram.setUniform("currentProbe", probe.getIndex());
			cubemapRadianceProgram.setUniform("screenWidth", (float) width);
			cubemapRadianceProgram.setUniform("screenHeight", (float) height);
            Engine.getInstance().getEnvironmentProbeFactory().bindEnvironmentProbePositions(cubemapRadianceProgram);
			cubemapRadianceProgram.dispatchCompute((EnvironmentProbeFactory.RESOLUTION / 2) / 32, (EnvironmentProbeFactory.RESOLUTION / 2) / 32, 1);
		}
	}
	private void _generateCubeMapMipMaps() {

		GPUProfiler.start("MipMap generation");

		boolean use2DMipMapping = false;
        CubeMapArray cubeMapArray = Engine.getInstance().getEnvironmentProbeFactory().getEnvironmentMapsArray(3);
		if (use2DMipMapping ) {
			for (int i = 0; i < 6; i++) {
				int cubeMapFaceView = GL11.glGenTextures();
                GL43.glTextureView(cubeMapFaceView, GL11.GL_TEXTURE_2D, cubeMapArray.getTextureID(), cubeMapArray.getInternalFormat(), 0, Engine.getInstance().getEnvironmentProbeFactory().CUBEMAPMIPMAPCOUNT, 6 * probe.getIndex() + i, 1);
                Engine.getInstance().getTextureFactory().generateMipMaps(cubeMapFaceView, GL11.GL_NEAREST, GL11.GL_NEAREST);
				GL11.glDeleteTextures(cubeMapFaceView);
			}
			
		} else {
			int cubeMapView = GL11.glGenTextures();
            GL43.glTextureView(cubeMapView, GL13.GL_TEXTURE_CUBE_MAP, cubeMapArray.getTextureID(), cubeMapArray.getInternalFormat(), 0, Engine.getInstance().getEnvironmentProbeFactory().CUBEMAPMIPMAPCOUNT, 6*probe.getIndex(), 6);
            Engine.getInstance().getTextureFactory().generateMipMapsCubeMap(cubeMapView);
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
		secondPassPointProgram.setUniform("screenWidth", (float) EnvironmentProbeFactory.RESOLUTION);
		secondPassPointProgram.setUniform("screenHeight", (float) EnvironmentProbeFactory.RESOLUTION);
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
			light.getPosition().sub(camPosition, distance);
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
			secondPassPointProgram.setUniform("lightPosition", light.getPosition());
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
		secondPassTubeProgram.setUniform("screenWidth", (float) EnvironmentProbeFactory.RESOLUTION);
		secondPassTubeProgram.setUniform("screenHeight", (float) EnvironmentProbeFactory.RESOLUTION);
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
		

		if(areaLights.isEmpty()) { return; }
		
		GPUProfiler.start("Area light: " + areaLights.size());
		
		secondPassAreaProgram.use();
		secondPassAreaProgram.setUniform("screenWidth", (float) EnvironmentProbeFactory.RESOLUTION);
		secondPassAreaProgram.setUniform("screenHeight", (float) EnvironmentProbeFactory.RESOLUTION);
		secondPassAreaProgram.setUniformAsMatrix4("viewMatrix", viewMatrix);
		secondPassAreaProgram.setUniformAsMatrix4("projectionMatrix", projectionMatrix);
        Engine.getInstance().getGpuContext().disable(CULL_FACE);
        Engine.getInstance().getGpuContext().disable(DEPTH_TEST);
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
            secondPassAreaProgram.setUniformAsMatrix4("shadowMatrix", Engine.getInstance().getLightFactory().getShadowMatrixForAreaLight(areaLight));

			// TODO: Add textures to arealights
//			try {
//				GL13.glActiveTexture(GL13.GL_TEXTURE0 + 8);
//				Texture lightTexture = TextureFactory.getInstance().getDiffuseTexture("brick.hptexture");
//				GL11.glBindTexture(GL11.GL_TEXTURE_2D, lightTexture.getTextureID());
//			} catch (IOException e) {
//				e.printStackTrace();
//			}

            Engine.getInstance().getGpuContext().bindTexture(9, TEXTURE_2D, Engine.getInstance().getLightFactory().getDepthMapForAreaLight(areaLight));
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

}
