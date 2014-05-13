package main;

import java.util.List;

import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;


public class PointLight extends Entity {
	
	public static float DEFAULT_RANGE = 1f;
	private static int counter = 0;
	private Vector4f color;
	
	public PointLight() {
		this(new Vector3f(0,0,0));
	}

	public PointLight(Vector3f position) {
		this(position, new Vector4f(0,0,0,0));
	}

	public PointLight(Vector3f position, Vector4f colorIntensity) {
		this(position, colorIntensity, DEFAULT_RANGE);
	}

	public PointLight(Vector3f position, Vector4f colorIntensity, float range) {
		setPosition(position);
		setColor(colorIntensity);
		setName();
		counter++;
		setScale(DEFAULT_RANGE);
	}
	
	public PointLight(Renderer renderer, Model model, Vector3f position) {
		super(renderer, model, position, new Material(renderer, "", "stone_diffuse.png"), false);
	}

	private void setName() {
		name = String.format("PointLight_%d", counter);
	}

	void setColor(Vector4f color) {
		this.color  = color;
	}

	public Vector4f getColor() {
		return color;
	}
	
	@Override
	public void destroy() {
	}

	@Override
	public Material getMaterial() {
		return null;
	}

	// TODO: IMPLEMENT
	@Override
	public Vector3f getPosition() {
		return position;
	}
	
	@Override
	public void setPosition(Vector3f position) {
		this.position = position;
	}
	
	public static float[] convert(List<PointLight> list) {
		final int elementsPerLight = 10;
		int elementCount = list.size() * elementsPerLight;
		float[] result = new float[elementCount];
		
		for(int i = 0; i < list.size(); i++) {
			PointLight light = list.get(i);
			result[i] = light.getPosition().x;
			result[i+1] = light.getPosition().y;
			result[i+2] = light.getPosition().z;
			
			result[i+3] = light.getScale().x;
			
			result[i+4] = light.getColor().x;
			result[i+5] = light.getColor().y;
			result[i+6] = light.getColor().z;
			
			result[i+7] = light.getColor().x;
			result[i+8] = light.getColor().y;
			result[i+9] = light.getColor().z;
		}
		return result;
	}
}
