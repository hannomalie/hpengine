package main.renderer;

import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import main.World;
import main.camera.Camera;
import main.event.GlobalDefineChangedEvent;
import main.event.MaterialChangedEvent;
import main.model.Entity;
import main.model.Entity.Update;
import main.model.IEntity;
import main.model.ITransformable;
import main.model.QuadVertexBuffer;
import main.model.VertexBuffer;
import main.octree.Octree;
import main.renderer.light.AreaLight;
import main.renderer.light.DirectionalLight;
import main.renderer.light.LightFactory;
import main.renderer.light.PointLight;
import main.renderer.light.TubeLight;
import main.renderer.rendertarget.CubeMapArrayRenderTarget;
import main.scene.AABB;
import main.scene.EnvironmentProbe;
import main.scene.EnvironmentProbeFactory;
import main.scene.TransformDistanceComparator;
import main.shader.ComputeShaderProgram;
import main.shader.Program;
import main.texture.CubeMap;
import main.texture.CubeMapArray;
import main.texture.Texture;
import main.texture.TextureFactory;
import main.util.Util;
import main.util.stopwatch.GPUProfiler;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GL42;
import org.lwjgl.opengl.GL43;
import org.lwjgl.opengl.GLSync;
import org.lwjgl.util.glu.GLU;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Quaternion;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;

import com.google.common.eventbus.Subscribe;

public class EnvironmentSampler {
	public static volatile boolean deferredRenderingForProbes = true;
	
	private Camera camera;
	private Program cubeMapProgram;
	private Program cubeMapLightingProgram;
	private Program depthPrePassProgram;
	private ComputeShaderProgram tiledProbeLightingProgram;
	private ComputeShaderProgram cubemapRadianceProgram;
	private FloatBuffer entityBuffer = BufferUtils.createFloatBuffer(16);
	private Renderer renderer;
	transient private boolean drawnOnce = false;
	transient Set<Integer> sidesDrawn = new HashSet<>();
	transient private EnvironmentProbe probe;
	private VertexBuffer fullscreenBuffer;
	private int cubeMapView;
	private int cubeMapView1;
	private int cubeMapView2;
	private int cubeMapFaceViews[][] = new int[3][6];
	private Program secondPassPointProgram;
	private Program secondPassTubeProgram;
	private Program secondPassAreaLightProgram;
	private Program secondPassDirectionalProgram;

	public EnvironmentSampler(Renderer renderer, EnvironmentProbe probe, Vector3f position, int width, int height, int probeIndex) {
		this.renderer = renderer;
		this.probe = probe;
		float far = 5000f;
		float near = 20f;
		float fov = 90f;
		Matrix4f projectionMatrix = Util.createPerpective(fov, 1, near, far);
//		projectionMatrix = Util.createOrthogonal(position.x-width/2, position.x+width/2, position.y+height/2, position.y-height/2, near, far);
		this.camera = new Camera(renderer, projectionMatrix, near, far, fov, 1);
		Quaternion cubeMapCamInitialOrientation = new Quaternion();
		Quaternion.setIdentity(cubeMapCamInitialOrientation);
		camera.setOrientation(cubeMapCamInitialOrientation);
		camera.rotate(new Vector4f(0,1,0,90));
//		position.y = -position.y;
		camera.setPosition(position.negate(null));

		cubeMapProgram = renderer.getProgramFactory().getProgram("first_pass_vertex.glsl", "cubemap_fragment.glsl");
		depthPrePassProgram = renderer.getProgramFactory().getProgram("first_pass_vertex.glsl", "cubemap_fragment.glsl");
		cubeMapLightingProgram = renderer.getProgramFactory().getProgram("first_pass_vertex.glsl", "cubemap_lighting_fragment.glsl");
		tiledProbeLightingProgram = renderer.getProgramFactory().getComputeProgram("tiled_probe_lighting_probe_rendering_compute.glsl");
		cubemapRadianceProgram = renderer.getProgramFactory().getComputeProgram("cubemap_radiance_compute.glsl");

//		secondPassDirectionalProgram = renderer.getProgramFactory().getProgram("second_pass_directional_vertex.glsl", "second_pass_directional_probe_fragment.glsl", Entity.POSITIONCHANNEL, false);
		secondPassDirectionalProgram = renderer.getSecondPassDirectionalProgram();
		secondPassPointProgram = renderer.getSecondPassPointProgram();
		secondPassTubeProgram = renderer.getSecondPassTubeProgram();
		secondPassAreaLightProgram = renderer.getSecondPassAreaLightProgram();

		CubeMapArrayRenderTarget cubeMapArrayRenderTarget = renderer.getEnvironmentProbeFactory().getCubeMapArrayRenderTarget();
		cubeMapView = GL11.glGenTextures();
		cubeMapView1 = GL11.glGenTextures();
		cubeMapView2 = GL11.glGenTextures();
		DeferredRenderer.exitOnGLError("EnvironmentSampler before view creation");
		for (int z = 0; z < 6; z++) {
			cubeMapFaceViews[0][z] = GL11.glGenTextures();
			cubeMapFaceViews[1][z] = GL11.glGenTextures();
			cubeMapFaceViews[2][z] = GL11.glGenTextures();
			//GL43.glTextureView(cubeMapFaceViews[i][z], GL11.GL_TEXTURE_2D, cubeMapArrayRenderTarget.getCubeMapArray(i).getTextureID(), cubeMapArrayRenderTarget.getCubeMapArray(i).getInternalFormat(), 0, 1, 6 * probe.getIndex() + z, 1);
			GL43.glTextureView(cubeMapFaceViews[0][z], GL11.GL_TEXTURE_2D, cubeMapArrayRenderTarget.getCubeMapArray(0).getTextureID(), cubeMapArrayRenderTarget.getCubeMapArray(0).getInternalFormat(), 0, 1, 6 * probeIndex + z, 1);
			GL43.glTextureView(cubeMapFaceViews[1][z], GL11.GL_TEXTURE_2D, cubeMapArrayRenderTarget.getCubeMapArray(1).getTextureID(), cubeMapArrayRenderTarget.getCubeMapArray(1).getInternalFormat(), 0, 1, 6 * probeIndex + z, 1);
			GL43.glTextureView(cubeMapFaceViews[2][z], GL11.GL_TEXTURE_2D, cubeMapArrayRenderTarget.getCubeMapArray(2).getTextureID(), cubeMapArrayRenderTarget.getCubeMapArray(2).getInternalFormat(), 0, 1, 6 * probeIndex + z, 1);
		}
		GL43.glTextureView(cubeMapView, GL13.GL_TEXTURE_CUBE_MAP, cubeMapArrayRenderTarget.getCubeMapArray(0).getTextureID(), cubeMapArrayRenderTarget.getCubeMapArray(0).getInternalFormat(), 0, renderer.getEnvironmentProbeFactory().CUBEMAPMIPMAPCOUNT, 6*probeIndex, 6);
		GL43.glTextureView(cubeMapView1, GL13.GL_TEXTURE_CUBE_MAP, cubeMapArrayRenderTarget.getCubeMapArray(1).getTextureID(), cubeMapArrayRenderTarget.getCubeMapArray(1).getInternalFormat(), 0, renderer.getEnvironmentProbeFactory().CUBEMAPMIPMAPCOUNT, 6*probeIndex, 6);
		GL43.glTextureView(cubeMapView2, GL13.GL_TEXTURE_CUBE_MAP, cubeMapArrayRenderTarget.getCubeMapArray(2).getTextureID(), cubeMapArrayRenderTarget.getCubeMapArray(2).getInternalFormat(), 0, renderer.getEnvironmentProbeFactory().CUBEMAPMIPMAPCOUNT, 6*probeIndex, 6);

		fullscreenBuffer = new QuadVertexBuffer(true).upload();
		World.getEventBus().register(this);
		DeferredRenderer.exitOnGLError("EnvironmentSampler constructor");
	}

	public void drawCubeMap(Octree octree, DirectionalLight light, boolean urgent) {
		drawCubeMapSides(octree, light, urgent);
	}
	
	private void drawCubeMapSides(Octree octree, DirectionalLight light, boolean urgent) {
		GPUProfiler.start("Cubemap render 6 sides");
		Quaternion initialOrientation = camera.getOrientation();
		Vector3f initialPosition = camera.getPosition();
		GL13.glActiveTexture(GL13.GL_TEXTURE0);
		
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + 6);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, light.getShadowMapId());
//		GL13.glActiveTexture(GL13.GL_TEXTURE0 + 10);
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + 8);
		renderer.getEnvironmentProbeFactory().getEnvironmentMapsArray().bind();
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + 10);
		renderer.getEnvironmentProbeFactory().getEnvironmentMapsArray(0).bind();

		GL11.glEnable(GL11.GL_DEPTH_TEST);
		GL11.glDepthFunc(GL11.GL_LEQUAL);
		
		bindProgramSpecificsPerCubeMap();

		boolean filteringRequired = false;
		for(int i = 0; i < 6; i++) {
			rotateForIndex(i, camera);
			List<IEntity> visibles = octree.getVisible(camera);
			List<IEntity> movedVisibles = visibles.stream().filter(e -> { return e.getUpdate() == Update.STATIC; }).
					sorted(new TransformDistanceComparator<ITransformable>(camera) {
						public int compare(ITransformable o1, ITransformable o2) {
							if(reference == null) { return 0; }
							Vector3f distanceToFirst = Vector3f.sub(reference.getPosition().negate(null), o1.getPosition(), null);
							Vector3f distanceToSecond = Vector3f.sub(reference.getPosition().negate(null), o2.getPosition(), null);
							return Float.compare(distanceToFirst.lengthSquared(), distanceToSecond.lengthSquared());
						}
					}).collect(Collectors.toList());
			boolean fullRerenderRequired = urgent || !drawnOnce;
			boolean aPointLightHasMoved = !renderer.getLightFactory().getPointLights().stream().filter(e -> { return e.hasMoved(); }).collect(Collectors.toList()).isEmpty();
			boolean areaLightHasMoved = !renderer.getLightFactory().getAreaLights().stream().filter(e -> { return e.hasMoved(); }).collect(Collectors.toList()).isEmpty();
			boolean rerenderLightingRequired = light.hasMoved() || aPointLightHasMoved || areaLightHasMoved;
			boolean noNeedToRedraw = !urgent && !fullRerenderRequired && !rerenderLightingRequired;

			if(noNeedToRedraw) {  // early exit if only static objects visible and light didn't change
				continue;
			} else if(rerenderLightingRequired) {
//				cubeMapLightingProgram.use();
			} else if(fullRerenderRequired) {
//				cubeMapProgram.use();
			}
			filteringRequired = true;

			GPUProfiler.start("side " + i);
			if (deferredRenderingForProbes) {
				if (!sidesDrawn.contains(i)) {
					GPUProfiler.start("Switch attachment");
					renderer.getEnvironmentProbeFactory().getCubeMapArrayRenderTarget().setCubeMapFace(0, probe.getIndex(), i);
					renderer.getEnvironmentProbeFactory().getCubeMapArrayRenderTarget().setCubeMapFace(1, probe.getIndex(), i);
					renderer.getEnvironmentProbeFactory().getCubeMapArrayRenderTarget().setCubeMapFace(2, probe.getIndex(), i);
					GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
					GPUProfiler.end();

					GPUProfiler.start("Fill GBuffer");
					drawFirstPass(i, camera, movedVisibles);
					renderer.getEnvironmentProbeFactory().getCubeMapArrayRenderTarget().resetAttachments();

					GPUProfiler.end();
				}
				GPUProfiler.start("Second pass");
				renderer.getEnvironmentProbeFactory().getCubeMapArrayRenderTarget().setCubeMapFace(3, 0, probe.getIndex(), i);
				GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
				drawSecondPass(i, camera, light, renderer.getLightFactory().getPointLights(), renderer.getLightFactory().getTubeLights(), renderer.getLightFactory().getAreaLights(), renderer.getEnvironmentMap());
				GPUProfiler.end();
				registerSideAsDrawn(i);
			} else {
				renderer.getEnvironmentProbeFactory().getCubeMapArrayRenderTarget().setCubeMapFace(3, 0, probe.getIndex(), i);
				GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
				drawEntities(cubeMapProgram, World.light, movedVisibles, camera.getViewMatrixAsBuffer(), camera.getProjectionMatrixAsBuffer());
			}
			GPUProfiler.end();
		}
		if (filteringRequired) {
			generateCubeMapMipMaps();
		}
		camera.setPosition(initialPosition);
		camera.setOrientation(initialOrientation);
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
		cubeMapProgram.setUniform("activePointLightCount", renderer.getLightFactory().getPointLights().size());
		cubeMapProgram.setUniformVector3ArrayAsFloatBuffer("pointLightPositions", renderer.getLightFactory().getPointLightPositions());
		cubeMapProgram.setUniformVector3ArrayAsFloatBuffer("pointLightColors", renderer.getLightFactory().getPointLightColors());
		cubeMapProgram.setUniformFloatArrayAsFloatBuffer("pointLightRadiuses", renderer.getLightFactory().getPointLightRadiuses());
		
		cubeMapProgram.setUniform("activeAreaLightCount", renderer.getLightFactory().getAreaLights().size());
		cubeMapProgram.setUniformVector3ArrayAsFloatBuffer("areaLightPositions", renderer.getLightFactory().getAreaLightPositions());
		cubeMapProgram.setUniformVector3ArrayAsFloatBuffer("areaLightColors", renderer.getLightFactory().getAreaLightColors());
		cubeMapProgram.setUniformVector3ArrayAsFloatBuffer("areaLightWidthHeightRanges", renderer.getLightFactory().getAreaLightWidthHeightRanges());
		cubeMapProgram.setUniformVector3ArrayAsFloatBuffer("areaLightViewDirections", renderer.getLightFactory().getAreaLightViewDirections());
		cubeMapProgram.setUniformVector3ArrayAsFloatBuffer("areaLightUpDirections", renderer.getLightFactory().getAreaLightUpDirections());
		cubeMapProgram.setUniformVector3ArrayAsFloatBuffer("areaLightRightDirections", renderer.getLightFactory().getAreaLightRightDirections());
		
		for(int i = 0; i < Math.min(renderer.getLightFactory().getAreaLights().size(), LightFactory.MAX_AREALIGHT_SHADOWMAPS); i++) {
			AreaLight areaLight = renderer.getLightFactory().getAreaLights().get(i);
			GL13.glActiveTexture(GL13.GL_TEXTURE0 + 9 + i);
			GL11.glBindTexture(GL11.GL_TEXTURE_2D, renderer.getLightFactory().getDepthMapForAreaLight(areaLight));
			cubeMapProgram.setUniformAsMatrix4("areaLightShadowMatrices[" + i + "]", renderer.getLightFactory().getShadowMatrixForAreaLight(areaLight));
		}
		
		cubeMapProgram.setUniform("probeIndex", probe.getIndex());
		renderer.getEnvironmentProbeFactory().bindEnvironmentProbePositions(cubeMapProgram);
	}

	private void drawEntities(Program program, DirectionalLight light, List<IEntity> visibles, FloatBuffer viewMatrixAsBuffer, FloatBuffer projectionMatrixAsBuffer) {
		bindShaderSpecificsPerCubeMapSide(light, viewMatrixAsBuffer, projectionMatrixAsBuffer);

		GPUProfiler.start("Cubemapside draw entities");
		for (IEntity e : visibles) {
			if(!e.isInFrustum(camera)) { continue; }
			entityBuffer.rewind();
			e.getModelMatrix().store(entityBuffer);
			entityBuffer.rewind();
			program.setUniformAsMatrix4("modelMatrix", entityBuffer);
			e.getMaterial().setTexturesActive((Entity) e, program);
			program.setUniform("hasDiffuseMap", e.getMaterial().hasDiffuseMap());
			program.setUniform("hasNormalMap", e.getMaterial().hasNormalMap());
			program.setUniform("color", e.getMaterial().getDiffuse());
			program.setUniform("metallic", e.getMaterial().getMetallic());
			program.setUniform("roughness", e.getMaterial().getRoughness());
			e.getMaterial().setTexturesActive(null, program);
			
			e.getVertexBuffer().draw();
		}
		GPUProfiler.end();
	}
	
	void drawFirstPass(int sideIndex, Camera camera, List<IEntity> entities) {
//		GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, renderer.getEnvironmentProbeFactory().getCubeMapArrayRenderTarget().getFrameBufferLocation());
//		renderer.getEnvironmentProbeFactory().getCubeMapArrayRenderTarget().resetAttachments();
		renderer.getEnvironmentProbeFactory().getCubeMapArrayRenderTarget().setCubeMapFace(0, probe.getIndex(), sideIndex);
		renderer.getEnvironmentProbeFactory().getCubeMapArrayRenderTarget().setCubeMapFace(1, probe.getIndex(), sideIndex);
		renderer.getEnvironmentProbeFactory().getCubeMapArrayRenderTarget().setCubeMapFace(2, probe.getIndex(), sideIndex);

		GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
		GL11.glEnable(GL11.GL_CULL_FACE);
		GL11.glDepthMask(true);
		renderer.getFirstPassProgram().use();
		GL11.glEnable(GL11.GL_DEPTH_TEST);
		GL11.glDepthFunc(GL11.GL_LESS);
		GL11.glDisable(GL11.GL_BLEND);

		GPUProfiler.start("Draw entities");
//			GPUProfiler.start("Depth prepass");
//			for (IEntity entity : entities) {
//				entity.draw(renderer, camera, depthPrePassProgram);
//			}
//			GPUProfiler.end();
		for (IEntity entity : entities) {
			entity.draw(renderer, camera);
		}
		GPUProfiler.end();

		GL11.glEnable(GL11.GL_CULL_FACE);
	}

	void drawSecondPass(int sideIndex, Camera camera, DirectionalLight directionalLight, List<PointLight> pointLights, List<TubeLight> tubeLights, List<AreaLight> areaLights, CubeMap cubeMap) {

		CubeMapArrayRenderTarget cubeMapArrayRenderTarget = renderer.getEnvironmentProbeFactory().getCubeMapArrayRenderTarget();
		Vector3f camPosition = camera.getPosition().negate(null);
//		Vector3f.add(camPosition, (Vector3f) camera.getViewDirection().negate(null).scale(-camera.getNear()), camPosition);
		Vector4f camPositionV4 = new Vector4f(camPosition.x, camPosition.y, camPosition.z, 0);
		
		GPUProfiler.start("Directional light");
		GL11.glDepthMask(false);
		GL11.glEnable(GL11.GL_DEPTH_TEST);
		GL11.glDepthFunc(GL11.GL_LESS);
		GL11.glEnable(GL11.GL_BLEND);
		GL14.glBlendEquation(GL14.GL_FUNC_ADD);
		GL11.glBlendFunc(GL11.GL_ONE, GL11.GL_ONE);

		secondPassDirectionalProgram.use();

		if (!sidesDrawn.contains(sideIndex))
		{
//			GL43.glTextureView(cubeMapFaceViews[0][sideIndex], GL11.GL_TEXTURE_2D, cubeMapArrayRenderTarget.getCubeMapArray(0).getTextureID(), cubeMapArrayRenderTarget.getCubeMapArray(0).getInternalFormat(), 0, 1, 6 * probe.getIndex() + sideIndex, 1);
//			GL43.glTextureView(cubeMapFaceViews[1][sideIndex], GL11.GL_TEXTURE_2D, cubeMapArrayRenderTarget.getCubeMapArray(1).getTextureID(), cubeMapArrayRenderTarget.getCubeMapArray(1).getInternalFormat(), 0, 1, 6 * probe.getIndex() + sideIndex, 1);
//			GL43.glTextureView(cubeMapFaceViews[2][sideIndex], GL11.GL_TEXTURE_2D, cubeMapArrayRenderTarget.getCubeMapArray(2).getTextureID(), cubeMapArrayRenderTarget.getCubeMapArray(2).getInternalFormat(), 0, 1, 6 * probe.getIndex() + sideIndex, 1);
		}

		GPUProfiler.start("Activate GBuffer textures");
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + 0);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, cubeMapFaceViews[0][sideIndex]);
//		GL42.glBindImageTexture(0, renderer.getEnvironmentProbeFactory().getCubeMapArrayRenderTarget().getCubeMapArray(0).getTextureID(), 0, false, 6 * probe.getIndex() + sideIndex, GL15.GL_READ_ONLY, GL30.GL_RGBA16F);
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + 1);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, cubeMapFaceViews[1][sideIndex]);
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + 2);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, cubeMapFaceViews[2][sideIndex]);
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + 4);
		cubeMap.bind();
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + 6);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, directionalLight.getShadowMapId()); // momentum 1, momentum 2
		GPUProfiler.end();
		
		secondPassDirectionalProgram.setUniform("eyePosition", camera.getPosition());
		secondPassDirectionalProgram.setUniform("ambientOcclusionRadius", World.AMBIENTOCCLUSION_RADIUS);
		secondPassDirectionalProgram.setUniform("ambientOcclusionTotalStrength", World.AMBIENTOCCLUSION_TOTAL_STRENGTH);
		secondPassDirectionalProgram.setUniform("screenWidth", (float) EnvironmentProbeFactory.RESOLUTION);
		secondPassDirectionalProgram.setUniform("screenHeight", (float) EnvironmentProbeFactory.RESOLUTION);
		secondPassDirectionalProgram.setUniform("secondPassScale", 1f);
		FloatBuffer viewMatrix = camera.getViewMatrixAsBuffer();
		secondPassDirectionalProgram.setUniformAsMatrix4("viewMatrix", viewMatrix);
		FloatBuffer projectionMatrix = camera.getProjectionMatrixAsBuffer();
		secondPassDirectionalProgram.setUniformAsMatrix4("projectionMatrix", projectionMatrix);
		secondPassDirectionalProgram.setUniformAsMatrix4("shadowMatrix", directionalLight.getLightMatrixAsBuffer());
		secondPassDirectionalProgram.setUniform("lightDirection", directionalLight.getViewDirection());
		secondPassDirectionalProgram.setUniform("lightDiffuse", directionalLight.getColor());
//		secondPassDirectionalProgram.setUniform("scatterFactor", directionalLight.getScatterFactor());
		secondPassDirectionalProgram.setUniform("currentProbe", probe.getIndex());
		secondPassDirectionalProgram.setUniform("activeProbeCount", renderer.getEnvironmentProbeFactory().getProbes().size());
		renderer.getEnvironmentProbeFactory().bindEnvironmentProbePositions(secondPassDirectionalProgram);
//		LOGGER.log(Level.INFO, String.format("DIR LIGHT: %f %f %f", directionalLight.getOrientation().x, directionalLight.getOrientation().y, directionalLight.getOrientation().z));
		GPUProfiler.start("Draw fullscreen buffer");
		fullscreenBuffer.draw();
		GPUProfiler.end();

		GPUProfiler.end();

		GL11.glEnable(GL11.GL_CULL_FACE);
		doPointLights(camera, pointLights, camPosition, viewMatrix, projectionMatrix);
		GL11.glDisable(GL11.GL_CULL_FACE);
		
		doTubeLights(tubeLights, camPositionV4, viewMatrix, projectionMatrix);

		doAreaLights(areaLights, viewMatrix, projectionMatrix);

		GL11.glDisable(GL11.GL_BLEND);

		if (GBuffer.RENDER_PROBES_WITH_FIRST_BOUNCE) {
			renderReflectionsSecondBounce(viewMatrix, projectionMatrix,
					cubeMapFaceViews[0][sideIndex],
					cubeMapFaceViews[1][sideIndex],
					cubeMapFaceViews[2][sideIndex], sideIndex);
		}
		GL11.glEnable(GL11.GL_CULL_FACE);

		GL11.glCullFace(GL11.GL_BACK);
		GL11.glDepthFunc(GL11.GL_LESS);

//		GL11.glDeleteTextures(cubeMapFaceView);
//		GL11.glDeleteTextures(cubeMapFaceView1);
//		GL11.glDeleteTextures(cubeMapFaceView2);
	}
	
	private void renderReflectionsSecondBounce(FloatBuffer viewMatrix, FloatBuffer projectionMatrix, int positionMap, int normalMap, int colorMap, int sideIndex) {
		GPUProfiler.start("Reflections");
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + 0);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, positionMap);
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + 1);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, normalMap);
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + 2);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, colorMap);
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + 8);
		renderer.getEnvironmentProbeFactory().getEnvironmentMapsArray(3).bind();
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + 9);
		renderer.getEnvironmentMap().bind();
		
		GL42.glBindImageTexture(6, renderer.getEnvironmentProbeFactory().getCubeMapArrayRenderTarget().getCubeMapArray(3).getTextureID(), 0, false, 6 * probe.getIndex() + sideIndex, GL15.GL_WRITE_ONLY, GL30.GL_RGBA16F);
		tiledProbeLightingProgram.use();
		tiledProbeLightingProgram.setUniform("secondBounce", GBuffer.RENDER_PROBES_WITH_SECOND_BOUNCE);
		tiledProbeLightingProgram.setUniform("screenWidth", (float) EnvironmentProbeFactory.RESOLUTION);
		tiledProbeLightingProgram.setUniform("screenHeight", (float) EnvironmentProbeFactory.RESOLUTION);
		tiledProbeLightingProgram.setUniform("currentProbe", probe.getIndex());
		tiledProbeLightingProgram.setUniformAsMatrix4("viewMatrix", viewMatrix);
		tiledProbeLightingProgram.setUniformAsMatrix4("projectionMatrix", projectionMatrix);
		tiledProbeLightingProgram.setUniform("activeProbeCount", renderer.getEnvironmentProbeFactory().getProbes().size());
		renderer.getEnvironmentProbeFactory().bindEnvironmentProbePositions(tiledProbeLightingProgram);
		tiledProbeLightingProgram.dispatchCompute(EnvironmentProbeFactory.RESOLUTION/16, EnvironmentProbeFactory.RESOLUTION/16+1, 1);
//		GL42.glMemoryBarrier(GL42.GL_ALL_BARRIER_BITS);
		GPUProfiler.end();
	}
	
	private void bindShaderSpecificsPerCubeMapSide(DirectionalLight light,
			FloatBuffer viewMatrixAsBuffer, FloatBuffer projectionMatrixAsBuffer) {
		GPUProfiler.start("Matrix uniforms");
		cubeMapProgram.setUniform("lightDirection", light.getViewDirection());
		cubeMapProgram.setUniform("lightDiffuse", light.getColor());
		cubeMapProgram.setUniform("lightAmbient", World.AMBIENT_LIGHT);
		cubeMapProgram.setUniformAsMatrix4("viewMatrix", viewMatrixAsBuffer);
		cubeMapProgram.setUniformAsMatrix4("projectionMatrix", projectionMatrixAsBuffer);
		cubeMapProgram.setUniformAsMatrix4("shadowMatrix", light.getLightMatrixAsBuffer());
		GPUProfiler.end();
	}

	private void generateCubeMapMipMaps() {
		if(World.PRECOMPUTED_RADIANCE) {
			
			_generateCubeMapMipMaps();
			
			if (World.CALCULATE_ACTUAL_RADIANCE) {
				GPUProfiler.start("Precompute radiance");
				CubeMapArray cubeMapArray = renderer.getEnvironmentProbeFactory().getEnvironmentMapsArray(3);
				int internalFormat = cubeMapArray.getInternalFormat();
				int cubemapArrayColorTextureId = cubeMapArray.getTextureID();
				int cubeMapView = GL11.glGenTextures();
				
				GL43.glTextureView(cubeMapView, GL13.GL_TEXTURE_CUBE_MAP, cubeMapArray.getTextureID(),
						cubeMapArray.getInternalFormat(), 0, renderer.getEnvironmentProbeFactory().CUBEMAPMIPMAPCOUNT,
						6 * probe.getIndex(), 6);
				
				int cubemapCopy = TextureFactory.copyCubeMap(cubeMapView, EnvironmentProbeFactory.RESOLUTION, EnvironmentProbeFactory.RESOLUTION, internalFormat);
				renderer.getTextureFactory().generateMipMapsCubeMap(cubemapCopy);
				cubemapRadianceProgram.use();
				int width = EnvironmentProbeFactory.RESOLUTION / 2;
				int height = EnvironmentProbeFactory.RESOLUTION / 2;
				
				for (int i = 0; i < 6; i++) {

					int indexOfFace = 6 * probe.getIndex() + i;

					for (int z = 0; z < EnvironmentProbeFactory.CUBEMAPMIPMAPCOUNT; z++) {
						GL42.glBindImageTexture(z, cubemapArrayColorTextureId, z + 1, false, indexOfFace, GL15.GL_WRITE_ONLY, internalFormat);
					}

					GL13.glActiveTexture(GL13.GL_TEXTURE0 + 8);
					GL11.glBindTexture(GL13.GL_TEXTURE_CUBE_MAP, cubemapCopy);
					GL11.glBindTexture(GL13.GL_TEXTURE_CUBE_MAP, cubeMapView);
					//GL11.glBindTexture(GL13.GL_TEXTURE_CUBE_MAP, renderer.getEnvironmentMap().getTextureID());

					cubemapRadianceProgram.setUniform("currentCubemapSide", i);
					cubemapRadianceProgram.setUniform("currentProbe", probe.getIndex());
					cubemapRadianceProgram.setUniform("screenWidth", (float) width);
					cubemapRadianceProgram.setUniform("screenHeight", (float) height);
					renderer.getEnvironmentProbeFactory() .bindEnvironmentProbePositions(cubemapRadianceProgram);
					
					cubemapRadianceProgram.dispatchCompute((EnvironmentProbeFactory.RESOLUTION / 2) / 16, (EnvironmentProbeFactory.RESOLUTION / 2) / 16, 1);
				}
				GL11.glDeleteTextures(cubeMapView);
				GL11.glDeleteTextures(cubemapCopy);
				GPUProfiler.end();
			}
			
		} else {
			_generateCubeMapMipMaps();
		}
	}
	private void _generateCubeMapMipMaps() {

		GPUProfiler.start("MipMap generation");

		boolean use2DMipMapping = false;
		CubeMapArray cubeMapArray = renderer.getEnvironmentProbeFactory().getEnvironmentMapsArray(3);
		if (use2DMipMapping ) {
			for (int i = 0; i < 6; i++) {
				int cubeMapFaceView = GL11.glGenTextures();
				GL43.glTextureView(cubeMapFaceView, GL11.GL_TEXTURE_2D, cubeMapArray.getTextureID(), cubeMapArray.getInternalFormat(), 0, renderer.getEnvironmentProbeFactory().CUBEMAPMIPMAPCOUNT, 6 * probe.getIndex() + i, 1);
				renderer.getTextureFactory().generateMipMaps(cubeMapFaceView, GL11.GL_NEAREST, GL11.GL_NEAREST);
				GL11.glDeleteTextures(cubeMapFaceView);
			}
			
		} else {
			int cubeMapView = GL11.glGenTextures();
			GL43.glTextureView(cubeMapView, GL13.GL_TEXTURE_CUBE_MAP, cubeMapArray.getTextureID(), cubeMapArray.getInternalFormat(), 0, renderer.getEnvironmentProbeFactory().CUBEMAPMIPMAPCOUNT, 6*probe.getIndex(), 6);
			renderer.getTextureFactory().generateMipMapsCubeMap(cubeMapView);
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
		secondPassPointProgram.setUniform("secondPassScale", 1);
		secondPassPointProgram.setUniformAsMatrix4("viewMatrix", viewMatrix);
		secondPassPointProgram.setUniformAsMatrix4("projectionMatrix", projectionMatrix);
		GPUProfiler.end();

		GPUProfiler.start("Draw lights");
		boolean firstLightDrawn = false;
		for (int i = 0 ; i < pointLights.size(); i++) {
			PointLight light = pointLights.get(i);
			if(!light.isInFrustum(camera)) {
				continue;
			}
			
			Vector3f distance = new Vector3f();
			Vector3f.sub(light.getPosition(), camPosition, distance); // <----- biggest Hack ever! TODO: Check where this fuckup with the cam goes on.... :(
			float lightRadius = light.getRadius();
			
			// camera is inside light
			if (distance.length() < lightRadius) {
				GL11.glCullFace(GL11.GL_FRONT);
				GL11.glDepthFunc(GL11.GL_GEQUAL);
			} else {
			// camera is outside light, cull back sides
				GL11.glCullFace(GL11.GL_BACK);
				GL11.glDepthFunc(GL11.GL_LEQUAL);
			}

//			secondPassPointProgram.setUniform("currentLightIndex", i);
			secondPassPointProgram.setUniform("lightPosition", light.getPosition());
			secondPassPointProgram.setUniform("lightRadius", lightRadius);
			secondPassPointProgram.setUniform("lightDiffuse", light.getColor().x, light.getColor().y, light.getColor().z);
			
			if(firstLightDrawn) {
				light.drawAgain(renderer, secondPassPointProgram);
			} else {
				light.draw(renderer, secondPassPointProgram);
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
		secondPassTubeProgram.setUniform("secondPassScale", 1);
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
			tubeLight.draw(renderer, secondPassTubeProgram);
		}
	}
	
	private void doAreaLights(List<AreaLight> areaLights, FloatBuffer viewMatrix, FloatBuffer projectionMatrix) {
		

		if(areaLights.isEmpty()) { return; }
		
		GPUProfiler.start("Area lights: " + areaLights.size());
		
		secondPassAreaLightProgram.use();
		secondPassAreaLightProgram.setUniform("screenWidth", (float) EnvironmentProbeFactory.RESOLUTION);
		secondPassAreaLightProgram.setUniform("screenHeight", (float) EnvironmentProbeFactory.RESOLUTION);
		secondPassAreaLightProgram.setUniform("secondPassScale", 1);
		secondPassAreaLightProgram.setUniformAsMatrix4("viewMatrix", viewMatrix);
		secondPassAreaLightProgram.setUniformAsMatrix4("projectionMatrix", projectionMatrix);
		GL11.glDisable(GL11.GL_CULL_FACE);
		GL11.glDisable(GL11.GL_DEPTH_TEST);
		for (AreaLight areaLight : areaLights) {
//			boolean camInsideLightVolume = new AABB(areaLight.getPosition(), 2*areaLight.getScale().x, 2*areaLight.getScale().y, 2*areaLight.getScale().z).contains(camPositionV4);
//			if (camInsideLightVolume) {
//				GL11.glCullFace(GL11.GL_FRONT);
//				GL11.glDepthFunc(GL11.GL_GEQUAL);
//			} else {
//				GL11.glCullFace(GL11.GL_BACK);
//				GL11.glDepthFunc(GL11.GL_LEQUAL);
//			}
			secondPassAreaLightProgram.setUniform("lightPosition", areaLight.getPosition());
			secondPassAreaLightProgram.setUniform("lightRightDirection", areaLight.getRightDirection());
			secondPassAreaLightProgram.setUniform("lightViewDirection", areaLight.getViewDirection());
			secondPassAreaLightProgram.setUniform("lightUpDirection", areaLight.getUpDirection());
			secondPassAreaLightProgram.setUniform("lightWidth", areaLight.getWidth());
			secondPassAreaLightProgram.setUniform("lightHeight", areaLight.getHeight());
			secondPassAreaLightProgram.setUniform("lightRange", areaLight.getRange());
			secondPassAreaLightProgram.setUniform("lightDiffuse", areaLight.getColor());
			secondPassAreaLightProgram.setUniformAsMatrix4("shadowMatrix", renderer.getLightFactory().getShadowMatrixForAreaLight(areaLight));

			try {
				GL13.glActiveTexture(GL13.GL_TEXTURE0 + 8);
				Texture lightTexture = renderer.getTextureFactory().getTexture("brick.hptexture");
				GL11.glBindTexture(GL11.GL_TEXTURE_2D, lightTexture.getTextureID());
			} catch (IOException e) {
				e.printStackTrace();
			}
			GL13.glActiveTexture(GL13.GL_TEXTURE0 + 9);
			GL11.glBindTexture(GL11.GL_TEXTURE_2D, renderer.getLightFactory().getDepthMapForAreaLight(areaLight));
			fullscreenBuffer.draw();
//			areaLight.getVertexBuffer().drawDebug();
		}
		
		GPUProfiler.end();
	}
	private void rotateForIndex(int i, Camera camera) {
		float deltaNear = 0.0f;
		float deltaFar = 100.0f;
		float halfSizeX = probe.getSize().x/2;
		float halfSizeY = probe.getSize().y/2;
		float halfSizeZ = probe.getSize().z/2;
		Vector3f position = getCamera().getPosition().negate(null); // TODO: AHHhhhh, kill this hack
		float width = probe.getSize().x;
		float height = probe.getSize().y;
//		Matrix4f projectionMatrix = Util.createOrthogonal(position.z-width/2, position.z+width/2, position.y+height/2, position.y-height/2, getCamera().getNear(), getCamera().getFar());
//		Transform oldTransform = camera.getTransform();
//		camera = new Camera(renderer, projectionMatrix, getCamera().getNear(), getCamera().getFar(), 90, 1);
//		camera.setPerspective(false);
//		camera.setTransform(oldTransform);
//		camera.updateShadow();
		this.probe.setCamera(camera);
		
		switch (i) {
		case 0:
			camera.rotate(new Vector4f(0,0,1, -180));
//			probe.getCamera().setNear(0 + halfSizeX*deltaNear);
			probe.getCamera().setFar((halfSizeX) * deltaFar);
			break;
		case 1:
			camera.rotate(new Vector4f(0,1,0, -180));
//			probe.getCamera().setNear(0 + halfSizeX*deltaNear);
			probe.getCamera().setFar((halfSizeX) * deltaFar);
			break;
		case 2:
			camera.rotate(new Vector4f(0,1,0, 90));
			camera.rotate(new Vector4f(1,0,0, 90));
//			probe.getCamera().setNear(0 + halfSizeY*deltaNear);
			probe.getCamera().setFar((halfSizeY) * deltaFar);
			break;
		case 3:
			camera.rotate(new Vector4f(1,0,0, -180));
//			probe.getCamera().setNear(0 + halfSizeY*deltaNear);
			probe.getCamera().setFar((halfSizeY) * deltaFar);
			break;
		case 4:
			camera.rotate(new Vector4f(1,0,0, 90));
//			probe.getCamera().setNear(0 + halfSizeZ*deltaNear);
			probe.getCamera().setFar((halfSizeZ) * deltaFar);
			break;
		case 5:
			camera.rotate(new Vector4f(0,1,0, -180));
//			probe.getCamera().setNear(0 + halfSizeZ*deltaNear);
			probe.getCamera().setFar((halfSizeZ) * deltaFar);
			break;
		default:
			break;
		}

		camera.calculateCurrentViewMatrix();
		camera.updateShadow();
	}

	public Camera getCamera() {
		return camera;
	}

	public void setCamera(Camera camera) {
		this.camera = camera;
	}
}
