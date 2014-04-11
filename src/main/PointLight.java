package main;

import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;


public class PointLight implements IEntity {
	
	public static float DEFAULT_RANGE = 5;
	private static int counter = 0;
	private Vector4f color;
	private float range;
	private Vector3f position;
	private String name;
	
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
		setRange(range);
		setName();
		counter++;
	}
	
	private void setName() {
		name = String.format("PointLight_%d", counter);
	}

	private void setRange(float range) {
		this.range = range;
	}

	private void setColor(Vector4f color) {
		this.color  = color;
	}

	public Vector4f getColor() {
		return color;
	}

	public float getRange() {
		return range;
	}
	
	@Override
	public void destroy() {
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public Material getMaterial() {
		return null;
	}

	@Override
	public Vector3f getPosition() {
		return position;
	}
	
	@Override
	public void setPosition(Vector3f position) {
		this.position = position;
	}

}
