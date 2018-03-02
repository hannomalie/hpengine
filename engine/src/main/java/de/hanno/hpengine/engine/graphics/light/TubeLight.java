package de.hanno.hpengine.engine.graphics.light;

import java.util.List;

import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.camera.Camera;
import de.hanno.hpengine.engine.entity.Entity;
import de.hanno.hpengine.engine.model.StaticModel;
import de.hanno.hpengine.engine.graphics.shader.Program;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;


public class TubeLight extends Entity {
	
	public static float DEFAULT_RANGE = 1f;
	private Vector3f color;

	protected TubeLight(Vector3f position, StaticModel model, Vector3f colorIntensity, float length, float radius, String materialName) {
		super(generateName(), position);
		setColor(colorIntensity);
		scale(new Vector3f(length, 2*radius, 2*radius)); // box has half extends = 0.5, so scale has not to be half range but range...m�h
	}
	public TubeLight(Vector3f position, StaticModel model, Vector3f color, float length, float radius) {
		super(generateName(), position);
		setColor(color);
		scale(new Vector3f(length, 2*radius, 2*radius)); // box has half extends = 0.5, so scale has not to be half range but range...m�h
	}
	
	private static String generateName() {
		return String.format("TubeLight_%d", System.currentTimeMillis());
	}

	public void setColor(Vector3f color) {
		this.color  = color;
	}

	public void setColor(Vector4f color) {
		this.color  = new Vector3f(color.x, color.y, color.z);
	}
	public Vector3f getColor() {
		return color;
	}

	@Override
	public void destroy() {
	}
	
	@Override
	public void update(float seconds) {
	}

    public void draw(Program program) {
        throw new IllegalStateException("Currently not implemented");
//		getComponentOption(ModelComponent.class).ifPresent(modelComponent -> {
//			program.setUniformAsMatrix4("modelMatrix", getTransform().getTransformationBuffer());
//			modelComponent.getVertexBuffer().draw();
//		});
	}

	Matrix4f tempOrientationMatrix = new Matrix4f();
//    private Matrix4f calculateCurrentModelMatrixWithLowerScale() {
//		Matrix4f temp = new Matrix4f();
//		Matrix4f.translate(getPosition(), temp, temp);
//		Matrix4f.mul(Util.toMatrix(getOrientation(), tempOrientationMatrix), temp, temp);
//		Matrix4f.scale(new Vector3f(0.2f, 0.2f, 0.2f), temp, temp);
//		return temp;
//	}
	
	public float getRadius() {
        return this.getScale().y/2;
	}
	
	public static float[] convert(List<TubeLight> list) {
		final int elementsPerLight = 10;
		int elementCount = list.size() * elementsPerLight;
		float[] result = new float[elementCount];
		
		for(int i = 0; i < list.size(); i++) {
			TubeLight light = list.get(i);
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

	@Override
	public boolean isInFrustum(Camera camera) {
		if (camera.getFrustum().sphereInFrustum(getPosition().x, getPosition().y, getPosition().z, getLength()/2)) {
			return true;
		}
		return false;
	}
	
	private float getOffset() {
		return (getLength()/2) - getRadius();
	}
	public Vector3f getStart() {
		return new Vector3f(getPosition()).sub(new Vector3f(getRightDirection()).mul(getOffset()));
	}
	public Vector3f getEnd() {
		return new Vector3f(getPosition()).add( new Vector3f(getRightDirection()).mul(getOffset()));
	}
	public Vector3f getOuterLeft() {
		return new Vector3f(getPosition()).sub(new Vector3f(getRightDirection()).mul((getLength()/2)));
	}
	public Vector3f getOuterRight() {
		return new Vector3f(getPosition()).add(new Vector3f(getRightDirection()).mul((getLength()/2)));
	}

	public float getLength() {
		return getScale().x;
	}
	public float setLength(float length) {
		return getScale().x = length;
	}
}
