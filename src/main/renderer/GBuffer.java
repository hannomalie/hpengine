package main.renderer;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import main.World;
import main.camera.Camera;
import main.model.IEntity;
import main.model.QuadVertexBuffer;
import main.model.VertexBuffer;
import main.octree.Octree;
import main.renderer.light.PointLight;
import main.renderer.light.Spotlight;
import main.renderer.material.Material;
import main.shader.Program;
import main.texture.CubeMap;
import main.util.stopwatch.GPUProfiler;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Vector3f;

import com.bulletphysics.dynamics.DynamicsWorld;

public class GBuffer {

	private Renderer renderer;
	private float secondPassScale = 0.5f;
	private RenderTarget gBuffer;
	private RenderTarget laBuffer;
	private VertexBuffer fullscreenBuffer;
	private Program firstPassProgram;
	private Program secondPassDirectionalProgram;
	private Program secondPassPointProgram;
	private Program combineProgram;
	private FloatBuffer identityMatrixBuffer = BufferUtils.createFloatBuffer(16);

	public GBuffer(Renderer renderer, Program firstPassProgram, Program secondPassDirectionalProgram, Program secondPassPointProgram, Program combineProgram) {
		this.renderer = renderer;
		this.firstPassProgram = firstPassProgram;
		this.secondPassDirectionalProgram = secondPassDirectionalProgram;
		this.secondPassPointProgram = secondPassPointProgram;
		this.combineProgram = combineProgram;
		fullscreenBuffer = new QuadVertexBuffer( true).upload();
		gBuffer = new RenderTarget(Renderer.WIDTH, Renderer.HEIGHT, GL30.GL_RGBA16F, 4);
		laBuffer = new RenderTarget((int) (Renderer.WIDTH * secondPassScale) , (int) (Renderer.HEIGHT * secondPassScale), GL30.GL_RGBA16F, 2);
		new Matrix4f().setIdentity().store(identityMatrixBuffer);
	}
	void drawFirstPass(Camera camera, Octree octree, List<PointLight> pointLights) {
		GL11.glEnable(GL11.GL_CULL_FACE);
		firstPassProgram.use();
		GL11.glDepthMask(true);
		gBuffer.use(true);
		GL11.glEnable(GL11.GL_DEPTH_TEST);
		GL11.glDisable(GL11.GL_BLEND);

		List<IEntity> entities = new ArrayList<>();
		if (World.useFrustumCulling) {
			entities.addAll(octree.getVisible(camera));
			
//			System.out.println("Visible: " + entities.size() + " / " + octree.getEntities().size() + " / " + octree.getEntityCount());
			for (int i = 0; i < entities.size(); i++) {
				if (!entities.get(i).isInFrustum(camera)) {
					entities.remove(i);
				}
			}
//			System.out.println("Visible exactly: " + entities.size() + " / " + octree.getEntities().size());
			
		} else {
			entities.addAll(octree.getEntities());
		}

		for (IEntity entity : entities) {
			entity.draw(renderer, camera);
		}
		
		for (IEntity entity : entities.stream().filter(entity -> { return entity.isSelected(); }).collect(Collectors.toList())) {
			Material old = entity.getMaterial();
			entity.setMaterial(renderer.getMaterialFactory().getDefaultMaterial());
			entity.draw(renderer, camera);
			entity.setMaterial(old);			
		}
		
		
		if (World.DRAWLIGHTS_ENABLED) {
			for (PointLight light : pointLights) {
				if (!light.isInFrustum(camera)) { continue;}
				light.drawAsMesh(renderer, camera);
			}
		}
		
		GL11.glDepthMask(false);
		GL11.glDisable(GL11.GL_DEPTH_TEST);
	}


	void drawSecondPass(Camera camera, Spotlight directionalLight, List<PointLight> pointLights, CubeMap cubeMap) {

		GPUProfiler.start("Directional light");
		GL11.glEnable(GL11.GL_BLEND);
		GL14.glBlendEquation(GL14.GL_FUNC_ADD);
		GL11.glBlendFunc(GL11.GL_ONE_MINUS_DST_COLOR, GL11.GL_ONE);

//		finalTarget.use(true);
		laBuffer.use(true);
		GL11.glClearColor(0,0,0,0);
		GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);

		secondPassDirectionalProgram.use();

		GPUProfiler.start("Activate GBuffer textures");
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + 0);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, gBuffer.getRenderedTexture(0)); // position
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + 1);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, gBuffer.getRenderedTexture(1)); // normal, depth
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + 2);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, gBuffer.getRenderedTexture(2)); // color, reflectiveness
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + 3);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, gBuffer.getRenderedTexture(3)); // specular
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + 4);
		cubeMap.bind();
		// 5 is missing yet
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + 6);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, directionalLight.getShadowMapId()); // momentum 1, momentum 2
		GPUProfiler.end();

		GPUProfiler.start("Set uniforms");
		secondPassDirectionalProgram.setUniform("eyePosition", camera.getPosition());
		secondPassDirectionalProgram.setUniform("useAmbientOcclusion", World.useAmbientOcclusion);
		secondPassDirectionalProgram.setUniform("ambientOcclusionRadius", World.AMBIENTOCCLUSION_RADIUS);
		secondPassDirectionalProgram.setUniform("ambientOcclusionTotalStrength", World.AMBIENTOCCLUSION_TOTAL_STRENGTH);
		secondPassDirectionalProgram.setUniform("screenWidth", (float) Renderer.WIDTH);
		secondPassDirectionalProgram.setUniform("screenHeight", (float) Renderer.HEIGHT);
		secondPassDirectionalProgram.setUniform("secondPassScale", secondPassScale);
		secondPassDirectionalProgram.setUniformAsMatrix4("viewMatrix", camera.getViewMatrixAsBuffer());
		secondPassDirectionalProgram.setUniformAsMatrix4("projectionMatrix", camera.getProjectionMatrixAsBuffer());
		secondPassDirectionalProgram.setUniformAsMatrix4("shadowMatrix", directionalLight.getLightMatrixAsBuffer());
		secondPassDirectionalProgram.setUniform("lightDirection", directionalLight.getOrientation().x, directionalLight.getOrientation().y, directionalLight.getOrientation().z);
		secondPassDirectionalProgram.setUniform("lightDiffuse", directionalLight.getColor());
//		LOGGER.log(Level.INFO, String.format("DIR LIGHT: %f %f %f", directionalLight.getOrientation().x, directionalLight.getOrientation().y, directionalLight.getOrientation().z));
		GPUProfiler.end();
		GPUProfiler.start("Draw fullscreen buffer");
		fullscreenBuffer.draw();
		GPUProfiler.end();

		GPUProfiler.end();
		GPUProfiler.start("Pointlights");
		secondPassPointProgram.use();

		GPUProfiler.start("Set shared uniforms");
//		secondPassPointProgram.setUniform("lightCount", pointLights.size());
//		secondPassPointProgram.setUniformAsBlock("pointlights", PointLight.convert(pointLights));
		secondPassPointProgram.setUniform("screenWidth", (float) Renderer.WIDTH);
		secondPassPointProgram.setUniform("screenHeight", (float) Renderer.HEIGHT);
		secondPassPointProgram.setUniform("secondPassScale", secondPassScale);
		secondPassPointProgram.setUniformAsMatrix4("viewMatrix", camera.getViewMatrixAsBuffer());
		secondPassPointProgram.setUniformAsMatrix4("projectionMatrix", camera.getProjectionMatrixAsBuffer());
		GPUProfiler.end();

		GPUProfiler.start("Draw lights");
		boolean firstLightDrawn = false;
		for (int i = 0 ; i < pointLights.size(); i++) {
			PointLight light = pointLights.get(i);
//			if(!light.isInFrustum(camera)) {
//				continue;
//			}
			
			Vector3f distance = new Vector3f();
			Vector3f.sub(camera.getPosition(), light.getPosition(), distance);
			float lightRadius = light.getRadius();
			
			//TODO: Check this....
			// camera is inside light range
			if (distance.length() < lightRadius) {
				GL11.glDisable(GL11.GL_CULL_FACE);
			// camera is outside light range, cull back sides
			} else {
				GL11.glEnable(GL11.GL_CULL_FACE);
				GL11.glCullFace(GL11.GL_BACK);
			}

//			secondPassPointProgram.setUniform("currentLightIndex", i);
			secondPassPointProgram.setUniform("lightPosition", light.getPosition());
			secondPassPointProgram.setUniform("lightRadius", lightRadius);
			secondPassPointProgram.setUniform("lightDiffuse", light.getColor().x, light.getColor().y, light.getColor().z);
			secondPassPointProgram.setUniform("lightSpecular", light.getColor().x, light.getColor().y, light.getColor().z);
			
			if(firstLightDrawn) {
				light.drawAgain(renderer, secondPassPointProgram);
			} else {
				light.draw(renderer, secondPassPointProgram);
			}
			firstLightDrawn = true;
		}
		laBuffer.unuse();
//		finalTarget.unuse();
		GL11.glDisable(GL11.GL_BLEND);
		GPUProfiler.end();
		GPUProfiler.end();
	}


	void combinePass(RenderTarget target, Spotlight light, Camera camera) {
		combineProgram.use();
		combineProgram.setUniform("screenWidth", (float) Renderer.WIDTH);
		combineProgram.setUniform("screenHeight", (float) Renderer.HEIGHT);
//		combineProgram.setUniform("secondPassScale", secondPassScale);
		combineProgram.setUniform("ambientColor", World.AMBIENT_LIGHT);
		
		if(target == null) {
			GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
		} else {
			target.use(true);
		}
		GL11.glDisable(GL11.GL_DEPTH_TEST);
		
		GL13.glActiveTexture(GL13.GL_TEXTURE0);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, gBuffer.getRenderedTexture(2)); // color, reflectiveness
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + 1);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, laBuffer.getRenderedTexture(0)); // light accumulation
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + 2);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, laBuffer.getRenderedTexture(1)); // ao, reflection
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + 3);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, gBuffer.getRenderedTexture(3)); // specular
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + 4);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, gBuffer.getRenderedTexture(0)); // position, glossiness
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + 5);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, gBuffer.getRenderedTexture(1)); // normal, depth
		
		fullscreenBuffer.draw();

//		GL11.glEnable(GL11.GL_DEPTH_TEST);
	}
	
	public void drawDebug(Camera camera, DynamicsWorld dynamicsWorld, Octree octree, List<IEntity> entities, Spotlight light, List<PointLight> pointLights, CubeMap cubeMap) {
		///////////// firstpass
		GL11.glEnable(GL11.GL_CULL_FACE);
		firstPassProgram.use();
		GL11.glDepthMask(true);
		gBuffer.use(true);
		GL11.glEnable(GL11.GL_DEPTH_TEST);
		GL11.glDisable(GL11.GL_BLEND);
		firstPassProgram.setUniform("screenWidth", (float) Renderer.WIDTH);
		firstPassProgram.setUniform("screenHeight", (float) Renderer.HEIGHT);
		firstPassProgram.setUniform("useParallax", World.useParallax);
		firstPassProgram.setUniform("useSteepParallax", World.useSteepParallax);
		firstPassProgram.setUniformAsMatrix4("viewMatrix", camera.getViewMatrixAsBuffer());
		firstPassProgram.setUniformAsMatrix4("projectionMatrix", camera.getProjectionMatrixAsBuffer());
		firstPassProgram.setUniform("eyePosition", camera.getPosition());
		
		List<IEntity> visibleEntities = new ArrayList<>();
		if (World.useFrustumCulling) {
			visibleEntities.addAll(octree.getVisible(camera));
//			entities.addAll(octree.getEntities());
			for (int i = 0; i < visibleEntities.size(); i++) {
				if (!visibleEntities.get(i).isInFrustum(camera)) {
					visibleEntities.remove(i);
				}
			}
		} else {
			visibleEntities.addAll(octree.getEntities());
		}
		
		for (IEntity entity : visibleEntities) {
			entity.drawDebug(firstPassProgram);
			firstPassProgram.setUniformAsMatrix4("modelMatrix", identityMatrixBuffer);
			renderer.drawLine(entity.getPosition(), (Vector3f) Vector3f.add(entity.getPosition(), entity.getViewDirection(), null).scale(10));
			renderer.drawLine(entity.getPosition(), (Vector3f) Vector3f.add(entity.getPosition(), entity.getUpDirection(), null).scale(10));
			renderer.drawLine(entity.getPosition(), (Vector3f) Vector3f.add(entity.getPosition(), entity.getRightDirection(), null).scale(10));
		}
		if (World.DRAWLIGHTS_ENABLED) {
			for (IEntity entity : pointLights) {
				entity.drawDebug(firstPassProgram);
			}	
		}
		
		if (Octree.DRAW_LINES) {
			octree.drawDebug(renderer, camera, firstPassProgram);
		}

	    renderer.drawLine(new Vector3f(), new Vector3f(15,0,0));
	    renderer.drawLine(new Vector3f(), new Vector3f(0,15,0));
	    renderer.drawLine(new Vector3f(), new Vector3f(0,0,-15));
	    renderer.drawLine(new Vector3f(), (Vector3f) ((Vector3f)(camera.getViewDirection())).scale(15));
	    renderer.drawLine(new Vector3f(), (Vector3f) ((Vector3f)(camera.getViewDirection())).scale(15));
	    renderer.drawLine(new Vector3f(), (Vector3f) ((Vector3f)(camera.getViewDirection())).scale(15));
	    dynamicsWorld.debugDrawWorld();
	    renderer.drawLines(firstPassProgram);
		
		GL11.glDepthMask(false);
		GL11.glDisable(GL11.GL_DEPTH_TEST);
		////////////////////
		
		drawSecondPass(camera, light, pointLights, cubeMap);
		
	}
	public int getColorReflectivenessImage() {
		return gBuffer.getRenderedTexture(2);
	}
	public int getLightAccumulationMapOneId() {
		return laBuffer.getRenderedTexture(0);
	}
	

}
