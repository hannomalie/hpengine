package main.renderer;

import java.nio.FloatBuffer;
import java.util.List;
import java.util.stream.Collectors;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;
import org.lwjgl.util.glu.MipMap;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Quaternion;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;

import main.World;
import main.camera.Camera;
import main.model.Entity;
import main.model.IEntity;
import main.octree.Octree;
import main.renderer.light.DirectionalLight;
import main.renderer.rendertarget.CubeRenderTarget;
import main.scene.EnvironmentProbe;
import main.scene.EnvironmentProbeFactory;
import main.shader.Program;
import main.texture.CubeMap;
import main.texture.DynamicCubeMap;
import main.texture.TextureFactory;
import main.util.Util;
import main.util.stopwatch.GPUProfiler;
import main.util.stopwatch.StopWatch;

public class EnvironmentSampler {
	
	private CubeRenderTarget cubeMapRenderTarget;
	private DynamicCubeMap cubeMap;
	
	private Camera camera;
	private Program cubeMapProgram;
	private Program cubeMapLightingProgram;
	private FloatBuffer entityBuffer = BufferUtils.createFloatBuffer(16);
	private Renderer renderer;
	transient private boolean drawnOnce = false;
	transient private EnvironmentProbe probe;

	public EnvironmentSampler(Renderer renderer, EnvironmentProbe probe, Vector3f position, int width, int height) {
		this.renderer = renderer;
		this.cubeMap = new DynamicCubeMap(width, height);
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

//		DeferredRenderer.exitOnGLError("EnvironmentSampler Before CubeRenderTarget");
		this.cubeMapRenderTarget = renderer.getEnvironmentProbeFactory().getCubeMapRenderTarget();//new CubeRenderTarget(width, height, cubeMap);
//		DeferredRenderer.exitOnGLError("EnvironmentSampler CubeRenderTarget");

		cubeMapProgram = renderer.getProgramFactory().getProgram("first_pass_vertex.glsl", "cubemap_fragment.glsl");
		cubeMapLightingProgram = renderer.getProgramFactory().getProgram("first_pass_vertex.glsl", "cubemap_lighting_fragment.glsl");
//		DeferredRenderer.exitOnGLError("EnvironmentSampler constructor");
	}
	
	public CubeMap drawCubeMap(Octree octree, DirectionalLight light) {
		return drawCubeMapSides(octree, light);
	}
	
	private CubeMap drawCubeMapSides(Octree octree, DirectionalLight light) {
		GPUProfiler.start("Cube map render 6 sides");
		Quaternion initialOrientation = camera.getOrientation();
		Vector3f initialPosition = camera.getPosition();
		GL13.glActiveTexture(GL13.GL_TEXTURE0);
		
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + 6);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, light.getShadowMapId());
		
		cubeMapProgram.use();
//		cubeMapRenderTarget.use(false);
		cubeMapRenderTarget.setCubeMap(cubeMap);
		boolean filteringRequired = false;
		for(int i = 0; i < 6; i++) {
			rotateForIndex(i, camera);
			List<IEntity> visibles = octree.getVisible(camera);
			List<IEntity> movedVisibles = visibles.stream().filter(e -> { return e.hasMoved(); }).collect(Collectors.toList());
			boolean fullRerenderRequired = !movedVisibles.isEmpty() || !drawnOnce;
			boolean rerenderLightingRequired = light.hasMoved();
			boolean noNeedToRedraw = !fullRerenderRequired && !rerenderLightingRequired;
			
			if(noNeedToRedraw) {  // early exit if only static objects visible and light didn't change
				continue;
			} else if(rerenderLightingRequired) {
				cubeMapLightingProgram.use();
			} else if(fullRerenderRequired) {
				cubeMapProgram.use();
			}
			filteringRequired = true;
			
			GPUProfiler.start("side " + i);
			GPUProfiler.start("Switch attachment");
			cubeMapRenderTarget.setCubeMapFace(i);
			GPUProfiler.end();
			FloatBuffer viewMatrixAsBuffer = camera.getViewMatrixAsBuffer();
			FloatBuffer projectionMatrixAsBuffer = camera.getProjectionMatrixAsBuffer();
			
			drawEntities(light, visibles, viewMatrixAsBuffer, projectionMatrixAsBuffer);
			
			drawnOnce = true;
		}
		if (filteringRequired) {
			generateCubeMapMipMaps();
			
		}
		camera.setPosition(initialPosition);
		camera.setOrientation(initialOrientation);
		GPUProfiler.end();
		return cubeMap;
	}

	private void drawEntities(DirectionalLight light, List<IEntity> visibles, FloatBuffer viewMatrixAsBuffer, FloatBuffer projectionMatrixAsBuffer) {
		GPUProfiler.start("Matrix uniforms");
		cubeMapProgram.setUniform("lightDirection", light.getViewDirection());
		cubeMapProgram.setUniform("lightDiffuse", light.getColor());
		cubeMapProgram.setUniform("lightAmbient", World.AMBIENT_LIGHT);
		cubeMapProgram.setUniformAsMatrix4("viewMatrix", viewMatrixAsBuffer);
		cubeMapProgram.setUniformAsMatrix4("projectionMatrix", projectionMatrixAsBuffer);
		cubeMapProgram.setUniformAsMatrix4("shadowMatrix", light.getLightMatrixAsBuffer());
		GPUProfiler.end();

		GPUProfiler.start("Draw entities");
		for (IEntity e : visibles) {
//				if(!e.isInFrustum(camera)) { continue; }
			entityBuffer.rewind();
			e.getModelMatrix().store(entityBuffer);
			entityBuffer.rewind();
			cubeMapProgram.setUniformAsMatrix4("modelMatrix", entityBuffer);
			e.getMaterial().setTexturesActive((Entity) e, cubeMapProgram);
			cubeMapProgram.setUniform("hasDiffuseMap", e.getMaterial().hasDiffuseMap());
			cubeMapProgram.setUniform("hasNormalMap", e.getMaterial().hasNormalMap());
			cubeMapProgram.setUniform("color", e.getMaterial().getDiffuse());
			cubeMapProgram.setUniform("metallic", e.getMaterial().getMetallic());
			cubeMapProgram.setUniform("roughness", e.getMaterial().getRoughness());
			e.getMaterial().setTexturesActive(null, cubeMapProgram);
			
			e.getVertexBuffer().draw();
		}
		GPUProfiler.end();
		GPUProfiler.end();
	}

	private void generateCubeMapMipMaps() {
		cubeMap.bind();
		GPUProfiler.start("MipMap generation");
		GL11.glTexParameteri(GL13.GL_TEXTURE_CUBE_MAP, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
		GL11.glTexParameteri(GL13.GL_TEXTURE_CUBE_MAP, GL12.GL_TEXTURE_MAX_LEVEL, EnvironmentProbeFactory.CUBEMAPMIPMAPCOUNT);
		GL30.glGenerateMipmap(GL13.GL_TEXTURE_CUBE_MAP);
		GPUProfiler.end();
		GL11.glTexParameteri(GL13.GL_TEXTURE_CUBE_MAP, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
	}
	
	private void rotateForIndex(int i, Camera camera) {
		float deltaNear = 0.0f;
		float deltaFar = 1.3f;
		float halfSizeX = probe.getSize().x/2;
		float halfSizeY = probe.getSize().y/2;
		float halfSizeZ = probe.getSize().z/2;
		Vector3f position = getCamera().getPosition().negate(null);
		float width = probe.getSize().x;
		float height = probe.getSize().y;
//		Matrix4f projectionMatrix = Util.createOrthogonal(position .x-width/2, position.x+width/2, position.y+height/2, position.y-height/2, getCamera().getNear(), getCamera().getFar());
//		camera = new Camera(renderer, projectionMatrix, getCamera().getNear(), getCamera().getFar(), 90, 1);
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

	public CubeMap getEnvironmentMap() {
		return cubeMap;
	}

	public void setCubeMap(DynamicCubeMap environmentMap) {
		cubeMap = environmentMap;
	}
	
	public Camera getCamera() {
		return camera;
	}

	public void setCamera(Camera camera) {
		this.camera = camera;
	}
}
