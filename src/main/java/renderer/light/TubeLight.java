package renderer.light;

import java.util.List;

import camera.Camera;
import component.ModelComponent;
import engine.model.Entity;
import engine.model.Model;
import org.lwjgl.util.vector.Matrix;
import renderer.Renderer;
import renderer.material.MaterialFactory;
import shader.Program;
import util.Util;

import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;


public class TubeLight extends Entity {
	
	public static float DEFAULT_RANGE = 1f;
	private Vector3f color;

	protected TubeLight(MaterialFactory materialFactory, Vector3f position, Model model, Vector3f colorIntensity, float length, float radius, String materialName) {
		super(position, generateName(), model, materialName);
		setColor(colorIntensity);
		setScale(new Vector3f(length, 2*radius, 2*radius)); // box has half extends = 0.5, so scale has not to be half range but range...m�h
		init();
	}
	public TubeLight(MaterialFactory materialFactory, Vector3f position, Model model, Vector3f color, float length, float radius) {
		super(position, generateName(), model, model.getMaterial().getName());
		setColor(color);
		setScale(new Vector3f(length, 2*radius, 2*radius)); // box has half extends = 0.5, so scale has not to be half range but range...m�h
		init();
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
		getComponentOption(ModelComponent.class).ifPresent(modelComponent -> {
			program.setUniformAsMatrix4("modelMatrix", getTransform().getTransformationBuffer());
			modelComponent.getVertexBuffer().draw();
		});
	}

	Matrix4f tempOrientationMatrix = new Matrix4f();
    private Matrix4f calculateCurrentModelMatrixWithLowerScale() {
		Matrix4f temp = new Matrix4f();
		Matrix4f.translate(getPosition(), temp, temp);
		Matrix4f.mul(Util.toMatrix(getOrientation(), tempOrientationMatrix), temp, temp);
		Matrix4f.scale(new Vector3f(0.2f, 0.2f, 0.2f), temp, temp);
		return temp;
	}
	
	public float getRadius() {
		return getTransform().getScale().y/2;
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
		return Vector3f.sub(getPosition(), (Vector3f) getRightDirection().scale(getOffset()), null);
	}
	public Vector3f getEnd() {
		return Vector3f.add(getPosition(), (Vector3f) getRightDirection().scale(getOffset()), null);
	}
	public Vector3f getOuterLeft() {
		return Vector3f.sub(getPosition(), (Vector3f) getRightDirection().scale((getLength()/2)), null);
	}
	public Vector3f getOuterRight() {
		return Vector3f.add(getPosition(), (Vector3f) getRightDirection().scale((getLength()/2)), null);
	}

	public float getLength() {
		return getScale().x;
	}
	public float setLength(float length) {
		return getScale().x = length;
	}
}
