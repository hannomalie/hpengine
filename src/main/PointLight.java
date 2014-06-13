package main;

import java.util.List;

import main.shader.Program;
import main.util.Util;

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
		super(renderer, model, position, new Material(renderer, "", "default.dds"), false);
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

	@Override
	public Vector3f getPosition() {
		return position;
	}
	
	@Override
	public void setPosition(Vector3f position) {
		this.position = position;
	}
	
	public void drawAsMesh(Renderer renderer, Camera camera) {
		Matrix4f tempModel = calculateCurrentModelMatrixWithLowerScale(); 
		tempModel.store(matrix44Buffer);
		matrix44Buffer.flip();
		super.draw(renderer, camera);
		modelMatrix.store(matrix44Buffer);
		matrix44Buffer.flip();
	}

	public void draw(Renderer renderer, Program program) {
		program.setUniformAsMatrix4("modelMatrix", matrix44Buffer);
		vertexBuffer.draw();
	}
	
	public void drawAgain(Renderer renderer, Program program) {
		program.setUniformAsMatrix4("modelMatrix", matrix44Buffer);
		vertexBuffer.drawAgain();
	}

	private Matrix4f calculateCurrentModelMatrixWithLowerScale() {
		Matrix4f temp = new Matrix4f();
		Matrix4f.translate(position, temp, temp);
		Matrix4f.mul(Util.toMatrix(getOrientation()), temp, temp);
		Matrix4f.scale(new Vector3f(0.2f, 0.2f, 0.2f), temp, temp);
		return temp;
	}
	
	public float getRadius() {
		return scale.x;
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

	@Override
	public boolean isInFrustum(Camera camera) {
		Vector4f[] minMaxWorld = getMinMaxWorld();
		Vector4f minWorld = minMaxWorld[0];
		Vector4f maxWorld = minMaxWorld[1];
		
		Vector3f centerWorld = new Vector3f();
		centerWorld.x = (maxWorld.x + minWorld.x)/2;
		centerWorld.y = (maxWorld.y + minWorld.y)/2;
		centerWorld.z = (maxWorld.z + minWorld.z)/2;
		
		Vector3f distVector = new Vector3f();
		Vector3f.sub(new Vector3f(maxWorld.x, maxWorld.y, maxWorld.z),
						new Vector3f(minWorld.x, minWorld.y, minWorld.z), distVector);

		//if (camera.getFrustum().sphereInFrustum(centerWorld.x, centerWorld.y, centerWorld.z, getRadius())) {
		if (camera.getFrustum().cubeInFrustum(centerWorld.x, centerWorld.y, centerWorld.z, getRadius())) {
			return true;
		}
		return false;
	}
}
