package renderer;

import camera.Camera;
import com.bulletphysics.dynamics.DynamicsWorld;
import component.ModelComponent;
import config.Config;
import engine.Transform;
import engine.World;
import engine.model.Entity;
import engine.model.Model;
import engine.model.QuadVertexBuffer;
import engine.model.VertexBuffer;
import event.EntitySelectedEvent;
import octree.Octree;
import org.lwjgl.BufferUtils;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.*;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;
import renderer.light.AreaLight;
import renderer.light.DirectionalLight;
import renderer.light.PointLight;
import renderer.light.TubeLight;
import renderer.material.Material;
import renderer.rendertarget.RenderTarget;
import scene.AABB;
import scene.EnvironmentProbe;
import shader.ComputeShaderProgram;
import shader.Program;
import shader.StorageBuffer;
import texture.CubeMap;
import texture.Texture;
import util.Util;
import util.stopwatch.GPUProfiler;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class GBuffer {

	public static volatile int IMPORTANCE_SAMPLE_COUNT = 8;
	public static float SECONDPASSSCALE = 1f;
	public static volatile boolean USE_COMPUTESHADER_FOR_REFLECTIONS = false;
	public static volatile boolean RENDER_PROBES_WITH_FIRST_BOUNCE = true;
	public static volatile boolean RENDER_PROBES_WITH_SECOND_BOUNCE = true;
	
	private Renderer renderer;
	private RenderTarget gBuffer;
	private RenderTarget reflectionBuffer;
	private RenderTarget laBuffer;
	private RenderTarget finalBuffer;
	private RenderTarget halfScreenBuffer;
	
	private VertexBuffer fullscreenBuffer;

	private Program firstPassProgram;
	private Program depthPrePassProgram;
	private Program secondPassDirectionalProgram;
	private Program secondPassPointProgram;
	private Program secondPassTubeProgram;
	private Program secondPassAreaLightProgram;
	private Program combineProgram;
	private Program postProcessProgram;
	private Program instantRadiosityProgram;

	private Program aoScatteringProgram;

	private Program highZProgram;
	private Program reflectionProgram;
	private Program linesProgram;
	private Program probeFirstpassProgram;
	private ComputeShaderProgram tiledProbeLightingProgram;
	private ComputeShaderProgram tiledDirectLightingProgram;
	
	private FloatBuffer identityMatrixBuffer = BufferUtils.createFloatBuffer(16);

	private int fullScreenMipmapCount;

	private Model probeBox;
	private Entity probeBoxEntity;

	private ByteBuffer vec4Buffer = BufferUtils.createByteBuffer(4*4).order(ByteOrder.nativeOrder());
	private FloatBuffer fBuffer = vec4Buffer.asFloatBuffer();
	private float[] onePixel = new float[4];

	private PixelBufferObject pixelBufferObject;
	
	private StorageBuffer storageBuffer;

	private final int exposureIndex = 0;
	private World world;
	
	public GBuffer(World world, Renderer renderer, Program firstPassProgram, Program secondPassDirectionalProgram, Program secondPassPointProgram, Program secondPassTubeProgram, Program secondPassAreaLightProgram,
					Program combineProgram, Program postProcessProgram, Program instantRadiosityProgram) {
		this.world = world;
		this.renderer = renderer;
		this.firstPassProgram = firstPassProgram;
		this.secondPassDirectionalProgram = secondPassDirectionalProgram;
		this.secondPassPointProgram = secondPassPointProgram;
		this.secondPassTubeProgram = secondPassTubeProgram;
		this.secondPassAreaLightProgram = secondPassAreaLightProgram;
		this.combineProgram = combineProgram;
		this.postProcessProgram = postProcessProgram;
		this.instantRadiosityProgram = instantRadiosityProgram;
		this.aoScatteringProgram = renderer.getProgramFactory().getProgram("passthrough_vertex.glsl", "scattering_ao_fragment.glsl");
		this.highZProgram = renderer.getProgramFactory().getProgram("passthrough_vertex.glsl", "highZ_fragment.glsl");
		this.reflectionProgram = renderer.getProgramFactory().getProgram("passthrough_vertex.glsl", "reflections_fragment.glsl");
		this.linesProgram = renderer.getProgramFactory().getProgram("mvp_vertex.glsl", "simple_color_fragment.glsl");
		this.probeFirstpassProgram = renderer.getProgramFactory().getProgram("first_pass_vertex.glsl", "probe_first_pass_fragment.glsl");
		this.depthPrePassProgram = renderer.getProgramFactory().getProgram("first_pass_vertex.glsl", "depth_prepass_fragment.glsl");
		this.tiledDirectLightingProgram = renderer.getProgramFactory().getComputeProgram("tiled_direct_lighting_compute.glsl");
		this.tiledProbeLightingProgram = renderer.getProgramFactory().getComputeProgram("tiled_probe_lighting_compute.glsl");
		
		fullscreenBuffer = new QuadVertexBuffer(true).upload();
		gBuffer = new RenderTarget(Config.WIDTH, Config.HEIGHT, GL30.GL_RGBA16F, 5);
		reflectionBuffer = new RenderTarget(Config.WIDTH, Config.HEIGHT, GL30.GL_RGBA16F, 0,0,0,0, GL11.GL_LINEAR, 2);
		laBuffer = new RenderTarget((int) (Config.WIDTH * SECONDPASSSCALE) , (int) (Config.HEIGHT * SECONDPASSSCALE), GL30.GL_RGBA16F, 2);
		finalBuffer = new RenderTarget(Config.WIDTH, Config.HEIGHT, GL11.GL_RGBA8, 1);
		halfScreenBuffer = new RenderTarget(Config.WIDTH/2, Config.HEIGHT/2, GL30.GL_RGBA16F, 1);
		new Matrix4f().store(identityMatrixBuffer);
		identityMatrixBuffer.rewind();

		fullScreenMipmapCount = Util.calculateMipMapCount(Math.max(Config.WIDTH, Config.HEIGHT));
		pixelBufferObject = new PixelBufferObject(1, 1);
		
		 storageBuffer = new StorageBuffer(16);
		 storageBuffer.putValues(1f,-1f,0f,1f);
	}
	
	public void init(Renderer renderer) {
		probeBox = null;
		try {
			probeBox = renderer.getOBJLoader().loadTexturedModel(new File(World.WORKDIR_NAME + "/assets/models/probebox.obj")).get(0);
			Material probeBoxMaterial = renderer.getMaterialFactory().getDefaultMaterial();
			probeBoxMaterial.setDiffuse(new Vector3f(0, 1, 0));
			probeBox.setMaterial(probeBoxMaterial);
			probeBoxEntity = renderer.getEntityFactory().getEntity(probeBox, probeBoxMaterial);
		} catch (IOException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	void drawFirstPass(Camera camera, Octree octree, List<PointLight> pointLights, List<TubeLight> tubeLights, List<AreaLight> areaLights) {
		GL11.glEnable(GL11.GL_CULL_FACE);
		GL11.glDepthMask(true);
		gBuffer.use(true);
		firstPassProgram.use();
		GL11.glEnable(GL11.GL_DEPTH_TEST);
		GL11.glDepthFunc(GL11.GL_LESS);
		GL11.glDisable(GL11.GL_BLEND);

		GPUProfiler.start("Culling");
		List<Entity> entities = new ArrayList<>();

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
		GPUProfiler.end();

		GPUProfiler.start("Draw entities");
		GPUProfiler.start("Sort by depth");
		entities = entities.parallelStream().sorted(new Comparator<Entity>() {
			@Override
			public int compare(Entity o1, Entity o2) {
				Vector3f center1 = o1.getCenter();
				Vector3f center2 = o2.getCenter();
				Vector4f center1InView = Matrix4f.transform(camera.getViewMatrix(), new Vector4f(center1.x, center1.y, center1.z, 1f), null);
				Vector4f center2InView = Matrix4f.transform(camera.getViewMatrix(), new Vector4f(center2.x, center2.y, center2.z, 1f), null);
				return Float.compare(-center1InView.z, -center2InView.z);
			}
		}).collect(Collectors.toList());
		GPUProfiler.end();

		if(World.DRAWSCENE_ENABLED) {
//			GPUProfiler.start("Depth prepass");
//			for (Entity entity : entities) {
//				entity.draw(renderer, camera, depthPrePassProgram);
//			}
//			GPUProfiler.end();
			for (Entity entity : entities) {
				entity.getComponentOption(ModelComponent.class).ifPresent(modelComponent -> {
					modelComponent.draw(camera);
				});
			}
		}
		GPUProfiler.end();
		
//		for (Entity entity : entities.stream().filter(entity -> { return entity.isSelected(); }).collect(Collectors.toList())) {
//			Material old = entity.getMaterial();
//			entity.setMaterial(renderer.getMaterialFactory().getDefaultMaterial().getName());
//			entity.draw(renderer, camera);
//			entity.setMaterial(old.getName());			
//		}

		if (World.DRAWLIGHTS_ENABLED) {
			for (PointLight light : pointLights) {
				if (!light.isInFrustum(camera)) { continue;}
				light.drawAsMesh(camera);
			}
			for (TubeLight light : tubeLights) {
//				if (!light.isInFrustum(camera)) { continue;}
				light.drawAsMesh(camera);
			}
			for (AreaLight light : areaLights) {
//				if (!light.isInFrustum(camera)) { continue;}
				light.drawAsMesh(camera);
			}
		}

//		linesProgram.use();
//		GL11.glDisable(GL11.GL_CULL_FACE);
		if(World.DEBUGDRAW_PROBES) {
			debugDrawProbes(camera);
			renderer.getEnvironmentProbeFactory().draw(octree);
		}
		GL11.glEnable(GL11.GL_CULL_FACE);

		GPUProfiler.start("Generate Mipmaps of colormap");
		GL13.glActiveTexture(GL13.GL_TEXTURE0);
		renderer.getTextureFactory().generateMipMaps(getColorReflectivenessMap());
		GPUProfiler.end();

		if(world.PICKING_CLICK == 1) {
			GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT4);
			
			FloatBuffer floatBuffer = BufferUtils.createFloatBuffer(4); // 4 channels
			GL11.glReadPixels(Mouse.getX(), Mouse.getY(), 1, 1, GL11.GL_RGBA, GL11.GL_FLOAT, floatBuffer);
			try {
				int componentIndex = 3; // alpha component
				world.getScene().getEntities().parallelStream().forEach(e -> { e.setSelected(false); });
				Entity entity = world.getScene().getEntities().get((int)floatBuffer.get(componentIndex));
				entity.setSelected(true);
				World.getEventBus().post(new EntitySelectedEvent(entity));
			} catch (Exception e) {
				e.printStackTrace();
			}
			floatBuffer = null;
		}
	}

	private void debugDrawProbes(Camera camera) {
		
		probeFirstpassProgram.use();
		renderer.getEnvironmentProbeFactory().bindEnvironmentProbePositions(probeFirstpassProgram);
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + 8);
		renderer.getEnvironmentProbeFactory().getEnvironmentMapsArray(3).bind();
		probeFirstpassProgram.setUniform("showContent", World.DEBUGDRAW_PROBES_WITH_CONTENT);
		
		Vector3f oldMaterialColor = new Vector3f(probeBoxEntity.getComponent(ModelComponent.class).getMaterial().getDiffuse());
		
		for (EnvironmentProbe probe : renderer.getEnvironmentProbeFactory().getProbes()) {
			Transform transform = new Transform();
			transform.setPosition(probe.getCenter());
			transform.setScale(probe.getSize());
			Vector3f colorHelper = probe.getDebugColor();
			probeBoxEntity.getComponent(ModelComponent.class).getMaterial().setDiffuse(colorHelper);
			probeBoxEntity.setTransform(transform);
			probeBoxEntity.update(0);
			probeFirstpassProgram.setUniform("probeCenter", probe.getCenter());
			probeFirstpassProgram.setUniform("probeIndex", probe.getIndex());
			probeBoxEntity.getComponent(ModelComponent.class).
					draw(camera, probeBoxEntity.getModelMatrixAsBuffer(), probeFirstpassProgram, world.getScene().getEntities().indexOf(probeBoxEntity));
		}

		probeBoxEntity.getComponent(ModelComponent.class).getMaterial().getDiffuse().x = oldMaterialColor.x;
		probeBoxEntity.getComponent(ModelComponent.class).getMaterial().getDiffuse().y = oldMaterialColor.y;
		probeBoxEntity.getComponent(ModelComponent.class).getMaterial().getDiffuse().z = oldMaterialColor.z;
		
	}

	void drawSecondPass(Camera camera, DirectionalLight directionalLight, List<PointLight> pointLights, List<TubeLight> tubeLights, List<AreaLight> areaLights, CubeMap cubeMap) {

		renderer.getTextureFactory().generateMipMaps(directionalLight.getShadowMapId());
		
		Vector3f camPosition = camera.getPosition().negate(null);
		Vector3f.add(camPosition, (Vector3f) camera.getViewDirection().negate(null).scale(-camera.getNear()), camPosition);
		Vector4f camPositionV4 = new Vector4f(camPosition.x, camPosition.y, camPosition.z, 0);
		
		GPUProfiler.start("Directional light");
		GL11.glDepthMask(false);
		GL11.glEnable(GL11.GL_DEPTH_TEST);
		GL11.glEnable(GL11.GL_BLEND);
		GL14.glBlendEquation(GL14.GL_FUNC_ADD);
		GL11.glBlendFunc(GL11.GL_ONE, GL11.GL_ONE);

		laBuffer.use(true);
//		laBuffer.resizeTextures();
		GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL30.GL_RENDERBUFFER, gBuffer.getDepthBufferTexture());
		GL11.glClearColor(0,0,0,0);
		GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);

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
//		GL13.glActiveTexture(GL13.GL_TEXTURE0 + 7);
//		GL11.glBindTexture(GL11.GL_TEXTURE_2D, directionalLight.getShadowMapWorldPositionId()); // world position
//		GL13.glActiveTexture(GL13.GL_TEXTURE0 + 8);
//		GL11.glBindTexture(GL11.GL_TEXTURE_2D, getVisibilityMap());
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + 8);
		renderer.getEnvironmentProbeFactory().getEnvironmentMapsArray(3).bind();
		
		GPUProfiler.end();

		secondPassDirectionalProgram.use();
		secondPassDirectionalProgram.setUniform("eyePosition", camera.getPosition());
		secondPassDirectionalProgram.setUniform("ambientOcclusionRadius", World.AMBIENTOCCLUSION_RADIUS);
		secondPassDirectionalProgram.setUniform("ambientOcclusionTotalStrength", World.AMBIENTOCCLUSION_TOTAL_STRENGTH);
		secondPassDirectionalProgram.setUniform("screenWidth", (float) Config.WIDTH);
		secondPassDirectionalProgram.setUniform("screenHeight", (float) Config.HEIGHT);
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
		renderer.getEnvironmentProbeFactory().bindEnvironmentProbePositions(secondPassDirectionalProgram);
		GPUProfiler.start("Draw fullscreen buffer");
		fullscreenBuffer.draw();
		GPUProfiler.end();

		GPUProfiler.end();
		
		doPointLights(camera, pointLights, camPosition, viewMatrix, projectionMatrix);

		doTubeLights(tubeLights, camPositionV4, viewMatrix, projectionMatrix);

		doAreaLights(areaLights, viewMatrix, projectionMatrix);
		
		doInstantRadiosity(directionalLight, viewMatrix, projectionMatrix);
		

		GPUProfiler.start("MipMap generation AO and light buffer");
		GL13.glActiveTexture(GL13.GL_TEXTURE0);
		renderer.getTextureFactory().generateMipMaps(getLightAccumulationMapOneId());
		renderer.getTextureFactory().generateMipMaps(getAmbientOcclusionMapId());
		GPUProfiler.end();
		
		GL11.glDisable(GL11.GL_BLEND);
//		GL11.glEnable(GL11.GL_CULL_FACE);

		laBuffer.unuse();
		
		renderAOAndScattering(camera, viewMatrix, projectionMatrix, directionalLight);
		
		if (World.USE_GI) {
			GL11.glDepthMask(false);
			GL11.glDisable(GL11.GL_DEPTH_TEST);
			GL11.glDisable(GL11.GL_BLEND);
			GL11.glCullFace(GL11.GL_BACK);
			renderReflectionsAndAO(viewMatrix, projectionMatrix);
		} else {
			reflectionBuffer.use(true);
			reflectionBuffer.unuse();
		}
		GPUProfiler.start("Blurring");
//		renderer.blur2DTexture(halfScreenBuffer.getRenderedTexture(), 0, (int)(Config.WIDTH/2), (int)(Config.HEIGHT/2), GL30.GL_RGBA16F, false, 1);
//		renderer.blur2DTexture(getLightAccumulationMapOneId(), 0, (int)(Config.WIDTH*SECONDPASSSCALE), (int)(Config.HEIGHT*SECONDPASSSCALE), GL30.GL_RGBA16F, false, 1);
//		renderer.blur2DTexture(getLightAccumulationMapOneId(), 0, (int)(Config.WIDTH*SECONDPASSSCALE), (int)(Config.HEIGHT*SECONDPASSSCALE), GL30.GL_RGBA16F, false, 1);
//		renderer.blur2DTexture(getAmbientOcclusionMapId(), (int)(renderer.WIDTH*SECONDPASSSCALE), (int)(renderer.HEIGHT*SECONDPASSSCALE), GL30.GL_RGBA16F, false, 1);
//		renderer.blur2DTextureBilateral(getLightAccumulationMapOneId(), 0, (int)(renderer.WIDTH*SECONDPASSSCALE), (int)(renderer.HEIGHT*SECONDPASSSCALE), GL30.GL_RGBA16F, false, 1);

//		renderer.blur2DTexture(halfScreenTarget.getRenderedTexture(0), (int)(renderer.WIDTH*0.5), (int)(renderer.HEIGHT*0.5), GL11.GL_RGBA8, false, 1);
//		renderer.blur2DTexture(halfScreenTarget.getRenderedTexture(1), (int)(renderer.WIDTH*0.5), (int)(renderer.HEIGHT*0.5), GL11.GL_RGBA8, false, 1);
		
		GL11.glCullFace(GL11.GL_BACK);
		GL11.glDepthFunc(GL11.GL_LESS);
		GPUProfiler.end();
	}

	private void renderAOAndScattering(Camera camera, FloatBuffer viewMatrix, FloatBuffer projectionMatrix, DirectionalLight directionalLight) {
		if(!World.useAmbientOcclusion && !World.SCATTERING) { return; }
		GPUProfiler.start("Scattering and AO");
		GL11.glDisable(GL11.GL_DEPTH_TEST);
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + 0);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, getPositionMap());
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + 1);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, getNormalMap());
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + 2);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, getColorReflectivenessMap());
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + 3);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, getMotionMap());
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + 6);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, directionalLight.getShadowMapId()); // momentum 1, momentum 2
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + 8);
		renderer.getEnvironmentProbeFactory().getEnvironmentMapsArray(3).bind();
		
		halfScreenBuffer.use(true);
//		halfScreenBuffer.setTargetTexture(halfScreenBuffer.getRenderedTexture(), 0);
		aoScatteringProgram.use();
		aoScatteringProgram.setUniform("eyePosition", camera.getPosition());
		aoScatteringProgram.setUniform("useAmbientOcclusion", World.useAmbientOcclusion);
		aoScatteringProgram.setUniform("ambientOcclusionRadius", World.AMBIENTOCCLUSION_RADIUS);
		aoScatteringProgram.setUniform("ambientOcclusionTotalStrength", World.AMBIENTOCCLUSION_TOTAL_STRENGTH);
		aoScatteringProgram.setUniform("screenWidth", (float) Config.WIDTH/2);
		aoScatteringProgram.setUniform("screenHeight", (float) Config.HEIGHT/2);
		aoScatteringProgram.setUniformAsMatrix4("viewMatrix", viewMatrix);
		aoScatteringProgram.setUniformAsMatrix4("projectionMatrix", projectionMatrix);
		aoScatteringProgram.setUniformAsMatrix4("shadowMatrix", directionalLight.getLightMatrixAsBuffer());
		aoScatteringProgram.setUniform("lightDirection", directionalLight.getViewDirection());
		aoScatteringProgram.setUniform("lightDiffuse", directionalLight.getColor());
		aoScatteringProgram.setUniform("scatterFactor", directionalLight.getScatterFactor());
		renderer.getEnvironmentProbeFactory().bindEnvironmentProbePositions(aoScatteringProgram);
		fullscreenBuffer.draw();
		GL11.glEnable(GL11.GL_DEPTH_TEST);
		renderer.getTextureFactory().generateMipMaps(halfScreenBuffer.getRenderedTexture());
		GPUProfiler.end();
	}

	private void renderReflectionsAndAO(FloatBuffer viewMatrix, FloatBuffer projectionMatrix) {
		GPUProfiler.start("Reflections and AO");
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + 0);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, getPositionMap());
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + 1);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, getNormalMap());
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + 2);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, getColorReflectivenessMap());
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + 3);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, getMotionMap()); // specular
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + 4);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, getLightAccumulationMapOneId());
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + 5);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, getFinalMap());
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + 6);
		renderer.getEnvironmentMap().bind();
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + 7);
		reflectionBuffer.getRenderedTexture(0);
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + 8);
		renderer.getEnvironmentProbeFactory().getEnvironmentMapsArray(3).bind();
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + 9);
		renderer.getEnvironmentMap().bind();
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + 10);
		renderer.getEnvironmentProbeFactory().getEnvironmentMapsArray(0).bind();
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + 11);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, reflectionBuffer.getRenderedTexture());

		int copyTextureId = GL11.glGenTextures();
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + 11);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, copyTextureId);
		GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RGBA16F, reflectionBuffer.getWidth(), reflectionBuffer.getHeight(), 0, GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE, (FloatBuffer) null);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
		GL43.glCopyImageSubData(reflectionBuffer.getRenderedTexture(), GL11.GL_TEXTURE_2D, 0, 0, 0, 0,
				copyTextureId, GL11.GL_TEXTURE_2D, 0, 0, 0, 0,
				reflectionBuffer.getWidth(), reflectionBuffer.getHeight(), 1);
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + 11);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, copyTextureId);
		
		if(!USE_COMPUTESHADER_FOR_REFLECTIONS) {
			reflectionBuffer.use(true);
			reflectionProgram.use();
			reflectionProgram.setUniform("N", IMPORTANCE_SAMPLE_COUNT);
			reflectionProgram.setUniform("useAmbientOcclusion", World.useAmbientOcclusion);
			reflectionProgram.setUniform("useSSR", World.useSSR);
			reflectionProgram.setUniform("screenWidth", (float) Config.WIDTH);
			reflectionProgram.setUniform("screenHeight", (float) Config.HEIGHT);
			reflectionProgram.setUniformAsMatrix4("viewMatrix", viewMatrix);
			reflectionProgram.setUniformAsMatrix4("projectionMatrix", projectionMatrix);
			renderer.getEnvironmentProbeFactory().bindEnvironmentProbePositions(reflectionProgram);
			reflectionProgram.setUniform("activeProbeCount", renderer.getEnvironmentProbeFactory().getProbes().size());
			reflectionProgram.bindShaderStorageBuffer(0, storageBuffer);
			fullscreenBuffer.draw();
			reflectionBuffer.unuse();
		} else {
			GL42.glBindImageTexture(6, reflectionBuffer.getRenderedTexture(0), 0, false, 0, GL15.GL_READ_WRITE, GL30.GL_RGBA16F);
			tiledProbeLightingProgram.use();
			tiledProbeLightingProgram.setUniform("N", IMPORTANCE_SAMPLE_COUNT);
			tiledProbeLightingProgram.setUniform("useAmbientOcclusion", World.useAmbientOcclusion);
			tiledProbeLightingProgram.setUniform("useSSR", World.useSSR);
			tiledProbeLightingProgram.setUniform("screenWidth", (float) Config.WIDTH);
			tiledProbeLightingProgram.setUniform("screenHeight", (float) Config.HEIGHT);
			tiledProbeLightingProgram.setUniformAsMatrix4("viewMatrix", viewMatrix);
			tiledProbeLightingProgram.setUniformAsMatrix4("projectionMatrix", projectionMatrix);
			tiledProbeLightingProgram.setUniform("activeProbeCount", renderer.getEnvironmentProbeFactory().getProbes().size());
			renderer.getEnvironmentProbeFactory().bindEnvironmentProbePositions(tiledProbeLightingProgram);
			tiledProbeLightingProgram.dispatchCompute(reflectionBuffer.getWidth()/16, reflectionBuffer.getHeight()/16, 1); //16+1
	//		GL42.glMemoryBarrier(GL42.GL_ALL_BARRIER_BITS);
		}

		GL11.glDeleteTextures(copyTextureId);
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
		secondPassPointProgram.setUniform("screenWidth", (float) Config.WIDTH);
		secondPassPointProgram.setUniform("screenHeight", (float) Config.HEIGHT);
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
				light.drawAgain(secondPassPointProgram);
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
		secondPassTubeProgram.setUniform("screenWidth", (float) Config.WIDTH);
		secondPassTubeProgram.setUniform("screenHeight", (float) Config.HEIGHT);
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
			tubeLight.draw(secondPassTubeProgram);
		}
	}
	
	private void doAreaLights(List<AreaLight> areaLights, FloatBuffer viewMatrix, FloatBuffer projectionMatrix) {
		

		GL11.glDisable(GL11.GL_CULL_FACE);
		GL11.glDisable(GL11.GL_DEPTH_TEST);
		if(areaLights.isEmpty()) { return; }
		
		GPUProfiler.start("Area lights: " + areaLights.size());
		
		secondPassAreaLightProgram.use();
		secondPassAreaLightProgram.setUniform("screenWidth", (float) Config.WIDTH);
		secondPassAreaLightProgram.setUniform("screenHeight", (float) Config.HEIGHT);
		secondPassAreaLightProgram.setUniform("secondPassScale", SECONDPASSSCALE);
		secondPassAreaLightProgram.setUniformAsMatrix4("viewMatrix", viewMatrix);
		secondPassAreaLightProgram.setUniformAsMatrix4("projectionMatrix", projectionMatrix);
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

			// TODO: Add textures to arealights
//			try {
//				GL13.glActiveTexture(GL13.GL_TEXTURE0 + 8);
//				Texture lightTexture = renderer.getTextureFactory().getTexture("brick.hptexture");
//				GL11.glBindTexture(GL11.GL_TEXTURE_2D, lightTexture.getTextureID());
//			} catch (IOException e) {
//				e.printStackTrace();
//			}
			GL13.glActiveTexture(GL13.GL_TEXTURE0 + 9);
			GL11.glBindTexture(GL11.GL_TEXTURE_2D, renderer.getLightFactory().getDepthMapForAreaLight(areaLight));
			fullscreenBuffer.draw();
//			areaLight.getVertexBuffer().drawDebug();
		}
		
		GPUProfiler.end();
	}
	
	private void doInstantRadiosity(DirectionalLight directionalLight, FloatBuffer viewMatrix, FloatBuffer projectionMatrix) {
		if(World.useInstantRadiosity) {
			GPUProfiler.start("Instant Radiosity");
			GL13.glActiveTexture(GL13.GL_TEXTURE0 + 6);
			GL11.glBindTexture(GL11.GL_TEXTURE_2D, directionalLight.getShadowMapId()); // momentum 1, momentum 2
			GL13.glActiveTexture(GL13.GL_TEXTURE0 + 7);
			GL11.glBindTexture(GL11.GL_TEXTURE_2D, directionalLight.getShadowMapWorldPositionId()); // world position
			GL13.glActiveTexture(GL13.GL_TEXTURE0 + 8);
			GL11.glBindTexture(GL11.GL_TEXTURE_2D, directionalLight.getShadowMapColorMapId()); // object's color
			instantRadiosityProgram.use();
			instantRadiosityProgram.setUniform("screenWidth", (float) Config.WIDTH);
			instantRadiosityProgram.setUniform("screenHeight", (float) Config.HEIGHT);
			instantRadiosityProgram.setUniform("secondPassScale", SECONDPASSSCALE);
			instantRadiosityProgram.setUniform("lightDiffuse", directionalLight.getColor());
			instantRadiosityProgram.setUniformAsMatrix4("viewMatrix", viewMatrix);
			instantRadiosityProgram.setUniformAsMatrix4("projectionMatrix", projectionMatrix);
			GL11.glDisable(GL11.GL_CULL_FACE);
			GL11.glDisable(GL11.GL_DEPTH_TEST);
			fullscreenBuffer.draw();
			GPUProfiler.end();
		}
	}


	void combinePass(RenderTarget target, DirectionalLight light, Camera camera) {
		renderer.getTextureFactory().generateMipMaps(finalBuffer.getRenderedTexture(0));

		combineProgram.use();
		combineProgram.setUniformAsMatrix4("projectionMatrix", camera.getProjectionMatrixAsBuffer());
		combineProgram.setUniformAsMatrix4("viewMatrix", camera.getViewMatrixAsBuffer());
		combineProgram.setUniform("screenWidth", (float) Config.WIDTH);
		combineProgram.setUniform("screenHeight", (float) Config.HEIGHT);
		combineProgram.setUniform("camPosition", camera.getPosition());
		combineProgram.setUniform("ambientColor", World.AMBIENT_LIGHT);
		combineProgram.setUniform("useAmbientOcclusion", World.useAmbientOcclusion);
		combineProgram.setUniform("worldExposure", World.EXPOSURE);
		combineProgram.setUniform("AUTO_EXPOSURE_ENABLED", World.AUTO_EXPOSURE_ENABLED);
		combineProgram.setUniform("fullScreenMipmapCount", fullScreenMipmapCount);
		combineProgram.setUniform("activeProbeCount", renderer.getEnvironmentProbeFactory().getProbes().size());
		combineProgram.bindShaderStorageBuffer(0, storageBuffer);
//		bindEnvironmentProbePositions(combineProgram);
		
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
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + 8);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, reflectionBuffer.getRenderedTexture(0));
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + 9);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, reflectionBuffer.getRenderedTexture(1));
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + 10);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, finalBuffer.getRenderedTexture(0));
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + 11);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, halfScreenBuffer.getRenderedTexture(0));
		
		fullscreenBuffer.draw();
			
		if(target == null) {
			GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
		} else {
			target.use(true);
		}

		GPUProfiler.start("Post processing");
		postProcessProgram.use();
		GL13.glActiveTexture(GL13.GL_TEXTURE0);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, finalBuffer.getRenderedTexture(0)); // output color
		postProcessProgram.setUniform("worldExposure", World.EXPOSURE);
		postProcessProgram.setUniform("AUTO_EXPOSURE_ENABLED", World.AUTO_EXPOSURE_ENABLED);
		postProcessProgram.setUniform("usePostProcessing", World.ENABLE_POSTPROCESSING);
		postProcessProgram.bindShaderStorageBuffer(0, storageBuffer);
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + 1);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, gBuffer.getRenderedTexture(1));
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + 2);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, getMotionMap());
		fullscreenBuffer.draw();
		
		GPUProfiler.end();
	}

	public void drawDebug(Camera camera, DynamicsWorld dynamicsWorld, Octree octree, List<Entity> entities, List<PointLight> pointLights, List<TubeLight> tubeLights, List<AreaLight> areaLights, CubeMap cubeMap) {
		///////////// firstpass
//		GL11.glEnable(GL11.GL_CULL_FACE);
//		GL11.glDepthMask(true);
		gBuffer.use(true);
//		GL11.glClearColor(1, 1, 1, 1);
//		GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
		GL11.glDisable(GL11.GL_DEPTH_TEST);
		GL11.glDisable(GL11.GL_BLEND);

		linesProgram.use();
		linesProgram.setUniform("screenWidth", (float) Config.WIDTH);
		linesProgram.setUniform("screenHeight", (float) Config.HEIGHT);
		linesProgram.setUniformAsMatrix4("viewMatrix", camera.getViewMatrixAsBuffer());
		linesProgram.setUniformAsMatrix4("projectionMatrix", camera.getProjectionMatrixAsBuffer());
		linesProgram.setUniform("eyePosition", camera.getPosition());
		
		if(World.DRAWSCENE_ENABLED) {
			linesProgram.setUniform("diffuseColor", new Vector3f(0,0,1));
			List<Entity> visibleEntities = new ArrayList<>();
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
			
			for (Entity entity : visibleEntities) {
				entity.getComponentOption(ModelComponent.class).ifPresent(modelComponent -> {
					modelComponent.drawDebug(linesProgram, entity.getModelMatrixAsBuffer());
				});
				renderer.drawLine(new Vector3f(), (Vector3f) Vector3f.add(new Vector3f(), entity.getViewDirection(), null).scale(10));
				renderer.drawLine(new Vector3f(), (Vector3f) Vector3f.add(new Vector3f(), entity.getUpDirection(), null).scale(10));
				renderer.drawLine(new Vector3f(), (Vector3f) Vector3f.add(new Vector3f(), entity.getRightDirection(), null).scale(10));
			}
		}

		linesProgram.setUniformAsMatrix4("modelMatrix", identityMatrixBuffer);
		if (World.DRAWLIGHTS_ENABLED) {
			linesProgram.setUniform("diffuseColor", new Vector3f(0,1,1));
			for (Entity entity : pointLights) {
				entity.getComponent(ModelComponent.class).drawDebug(linesProgram, entity.getModelMatrixAsBuffer());
			}	
			for (Entity entity : tubeLights) {
				entity.getComponent(ModelComponent.class).drawDebug(linesProgram, entity.getModelMatrixAsBuffer());
			}	
		}

		if (Octree.DRAW_LINES) {
			linesProgram.setUniform("diffuseColor", new Vector3f(1,1,1));
			octree.drawDebug(renderer, camera, linesProgram);
		}
		
		if (World.DRAW_PROBES) {
			linesProgram.setUniform("diffuseColor", new Vector3f(0,1,0));
			linesProgram.setUniformAsMatrix4("modelMatrix", identityMatrixBuffer);
			renderer.getEnvironmentProbeFactory().drawDebug(linesProgram, octree);
		}

	    renderer.drawLine(new Vector3f(), new Vector3f(15,0,0));
	    renderer.drawLine(new Vector3f(), new Vector3f(0,15,0));
	    renderer.drawLine(new Vector3f(), new Vector3f(0,0,-15));
	    renderer.drawLine(new Vector3f(), (Vector3f) ((Vector3f)(camera.getViewDirection())).scale(15));
	    renderer.drawLine(new Vector3f(), (Vector3f) ((Vector3f)(camera.getViewDirection())).scale(15));
	    renderer.drawLine(new Vector3f(), (Vector3f) ((Vector3f)(camera.getViewDirection())).scale(15));
	    dynamicsWorld.debugDrawWorld();
	    renderer.drawLines(linesProgram);
		
		GL11.glDepthMask(false);
		GL11.glDisable(GL11.GL_DEPTH_TEST);
		////////////////////

		drawSecondPass(camera, renderer.getLightFactory().getDirectionalLight(), pointLights, tubeLights, areaLights, cubeMap);
	}

	public int getLightAccumulationMapOneId() {
		return laBuffer.getRenderedTexture(0);
	}
	public int getAmbientOcclusionMapId() {
		return laBuffer.getRenderedTexture(1);
	}
	public int getPositionMap() {
		return gBuffer.getRenderedTexture(0);
	}
	public int getNormalMap() {
		return gBuffer.getRenderedTexture(1);
	}
	public int getColorReflectivenessMap() {
		return gBuffer.getRenderedTexture(2);
	}
	public int getMotionMap() {
		return gBuffer.getRenderedTexture(3);
	}

	public int getFinalMap() {
		return finalBuffer.getRenderedTexture(0);
	}

	public StorageBuffer getStorageBuffer() {
		return storageBuffer;
	}

	public void setStorageBuffer(StorageBuffer storageBuffer) {
		this.storageBuffer = storageBuffer;
	}
}
