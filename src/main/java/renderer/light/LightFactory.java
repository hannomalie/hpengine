package renderer.light;

import camera.Camera;
import component.InputControllerComponent;
import component.ModelComponent;
import engine.World;
import engine.model.Entity;
import engine.model.Model;
import octree.Octree;
import org.lwjgl.BufferUtils;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;
import org.lwjgl.util.vector.Quaternion;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;
import renderer.Renderer;
import renderer.material.Material;
import renderer.rendertarget.ColorAttachmentDefinition;
import renderer.rendertarget.RenderTarget;
import renderer.rendertarget.RenderTargetBuilder;
import shader.Program;
import util.Util;
import util.stopwatch.GPUProfiler;

import java.io.File;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

public class LightFactory {

	public static int MAX_AREALIGHT_SHADOWMAPS = 2;
	public static int AREALIGHT_SHADOWMAP_RESOLUTION = 512;
	private RenderTarget renderTarget;
	private List<Integer> areaLightDepthMaps = new ArrayList<>();
	FloatBuffer entityBuffer = BufferUtils.createFloatBuffer(16);
	FloatBuffer buffer = BufferUtils.createFloatBuffer(16);
	private Program areaShadowPassProgram;
	private Camera camera;
	
	private List<PointLight> pointLights = new ArrayList<>();
	private List<TubeLight> tubeLights = new ArrayList<>();
	private List<AreaLight> areaLights = new ArrayList<>();

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

	private World world;
	private Renderer renderer;
	private Model sphereModel;
	private Model cubeModel;
	
	public LightFactory(World world) {
		this.world = world;
		this.renderer = world.getRenderer();
		sphereModel = null;
		try {
			sphereModel = renderer.getOBJLoader().loadTexturedModel(new File(World.WORKDIR_NAME + "/assets/models/sphere.obj")).get(0);
			sphereModel.setMaterial(renderer.getMaterialFactory().getDefaultMaterial());
			cubeModel = renderer.getOBJLoader().loadTexturedModel(new File(World.WORKDIR_NAME + "/assets/models/cube.obj")).get(0);
			cubeModel.setMaterial(renderer.getMaterialFactory().getDefaultMaterial());
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
                                        .setTextureFilter(GL11.GL_NEAREST))
								.build();
		this.areaShadowPassProgram = renderer.getProgramFactory().getProgram("mvp_vertex.glsl", "shadowmap_fragment.glsl", ModelComponent.DEFAULTCHANNELS, true);
		this.camera = new Camera(Util.createPerpective(90f, 1, 1f, 500f), 1f, 500f, 90f, 1);

		for(int i = 0; i < MAX_AREALIGHT_SHADOWMAPS; i++) {
			int renderedTextureTemp = GL11.glGenTextures();
			GL11.glBindTexture(GL11.GL_TEXTURE_2D, renderedTextureTemp);
			GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, AREALIGHT_SHADOWMAP_RESOLUTION, 512, 0, GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE, (FloatBuffer) null);

			
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);

			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
			areaLightDepthMaps.add(renderedTextureTemp);
		}
	}

    public void update(float seconds) {
        for (AreaLight areaLight : areaLights) {
            areaLight.update(seconds);
        }
    }
	
	public PointLight getPointLight(Model model) {
		return getPointLight(new Vector3f(), model);
	}
	
	public PointLight getPointLight(Vector3f position, Model model) {
		return getPointLight(position, model, new Vector4f(1,1,1,1), PointLight.DEFAULT_RANGE);
	}

	public PointLight getPointLight(Vector3f position, Model model, Vector4f colorIntensity) {
		return getPointLight(position, model, colorIntensity, PointLight.DEFAULT_RANGE);
	}

	public PointLight getPointLight() {
		return getPointLight(sphereModel);
	}
	public PointLight getPointLight(float range) {
		return getPointLight(new Vector3f(), sphereModel, new Vector4f(1,1,1,1), range);
	}

	public PointLight getPointLight(PointLightSerializationProxy proxy) {
		return getPointLight(proxy.getPosition(), sphereModel, proxy.getColor(), proxy.getRadius());
	}
	public PointLight getPointLight(Vector3f position, Model model, Vector4f colorIntensity, float range) {
		Material material = renderer.getMaterialFactory().getDefaultMaterial();
		
		PointLight light = new PointLight(world, renderer.getMaterialFactory(), position, model, colorIntensity, range, material.getName());
		pointLights.add(light);
		updatePointLightArrays();
		return light;
	}
	private void updatePointLightArrays() {
		float[] positions = new float[pointLightsForwardMaxCount*3];
		float[] colors = new float[pointLightsForwardMaxCount*3];
		float[] radiuses = new float[pointLightsForwardMaxCount];
		
		for(int i = 0; i < Math.min(pointLightsForwardMaxCount, pointLights.size()); i++) {
			PointLight light = pointLights.get(i);
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
		TubeLight tubeLight = new TubeLight(world, renderer.getMaterialFactory(), new Vector3f(), cubeModel, new Vector3f(1, 1, 1), length, radius);
		tubeLights.add(tubeLight);
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

	public AreaLight getAreaLight(AreaLightSerializationProxy proxy) {
		return getAreaLight(proxy.getPosition(), proxy.getOrientation(), proxy.getColor(), proxy.getWidth(), proxy.getHeight(), proxy.getRadius());
	}
	public AreaLight getAreaLight(Vector3f position, Quaternion orientation, Vector3f color, float width, float height, float range) {
		return getAreaLight(position, orientation, color, (int) width, (int) height, (int) range);
	}
	public AreaLight getAreaLight(Vector3f position, Quaternion orientation, Vector3f color, int width, int height, int range) {
		AreaLight areaLight = new AreaLight(world, renderer, position, cubeModel, color, new Vector3f(width, height, range));
		areaLight.setOrientation(orientation);
		areaLights.add(areaLight);
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

	public List<PointLight> getPointLights() {
		return pointLights;
	}
	public List<PointLightSerializationProxy> getPointLightProxies() {
		List<PointLightSerializationProxy> result = new ArrayList<>();
		for (PointLight pointLight : pointLights) {
			result.add(new PointLightSerializationProxy(pointLight));
		}
		return result;
	}
	public List<AreaLightSerializationProxy> getAreaLightProxies() {
		List<AreaLightSerializationProxy> result = new ArrayList<>();
		for (AreaLight areaLight : areaLights) {
			result.add(new AreaLightSerializationProxy(areaLight));
		}
		return result;
	}

	public void setPointLights(List<PointLight> pointLights) {
		this.pointLights = pointLights;
	}

	public List<TubeLight> getTubeLights() {
		return tubeLights;
	}

	public void setTubeLights(List<TubeLight> tubeLights) {
		this.tubeLights = tubeLights;
	}

	public List<AreaLight> getAreaLights() {
		return areaLights;
	}

	public void setAreaLights(List<AreaLight> areaLights) {
		this.areaLights = areaLights;
	}
	
	public void renderAreaLightShadowMaps(Octree octree) {
		GPUProfiler.start("Arealight shadowmaps");
		GL11.glDepthMask(true);
		GL11.glEnable(GL11.GL_DEPTH_TEST);
		GL11.glDisable(GL11.GL_CULL_FACE);
		renderTarget.use(true);
		
		for(int i = 0; i < Math.min(MAX_AREALIGHT_SHADOWMAPS, areaLights.size()); i++) {

			renderTarget.setTargetTexture(areaLightDepthMaps.get(i), 0);

			GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
			GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
			
			AreaLight light = areaLights.get(i);
			Camera camera = light;

			List<Entity> visibles = octree.getEntities();//getVisible(camera);

			areaShadowPassProgram.use();
			areaShadowPassProgram.setUniformAsMatrix4("viewMatrix", camera.getViewMatrixAsBuffer());
			areaShadowPassProgram.setUniformAsMatrix4("projectionMatrix", camera.getProjectionMatrixAsBuffer());
//			directionalShadowPassProgram.setUniform("near", camera.getNear());
//			directionalShadowPassProgram.setUniform("far", camera.getFar());
			
			for (Entity e : visibles) {
				e.getComponentOption(ModelComponent.class).ifPresent(modelComponent -> {
					areaShadowPassProgram.setUniformAsMatrix4("modelMatrix", e.getModelMatrixAsBuffer());
					modelComponent.getMaterial().setTexturesActive(areaShadowPassProgram);
					areaShadowPassProgram.setUniform("hasDiffuseMap", modelComponent.getMaterial().hasDiffuseMap());
					areaShadowPassProgram.setUniform("color", modelComponent.getMaterial().getDiffuse());

					modelComponent.getVertexBuffer().draw();
				});
			}
		}
		GPUProfiler.end();
	}
	
	public int getDepthMapForAreaLight(AreaLight light) {
		int index = areaLights.indexOf(light);
		if(index >= MAX_AREALIGHT_SHADOWMAPS) {return -1;}
		
		return areaLightDepthMaps.get(index);
	}

	public Camera getCameraForAreaLight(AreaLight light) {
		camera.setPosition(light.getPosition().negate(null));
//		camera.getOrientation().x = -light.getOrientation().x;
//		camera.getOrientation().y = -light.getOrientation().y;
//		camera.getOrientation().z = -light.getOrientation().z;
//		camera.getOrientation().w = -light.getOrientation().w;
//		camera.rotate(new Vector4f(0, 1, 0, 180)); // TODO: CHECK THIS SHIT UP
		//return camera.getComponent(CameraComponent.class).getCamera();
		return camera;
	}

	public FloatBuffer getShadowMatrixForAreaLight(AreaLight light) {
//		c.getOrientation().x = light.getOrientation().negate(null).x;
//		c.getOrientation().y = light.getOrientation().negate(null).y;
//		c.getOrientation().z = light.getOrientation().negate(null).z;
//		c.getOrientation().w = light.getOrientation().negate(null).w;
//		c.rotateWorld(new Vector4f(0, 1, 0, 180)); // TODO: CHECK THIS SHIT UP

//		Vector3f newPosition = new Vector3f(-light.getPosition().x, -light.getPosition().y, -light.getPosition().y);
//		newPosition = new Vector3f(0, 0, 10);
//		c.setPosition(newPosition);
//		c.rotateWorld(new Vector4f(0, 1, 0, 180)); // TODO: CHECK THIS SHIT UP
//		c.updateShadow();
		return light.getViewProjectionMatrixAsBuffer();
	}

	public void clearAll() {
		pointLights.clear();
		areaLights.clear();
		tubeLights.clear();
	}
}
