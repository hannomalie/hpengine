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
import org.lwjgl.util.vector.Quaternion;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;

import main.World;
import main.camera.Camera;
import main.model.IEntity;
import main.octree.Octree;
import main.renderer.light.Spotlight;
import main.shader.Program;
import main.texture.CubeMap;
import main.texture.DynamicCubeMap;
import main.util.Util;
import main.util.stopwatch.GPUProfiler;
import main.util.stopwatch.StopWatch;

public class EnvironmentSampler {
	
	private CubeRenderTarget cubeMapRenderTarget;
	private DynamicCubeMap cubeMap;
	
	private Camera camera;
	private Program cubeMapDiffuseProgram;
	private FloatBuffer entityBuffer = BufferUtils.createFloatBuffer(16);
	private Renderer renderer;

	public EnvironmentSampler(Renderer renderer, Vector3f position, int width, int height) {
		this.renderer = renderer;
		this.cubeMap = new DynamicCubeMap(width, height);
		this.camera = new Camera(renderer, Util.createPerpective(90f, 1, 20f, 5000f), 20f, 5000f);
		Quaternion cubeMapCamInitialOrientation = new Quaternion();
		Quaternion.setIdentity(cubeMapCamInitialOrientation);
		camera.setOrientation(cubeMapCamInitialOrientation);
		camera.rotate(new Vector4f(0,1,0,90));
//		position.y = -position.y;
		camera.setPosition(position.negate(null));

//		DeferredRenderer.exitOnGLError("EnvironmentSampler Before CubeRenderTarget");
		this.cubeMapRenderTarget = new CubeRenderTarget(width, height, cubeMap);
//		DeferredRenderer.exitOnGLError("EnvironmentSampler CubeRenderTarget");
		
		cubeMapDiffuseProgram = renderer.getProgramFactory().getProgram("first_pass_vertex.glsl", "cubemap_fragment.glsl");
//		DeferredRenderer.exitOnGLError("EnvironmentSampler constructor");
	}
	
	public CubeMap drawCubeMap(Octree octree, Spotlight light) {
		return drawCubeMapSides(octree, light);
	}
	
	private CubeMap drawCubeMapSides(Octree octree, Spotlight light) {
		GPUProfiler.start("Cube map render 6 sides");
		Quaternion initialOrientation = camera.getOrientation();
		Vector3f initialPosition = camera.getPosition();
		GL13.glActiveTexture(GL13.GL_TEXTURE0);
		
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + 6);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, light.getShadowMapId());
		
		cubeMapDiffuseProgram.use();
		cubeMapRenderTarget.use(true);
		for(int i = 0; i < 6; i++) {
			rotateForIndex(i, camera);
			List<IEntity> visibles = octree.getVisible(camera);
			GPUProfiler.start("side " + i);
			GPUProfiler.start("Switch attachment");
			cubeMapRenderTarget.setCubeMapFace(i);
			GPUProfiler.end();
			GPUProfiler.start("Matrix uniforms");
			FloatBuffer viewMatrixAsBuffer = camera.getViewMatrixAsBuffer();
			FloatBuffer projectionMatrixAsBuffer = camera.getProjectionMatrixAsBuffer();
			cubeMapDiffuseProgram.setUniform("lightDirection", light.getViewDirection());
			cubeMapDiffuseProgram.setUniform("lightDiffuse", light.getColor());
			cubeMapDiffuseProgram.setUniform("lightAmbient", World.AMBIENT_LIGHT);
			cubeMapDiffuseProgram.setUniformAsMatrix4("viewMatrix", viewMatrixAsBuffer);
			cubeMapDiffuseProgram.setUniformAsMatrix4("projectionMatrix", projectionMatrixAsBuffer);
			cubeMapDiffuseProgram.setUniformAsMatrix4("shadowMatrix", light.getLightMatrixAsBuffer());
			GPUProfiler.end();

			GPUProfiler.start("Draw entities");
			for (IEntity e : visibles) {
//				if(!e.isInFrustum(camera)) { continue; }
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

		cubeMap.bind();
		GL11.glTexParameteri(GL13.GL_TEXTURE_CUBE_MAP, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
		GL30.glGenerateMipmap(GL13.GL_TEXTURE_CUBE_MAP);
//		GL11.glTexParameteri(GL13.GL_TEXTURE_CUBE_MAP, GL14.GL_GENERATE_MIPMAP, GL11.GL_TRUE);
//		GL11.glTexParameteri(GL13.GL_TEXTURE_CUBE_MAP, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
//		int errorValue = GL11.glGetError();
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
