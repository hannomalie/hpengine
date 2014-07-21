package main.renderer.light;

import java.awt.Color;
import java.awt.Point;
import java.util.HashMap;

import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;

import com.sun.javafx.geom.Vec4f;

import main.model.Model;
import main.renderer.Renderer;
import main.renderer.material.Material;
import main.renderer.material.Material.MAP;

public class LightFactory {
	
	private Renderer renderer;

	public LightFactory(Renderer renderer) {
		this.renderer = renderer;
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
		return light;
	}
}
