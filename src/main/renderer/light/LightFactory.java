package main.renderer.light;

import java.awt.Color;
import java.awt.Point;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;

import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;

import com.sun.javafx.geom.Vec4f;

import main.World;
import main.model.Model;
import main.renderer.DeferredRenderer;
import main.renderer.Renderer;
import main.renderer.material.Material;
import main.renderer.material.Material.MAP;

public class LightFactory {
	
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
	public PointLight getPointLight(Vector3f position, Model model, Vector4f colorIntensity, float range) {
		Material material = renderer.getMaterialFactory().getMaterial(new HashMap<MAP,String>(){{
    		put(MAP.DIFFUSE,"assets/textures/default.dds");
		}});
		
		PointLight light = new PointLight(renderer.getMaterialFactory(), position, model, colorIntensity, range, material.getName());
		DeferredRenderer.pointLights.add(light);
		return light;
	}

	public PointLight getPointLight() {
		return getPointLight(sphereModel);
	}

	public TubeLight getTubeLight() {
		return getTubeLight(200.0f, 50.0f);
	}
	public TubeLight getTubeLight(float length, float radius) {
		return new TubeLight(renderer.getMaterialFactory(), new Vector3f(), cubeModel, new Vector3f(1, 1, 1), length, radius);
	}
}
