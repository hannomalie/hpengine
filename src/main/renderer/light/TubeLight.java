package main.renderer.light;

import java.util.List;

import main.camera.Camera;
import main.model.Entity;
import main.model.Model;
import main.renderer.Renderer;
import main.renderer.material.Material;
import main.renderer.material.MaterialFactory;
import main.shader.Program;
import main.util.Util;

import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;


public class TubeLight extends Entity {
	
	public static float DEFAULT_RANGE = 1f;
	private static int counter = 0;
	private Vector3f color;

	protected TubeLight(MaterialFactory materialFactory, Vector3f position, Model model, Vector3f colorIntensity, float range, String materialName) {
		super(materialFactory, position, generateName(), model, materialName);
		setColor(colorIntensity);
		counter++;
		setScale(range);
	}
	public TubeLight(MaterialFactory materialFactory, Vector3f position, Model model, Vector3f color, float length, float radius) {
		super(materialFactory, position, generateName(), model, model.getMaterial().getName());
		setColor(color);
		setScale(new Vector3f(length, 2*radius, 2*radius)); // box has half extends = 0.5, so scale has not to be half range but range...m�h
	}
	
	private static String generateName() {
		return String.format("PointLight_%d", counter);
	}

	public void setColor(Vector3f color) {
		this.color  = color;
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

	@Override
	public Material getMaterial() {
		return materialFactory.getDefaultMaterial();
	}

	public void drawAsMesh(Renderer renderer, Camera camera) {
		Matrix4f tempModel = calculateCurrentModelMatrix(); 
		tempModel.store(matrix44Buffer);
		matrix44Buffer.flip();
		super.draw(renderer, camera);
		modelMatrix.store(matrix44Buffer);
		matrix44Buffer.flip();
	}

	public void draw(Renderer renderer, Program program) {
		calculateCurrentModelMatrix();
		modelMatrix.store(matrix44Buffer);
		matrix44Buffer.flip();
		program.setUniformAsMatrix4("modelMatrix", matrix44Buffer);
		vertexBuffer.draw();
	}
	
	public void drawAgain(Renderer renderer, Program program) {
		calculateCurrentModelMatrix();
		modelMatrix.store(matrix44Buffer);
		matrix44Buffer.flip();
		program.setUniformAsMatrix4("modelMatrix", matrix44Buffer);
		vertexBuffer.drawAgain();
	}

	private Matrix4f calculateCurrentModelMatrixWithLowerScale() {
		Matrix4f temp = new Matrix4f();
		Matrix4f.translate(getPosition(), temp, temp);
		Matrix4f.mul(Util.toMatrix(getOrientation()), temp, temp);
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
		if (camera.getFrustum().sphereInFrustum(getPosition().x, getPosition().y, getPosition().z, getRadius())) {
//		if (camera.getFrustum().cubeInFrustum(centerWorld.x, centerWorld.y, centerWorld.z, getRadius())) {
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