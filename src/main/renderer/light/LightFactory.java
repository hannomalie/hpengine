package main.renderer.light;

import java.io.File;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import main.World;
import main.model.Model;
import main.renderer.Renderer;
import main.renderer.material.Material;
import main.renderer.material.Material.MAP;

import org.lwjgl.BufferUtils;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;

public class LightFactory {
	

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
	
	private Renderer renderer;
	private Model sphereModel;
	private Model cubeModel;
	
	public LightFactory(Renderer renderer) {
		this.renderer = renderer;
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
	public PointLight getPointLight(Vector3f position, Model model, Vector4f colorIntensity, float range) {
		Material material = renderer.getMaterialFactory().getMaterial(new HashMap<MAP,String>(){{
    		put(MAP.DIFFUSE,"assets/textures/default.dds");
		}});
		
		PointLight light = new PointLight(renderer.getMaterialFactory(), position, model, colorIntensity, range, material.getName());
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
		TubeLight tubeLight = new TubeLight(renderer.getMaterialFactory(), new Vector3f(), cubeModel, new Vector3f(1, 1, 1), length, radius);
		tubeLights.add(tubeLight);
		return tubeLight;
	}

	public AreaLight getAreaLight(int width, int height, int range) {
		AreaLight areaLight = new AreaLight(renderer.getMaterialFactory(), new Vector3f(), cubeModel, new Vector3f(1, 1, 1), new Vector3f(width, height, range));
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

}
