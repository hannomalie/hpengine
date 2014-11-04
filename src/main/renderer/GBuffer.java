package main.renderer;

import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import javafx.scene.effect.BlurType;
import main.World;
import main.camera.Camera;
import main.model.IEntity;
import main.model.QuadVertexBuffer;
import main.model.VertexBuffer;
import main.octree.Octree;
import main.renderer.light.AreaLight;
import main.renderer.light.PointLight;
import main.renderer.light.Spotlight;
import main.renderer.light.TubeLight;
import main.renderer.material.Material;
import main.scene.AABB;
import main.scene.EnvironmentProbe;
import main.shader.Program;
import main.texture.CubeMap;
import main.texture.DynamicCubeMap;
import main.texture.Texture;
import main.util.Util;
import main.util.stopwatch.GPUProfiler;
import main.util.stopwatch.StopWatch;

import org.lwjgl.BufferUtils;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;

import com.bulletphysics.dynamics.DynamicsWorld;
import com.sun.corba.se.spi.legacy.connection.GetEndPointInfoAgainException;

public class GBuffer {

	public static float SECONDPASSSCALE = 1f;
	
	private Renderer renderer;
	private RenderTarget gBuffer;
	private RenderTarget laBuffer;
	private RenderTarget finalBuffer;
	private VertexBuffer fullscreenBuffer;
	private Program firstPassProgram;
	private Program secondPassDirectionalProgram;
	private Program secondPassPointProgram;
	private Program secondPassTubeProgram;
	private Program secondPassAreaLightProgram;
	private Program combineProgram;
	private Program postProcessProgram;
	private Program instantRadiosityProgram;
	private FloatBuffer identityMatrixBuffer = BufferUtils.createFloatBuffer(16);

	public GBuffer(Renderer renderer, Program firstPassProgram, Program secondPassDirectionalProgram, Program secondPassPointProgram, Program secondPassTubeProgram, Program secondPassAreaLightProgram,
					Program combineProgram, Program postProcessProgram, Program instantRadiosityProgram) {
		this.renderer = renderer;
		this.firstPassProgram = firstPassProgram;
		this.secondPassDirectionalProgram = secondPassDirectionalProgram;
		this.secondPassPointProgram = secondPassPointProgram;
		this.secondPassTubeProgram = secondPassTubeProgram;
		this.secondPassAreaLightProgram = secondPassAreaLightProgram;
		this.combineProgram = combineProgram;
		this.postProcessProgram = postProcessProgram;
		this.instantRadiosityProgram = instantRadiosityProgram;
		fullscreenBuffer = new QuadVertexBuffer( true).upload();
		gBuffer = new RenderTarget(Renderer.WIDTH, Renderer.HEIGHT, GL30.GL_RGBA16F, 4);
		laBuffer = new RenderTarget((int) (Renderer.WIDTH * SECONDPASSSCALE) , (int) (Renderer.HEIGHT * SECONDPASSSCALE), GL30.GL_RGBA16F, 2);
		finalBuffer = new RenderTarget(Renderer.WIDTH, Renderer.HEIGHT, GL11.GL_RGB, 1);
		new Matrix4f().store(identityMatrixBuffer);
		identityMatrixBuffer.rewind();
	}
	
	void drawFirstPass(Camera camera, Octree octree, List<PointLight> pointLights, List<TubeLight> tubeLights, List<AreaLight> areaLights) {
		GL11.glEnable(GL11.GL_CULL_FACE);
		firstPassProgram.use();
		GL11.glDepthMask(true);
		gBuffer.use(true);
		GL11.glEnable(GL11.GL_DEPTH_TEST);
		GL11.glDisable(GL11.GL_BLEND);

		List<IEntity> entities = new ArrayList<>();
		if (World.useFrustumCulling) {
			entities = (octree.getVisible(camera));
			
			for (int i = 0; i < entities.size(); i++) {
				if (!entities.get(i).isInFrustum(camera)) {
					entities.remove(i);
				}
			}
			
		} else {
			entities = (octree.getEntities());
		}

		for (IEntity entity : entities) {
			entity.draw(renderer, camera);
		}
		
//		for (IEntity entity : entities.stream().filter(entity -> { return entity.isSelected(); }).collect(Collectors.toList())) {
//			Material old = entity.getMaterial();
//			entity.setMaterial(renderer.getMaterialFactory().getDefaultMaterial().getName());
//			entity.draw(renderer, camera);
//			entity.setMaterial(old.getName());			
//		}
		
		if (World.DRAWLIGHTS_ENABLED) {
			for (PointLight light : pointLights) {
				if (!light.isInFrustum(camera)) { continue;}
				light.drawAsMesh(renderer, camera);
			}
			for (TubeLight light : tubeLights) {
//				if (!light.isInFrustum(camera)) { continue;}
				light.drawAsMesh(renderer, camera);
			}
			for (AreaLight light : areaLights) {
//				if (!light.isInFrustum(camera)) { continue;}
				light.drawAsMesh(renderer, camera);
			}
		}
		
		GL13.glActiveTexture(GL13.GL_TEXTURE0);
		renderer.getTextureFactory().generateMipMaps(getColorReflectivenessMap());
		camera.saveViewMatrixAsLastViewMatrix();
	}


	void drawSecondPass(Camera camera, Spotlight directionalLight, List<PointLight> pointLights, List<TubeLight> tubeLights, List<AreaLight> areaLights, CubeMap cubeMap) {

		Vector3f camPosition = camera.getPosition().negate(null);
		Vector3f.add(camPosition, (Vector3f) camera.getViewDirection().negate(null).scale(-camera.getNear()), camPosition);
		Vector4f camPositionV4 = new Vector4f(camPosition.x, camPosition.y, camPosition.z, 0);
		
		GPUProfiler.start("Directional light");
		GL11.glDepthMask(false);
		GL11.glEnable(GL11.GL_BLEND);
		GL11.glEnable(GL11.GL_DEPTH_TEST);
		GL14.glBlendEquation(GL14.GL_FUNC_ADD);
		GL11.glBlendFunc(GL11.GL_ONE, GL11.GL_ONE);

		laBuffer.use(false);
		GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL30.GL_RENDERBUFFER, gBuffer.getDepthBufferTexture());
		GL11.glClearColor(0,0,0,0);
		GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);

		secondPassDirectionalProgram.use();

		GPUProfiler.start("Activate GBuffer textures");
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + 0);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, getPositionMap());
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + 1);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, getNormalMap());
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + 2);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, getColorReflectivenessMap());
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + 3);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, getMotionMap());
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + 4);
		cubeMap.bind();
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + 6);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, directionalLight.getShadowMapId()); // momentum 1, momentum 2
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + 7);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, directionalLight.getShadowMapWorldPositionId()); // world position
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + 8);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, directionalLight.getShadowMapColorMapId()); // object's color
		GPUProfiler.end();
		
		secondPassDirectionalProgram.setUniform("eyePosition", camera.getPosition());
		secondPassDirectionalProgram.setUniform("useAmbientOcclusion", World.useAmbientOcclusion);
		secondPassDirectionalProgram.setUniform("useColorBleeding", World.useColorBleeding);
		secondPassDirectionalProgram.setUniform("ambientOcclusionRadius", World.AMBIENTOCCLUSION_RADIUS);
		secondPassDirectionalProgram.setUniform("ambientOcclusionTotalStrength", World.AMBIENTOCCLUSION_TOTAL_STRENGTH);
		secondPassDirectionalProgram.setUniform("screenWidth", (float) Renderer.WIDTH);
		secondPassDirectionalProgram.setUniform("screenHeight", (float) Renderer.HEIGHT);
		secondPassDirectionalProgram.setUniform("secondPassScale", SECONDPASSSCALE);
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
		
		doPointLights(camera, pointLights, camPosition, viewMatrix, projectionMatrix);

		doTubeLights(tubeLights, camPositionV4, viewMatrix, projectionMatrix);

		doAreaLights(areaLights, viewMatrix, projectionMatrix);
		
		doInstantRadiosity(directionalLight, viewMatrix, projectionMatrix);
		

		GL13.glActiveTexture(GL13.GL_TEXTURE0);
		renderer.getTextureFactory().generateMipMaps(getLightAccumulationMapOneId());
		renderer.getTextureFactory().generateMipMaps(getAmbientOcclusionMapId());
		
		laBuffer.unuse();
		GL11.glDisable(GL11.GL_BLEND);
//		GL11.glEnable(GL11.GL_CULL_FACE);
		GL11.glCullFace(GL11.GL_BACK);
		GL11.glDepthFunc(GL11.GL_LESS);
		GPUProfiler.end();
		GPUProfiler.end();

//		renderer.blur2DTexture(getLightAccumulationMapOneId(), (int)(renderer.WIDTH*SECONDPASSSCALE), (int)(renderer.HEIGHT*SECONDPASSSCALE), GL30.GL_RGBA16F, 1);
	}
	
	private void doPointLights(Camera camera, List<PointLight> pointLights,
			Vector3f camPosition, FloatBuffer viewMatrix,
			FloatBuffer projectionMatrix) {
		GPUProfiler.start("Pointlights");
		secondPassPointProgram.use();

		GPUProfiler.start("Set shared uniforms");
//		secondPassPointProgram.setUniform("lightCount", pointLights.size());
//		secondPassPointProgram.setUniformAsBlock("pointlights", PointLight.convert(pointLights));
		secondPassPointProgram.setUniform("screenWidth", (float) Renderer.WIDTH);
		secondPassPointProgram.setUniform("screenHeight", (float) Renderer.HEIGHT);
		secondPassPointProgram.setUniform("secondPassScale", SECONDPASSSCALE);
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
	}
	private void doTubeLights(List<TubeLight> tubeLights,
			Vector4f camPositionV4, FloatBuffer viewMatrix,
			FloatBuffer projectionMatrix) {
		secondPassTubeProgram.use();
		secondPassTubeProgram.setUniform("screenWidth", (float) Renderer.WIDTH);
		secondPassTubeProgram.setUniform("screenHeight", (float) Renderer.HEIGHT);
		secondPassTubeProgram.setUniform("secondPassScale", SECONDPASSSCALE);
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
	
	private void doAreaLights(List<AreaLight> areaLights,
			FloatBuffer viewMatrix, FloatBuffer projectionMatrix) {
		secondPassAreaLightProgram.use();
		secondPassAreaLightProgram.setUniform("screenWidth", (float) Renderer.WIDTH);
		secondPassAreaLightProgram.setUniform("screenHeight", (float) Renderer.HEIGHT);
		secondPassAreaLightProgram.setUniform("secondPassScale", SECONDPASSSCALE);
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
			try {
				GL13.glActiveTexture(GL13.GL_TEXTURE0 + 8);
				Texture lightTexture = renderer.getTextureFactory().getTexture("brick.hptexture");
				GL11.glBindTexture(GL11.GL_TEXTURE_2D, lightTexture.getTextureID());
			} catch (IOException e) {
				e.printStackTrace();
			}
			fullscreenBuffer.draw();
//			areaLight.getVertexBuffer().drawDebug();
		}
	}
	
	private void doInstantRadiosity(Spotlight directionalLight,
			FloatBuffer viewMatrix, FloatBuffer projectionMatrix) {
		if(World.useInstantRadiosity) {
			GL13.glActiveTexture(GL13.GL_TEXTURE0 + 6);
			GL11.glBindTexture(GL11.GL_TEXTURE_2D, directionalLight.getShadowMapId()); // momentum 1, momentum 2
			GL13.glActiveTexture(GL13.GL_TEXTURE0 + 7);
			GL11.glBindTexture(GL11.GL_TEXTURE_2D, directionalLight.getShadowMapWorldPositionId()); // world position
			GL13.glActiveTexture(GL13.GL_TEXTURE0 + 8);
			GL11.glBindTexture(GL11.GL_TEXTURE_2D, directionalLight.getShadowMapColorMapId()); // object's color
			instantRadiosityProgram.use();
			instantRadiosityProgram.setUniform("screenWidth", (float) Renderer.WIDTH);
			instantRadiosityProgram.setUniform("screenHeight", (float) Renderer.HEIGHT);
			instantRadiosityProgram.setUniform("secondPassScale", SECONDPASSSCALE);
			instantRadiosityProgram.setUniform("lightDiffuse", directionalLight.getColor());
			instantRadiosityProgram.setUniformAsMatrix4("viewMatrix", viewMatrix);
			instantRadiosityProgram.setUniformAsMatrix4("projectionMatrix", projectionMatrix);
			GL11.glDisable(GL11.GL_CULL_FACE);
			GL11.glDisable(GL11.GL_DEPTH_TEST);
			fullscreenBuffer.draw();
		}
	}


	void combinePass(RenderTarget target, Spotlight light, Camera camera) {
		combineProgram.use();
		combineProgram.setUniformAsMatrix4("projectionMatrix", camera.getProjectionMatrixAsBuffer());
		combineProgram.setUniformAsMatrix4("viewMatrix", camera.getViewMatrixAsBuffer());
		combineProgram.setUniform("screenWidth", (float) Renderer.WIDTH);
		combineProgram.setUniform("screenHeight", (float) Renderer.HEIGHT);
		combineProgram.setUniform("camPosition", camera.getPosition());
		combineProgram.setUniform("ambientColor", World.AMBIENT_LIGHT);
		combineProgram.setUniform("exposure", World.EXPOSURE);
		combineProgram.setUniform("activeProbeCount", renderer.getEnvironmentProbeFactory().getProbes().size());
		bindEnvironmentProbePositions(combineProgram);
		
		if(target == null) {
			GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
		} else {
			target.use(true);
		}
		finalBuffer.use(true);
		GL11.glDisable(GL11.GL_DEPTH_TEST);
		
		GL13.glActiveTexture(GL13.GL_TEXTURE0);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, getColorReflectivenessMap());
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + 1);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, getLightAccumulationMapOneId());
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + 2);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, laBuffer.getRenderedTexture(1)); // ao, reflection
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + 3);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, getMotionMap()); // specular
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + 4);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, getPositionMap()); // position, glossiness
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + 5);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, getNormalMap()); // normal, depth
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + 6);
		renderer.getEnvironmentMap().bind();
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + 7);
		renderer.getEnvironmentProbeFactory().getEnvironmentMapsArray().bind();
		fullscreenBuffer.draw();

		if(target == null) {
			GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
		} else {
			target.use(true);
		}
		
		GL13.glActiveTexture(GL13.GL_TEXTURE0);
		renderer.getTextureFactory().generateMipMaps(finalBuffer.getRenderedTexture());
		
		postProcessProgram.use();
		GL13.glActiveTexture(GL13.GL_TEXTURE0);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, finalBuffer.getRenderedTexture(0)); // output color
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + 1);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, gBuffer.getRenderedTexture(1));
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + 2);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, getMotionMap());
		fullscreenBuffer.draw();
	}
	
	private void bindEnvironmentProbePositions(Program program) {
		renderer.getEnvironmentProbeFactory().getProbes().forEach(probe -> {
			int probeIndex = probe.getTextureUnitIndex();
			program.setUniform(String.format("environmentMapMin[%d]", probeIndex), probe.getBox().getBottomLeftBackCorner());
			program.setUniform(String.format("environmentMapMax[%d]", probeIndex), probe.getBox().getTopRightForeCorner());
//			System.out.println(String.format("environmentMapMin[%d] has %s", probeIndex, probe.getBox().getBottomLeftBackCorner()));
//			System.out.println(String.format("environmentMapMax[%d] has %s", probeIndex, probe.getBox().getTopRightForeCorner()));
		});
		program.setUniform("environmentMapMin[0]", new Vector3f(-10000, -10000, -10000));
		program.setUniform("environmentMapMax[0]", new Vector3f(10000, 10000, 10000));
		
	}
	
	public void drawDebug(Camera camera, DynamicsWorld dynamicsWorld, Octree octree, List<IEntity> entities, Spotlight light, List<PointLight> pointLights, List<TubeLight> tubeLights, List<AreaLight> areaLights, CubeMap cubeMap) {
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
		
		if(World.DRAWSCENE_ENABLED) {
			List<IEntity> visibleEntities = new ArrayList<>();
			if (World.useFrustumCulling) {
				visibleEntities.addAll(octree.getVisible(camera));
//				entities.addAll(octree.getEntities());
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
				renderer.drawLine(new Vector3f(), (Vector3f) Vector3f.add(new Vector3f(), entity.getViewDirection(), null).scale(10));
				renderer.drawLine(new Vector3f(), (Vector3f) Vector3f.add(new Vector3f(), entity.getUpDirection(), null).scale(10));
				renderer.drawLine(new Vector3f(), (Vector3f) Vector3f.add(new Vector3f(), entity.getRightDirection(), null).scale(10));
			}
		}

//		firstPassProgram.setUniformAsMatrix4("modelMatrix", identityMatrixBuffer);
		if (World.DRAWLIGHTS_ENABLED) {
			for (IEntity entity : pointLights) {
				entity.drawDebug(firstPassProgram);
			}	
			for (IEntity entity : tubeLights) {
				entity.drawDebug(firstPassProgram);
			}	
		}

		if (Octree.DRAW_LINES) {
			octree.drawDebug(renderer, camera, firstPassProgram);
		}
		
		if (World.DRAW_PROBES) {
			firstPassProgram.setUniformAsMatrix4("modelMatrix", identityMatrixBuffer);
			renderer.getEnvironmentProbeFactory().drawDebug(firstPassProgram, octree);
		}

	    renderer.drawLine(new Vector3f(), new Vector3f(15,0,0));
	    renderer.drawLine(new Vector3f(), new Vector3f(0,15,0));
	    renderer.drawLine(new Vector3f(), new Vector3f(0,0,-15));
	    renderer.drawLine(new Vector3f(), (Vector3f) ((Vector3f)(camera.getViewDirection())).scale(15));
	    renderer.drawLine(new Vector3f(), (Vector3f) ((Vector3f)(camera.getViewDirection())).scale(15));
	    renderer.drawLine(new Vector3f(), (Vector3f) ((Vector3f)(camera.getViewDirection())).scale(15));
//	    dynamicsWorld.debugDrawWorld();
	    renderer.drawLines(firstPassProgram);
		
		GL11.glDepthMask(false);
		GL11.glDisable(GL11.GL_DEPTH_TEST);
		////////////////////
		
		drawSecondPass(camera, light, pointLights, tubeLights, areaLights, cubeMap);
		
	}
	public int getColorReflectivenessMap() {
		return gBuffer.getRenderedTexture(2);
	}
	public int getLightAccumulationMapOneId() {
		return laBuffer.getRenderedTexture(0);
	}
	public int getAmbientOcclusionMapId() {
		return laBuffer.getRenderedTexture(1);
	}
	public int getNormalMap() {
		return gBuffer.getRenderedTexture(1);
	}
	public int getPositionMap() {
		return gBuffer.getRenderedTexture(0);
	}
	public int getMotionMap() {
		return gBuffer.getRenderedTexture(3);
	}

}
