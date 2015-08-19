package renderer.light;

import java.io.Serializable;
import java.util.List;

import camera.Camera;
import component.ModelComponent;
import engine.World;
import engine.model.Entity;
import engine.model.Model;
import renderer.material.MaterialFactory;
import shader.Program;
import util.Util;

import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;


public class PointLight extends Entity implements Serializable {
	private static final long serialVersionUID = 1L;
	
	public static float DEFAULT_RANGE = 1f;
	private static int counter = 0;
	private Vector4f color;
	
	protected PointLight(World world, MaterialFactory materialFactory, Vector3f position, Model model, Vector4f colorIntensity, float range, String materialName) {
		super(world, materialFactory, position, generateName(), model, materialName);
		setColor(colorIntensity);
		counter++;
		setScale(range);
		init(world);
	}
	
	private static String generateName() {
		return String.format("PointLight_%d", counter);
	}

	public void setColor(Vector4f color) {
		this.color  = color;
	}

	public Vector4f getColor() {
		return color;
	}

	public void drawAsMesh(Camera camera) {
		getComponentOption(ModelComponent.class).ifPresent(modelComponent -> {
			modelComponent.draw(camera, getTransform().getTransformationBuffer(), 0);
		});
	}

	public void draw(Program program) {
		getComponentOption(ModelComponent.class).ifPresent(modelComponent -> {
			program.setUniformAsMatrix4("modelMatrix", getTransform().getTransformationBuffer());
			modelComponent.getVertexBuffer().draw();
		});
	}

	public void drawAgain(Program program) {
		getComponentOption(ModelComponent.class).ifPresent(modelComponent -> {
			program.setUniformAsMatrix4("modelMatrix", getTransform().getTransformationBuffer());
			modelComponent.getVertexBuffer().drawAgain();
		});
	}

	private Matrix4f calculateCurrentModelMatrixWithLowerScale() {
		Matrix4f temp = new Matrix4f();
		Matrix4f.translate(getPosition(), temp, temp);
		Matrix4f.mul(Util.toMatrix(getOrientation()), temp, temp);
		Matrix4f.scale(new Vector3f(0.2f, 0.2f, 0.2f), temp, temp);
		return temp;
	}
	
	public float getRadius() {
		return getTransform().getScale().x;
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
		if (camera.getFrustum().sphereInFrustum(getPosition().x, getPosition().y, getPosition().z, getRadius())) {
//		if (camera.getFrustum().cubeInFrustum(centerWorld.x, centerWorld.y, centerWorld.z, getRadius())) {
			return true;
		}
		return false;
	}
	
	private Object writeReplace() {
		return new PointLightSerializationProxy(this);
	}
}
