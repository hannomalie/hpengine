package renderer.light;

import camera.Camera;
import camera.Frustum;
import component.ModelComponent;
import engine.model.Entity;
import engine.model.EntityFactory;
import engine.model.Model;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;
import renderer.RenderExtract;
import renderer.material.MaterialFactory;
import renderer.rendertarget.RenderTarget;
import shader.Program;
import util.Util;

import java.util.List;


public class AreaLight extends Camera {
	
	public static float DEFAULT_RANGE = 1f;
	private Vector3f color;
	transient private RenderTarget renderTarget;

	protected AreaLight(Vector3f position, Model model, Vector3f color, Vector3f scale) {
		super(MaterialFactory.getInstance(), position, generateName(), model, model.getMaterial().getName());
        projectionMatrix = Util.createPerpective(getFov(), getRatio(), getNear(), getFar());
        frustum = new Frustum(this);
		setColor(color);
		setScale(scale);
		setNear(1f);
        setFar(5000f);
        setFov(180f);
        setRatio(1);
        Entity plane = EntityFactory.getInstance().getEntity(model);
        plane.setPosition(new Vector3f(0, 0, -getNear()));
        plane.setParent(this);
		init();
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
		return String.format("AreaLight_%d", System.currentTimeMillis());
	}

	public void drawAsMesh(Camera camera, RenderExtract extract) {
		getComponentOption(ModelComponent.class).ifPresent(component -> {
			component.draw(extract, camera, getTransform().getTransformationBuffer(), 0);
		});
        for(Entity entity : getChildren()) {
            entity.getComponentOption(ModelComponent.class).ifPresent(component ->{
                component.draw(extract, camera);
            });
        }
	}

	public void draw(Camera camera, RenderExtract extract) {
		getComponentOption(ModelComponent.class).ifPresent(component -> {
			component.draw(extract, camera, getTransform().getTransformationBuffer(), 0);
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
