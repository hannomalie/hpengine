package de.hanno.hpengine.engine.graphics.light;

import com.google.common.eventbus.Subscribe;
import de.hanno.hpengine.engine.graphics.buffer.GPUBuffer;
import de.hanno.hpengine.engine.graphics.buffer.PersistentMappedBuffer;
import de.hanno.hpengine.engine.camera.Camera;
import de.hanno.hpengine.engine.component.ModelComponent;
import de.hanno.hpengine.engine.config.Config;
import de.hanno.hpengine.engine.container.EntitiesContainer;
import de.hanno.hpengine.engine.DirectoryManager;
import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.graphics.renderer.RenderBatch;
import de.hanno.hpengine.engine.model.Entity;
import de.hanno.hpengine.engine.model.Model;
import de.hanno.hpengine.engine.model.OBJLoader;
import de.hanno.hpengine.engine.event.LightChangedEvent;
import de.hanno.hpengine.engine.event.PointLightMovedEvent;
import de.hanno.hpengine.engine.event.SceneInitEvent;
import de.hanno.hpengine.engine.graphics.renderer.GraphicsContext;
import de.hanno.hpengine.engine.graphics.renderer.constants.GlTextureTarget;
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DrawStrategy;
import de.hanno.hpengine.engine.model.material.Material;
import de.hanno.hpengine.engine.model.material.MaterialFactory;
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.ColorAttachmentDefinition;
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.CubeMapArrayRenderTarget;
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.RenderTarget;
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.RenderTargetBuilder;
import de.hanno.hpengine.engine.graphics.state.RenderState;
import de.hanno.hpengine.engine.scene.Scene;
import de.hanno.hpengine.engine.graphics.shader.*;
import de.hanno.hpengine.engine.model.texture.CubeMapArray;
import de.hanno.hpengine.util.TypedTuple;
import de.hanno.hpengine.util.Util;
import de.hanno.hpengine.util.stopwatch.GPUProfiler;
import net.engio.mbassy.listener.Handler;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL42;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Quaternion;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;

import java.io.File;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

import static de.hanno.hpengine.engine.graphics.renderer.constants.GlCap.CULL_FACE;
import static de.hanno.hpengine.engine.graphics.renderer.constants.GlCap.DEPTH_TEST;

public class LightFactory {

	public static int MAX_AREALIGHT_SHADOWMAPS = 2;
	public static int MAX_POINTLIGHT_SHADOWMAPS = 5;
	public static int AREALIGHT_SHADOWMAP_RESOLUTION = 512;
    private static LightFactory instance;

	public static LightFactory getInstance() {
        if(instance == null) {
            throw new IllegalStateException("Call Engine.init() before using it");
        }
        return instance;
    }

    public static void init() {
        instance = new LightFactory();
    }

    private int pointLightDepthMapsArrayCube;
	private int pointLightDepthMapsArrayFront;
	private int pointLightDepthMapsArrayBack;
	private RenderTarget renderTarget;
	private CubeMapArrayRenderTarget cubemapArrayRenderTarget;
	private List<Integer> areaLightDepthMaps = new ArrayList<>();
	FloatBuffer entityBuffer = BufferUtils.createFloatBuffer(16);
	FloatBuffer buffer = BufferUtils.createFloatBuffer(16);
	private Program areaShadowPassProgram;
	private Program pointShadowPassProgram;
	private Program pointCubeShadowPassProgram;
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

	private Model sphereMesh;
    private Model cubeMesh;
    private Model planeMesh;

	private volatile GPUBuffer lightBuffer;
	
	public LightFactory() {
		sphereMesh = null;
		try {
			sphereMesh = new OBJLoader().loadTexturedModel(new File(DirectoryManager.WORKDIR_NAME + "/assets/models/sphere.obj"));
			sphereMesh.setMaterial(MaterialFactory.getInstance().getDefaultMaterial());
            cubeMesh = new OBJLoader().loadTexturedModel(new File(DirectoryManager.WORKDIR_NAME + "/assets/models/cube.obj"));
            cubeMesh.setMaterial(MaterialFactory.getInstance().getDefaultMaterial());
            planeMesh = new OBJLoader().loadTexturedModel(new File(DirectoryManager.WORKDIR_NAME + "/assets/models/planeRotated.obj"));
            planeMesh.setMaterial(MaterialFactory.getInstance().getDefaultMaterial());
		} catch (IOException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}

		this.renderTarget = new RenderTargetBuilder()
                                .setWidth(AREALIGHT_SHADOWMAP_RESOLUTION)
								.setHeight(AREALIGHT_SHADOWMAP_RESOLUTION)
								.add(new ColorAttachmentDefinition()
                                        .setInternalFormat(GL30.GL_RGBA32F)
                                        .setTextureFilter(GL11.GL_NEAREST_MIPMAP_LINEAR))
								.build();

		if(Config.getInstance().isUseDpsm()) {
// TODO: Use wrapper
			this.pointShadowPassProgram = ProgramFactory.getInstance().getProgram(true, Shader.ShaderSourceFactory.getShaderSource(new File(Shader.getDirectory() + "pointlight_shadow_vertex.glsl")), Shader.ShaderSourceFactory.getShaderSource(new File(Shader.getDirectory() + "pointlight_shadow_fragment.glsl")));

			pointLightDepthMapsArrayFront = GL11.glGenTextures();
			GraphicsContext.getInstance().bindTexture(GlTextureTarget.TEXTURE_2D_ARRAY, pointLightDepthMapsArrayFront);
			GL42.glTexStorage3D(GL30.GL_TEXTURE_2D_ARRAY, 1, GL30.GL_RGBA16F, AREALIGHT_SHADOWMAP_RESOLUTION, AREALIGHT_SHADOWMAP_RESOLUTION, MAX_POINTLIGHT_SHADOWMAPS);
			GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
			GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
			GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
			GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);

			pointLightDepthMapsArrayBack = GL11.glGenTextures();
			GraphicsContext.getInstance().bindTexture(GlTextureTarget.TEXTURE_2D_ARRAY, pointLightDepthMapsArrayBack);
			GL42.glTexStorage3D(GL30.GL_TEXTURE_2D_ARRAY, 1, GL30.GL_RGBA16F, AREALIGHT_SHADOWMAP_RESOLUTION, AREALIGHT_SHADOWMAP_RESOLUTION, MAX_POINTLIGHT_SHADOWMAPS);
			GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
			GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
			GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
			GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
		} else {
			this.pointCubeShadowPassProgram = ProgramFactory.getInstance().getProgram(true, Shader.ShaderSourceFactory.getShaderSource(new File(Shader.getDirectory() + "pointlight_shadow_cubemap_vertex.glsl")), Shader.ShaderSourceFactory.getShaderSource(new File(Shader.getDirectory() + "pointlight_shadow_cubemap_geometry.glsl")), Shader.ShaderSourceFactory.getShaderSource(new File(Shader.getDirectory() + "pointlight_shadow_cube_fragment.glsl")));

			CubeMapArray cubeMapArray = new CubeMapArray(MAX_POINTLIGHT_SHADOWMAPS, GL11.GL_LINEAR, GL30.GL_RGBA16F, AREALIGHT_SHADOWMAP_RESOLUTION);
			pointLightDepthMapsArrayCube = cubeMapArray.getTextureID();
			this.cubemapArrayRenderTarget = new CubeMapArrayRenderTarget(
					AREALIGHT_SHADOWMAP_RESOLUTION, AREALIGHT_SHADOWMAP_RESOLUTION, MAX_POINTLIGHT_SHADOWMAPS, cubeMapArray);
		}

		this.areaShadowPassProgram = ProgramFactory.getInstance().getProgram(true, Shader.ShaderSourceFactory.getShaderSource(new File(Shader.getDirectory() + "mvp_vertex.glsl")), Shader.ShaderSourceFactory.getShaderSource(new File(Shader.getDirectory() + "shadowmap_fragment.glsl")));
		this.camera = new Camera(Util.createPerpective(90f, 1, 1f, 500f), 1f, 500f, 90f, 1);

		// TODO: WRAP METHODS SEPARATELY
		GraphicsContext.getInstance().execute(() -> {
			for(int i = 0; i < MAX_AREALIGHT_SHADOWMAPS; i++) {
				int renderedTextureTemp = GraphicsContext.getInstance().genTextures();
				GraphicsContext.getInstance().bindTexture(GlTextureTarget.TEXTURE_2D, renderedTextureTemp);
				GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA16, AREALIGHT_SHADOWMAP_RESOLUTION/2, AREALIGHT_SHADOWMAP_RESOLUTION/2, 0, GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE, (FloatBuffer) null);
				GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
				GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
				GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
				GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
				areaLightDepthMaps.add(renderedTextureTemp);
			}
		});

//		lightBuffer = OpenGLContext.getInstance().calculate(() -> new StorageBuffer(1000));
		lightBuffer = GraphicsContext.getInstance().calculate(() -> new PersistentMappedBuffer(1000));
		Engine.getEventBus().register(this);
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
		Material material = MaterialFactory.getInstance().getDefaultMaterial();
		
		PointLight light = new PointLight(position, sphereMesh, colorIntensity, range);
		light.init();
		updatePointLightArrays();
		return light;
	}
	private void updatePointLightArrays() {
		float[] positions = new float[pointLightsForwardMaxCount*3];
		float[] colors = new float[pointLightsForwardMaxCount*3];
		float[] radiuses = new float[pointLightsForwardMaxCount];
		
		for(int i = 0; i < Math.min(pointLightsForwardMaxCount, Engine.getInstance().getScene().getPointLights().size()); i++) {
			PointLight light =  Engine.getInstance().getScene().getPointLights().get(i);
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
		return getAreaLight(position, new Quaternion(), color, width, height, range);
	}
	public AreaLight getAreaLight(Vector3f position, int width, int height, int range) {
		return getAreaLight(position, new Quaternion(), new Vector3f(1, 1, 1), width, height, range);
	}

	public AreaLight getAreaLight(Vector3f position, Quaternion orientation, Vector3f color, float width, float height, float range) {
		return getAreaLight(position, orientation, color, (int) width, (int) height, (int) range);
	}
	public AreaLight getAreaLight(Vector3f position, Quaternion orientation, Vector3f color, int width, int height, int range) {
		AreaLight areaLight = new AreaLight(position, planeMesh, color, new Vector3f(width, height, range));
		areaLight.setOrientation(orientation);
		return areaLight;
	}
	
	private void updateAreaLightArrays() {
		List<AreaLight> areaLights = Engine.getInstance().getScene().getAreaLights();

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

	public void renderAreaLightShadowMaps(RenderState renderState, EntitiesContainer octree) {
        Scene scene = Engine.getInstance().getScene();
        if(scene == null) { return; }

        List<AreaLight> areaLights = scene.getAreaLights();

		GPUProfiler.start("Arealight shadowmaps");
		GraphicsContext.getInstance().depthMask(true);
		GraphicsContext.getInstance().enable(DEPTH_TEST);
		GraphicsContext.getInstance().disable(CULL_FACE);
		renderTarget.use(true);

		for(int i = 0; i < Math.min(MAX_AREALIGHT_SHADOWMAPS, areaLights.size()); i++) {

			renderTarget.setTargetTexture(areaLightDepthMaps.get(i), 0);

			GraphicsContext.getInstance().clearDepthAndColorBuffer();

			AreaLight light = areaLights.get(i);
			Camera camera = light;

			areaShadowPassProgram.use();
			areaShadowPassProgram.setUniformAsMatrix4("viewMatrix", camera.getViewMatrixAsBuffer());
			areaShadowPassProgram.setUniformAsMatrix4("projectionMatrix", camera.getProjectionMatrixAsBuffer());
//			directionalShadowPassProgram.setUniform("near", de.hanno.hpengine.camera.getNear());
//			directionalShadowPassProgram.setUniform("far", de.hanno.hpengine.camera.getFar());

			for (RenderBatch e : renderState.perEntityInfos()) {
//				TODO: Use model component index here
//				areaShadowPassProgram.setUniformAsMatrix4("modelMatrix", e.getModelMatrixAsBuffer());
//				modelComponent.getMaterial().setTexturesActive(areaShadowPassProgram);
//				areaShadowPassProgram.setUniform("hasDiffuseMap", modelComponent.getMaterial().hasDiffuseMap());
//				areaShadowPassProgram.setUniform("color", modelComponent.getMaterial().getDiffuse());

				DrawStrategy.draw(renderState.getVertexIndexBuffer().getVertexBuffer(), renderState.getVertexIndexBuffer().getIndexBuffer(), e, areaShadowPassProgram, e.isVisible());
			}
		}
		GPUProfiler.end();
	}

	private long pointlightShadowMapsRenderedInCycle;
	public void renderPointLightShadowMaps(RenderState renderState) {
        Scene scene = Engine.getInstance().getScene();
        if(scene == null) { return; }

        boolean needToRedraw = pointlightShadowMapsRenderedInCycle < renderState.entitiesState.entityMovedInCycle || pointlightShadowMapsRenderedInCycle < renderState.pointlightMovedInCycle;
        if(!needToRedraw) { return; }

		GPUProfiler.start("PointLight shadowmaps");
		GraphicsContext.getInstance().depthMask(true);
		GraphicsContext.getInstance().enable(DEPTH_TEST);
		GraphicsContext.getInstance().enable(CULL_FACE);
		cubemapArrayRenderTarget.use(false);
		GraphicsContext.getInstance().clearDepthAndColorBuffer();
		GraphicsContext.getInstance().viewPort(0, 0, 2*128, 2*128);
        //TODO: WTF is with the 256...

		for(int i = 0; i < Math.min(MAX_POINTLIGHT_SHADOWMAPS, scene.getPointLights().size()); i++) {

			PointLight light = Engine.getInstance().getScene().getPointLights().get(i);
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

                viewProjectionMatrices.getLeft()[floatBufferIndex].store(viewMatrices[floatBufferIndex]);
                viewProjectionMatrices.getRight()[floatBufferIndex].store(projectionMatrices[floatBufferIndex]);

                viewMatrices[floatBufferIndex].rewind();
                projectionMatrices[floatBufferIndex].rewind();
                pointCubeShadowPassProgram.setUniformAsMatrix4("viewMatrices[" + floatBufferIndex + "]", viewMatrices[floatBufferIndex]);
                pointCubeShadowPassProgram.setUniformAsMatrix4("projectionMatrices[" + floatBufferIndex + "]", projectionMatrices[floatBufferIndex]);
//				floatBuffers[floatBufferIndex] = null;
			}
//			pointCubeShadowPassProgram.setUniformAsMatrix4("projectionMatrix", renderState.camera.getProjectionMatrixAsBuffer());
//			pointCubeShadowPassProgram.setUniformAsMatrix4("viewMatrix", renderState.camera.getViewMatrixAsBuffer());
//			pointCubeShadowPassProgram.setUniformAsMatrix4("viewProjectionMatrix", renderState.camera.getViewProjectionMatrixAsBuffer());

			GPUProfiler.start("PointLight shadowmap entity rendering");
			for (RenderBatch e : renderState.perEntityInfos()) {
                DrawStrategy.draw(renderState.getVertexIndexBuffer().getVertexBuffer(), renderState.getVertexIndexBuffer().getIndexBuffer(), e, pointCubeShadowPassProgram, !e.isVisible());
			}
			GPUProfiler.end();
		}
		GPUProfiler.end();
		pointlightShadowMapsRenderedInCycle = renderState.getCycle();
	}

	public void renderPointLightShadowMaps_dpsm(RenderState renderState, EntitiesContainer octree) {
		GPUProfiler.start("PointLight shadowmaps");
		GraphicsContext.getInstance().depthMask(true);
		GraphicsContext.getInstance().enable(DEPTH_TEST);
		GraphicsContext.getInstance().disable(CULL_FACE);
		renderTarget.use(false);

		pointShadowPassProgram.use();
		for(int i = 0; i < Math.min(MAX_POINTLIGHT_SHADOWMAPS, Engine.getInstance().getScene().getPointLights().size()); i++) {
			renderTarget.setTargetTextureArrayIndex(pointLightDepthMapsArrayFront, i);

			GraphicsContext.getInstance().clearDepthAndColorBuffer();
			PointLight light = Engine.getInstance().getScene().getPointLights().get(i);
			List<Entity> visibles = octree.getEntities();
			pointShadowPassProgram.setUniform("pointLightPositionWorld", light.getPosition());
			pointShadowPassProgram.setUniform("pointLightRadius", light.getRadius());
			pointShadowPassProgram.setUniform("isBack", false);

			for (Entity e : visibles) {
				e.getComponentOption(ModelComponent.class, ModelComponent.COMPONENT_KEY).ifPresent(modelComponent -> {
					pointShadowPassProgram.setUniformAsMatrix4("modelMatrix", e.getModelMatrixAsBuffer());
					modelComponent.getMaterial().setTexturesActive(pointShadowPassProgram);
					pointShadowPassProgram.setUniform("hasDiffuseMap", modelComponent.getMaterial().hasDiffuseMap());
					pointShadowPassProgram.setUniform("color", modelComponent.getMaterial().getDiffuse());

                    RenderBatch batch = new RenderBatch(pointShadowPassProgram, Engine.getInstance().getScene().getEntityBufferIndex(e.getComponent(ModelComponent.class, ModelComponent.COMPONENT_KEY)), e.isVisible(), e.isSelected(), Config.getInstance().isDrawLines(), camera.getWorldPosition(), true, e.getInstanceCount(), true, e.getUpdate(), e.getMinMaxWorld()[0], e.getMinMaxWorld()[1], modelComponent.getIndexCount(), modelComponent.getIndexOffset(), modelComponent.getBaseVertex());
                    DrawStrategy.draw(renderState, batch);
				});
			}

			pointShadowPassProgram.setUniform("isBack", true);
			renderTarget.setTargetTextureArrayIndex(pointLightDepthMapsArrayBack, i);
			GraphicsContext.getInstance().clearDepthAndColorBuffer();
			for (Entity e : visibles) {
				e.getComponentOption(ModelComponent.class, ModelComponent.COMPONENT_KEY).ifPresent(modelComponent -> {
					pointShadowPassProgram.setUniformAsMatrix4("modelMatrix", e.getModelMatrixAsBuffer());
					modelComponent.getMaterial().setTexturesActive(pointShadowPassProgram);
					pointShadowPassProgram.setUniform("hasDiffuseMap", modelComponent.getMaterial().hasDiffuseMap());
					pointShadowPassProgram.setUniform("color", modelComponent.getMaterial().getDiffuse());

                    RenderBatch batch = new RenderBatch(pointShadowPassProgram, Engine.getInstance().getScene().getEntityBufferIndex(e.getComponent(ModelComponent.class, ModelComponent.COMPONENT_KEY)), e.isVisible(), e.isSelected(), Config.getInstance().isDrawLines(), camera.getWorldPosition(), true, e.getInstanceCount(), true, e.getUpdate(), e.getMinMaxWorld()[0], e.getMinMaxWorld()[1], modelComponent.getIndexCount(), modelComponent.getIndexOffset(), modelComponent.getBaseVertex());
                    DrawStrategy.draw(renderState, batch);
				});
			}
		}
		GPUProfiler.end();
	}

	public int getDepthMapForAreaLight(AreaLight light) {
		int index = Engine.getInstance().getScene().getAreaLights().indexOf(light);
		if(index >= MAX_AREALIGHT_SHADOWMAPS) {return -1;}

		return areaLightDepthMaps.get(index);
	}

	public Camera getCameraForAreaLight(AreaLight light) {
		camera.setPosition(light.getPosition().negate(null));
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
		return light.getViewProjectionMatrixAsBuffer();
	}

	private void bufferLights() {
		List<PointLight> pointLights = Engine.getInstance().getScene().getPointLights();
		GraphicsContext.getInstance().execute(() -> {
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

}