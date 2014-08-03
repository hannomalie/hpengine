package main.renderer;

import java.nio.FloatBuffer;
import java.util.List;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Quaternion;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;

import main.camera.Camera;
import main.model.IEntity;
import main.octree.Octree;
import main.shader.Program;
import main.texture.CubeMap;
import main.texture.DynamicCubeMap;
import main.util.Util;
import main.util.stopwatch.GPUProfiler;
import main.util.stopwatch.StopWatch;

public class EnvironmentSampler {
	
	private CubeRenderTarget[] cubeMapRenderTargets;
	private DynamicCubeMap cubeMap;
	
	private Camera camera;
	private Program cubeMapDiffuseProgram;
	private FloatBuffer entityBuffer = BufferUtils.createFloatBuffer(16);

	public EnvironmentSampler(Renderer renderer, Vector3f position, int width, int height) {
		this.cubeMap = new DynamicCubeMap(width, height);
		this.camera = new Camera(renderer, Util.createPerpective(90f, 1, 20f, 5000f), 20f, 5000f);
		Quaternion cubeMapCamInitialOrientation = new Quaternion();
		Quaternion.setIdentity(cubeMapCamInitialOrientation);
		camera.setOrientation(cubeMapCamInitialOrientation);
		camera.rotate(new Vector4f(0,1,0,90));
		camera.setPosition(position);
		
		this.cubeMapRenderTargets = new CubeRenderTarget[6];
		for(int i = 0; i < 6; i++) {
			cubeMapRenderTargets[i] = new CubeRenderTarget(width, height, cubeMap.getTextureID(), i);
		}
		cubeMapDiffuseProgram = renderer.getProgramFactory().getProgram("first_pass_vertex.glsl", "cubemap_fragment.glsl");
		DeferredRenderer.exitOnGLError("ZZZ");
	}
	
	public CubeMap drawCubeMap(Octree octree) {
		return drawCubeMapSides(octree);
	}
	
	private CubeMap drawCubeMapSides(Octree octree) {
		GPUProfiler.start("Cube map render 6 sides");
		Quaternion initialOrientation = camera.getOrientation();
		Vector3f initialPosition = camera.getPosition();
		
		cubeMapDiffuseProgram.use();
		for(int i = 0; i < 6; i++) {
			rotateForIndex(i, camera);
			List<IEntity> visibles = octree.getVisible(camera);
			GPUProfiler.start("side " + i);
			GPUProfiler.start("Switch rendertarget");
			cubeMapRenderTargets[i].use(true);
			GPUProfiler.end();
			GPUProfiler.start("Matrix uniforms");
			FloatBuffer viewMatrixAsBuffer = camera.getViewMatrixAsBuffer();
			FloatBuffer projectionMatrixAsBuffer = camera.getProjectionMatrixAsBuffer();
			cubeMapDiffuseProgram.setUniformAsMatrix4("viewMatrix", viewMatrixAsBuffer);
			cubeMapDiffuseProgram.setUniformAsMatrix4("projectionMatrix", projectionMatrixAsBuffer);
			GPUProfiler.end();

			GPUProfiler.start("Draw entities");
			for (IEntity e : visibles) {
				entityBuffer.rewind();
				e.getModelMatrix().store(entityBuffer);
				entityBuffer.rewind();
				cubeMapDiffuseProgram.setUniformAsMatrix4("modelMatrix", entityBuffer);
				e.getMaterial().setTexturesActive(null, cubeMapDiffuseProgram);
				e.getVertexBuffer().draw();
			}
			GPUProfiler.end();
			GPUProfiler.end();
			
		}
		int errorValue = GL11.glGetError();
		camera.setPosition(initialPosition);
		camera.setOrientation(initialOrientation);
		
		GPUProfiler.end();
		return cubeMap;
	}
	
	private void rotateForIndex(int i, Camera camera) {
		switch (i) {
		case 0:
			camera.rotate(new Vector4f(0,0,1, -180));
			break;
		case 1:
			camera.rotate(new Vector4f(0,1,0, -180));
			break;
		case 2:
			camera.rotate(new Vector4f(0,1,0, 90));
			camera.rotate(new Vector4f(1,0,0, 90));
			break;
		case 3:
			camera.rotate(new Vector4f(1,0,0, -180));
			break;
		case 4:
			camera.rotate(new Vector4f(1,0,0, 90));
			break;
		case 5:
			camera.rotate(new Vector4f(0,1,0, -180));
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
}
