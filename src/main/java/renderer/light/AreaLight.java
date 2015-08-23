package renderer.light;

import camera.Camera;
import camera.Frustum;
import component.ModelComponent;
import engine.World;
import engine.model.Entity;
import engine.model.Model;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;
import renderer.Renderer;
import renderer.rendertarget.RenderTarget;
import shader.Program;
import util.Util;

import java.nio.FloatBuffer;
import java.util.List;


public class AreaLight extends Camera {
	
	public static float DEFAULT_RANGE = 1f;
	private static int counter = 0;
	private Vector3f color;
	transient private RenderTarget renderTarget;

	protected AreaLight(World world, Renderer renderer, Vector3f position, Model model, Vector3f color, Vector3f scale) {
		super(world, renderer.getMaterialFactory(), position, generateName(), model, model.getMaterial().getName());
        projectionMatrix = Util.createPerpective(getFov(), getRatio(), getNear(), getFar());
        frustum = new Frustum(this);
		setColor(color);
		setScale(scale);
		setNear(0.1f);
        setFar(500f);
        setFov(60f);
        setRatio(1);
		init(world);
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

	public void drawAsMesh(Camera camera) {
		getComponentOption(ModelComponent.class).ifPresent(component -> {
			component.draw(camera, getTransform().getTransformationBuffer(), 0);
		});
	}

	public void draw(Camera camera, Program program) {
//		program.setUniformAsMatrix4("modelMatrix", getTransform().getTransformationBuffer());
		getComponentOption(ModelComponent.class).ifPresent(component -> {
			component.draw(camera, getTransform().getTransformationBuffer(), 0);
		});
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
