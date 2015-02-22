package main.renderer;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GL33;
import org.lwjgl.opengl.GL40;
import org.lwjgl.opengl.GL42;
import org.lwjgl.opengl.GL43;
import org.lwjgl.opengl.GL44;
import org.lwjgl.util.glu.GLU;
import org.lwjgl.util.glu.MipMap;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Quaternion;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;

import main.Transform;
import main.World;
import main.camera.Camera;
import main.model.Entity;
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
import main.renderer.rendertarget.CubeRenderTarget;
import main.scene.EnvironmentProbe;
import main.scene.EnvironmentProbeFactory;
import main.scene.TransformDistanceComparator;
import main.shader.Program;
import main.texture.CubeMap;
import main.texture.CubeMapArray;
import main.texture.DynamicCubeMap;
import main.texture.TextureFactory;
import main.util.Util;
import main.util.stopwatch.GPUProfiler;
import main.util.stopwatch.StopWatch;

public class EnvironmentSampler {
	
	private Camera camera;
	private Program cubeMapProgram;
	private Program cubeMapLightingProgram;
	private Program depthPrePassProgram;
	private FloatBuffer entityBuffer = BufferUtils.createFloatBuffer(16);
	private Renderer renderer;
	transient private boolean drawnOnce = false;
	transient Set<Integer> sidesDrawn = new HashSet<>();
	transient private EnvironmentProbe probe;
	private VertexBuffer fullscreenBuffer;
	private int cubeMapView;
	private int cubeMapView1;
	private int cubeMapView2;
	private int cubeMapFaceView;
	private int cubeMapFaceView1;
	private int cubeMapFaceView2;

	public EnvironmentSampler(Renderer renderer, EnvironmentProbe probe, Vector3f position, int width, int height) {
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

		cubeMapView = GL11.glGenTextures();
		cubeMapView1 = GL11.glGenTextures();
		cubeMapView2 = GL11.glGenTextures();
		cubeMapFaceView = GL11.glGenTextures();
		cubeMapFaceView1 = GL11.glGenTextures();
		cubeMapFaceView2 = GL11.glGenTextures();
		CubeMapArrayRenderTarget cubeMapArrayRenderTarget = renderer.getEnvironmentProbeFactory().getCubeMapArrayRenderTarget();
		GL43.glTextureView(cubeMapView, GL13.GL_TEXTURE_CUBE_MAP, cubeMapArrayRenderTarget.getCubeMapArray(0).getTextureID(), cubeMapArrayRenderTarget.getCubeMapArray(0).getInternalFormat(), 0, renderer.getEnvironmentProbeFactory().CUBEMAPMIPMAPCOUNT, 6*probe.getIndex(), 6);
		GL43.glTextureView(cubeMapView1, GL13.GL_TEXTURE_CUBE_MAP, cubeMapArrayRenderTarget.getCubeMapArray(1).getTextureID(), cubeMapArrayRenderTarget.getCubeMapArray(1).getInternalFormat(), 0, renderer.getEnvironmentProbeFactory().CUBEMAPMIPMAPCOUNT, 6*probe.getIndex(), 6);
		GL43.glTextureView(cubeMapView2, GL13.GL_TEXTURE_CUBE_MAP, cubeMapArrayRenderTarget.getCubeMapArray(2).getTextureID(), cubeMapArrayRenderTarget.getCubeMapArray(2).getInternalFormat(), 0, renderer.getEnvironmentProbeFactory().CUBEMAPMIPMAPCOUNT, 6*probe.getIndex(), 6);
		
		fullscreenBuffer = new QuadVertexBuffer(true).upload();
//		DeferredRenderer.exitOnGLError("EnvironmentSampler constructor");
	}
	
	public void drawCubeMap(Octree octree, DirectionalLight light) {
		drawCubeMapSides(octree, light);
	}
	
	private void drawCubeMapSides(Octree octree, DirectionalLight light) {
		GPUProfiler.start("Cubemap render 6 sides");
		Quaternion initialOrientation = camera.getOrientation();
		Vector3f initialPosition = camera.getPosition();
		GL13.glActiveTexture(GL13.GL_TEXTURE0);
		
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + 6);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, light.getShadowMapId());
//		GL13.glActiveTexture(GL13.GL_TEXTURE0 + 10);
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + 8);
		renderer.getEnvironmentProbeFactory().getEnvironmentMapsArray().bind();

		GL11.glEnable(GL11.GL_DEPTH_TEST);
		GL11.glDepthFunc(GL11.GL_LEQUAL);
		
		bindProgramSpecificsPerCubeMap();
		
		boolean filteringRequired = false;
		for(int i = 0; i < 6; i++) {
			rotateForIndex(i, camera);
			List<IEntity> visibles = octree.getVisible(camera);
			List<IEntity> movedVisibles = visibles.stream().filter(e -> { return e.hasMoved(); }).
					sorted(new TransformDistanceComparator<ITransformable>(camera) {
						public int compare(ITransformable o1, ITransformable o2) {
							if(reference == null) { return 0; }
							Vector3f distanceToFirst = Vector3f.sub(reference.getPosition().negate(null), o1.getPosition(), null);
							Vector3f distanceToSecond = Vector3f.sub(reference.getPosition().negate(null), o2.getPosition(), null);
							return Float.compare(distanceToFirst.lengthSquared(), distanceToSecond.lengthSquared());
						}
					}).collect(Collectors.toList());
			boolean fullRerenderRequired = !movedVisibles.isEmpty() || !drawnOnce;
			boolean aPointLightHasMoved = !renderer.getLightFactory().getPointLights().stream().filter(e -> { return e.hasMoved(); }).collect(Collectors.toList()).isEmpty();
			boolean areaLightHasMoved = !renderer.getLightFactory().getAreaLights().stream().filter(e -> { return e.hasMoved(); }).collect(Collectors.toList()).isEmpty();
			boolean rerenderLightingRequired = light.hasMoved() || aPointLightHasMoved || areaLightHasMoved;
			boolean noNeedToRedraw = !fullRerenderRequired && !rerenderLightingRequired;
			
			if(noNeedToRedraw) {  // early exit if only static objects visible and light didn't change
				continue;
			} else if(rerenderLightingRequired) {
//				cubeMapLightingProgram.use();
			} else if(fullRerenderRequired) {
//				cubeMapProgram.use();
			}
			filteringRequired = true;
			
			GPUProfiler.start("side " + i);
			boolean deferredRenderingForProbes = false;
			if (deferredRenderingForProbes) {
				if (!sidesDrawn.contains(i)) {
					GPUProfiler.start("Switch attachment");
					renderer.getEnvironmentProbeFactory().getCubeMapArrayRenderTarget().setCubeMapFace(0, probe.getIndex(), i);
					renderer.getEnvironmentProbeFactory().getCubeMapArrayRenderTarget().setCubeMapFace(1, probe.getIndex(), i);
					renderer.getEnvironmentProbeFactory().getCubeMapArrayRenderTarget().setCubeMapFace(2, probe.getIndex(), i);
					GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
					GPUProfiler.end();

					GPUProfiler.start("Fill GBuffer");
					drawFirstPass(i, camera, visibles);
					renderer.getEnvironmentProbeFactory().getCubeMapArrayRenderTarget().resetAttachments();

					GPUProfiler.end();
				}
				GPUProfiler.start("Second pass");
				renderer.getEnvironmentProbeFactory().getCubeMapArrayRenderTarget().setCubeMapFace(3, 0, probe.getIndex(), i);
				GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
				drawSecondPass(i, camera, light, renderer.getLightFactory().getPointLights(), renderer.getLightFactory().getTubeLights(), renderer.getLightFactory().getAreaLights(), renderer.getEnvironmentMap());
				GPUProfiler.end();
				GPUProfiler.end();
				registerSideAsDrawn(i);
			} else {
				renderer.getEnvironmentProbeFactory().getCubeMapArrayRenderTarget().setCubeMapFace(3, 0, probe.getIndex(), i);
				GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
				drawEntities(cubeMapProgram, World.light, visibles, camera.getViewMatrixAsBuffer(), camera.getProjectionMatrixAsBuffer());
			}
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

	private void bindProgramSpecificsPerCubeMap() {
		cubeMapProgram.use();
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
		Vector3f.add(camPosition, (Vector3f) camera.getViewDirection().negate(null).scale(-camera.getNear()), camPosition);
		Vector4f camPositionV4 = new Vector4f(camPosition.x, camPosition.y, camPosition.z, 0);
		
		GPUProfiler.start("Directional light");
		GL11.glDepthMask(false);
		GL11.glEnable(GL11.GL_DEPTH_TEST);
		GL11.glDepthFunc(GL11.GL_LESS);
		GL11.glEnable(GL11.GL_BLEND);
		GL14.glBlendEquation(GL14.GL_FUNC_ADD);
		GL11.glBlendFunc(GL11.GL_ONE, GL11.GL_ONE);

		Program secondPassDirectionalProgram = renderer.getSecondPassDirectionalProgram();
		secondPassDirectionalProgram.use();

		// WHY DOES THIS SOLVE THE ISSUE!? SHOULD ONLY DO THIS ONCE IN THE CONSTRUCTOR....
		cubeMapFaceView = GL11.glGenTextures();
		cubeMapFaceView1 = GL11.glGenTextures();
		cubeMapFaceView2 = GL11.glGenTextures();
//		System.out.println(cubeMapFaceView);
//		System.out.println(cubeMapFaceView1);
//		System.out.println(cubeMapFaceView2);

		GL43.glTextureView(cubeMapFaceView, GL11.GL_TEXTURE_2D, cubeMapArrayRenderTarget.getCubeMapArray(0).getTextureID(), cubeMapArrayRenderTarget.getCubeMapArray(0).getInternalFormat(), 0, 1, 6 * probe.getIndex() + sideIndex, 1);
		GL43.glTextureView(cubeMapFaceView1, GL11.GL_TEXTURE_2D, cubeMapArrayRenderTarget.getCubeMapArray(1).getTextureID(), cubeMapArrayRenderTarget.getCubeMapArray(1).getInternalFormat(), 0, 1, 6 * probe.getIndex() + sideIndex, 1);
		GL43.glTextureView(cubeMapFaceView2, GL11.GL_TEXTURE_2D, cubeMapArrayRenderTarget.getCubeMapArray(2).getTextureID(), cubeMapArrayRenderTarget.getCubeMapArray(2).getInternalFormat(), 0, 1, 6 * probe.getIndex() + sideIndex, 1);


		GPUProfiler.start("Activate GBuffer textures");
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + 0);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, cubeMapFaceView);
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + 1);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, cubeMapFaceView1);
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + 2);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, cubeMapFaceView2);
		
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + 4);
		cubeMap.bind();
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + 6);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, directionalLight.getShadowMapId()); // momentum 1, momentum 2
//		GL13.glActiveTexture(GL13.GL_TEXTURE0 + 7);
//		GL11.glBindTexture(GL11.GL_TEXTURE_2D, directionalLight.getShadowMapWorldPositionId()); // world position
//		GL13.glActiveTexture(GL13.GL_TEXTURE0 + 8);
//		GL11.glBindTexture(GL11.GL_TEXTURE_2D, getVisibilityMap());
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
		secondPassDirectionalProgram.setUniform("scatterFactor", directionalLight.getScatterFactor());
//		LOGGER.log(Level.INFO, String.format("DIR LIGHT: %f %f %f", directionalLight.getOrientation().x, directionalLight.getOrientation().y, directionalLight.getOrientation().z));
		GPUProfiler.start("Draw fullscreen buffer");
		fullscreenBuffer.draw();
		GPUProfiler.end();

		GPUProfiler.end();
		
//		doPointLights(camera, pointLights, camPosition, viewMatrix, projectionMatrix);
//
//		doTubeLights(tubeLights, camPositionV4, viewMatrix, projectionMatrix);
//
//		doAreaLights(areaLights, viewMatrix, projectionMatrix);
//		
		GL11.glDisable(GL11.GL_BLEND);
		GL11.glEnable(GL11.GL_CULL_FACE);

		GL11.glCullFace(GL11.GL_BACK);
		GL11.glDepthFunc(GL11.GL_LESS);
		
//		GL11.glDeleteTextures(cubeMapFaceView);
//		GL11.glDeleteTextures(cubeMapFaceView1);
//		GL11.glDeleteTextures(cubeMapFaceView2);
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
		//		
//		int errorValue = GL11.glGetError();
//		if (errorValue != GL11.GL_NO_ERROR) {
//			String errorString = GLU.gluErrorString(errorValue);
//			System.err.println("ERROR: " + errorString);
//		}
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

		camera.updateShadow();
	}

	public Camera getCamera() {
		return camera;
	}

	public void setCamera(Camera camera) {
		this.camera = camera;
	}
}
