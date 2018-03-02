package de.hanno.hpengine.engine.graphics.light;

import com.google.common.eventbus.Subscribe;
import de.hanno.hpengine.engine.DirectoryManager;
import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.camera.Camera;
import de.hanno.hpengine.engine.camera.InputComponentSystem;
import de.hanno.hpengine.engine.component.ModelComponent;
import de.hanno.hpengine.engine.config.Config;
import de.hanno.hpengine.engine.entity.Entity;
import de.hanno.hpengine.engine.event.LightChangedEvent;
import de.hanno.hpengine.engine.event.PointLightMovedEvent;
import de.hanno.hpengine.engine.event.SceneInitEvent;
import de.hanno.hpengine.engine.event.bus.EventBus;
import de.hanno.hpengine.engine.graphics.buffer.GPUBuffer;
import de.hanno.hpengine.engine.graphics.buffer.PersistentMappedBuffer;
import de.hanno.hpengine.engine.graphics.GpuContext;
import de.hanno.hpengine.engine.graphics.renderer.RenderBatch;
import de.hanno.hpengine.engine.graphics.renderer.constants.GlTextureTarget;
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DrawStrategy;
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.ColorAttachmentDefinition;
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.CubeMapArrayRenderTarget;
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.RenderTarget;
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.RenderTargetBuilder;
import de.hanno.hpengine.engine.graphics.shader.Program;
import de.hanno.hpengine.engine.graphics.shader.ProgramManager;
import de.hanno.hpengine.engine.graphics.shader.Shader;
import de.hanno.hpengine.engine.graphics.shader.define.Defines;
import de.hanno.hpengine.engine.graphics.state.RenderState;
import de.hanno.hpengine.engine.model.ModelComponentSystem;
import de.hanno.hpengine.engine.model.OBJLoader;
import de.hanno.hpengine.engine.model.StaticModel;
import de.hanno.hpengine.engine.model.material.Material;
import de.hanno.hpengine.engine.model.material.MaterialManager;
import de.hanno.hpengine.engine.model.texture.CubeMapArray;
import de.hanno.hpengine.engine.scene.SceneManager;
import de.hanno.hpengine.util.TypedTuple;
import de.hanno.hpengine.util.Util;
import de.hanno.hpengine.util.stopwatch.GPUProfiler;
import net.engio.mbassy.listener.Handler;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL42;

import java.io.File;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static de.hanno.hpengine.engine.graphics.renderer.constants.GlCap.CULL_FACE;
import static de.hanno.hpengine.engine.graphics.renderer.constants.GlCap.DEPTH_TEST;
import static de.hanno.hpengine.engine.model.ModelComponentSystemKt.getInstanceCount;

public class LightManager {

	public static int MAX_AREALIGHT_SHADOWMAPS = 2;
	public static int MAX_POINTLIGHT_SHADOWMAPS = 5;
	public static int AREALIGHT_SHADOWMAP_RESOLUTION = 512;
    private final SceneManager sceneManager;
    private final GpuContext gpuContext;
    private final MaterialManager materialManager;
    private final ProgramManager programManager;
	private final ModelComponentSystem modelComponentSystem;
	private final Engine engine;

	private int pointLightDepthMapsArrayCube;
	private int pointLightDepthMapsArrayFront;
	private int pointLightDepthMapsArrayBack;
	private RenderTarget renderTarget;
	private CubeMapArrayRenderTarget cubemapArrayRenderTarget;
	private List<Integer> areaLightDepthMaps = new ArrayList<>();
	private Program areaShadowPassProgram;
	private Program pointShadowPassProgram;
	private Program pointCubeShadowPassProgram;
	private final Entity cameraEntity;
	private Camera camera;

	private int pointLightsForwardMaxCount = 20;
	private FloatBuffer pointLightPositions = BufferUtils.createFloatBuffer(pointLightsForwardMaxCount * 3);
	private FloatBuffer pointLightColors = BufferUtils.createFloatBuffer(pointLightsForwardMaxCount * 3);
	private FloatBuffer pointLightRadiuses = BufferUtils.createFloatBuffer(pointLightsForwardMaxCount);

	private int areaLightsForwardMaxCount = 5;
	private FloatBuffer areaLightPositions = BufferUtils.createFloatBuffer(areaLightsForwardMaxCount * 3);
	private FloatBuffer areaLightColors = BufferUtils.createFloatBuffer(areaLightsForwardMaxCount * 3);
	private FloatBuffer areaLightWidthHeightRanges = BufferUtils.createFloatBuffer(areaLightsForwardMaxCount * 3);
	private FloatBuffer areaLightViewDirections = BufferUtils.createFloatBuffer(areaLightsForwardMaxCount * 3);
	private FloatBuffer areaLightUpDirections = BufferUtils.createFloatBuffer(areaLightsForwardMaxCount * 3);
	private FloatBuffer areaLightRightDirections = BufferUtils.createFloatBuffer(areaLightsForwardMaxCount * 3);

	private StaticModel sphereMesh;
    private StaticModel cubeMesh;
    private StaticModel planeMesh;

	private volatile GPUBuffer lightBuffer;

    public List<PointLight> pointLights = new CopyOnWriteArrayList<>();
    public List<TubeLight> tubeLights = new CopyOnWriteArrayList<>();
    public List<AreaLight> areaLights = new CopyOnWriteArrayList();
    public DirectionalLight directionalLight = new DirectionalLight(new Entity());
//                    .apply { addComponent(cameraComponentSystem.create(this, Util.createOrthogonal(-1000f, 1000f, 1000f, -1000f, -2500f, 2500f), -2500f, 2500f, 60f, 16f / 9f)) }

    private DirectionalLight directionalLightComponent;
	
	public LightManager(Engine engine, EventBus eventBus, MaterialManager materialManager, SceneManager sceneManager, GpuContext gpuContext, ProgramManager programManager, InputComponentSystem inputControllerSystem, ModelComponentSystem modelComponentSystem) {
		this.engine = engine;
		this.materialManager = materialManager;
		this.sceneManager = sceneManager;
		this.gpuContext = gpuContext;
        this.programManager = programManager;
        this.modelComponentSystem = modelComponentSystem;
		sphereMesh = null;
		try {
            sphereMesh = new OBJLoader().loadTexturedModel(this.materialManager, new File(DirectoryManager.WORKDIR_NAME + "/assets/models/sphere.obj"));
			sphereMesh.setMaterial(this.materialManager.getDefaultMaterial());
            cubeMesh = new OBJLoader().loadTexturedModel(this.materialManager, new File(DirectoryManager.WORKDIR_NAME + "/assets/models/cube.obj"));
			cubeMesh.setMaterial(this.materialManager.getDefaultMaterial());
            planeMesh = new OBJLoader().loadTexturedModel(this.materialManager, new File(DirectoryManager.WORKDIR_NAME + "/assets/models/planeRotated.obj"));
			planeMesh.setMaterial(this.materialManager.getDefaultMaterial());
		} catch (IOException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}

		this.renderTarget = new RenderTargetBuilder(this.gpuContext)
                                .setWidth(AREALIGHT_SHADOWMAP_RESOLUTION)
								.setHeight(AREALIGHT_SHADOWMAP_RESOLUTION)
								.add(new ColorAttachmentDefinition()
                                        .setInternalFormat(GL30.GL_RGBA32F)
                                        .setTextureFilter(GL11.GL_NEAREST_MIPMAP_LINEAR))
								.build();

        if(Config.getInstance().isUseDpsm()) {
// TODO: Use wrapper
			this.pointShadowPassProgram = this.programManager.getProgram(Shader.ShaderSourceFactory.getShaderSource(new File(Shader.getDirectory() + "pointlight_shadow_vertex.glsl")), Shader.ShaderSourceFactory.getShaderSource(new File(Shader.getDirectory() + "pointlight_shadow_fragment.glsl")), new Defines());

			pointLightDepthMapsArrayFront = GL11.glGenTextures();
            this.gpuContext.bindTexture(GlTextureTarget.TEXTURE_2D_ARRAY, pointLightDepthMapsArrayFront);
			GL42.glTexStorage3D(GL30.GL_TEXTURE_2D_ARRAY, 1, GL30.GL_RGBA16F, AREALIGHT_SHADOWMAP_RESOLUTION, AREALIGHT_SHADOWMAP_RESOLUTION, MAX_POINTLIGHT_SHADOWMAPS);
			GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
			GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
			GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
			GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);

			pointLightDepthMapsArrayBack = GL11.glGenTextures();
            this.gpuContext.bindTexture(GlTextureTarget.TEXTURE_2D_ARRAY, pointLightDepthMapsArrayBack);
			GL42.glTexStorage3D(GL30.GL_TEXTURE_2D_ARRAY, 1, GL30.GL_RGBA16F, AREALIGHT_SHADOWMAP_RESOLUTION, AREALIGHT_SHADOWMAP_RESOLUTION, MAX_POINTLIGHT_SHADOWMAPS);
			GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
			GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
			GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
			GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
		} else {
			this.pointCubeShadowPassProgram = this.programManager.getProgram(Shader.ShaderSourceFactory.getShaderSource(new File(Shader.getDirectory() + "pointlight_shadow_cubemap_vertex.glsl")), Shader.ShaderSourceFactory.getShaderSource(new File(Shader.getDirectory() + "pointlight_shadow_cubemap_geometry.glsl")), Shader.ShaderSourceFactory.getShaderSource(new File(Shader.getDirectory() + "pointlight_shadow_cube_fragment.glsl")), new Defines());

			CubeMapArray cubeMapArray = new CubeMapArray(this.gpuContext, MAX_POINTLIGHT_SHADOWMAPS, GL11.GL_LINEAR, GL30.GL_RGBA16F, AREALIGHT_SHADOWMAP_RESOLUTION);
			pointLightDepthMapsArrayCube = cubeMapArray.getTextureID();
			this.cubemapArrayRenderTarget = new CubeMapArrayRenderTarget(
                    gpuContext, AREALIGHT_SHADOWMAP_RESOLUTION, AREALIGHT_SHADOWMAP_RESOLUTION, MAX_POINTLIGHT_SHADOWMAPS, cubeMapArray);
		}

		this.areaShadowPassProgram = this.programManager.getProgram(Shader.ShaderSourceFactory.getShaderSource(new File(Shader.getDirectory() + "mvp_vertex.glsl")), Shader.ShaderSourceFactory.getShaderSource(new File(Shader.getDirectory() + "shadowmap_fragment.glsl")), new Defines());
		this.cameraEntity = new Entity();
		this.camera = new Camera(cameraEntity, Util.createPerspective(90f, 1, 1f, 500f), 1f, 500f, 90f, 1);

		// TODO: WRAP METHODS SEPARATELY
        this.gpuContext.execute(() -> {
			for(int i = 0; i < MAX_AREALIGHT_SHADOWMAPS; i++) {
                int renderedTextureTemp = this.gpuContext.genTextures();
                this.gpuContext.bindTexture(GlTextureTarget.TEXTURE_2D, renderedTextureTemp);
				GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA16, AREALIGHT_SHADOWMAP_RESOLUTION/2, AREALIGHT_SHADOWMAP_RESOLUTION/2, 0, GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE, (FloatBuffer) null);
				GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
				GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
				GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
				GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
				areaLightDepthMaps.add(renderedTextureTemp);
			}
		});

//		lightBuffer = OpenGLContext.getInstance().calculate(() -> new StorageBuffer(1000));
        lightBuffer = this.gpuContext.calculate(() -> new PersistentMappedBuffer(this.gpuContext, 1000));
		eventBus.register(this);
        directionalLightComponent = createDirectionalLight(directionalLight.getEntity());
        directionalLight.getEntity().addComponent(directionalLightComponent);
        inputControllerSystem.addComponent(directionalLight.addInputController(this.engine));
        initLights();
    }

    private void initLights() {
        for (PointLight pointLight : pointLights) {
        }
        for (AreaLight areaLight : areaLights) {
        }

    }

	public PointLight getPointLight() {
		return getPointLight(new Vector3f());
	}
	
	public PointLight getPointLight(Vector3f position) {
		return getPointLight(position, new Vector4f(1,1,1,1), PointLight.DEFAULT_RANGE);
	}

	public PointLight getPointLight(Vector3f position, Vector4f colorIntensity) {
		return getPointLight(position, colorIntensity, PointLight.DEFAULT_RANGE);
	}

	public PointLight getPointLight(float range) {
		return getPointLight(new Vector3f(), new Vector4f(1,1,1,1), range);
	}
	public PointLight getPointLight(Vector3f position, Vector4f colorIntensity, float range) {
		Material material = materialManager.getDefaultMaterial();
		
		PointLight light = new PointLight(position, colorIntensity, range);
        updatePointLightArrays();
		return light;
	}
	private void updatePointLightArrays() {
		float[] positions = new float[pointLightsForwardMaxCount*3];
		float[] colors = new float[pointLightsForwardMaxCount*3];
		float[] radiuses = new float[pointLightsForwardMaxCount];
		
		for(int i = 0; i < Math.min(pointLightsForwardMaxCount, sceneManager.getScene().getPointLights().size()); i++) {
			PointLight light =  sceneManager.getScene().getPointLights().get(i);
			positions[3*i] = light.getPosition().x;
			positions[3*i+1] = light.getPosition().y;
			positions[3*i+2] = light.getPosition().z;
			
			colors[3*i] = light.getColor().x;
			colors[3*i+1] = light.getColor().y;
			colors[3*i+2] = light.getColor().z;
			
			radiuses[i] = light.getRadius();
		}

		pointLightPositions = BufferUtils.createFloatBuffer(pointLightsForwardMaxCount*3);
		pointLightPositions.put(positions);
		pointLightPositions.rewind();
		pointLightColors = BufferUtils.createFloatBuffer(pointLightsForwardMaxCount*3);
		pointLightColors.put(colors);
		pointLightColors.rewind();
		pointLightRadiuses = BufferUtils.createFloatBuffer(pointLightsForwardMaxCount);
		pointLightRadiuses.put(radiuses);
		pointLightRadiuses.rewind();
	}

	public FloatBuffer getPointLightPositions() {
		updatePointLightArrays();
		return pointLightPositions;
	}
	public FloatBuffer getPointLightColors() {
		return pointLightColors;
	}
	public FloatBuffer getPointLightRadiuses() {
		return pointLightRadiuses;
	}

	public FloatBuffer getAreaLightPositions() {
		updateAreaLightArrays();
		return areaLightPositions;
	}

	public FloatBuffer getAreaLightColors() {
		return areaLightColors;
	}

	public FloatBuffer getAreaLightWidthHeightRanges() {
		return areaLightWidthHeightRanges;
	}

	public FloatBuffer getAreaLightViewDirections() {
		return areaLightViewDirections;
	}

	public FloatBuffer getAreaLightUpDirections() {
		return areaLightUpDirections;
	}

	public FloatBuffer getAreaLightRightDirections() {
		return areaLightRightDirections;
	}
	public TubeLight getTubeLight() {
		return getTubeLight(200.0f, 50.0f);
	}
	public TubeLight getTubeLight(float length, float radius) {
		TubeLight tubeLight = new TubeLight(new Vector3f(), cubeMesh, new Vector3f(1, 1, 1), length, radius);
		return tubeLight;
	}

	public AreaLight getAreaLight(int width, int height, int range) {
		return getAreaLight(new Vector3f(), new Vector3f(1, 1, 1), width, height, range);
	}
	public AreaLight getAreaLight(Vector3f position, Vector3f color, int width, int height, int range) {
		return getAreaLight(position, new Quaternionf(), color, width, height, range);
	}
	public AreaLight getAreaLight(Vector3f position, int width, int height, int range) {
		return getAreaLight(position, new Quaternionf(), new Vector3f(1, 1, 1), width, height, range);
	}

	public AreaLight getAreaLight(Vector3f position, Quaternionf orientation, Vector3f color, float width, float height, float range) {
		return getAreaLight(position, orientation, color, (int) width, (int) height, (int) range);
	}
	public AreaLight getAreaLight(Vector3f position, Quaternionf orientation, Vector3f color, int width, int height, int range) {
		AreaLight areaLight = new AreaLight(position, color, new Vector3f(width, height, range));
		areaLight.setOrientation(orientation);
		return areaLight;
	}
	
	private void updateAreaLightArrays() {
		float[] positions = new float[areaLightsForwardMaxCount*3];
		float[] colors = new float[areaLightsForwardMaxCount*3];
		float[] widthHeightRanges = new float[areaLightsForwardMaxCount*3];
		float[] viewDirections = new float[areaLightsForwardMaxCount*3];
		float[] upDirections = new float[areaLightsForwardMaxCount*3];
		float[] rightDirections = new float[areaLightsForwardMaxCount*3];
		
		for(int i = 0; i < Math.min(areaLightsForwardMaxCount, areaLights.size()); i++) {
			AreaLight light = areaLights.get(i);
			positions[3*i] = light.getPosition().x;
			positions[3*i+1] = light.getPosition().y;
			positions[3*i+2] = light.getPosition().z;

			colors[3*i] = light.getColor().x;
			colors[3*i+1] = light.getColor().y;
			colors[3*i+2] = light.getColor().z;

			widthHeightRanges[3*i] = light.getWidth();
			widthHeightRanges[3*i+1] = light.getHeight();
			widthHeightRanges[3*i+2] = light.getRange();

			viewDirections[3*i] = light.getViewDirection().x;
			viewDirections[3*i+1] = light.getViewDirection().y;
			viewDirections[3*i+2] = light.getViewDirection().z;
			
			upDirections[3*i] = light.getUpDirection().x;
			upDirections[3*i+1] = light.getUpDirection().y;
			upDirections[3*i+2] = light.getUpDirection().z;
			
			rightDirections[3*i] = light.getRightDirection().x;
			rightDirections[3*i+1] = light.getRightDirection().y;
			rightDirections[3*i+2] = light.getRightDirection().z;
		}

		areaLightPositions = BufferUtils.createFloatBuffer(areaLightsForwardMaxCount*3);
		areaLightPositions.put(positions);
		areaLightPositions.rewind();
		areaLightColors = BufferUtils.createFloatBuffer(areaLightsForwardMaxCount*3);
		areaLightColors.put(colors);
		areaLightColors.rewind();
		areaLightWidthHeightRanges = BufferUtils.createFloatBuffer(areaLightsForwardMaxCount*3);
		areaLightWidthHeightRanges.put(widthHeightRanges);
		areaLightWidthHeightRanges.rewind();
		areaLightViewDirections = BufferUtils.createFloatBuffer(areaLightsForwardMaxCount*3);
		areaLightViewDirections.put(viewDirections);
		areaLightViewDirections.rewind();
		areaLightUpDirections = BufferUtils.createFloatBuffer(areaLightsForwardMaxCount*3);
		areaLightUpDirections.put(upDirections);
		areaLightUpDirections.rewind();
		areaLightRightDirections = BufferUtils.createFloatBuffer(areaLightsForwardMaxCount*3);
		areaLightRightDirections.put(rightDirections);
		areaLightRightDirections.rewind();
	}

	public void renderAreaLightShadowMaps(RenderState renderState) {
		GPUProfiler.start("Arealight shadowmaps");
        gpuContext.depthMask(true);
        gpuContext.enable(DEPTH_TEST);
        gpuContext.disable(CULL_FACE);
		renderTarget.use(true);

		for(int i = 0; i < Math.min(MAX_AREALIGHT_SHADOWMAPS, areaLights.size()); i++) {

			renderTarget.setTargetTexture(areaLightDepthMaps.get(i), 0);

            gpuContext.clearDepthAndColorBuffer();

			AreaLight light = areaLights.get(i);

			areaShadowPassProgram.use();
			areaShadowPassProgram.setUniformAsMatrix4("viewMatrix", light.getViewMatrixAsBuffer());
			areaShadowPassProgram.setUniformAsMatrix4("projectionMatrix", light.getComponent(Camera.class).getProjectionMatrixAsBuffer());
//			directionalShadowPassProgram.setUniform("near", de.hanno.hpengine.camera.getNear());
//			directionalShadowPassProgram.setUniform("far", de.hanno.hpengine.camera.getFar());

			for (RenderBatch e : renderState.getRenderBatchesStatic()) {
//				TODO: Use model component index here
//				areaShadowPassProgram.setUniformAsMatrix4("modelMatrix", e.getModelMatrixAsBuffer());
//				modelComponent.getMaterials().setTexturesActive(areaShadowPassProgram);
//				areaShadowPassProgram.setUniform("hasDiffuseMap", modelComponent.getMaterials().hasDiffuseMap());
//				areaShadowPassProgram.setUniform("color", modelComponent.getMaterials().getDiffuse());

				DrawStrategy.draw(gpuContext, renderState.getVertexIndexBufferStatic().getVertexBuffer(), renderState.getVertexIndexBufferStatic().getIndexBuffer(), e, areaShadowPassProgram, e.isVisible());
			}
		}
		GPUProfiler.end();
	}

	private long pointlightShadowMapsRenderedInCycle;
	public void renderPointLightShadowMaps(RenderState renderState) {
        boolean needToRedraw = pointlightShadowMapsRenderedInCycle < renderState.getEntitiesState().entityMovedInCycle || pointlightShadowMapsRenderedInCycle < renderState.getPointlightMovedInCycle();
        if(!needToRedraw) { return; }

		GPUProfiler.start("PointLight shadowmaps");
        gpuContext.depthMask(true);
        gpuContext.enable(DEPTH_TEST);
        gpuContext.enable(CULL_FACE);
		cubemapArrayRenderTarget.use(false);
        gpuContext.clearDepthAndColorBuffer();
        gpuContext.viewPort(0, 0, 2*128, 2*128);
        //TODO: WTF is with the 256...

		for(int i = 0; i < Math.min(MAX_POINTLIGHT_SHADOWMAPS, sceneManager.getScene().getPointLights().size()); i++) {

			PointLight light = sceneManager.getScene().getPointLights().get(i);
			pointCubeShadowPassProgram.use();
			pointCubeShadowPassProgram.bindShaderStorageBuffer(1, renderState.getMaterialBuffer());
			pointCubeShadowPassProgram.bindShaderStorageBuffer(3, renderState.getEntitiesBuffer());
			pointCubeShadowPassProgram.setUniform("pointLightPositionWorld", light.getPosition());
            pointCubeShadowPassProgram.setUniform("pointLightRadius", light.getRadius());
            pointCubeShadowPassProgram.setUniform("lightIndex", i);
			TypedTuple<Matrix4f[], Matrix4f[]> viewProjectionMatrices
                    = Util.getCubeViewProjectionMatricesForPosition(light.getPosition());
            FloatBuffer[] viewMatrices = new FloatBuffer[6];
            FloatBuffer[] projectionMatrices = new FloatBuffer[6];
			for(int floatBufferIndex = 0; floatBufferIndex < 6; floatBufferIndex++) {
                viewMatrices[floatBufferIndex] = BufferUtils.createFloatBuffer(16);
                projectionMatrices[floatBufferIndex] = BufferUtils.createFloatBuffer(16);

                viewProjectionMatrices.getLeft()[floatBufferIndex].get(viewMatrices[floatBufferIndex]);
                viewProjectionMatrices.getRight()[floatBufferIndex].get(projectionMatrices[floatBufferIndex]);

                viewMatrices[floatBufferIndex].rewind();
                projectionMatrices[floatBufferIndex].rewind();
                pointCubeShadowPassProgram.setUniformAsMatrix4("viewMatrices[" + floatBufferIndex + "]", viewMatrices[floatBufferIndex]);
                pointCubeShadowPassProgram.setUniformAsMatrix4("projectionMatrices[" + floatBufferIndex + "]", projectionMatrices[floatBufferIndex]);
//				floatBuffers[floatBufferIndex] = null;
			}
//			pointCubeShadowPassProgram.setUniformAsMatrix4("projectionMatrix", renderState.getCamera().getProjectionMatrixAsBuffer());
//			pointCubeShadowPassProgram.setUniformAsMatrix4("viewMatrix", renderState.getCamera().getViewMatrixAsBuffer());
//			pointCubeShadowPassProgram.setUniformAsMatrix4("viewProjectionMatrix", renderState.getCamera().getViewProjectionMatrixAsBuffer());

			GPUProfiler.start("PointLight shadowmap entity rendering");
			for (RenderBatch e : renderState.getRenderBatchesStatic()) {
                DrawStrategy.draw(gpuContext, renderState.getVertexIndexBufferStatic().getVertexBuffer(), renderState.getVertexIndexBufferStatic().getIndexBuffer(), e, pointCubeShadowPassProgram, !e.isVisible());
			}
			GPUProfiler.end();
		}
		GPUProfiler.end();
		pointlightShadowMapsRenderedInCycle = renderState.getCycle();
	}

	public void renderPointLightShadowMaps_dpsm(RenderState renderState, List<Entity> entities) {
		GPUProfiler.start("PointLight shadowmaps");
        gpuContext.depthMask(true);
        gpuContext.enable(DEPTH_TEST);
        gpuContext.disable(CULL_FACE);
		renderTarget.use(false);

		pointShadowPassProgram.use();
		for(int i = 0; i < Math.min(MAX_POINTLIGHT_SHADOWMAPS, sceneManager.getScene().getPointLights().size()); i++) {
			renderTarget.setTargetTextureArrayIndex(pointLightDepthMapsArrayFront, i);

            gpuContext.clearDepthAndColorBuffer();
			PointLight light = sceneManager.getScene().getPointLights().get(i);
			pointShadowPassProgram.setUniform("pointLightPositionWorld", light.getPosition());
			pointShadowPassProgram.setUniform("pointLightRadius", light.getRadius());
			pointShadowPassProgram.setUniform("isBack", false);

			for (Entity e : entities) {
				e.getComponentOption(ModelComponent.class, ModelComponent.COMPONENT_KEY).ifPresent(modelComponent -> {
                    pointShadowPassProgram.setUniformAsMatrix4("modelMatrix", e.getTransformationBuffer());
					modelComponent.getMaterial(materialManager).setTexturesActive(pointShadowPassProgram);
					pointShadowPassProgram.setUniform("hasDiffuseMap", modelComponent.getMaterial(materialManager).hasDiffuseMap());
					pointShadowPassProgram.setUniform("color", modelComponent.getMaterial(materialManager).getDiffuse());

                    RenderBatch batch = new RenderBatch().init(pointShadowPassProgram, e.getComponent(ModelComponent.class, ModelComponent.COMPONENT_KEY).getEntityBufferIndex(), e.isVisible(), e.isSelected(), Config.getInstance().isDrawLines(), cameraEntity.getPosition(), true, getInstanceCount(e), true, e.getUpdate(), e.getMinMaxWorld().getMin(), e.getMinMaxWorld().getMax(), e.getCenterWorld(), e.getBoundingSphereRadius(), modelComponent.getIndexCount(), modelComponent.getIndexOffset(), modelComponent.getBaseVertex(), false, e.getInstanceMinMaxWorlds());
                    DrawStrategy.draw(gpuContext, renderState, batch);
				});
			}

			pointShadowPassProgram.setUniform("isBack", true);
			renderTarget.setTargetTextureArrayIndex(pointLightDepthMapsArrayBack, i);
            gpuContext.clearDepthAndColorBuffer();
			for (Entity e : entities) {
				e.getComponentOption(ModelComponent.class, ModelComponent.COMPONENT_KEY).ifPresent(modelComponent -> {
                    pointShadowPassProgram.setUniformAsMatrix4("modelMatrix", e.getTransformationBuffer());
					modelComponent.getMaterial(materialManager).setTexturesActive(pointShadowPassProgram);
					pointShadowPassProgram.setUniform("hasDiffuseMap", modelComponent.getMaterial(materialManager).hasDiffuseMap());
					pointShadowPassProgram.setUniform("color", modelComponent.getMaterial(materialManager).getDiffuse());

                    RenderBatch batch = new RenderBatch().init(pointShadowPassProgram, e.getComponent(ModelComponent.class, ModelComponent.COMPONENT_KEY).getEntityBufferIndex(), e.isVisible(), e.isSelected(), Config.getInstance().isDrawLines(), cameraEntity.getPosition(), true, getInstanceCount(e), true, e.getUpdate(), e.getMinMaxWorld().getMin(), e.getMinMaxWorld().getMax(), e.getCenterWorld(), e.getBoundingSphereRadius(), modelComponent.getIndexCount(), modelComponent.getIndexOffset(), modelComponent.getBaseVertex(), false, e.getInstanceMinMaxWorlds());
                    DrawStrategy.draw(gpuContext, renderState, batch);
				});
			}
		}
		GPUProfiler.end();
	}

	public void update(float deltaSeconds, long currentCycle) {

        Iterator<PointLight> pointLightsIterator = pointLights.iterator();
        while (pointLightsIterator.hasNext()) {
            pointLightsIterator.next().update(deltaSeconds);
        }

        for (int i = 0; i < areaLights.size(); i++) {
            areaLights.get(i).update(deltaSeconds);
        }
        directionalLight.update(deltaSeconds);

        if (directionalLight.getEntity().hasMoved()) {
            directionalLightMovedInCycle = currentCycle;
            directionalLight.entity.setHasMoved(false);
        }
    }
    transient private long directionalLightMovedInCycle = 0;

	public long getDirectionalLightMovedInCycle() {
		return directionalLightMovedInCycle;
	}

	public int getDepthMapForAreaLight(AreaLight light) {
		int index = areaLights.indexOf(light);
		if(index >= MAX_AREALIGHT_SHADOWMAPS) {return -1;}

		return areaLightDepthMaps.get(index);
	}

	public Camera getCameraForAreaLight(AreaLight light) {
		cameraEntity.setTranslation(light.getPosition().negate(null));
		//		de.hanno.hpengine.camera.getOrientation().x = -lights.getOrientation().x;
//		de.hanno.hpengine.camera.getOrientation().y = -lights.getOrientation().y;
//		de.hanno.hpengine.camera.getOrientation().z = -lights.getOrientation().z;
//		de.hanno.hpengine.camera.getOrientation().w = -lights.getOrientation().w;
//		de.hanno.hpengine.camera.rotate(new Vector4f(0, 1, 0, 180)); // TODO: CHECK THIS SHIT UP
		//return de.hanno.hpengine.camera.getComponent(CameraComponent.class).getCamera();
		return camera;
	}

	public FloatBuffer getShadowMatrixForAreaLight(AreaLight light) {
//		c.getOrientation().x = lights.getOrientation().negate(null).x;
//		c.getOrientation().y = lights.getOrientation().negate(null).y;
//		c.getOrientation().z = lights.getOrientation().negate(null).z;
//		c.getOrientation().w = lights.getOrientation().negate(null).w;
//		c.rotateWorld(new Vector4f(0, 1, 0, 180)); // TODO: CHECK THIS SHIT UP

//		Vector3f newPosition = new Vector3f(-lights.getPosition().x, -lights.getPosition().y, -lights.getPosition().y);
//		newPosition = new Vector3f(0, 0, 10);
//		c.setPosition(newPosition);
//		c.rotateWorld(new Vector4f(0, 1, 0, 180)); // TODO: CHECK THIS SHIT UP
//		c.updateShadow();
		return light.getComponent(Camera.class).getViewProjectionMatrixAsBuffer();
	}

	private void bufferLights() {
		List<PointLight> pointLights = sceneManager.getScene().getPointLights();
        gpuContext.execute(() -> {
//			lightBuffer.putValues(0, pointLights.size());
			if(pointLights.size() > 0) {
				lightBuffer.put(Util.toArray(pointLights, PointLight.class));
			}
		});
//		Util.printFloatBuffer(lightBuffer.getValues());
	}

	@Subscribe
    @Handler
	public void bufferLights(LightChangedEvent event) {
		bufferLights();
	}

	@Subscribe
    @Handler
	public void bufferLights(PointLightMovedEvent event) {
		bufferLights();
	}

	@Subscribe
    @Handler
	public void bufferLights(SceneInitEvent event) {
		bufferLights();
	}

	public GPUBuffer getLightBuffer() {
		return lightBuffer;
	}

	public int getPointLightDepthMapsArrayFront() {
		return pointLightDepthMapsArrayFront;
	}
	public int getPointLightDepthMapsArrayBack() {
		return pointLightDepthMapsArrayBack;
	}
	public int getPointLightDepthMapsArrayCube() {
		return pointLightDepthMapsArrayCube;
	}
	public CubeMapArrayRenderTarget getCubemapArrayRenderTarget() {
		return cubemapArrayRenderTarget;
	}

    public DirectionalLight createDirectionalLight(Entity entity) {
        return new DirectionalLight(entity);
	}
}
