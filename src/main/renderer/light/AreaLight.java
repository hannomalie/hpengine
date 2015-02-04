package main.renderer.light;

import java.util.List;

import main.camera.Camera;
import main.model.Entity;
import main.model.Model;
import main.renderer.Renderer;
import main.renderer.material.Material;
import main.renderer.material.MaterialFactory;
import main.renderer.rendertarget.RenderTarget;
import main.shader.Program;
import main.util.Util;

import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;


public class AreaLight extends Entity {
	
	public static float DEFAULT_RANGE = 1f;
	private static int counter = 0;
	private Vector3f color;
	private RenderTarget renderTarget;
	
	protected AreaLight(Renderer renderer, Vector3f position, Model model, Vector3f color, Vector3f scale) {
		super(renderer.getMaterialFactory(), position, generateName(), model, model.getMaterial().getName());
		setColor(color);
		setScale(scale);
		counter++;
	}
	
	public void setColor(Vector3f color) {
		this.color = color;
	}

	public void setColor(Vector4f color) {
		this.color  = new Vector3f(color.x, color.y, color.z);
	}
	public Vector3f getColor() {
		return color;
	}
	private static String generateName() {
		return String.format("AreaLight_%d", counter);
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
	
	public static float[] convert(List<AreaLight> list) {
		final int elementsPerLight = 10;
		int elementCount = list.size() * elementsPerLight;
		float[] result = new float[elementCount];
		
		for(int i = 0; i < list.size(); i++) {
			AreaLight light = list.get(i);
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

	public float getRange() {
		return getScale().z;
	}

	public void setRange(float range) {
		getScale().z = range;
	}
	
	@Override
	public boolean isInFrustum(Camera camera) {
		if (camera.getFrustum().sphereInFrustum(getPosition().x, getPosition().y, getPosition().z,getRange()*2)) {
			return true;
		}
		return false;
	}

	public float getWidth() {
		return (getScale().x);
	}
	public float getHeight() {
		return (getScale().y);
	}
}
