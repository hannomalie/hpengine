package engine.model;

import camera.Camera;
import component.Component;
import component.ModelComponent;
import component.PhysicsComponent;
import engine.AppContext;
import engine.Transform;
import engine.lifecycle.LifeCycle;
import event.UpdateChangedEvent;
import org.apache.commons.io.FilenameUtils;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;
import renderer.material.MaterialFactory;
import shader.Bufferable;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.FloatBuffer;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class Entity implements Transformable, LifeCycle, Serializable, Bufferable {
	private static final long serialVersionUID = 1;
	public static int count = 0;

    public List<Transform> getInstances() {
        return instances;
    }

    public enum Update {
		STATIC,
		DYNAMIC
	}

	private Update update = Update.STATIC;

	private engine.Transform transform = new engine.Transform();

    private List<Transform> instances = new CopyOnWriteArrayList<>();

	protected transient AppContext appContext;

	protected boolean initialized = false;

	protected String name = "Entity_" + System.currentTimeMillis();

	private Entity parent = null;
	private List<Entity> children = new ArrayList<>();

	private boolean selected = false;
	private boolean visible = true;
	
	public Map<String, Component> components = new HashMap<>();

	protected Entity() { }

	protected Entity(Model model, String materialName) {
		this(new Vector3f(0, 0, 0), model.getName(), model, materialName);
	}

	protected Entity(Vector3f position, String name, Model model, String materialName) {
		transform.init();
		addComponent(new ModelComponent(model, materialName));
		this.name = name;
		transform.setPosition(position);
	}

	@Override
	public void init() {
		LifeCycle.super.init();
		transform.init();

		for(Component component : components.values()) {
			component.init();
		}
		for(Entity child: children) {
			child.init();
		}
		initialized = true;
	}

	public Entity addComponent(Component component) {
		return addComponent(component, component.getIdentifier());
	}
	public Entity addComponent(Component component , String key) {
		component.setEntity(this);
		getComponents().put(key, component);
		component.initAfterAdd(this);
		return this;
	}

	public void removeComponent(Component component) {
		getComponents().remove(component.getIdentifier(), component);
	}
	public void removeComponent(String key) {
		getComponents().remove(key);
	}

	public <T extends Component> T getComponent(Class<T> type) {
		Component component = getComponents().get(type.getSimpleName().toString());
		return type.cast(component);
	}
	public <T extends Component> T getComponent(Class<T> type, String key) {
		Component component = getComponents().get(key);
		return type.cast(component);
	}

	public <T extends Component> Optional<T> getComponentOption(Class<T> type) {
		Component component = getComponents().get(type.getSimpleName().toString());
		return Optional.ofNullable(type.cast(component));
	}

	private boolean hasComponent(Class<? extends Component> type) {
		return getComponents().keySet().contains(type.getSimpleName().toString());
	}

	public List<Entity> getAllChildrenAndSelf() {
		List<Entity> allChildrenAndSelf = new ArrayList<>();
		allChildrenAndSelf.add(this);
		if(hasChildren()) {
			for (Entity child: children) {
				allChildrenAndSelf.addAll(child.getAllChildrenAndSelf());
			}
		}
		return allChildrenAndSelf;
	}

	public Entity getParent() {
		return parent;
	}

	public boolean hasParent() {
		return parent != null;
	}

	public boolean hasChildren() {
		return !children.isEmpty();
	}

	public List<Entity> getChildren() {
		return children;
	}

	public void removeParent() {
		parent.getChildren().remove(this);
		parent = null;
	}
	public void setParent(Entity parent) {
		this.parent = parent;
		parent.addChild(this);
		getTransform().setParent(parent.getTransform());
		getTransform().recalculate();
	}

	private Entity addChild(Entity child) {
		if(!children.contains(child) && !(this.getParent() == null && this.getParent() == child)) {
			children.add(child);
		}
		return child;
	}

	private void removeChild(Entity entity) {
		children.remove(entity);
	}

	@Override
	public void update(float seconds) {
		for (Component c : components.values()) {
            if(!c.isInitialized()) { continue; }
			c.update(seconds);
		}
//        Iterator<Entity> childrenIterator = getChildren().iterator();
//        while(childrenIterator.hasNext()) {
//            Entity current = childrenIterator.next();
//            current.update(seconds);
//        }

		for (Entity child: getChildren()) {
			child.update(seconds);
		}
	}

	public void setAppContext(AppContext appContext) {
		this.appContext = appContext;
	}

    public Map<String,Component> getComponents() {
		return components;
	}

	public FloatBuffer getModelMatrixAsBuffer() {
		return transform.getTransformationBuffer();
	}

	public FloatBuffer getViewMatrixAsBuffer() {
		return getViewMatrixAsBuffer(true);
	}
	public FloatBuffer getViewMatrixAsBuffer(boolean recalculateBefore) {
		return transform.getTranslationRotationBuffer(recalculateBefore);
	}

	public void setTransform(Transform transform) {
		this.transform = transform;
	}
	public Transform getTransform() {
		return transform;
	}

	public String getName() {
		return name;
	}
	public void setName(String string) {
		this.name = string;
	}

	public boolean isInFrustum(Camera camera) {
		Vector4f[] minMaxWorld = getMinMaxWorld();
		Vector4f minWorld = minMaxWorld[0];
		Vector4f maxWorld = minMaxWorld[1];

		Vector3f centerWorld = getCenterWorld();
		
		Vector3f distVector = new Vector3f();
		Vector3f.sub(new Vector3f(maxWorld.x, maxWorld.y, maxWorld.z),
						new Vector3f(minWorld.x, minWorld.y, minWorld.z), distVector);

//		if (camera.getFrustum().pointInFrustum(minWorld.x, minWorld.y, minWorld.z) ||
//			camera.getFrustum().pointInFrustum(maxWorld.x, maxWorld.y, maxWorld.z)) {
//		if (camera.getFrustum().cubeInFrustum(cubeCenterX, cubeCenterY, cubeCenterZ, size)) {
//		if (camera.getFrustum().pointInFrustum(minView.x, minView.y, minView.z)
//				|| camera.getFrustum().pointInFrustum(maxView.x, maxView.y, maxView.z)) {
		if (camera.getFrustum().sphereInFrustum(centerWorld.x, centerWorld.y, centerWorld.z, distVector.length()/2)) {
			return true;
		}
		return false;
	}

	private transient Vector3f centerWorld = null;
	public Vector3f getCenterWorld() {
		if(centerWorld != null) {
			return centerWorld;
		}
		Vector4f[] minMaxWorld = getMinMaxWorld();
		Vector4f minWorld = minMaxWorld[0];
		Vector4f maxWorld = minMaxWorld[1];

		centerWorld = new Vector3f();
		centerWorld.x = minWorld.x + (maxWorld.x - minWorld.x)/2;
		centerWorld.y = minWorld.y + (maxWorld.y - minWorld.y)/2;
		centerWorld.z = minWorld.z + (maxWorld.z - minWorld.z)/2;
		return centerWorld;
	}

	public boolean isVisible() {
		return visible;
	}
	public void setVisible(boolean visible) {
		this.visible = visible;
	}

    Vector4f[] minMax;
    Matrix4f lastUsedTransformationMatrix;
	public Vector4f[] getMinMaxWorld() {
        if(!getTransform().isDirty() && minMax != null) {
            if(lastUsedTransformationMatrix == null || getTransform().getTransformation().equals(lastUsedTransformationMatrix)) {
                return minMax;
            }
        }
        centerWorld = null;
        if(hasComponent(ModelComponent.class) || getComponents().containsKey("ModelComponent")) {
			ModelComponent modelComponent = getComponent(ModelComponent.class);
			minMax = modelComponent.getMinMax();

			Vector4f minWorld = new Vector4f(0,0,0,1);
			Vector4f maxWorld = new Vector4f(0,0,0,1);

			Matrix4f modelMatrix = getModelMatrix();

			Matrix4f.transform(modelMatrix, minMax[0], minWorld);
			Matrix4f.transform(modelMatrix, minMax[1], maxWorld);

			minWorld.w = 0;
			maxWorld.w = 0;
			minMax = new Vector4f[] {minWorld, maxWorld};

			return minMax;
		} else {
			minMax = new Vector4f[2];
			float amount = 5;
			Vector4f vectorMin = new Vector4f(getPosition().x-amount, getPosition().y-amount, getPosition().z-amount, 1);
			Vector4f vectorMax = new Vector4f(getPosition().x+amount, getPosition().y+amount, getPosition().z+amount, 1);
			minMax[0] = vectorMin;
			minMax[1] = vectorMax;
		}
        lastUsedTransformationMatrix = new Matrix4f(getTransform().getTransformation());

//		if(hasChildren()) {
//			List<Vector4f[]> minMaxFromChildren = new ArrayList<>();
//			for (Entity child : children) {
//				minMaxFromChildren.add(child.getMinMaxWorld());
//			}
//			Util.getOverallMinMax(minMax, minMaxFromChildren);
//		}

		return minMax;
	}


	public Vector3f[] getMinMaxWorldVec3() {
		Vector4f[] asVec4 = getMinMaxWorld();
		Vector3f[] result = {new Vector3f(asVec4[0].x, asVec4[0].y, asVec4[0].z),
							new Vector3f(asVec4[1].x, asVec4[1].y, asVec4[1].z)};
		return result;
	}

	public boolean isSelected() {
		return selected;
	}
	public void setSelected(boolean selected) {
		this.selected = selected;
	}
	
	@Override
	public String toString() {
		return name;
	}
	
	public static boolean write(Entity entity, String resourceName) {
		String fileName = FilenameUtils.getBaseName(resourceName);
		FileOutputStream fos = null;
		ObjectOutputStream out = null;

		try {
			fos = new FileOutputStream(getDirectory() + fileName + ".hpentity");
			out = new ObjectOutputStream(fos);
			out.writeObject(entity);

		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				out.close();
				fos.close();
				return true;
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return false;
	}

	public static String getDirectory() {
		return AppContext.WORKDIR_NAME + "/assets/entities/";
	}
	public boolean equals(Object other) {
		if (!(other instanceof Entity)) {
			return false;
		}
		
		Entity b = (Entity) other;
		
		return b.getName().equals(getName());
	}

	@Override
	public int hashCode() {
		return getName().hashCode();
	}

	public void destroy() {

//		// Select the VAO
//		GL30.glBindVertexArray(vaoId);
//		
//		// Disable the VBO index from the VAO attributes list
//		GL20.glDisableVertexAttribArray(0);
//		GL20.glDisableVertexAttribArray(1);
//		
//		// Delete the vertex VBO
//		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
//		GL15.glDeleteBuffers(vboId);
//		
//		// Delete the index VBO
//		GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
//		GL15.glDeleteBuffers(vboiId);
//		
//		// Delete the VAO
//		GL30.glBindVertexArray(0);
//		GL30.glDeleteVertexArrays(vaoId);
//		
	}

	@Override
	public boolean isInitialized() {
		return initialized;
	}

	public void setHasMoved(boolean value) {
        transform.setHasMoved(value);
        for(Transform inst : instances) {
            inst.setHasMoved(value);
        }
    }

	public boolean hasMoved() {
		return transform.isHasMoved() || instances.stream().anyMatch(inst -> inst.isHasMoved());
	}

	public Update getUpdate() {
        if(hasComponent(PhysicsComponent.class) && getComponent(PhysicsComponent.class).isDynamic()) {
            return Update.DYNAMIC;
        }
		return update;
	}

	public void setUpdate(Update update) {
		this.update = update;
		if (hasChildren()) {
			for (Entity child : children) {
				child.setUpdate(update);
			}
		}
		AppContext.getEventBus().post(new UpdateChangedEvent(this));
	}


    @Override
    public int getElementsPerObject() {
        return getInstanceCount() * (16+4);
    }


    @Override
    public double[] get() {

        double[] doubles = new double[getElementsPerObject()];

        int index = 0;
        {
            Matrix4f mm = getModelMatrix();
            index = getValues(doubles, index, mm);
        }

        for(Transform instanceTransform : instances) {
            Matrix4f instanceMatrix = instanceTransform.getTransformation();
            index = getValues(doubles, index, instanceMatrix);
        }
        if(hasParent()) {
            for(Transform instanceTransform : getParent().getInstances()) {
                Matrix4f instanceMatrix = instanceTransform.getTransformation();
                index = getValues(doubles, index, instanceMatrix);
            }
        }
        return doubles;
    }

    private int getValues(double[] doubles, int index, Matrix4f mm) {
        doubles[index++] = mm.m00;
        doubles[index++] = mm.m01;
        doubles[index++] = mm.m02;
        doubles[index++] = mm.m03;
        doubles[index++] = mm.m10;
        doubles[index++] = mm.m11;
        doubles[index++] = mm.m12;
        doubles[index++] = mm.m13;
        doubles[index++] = mm.m20;
        doubles[index++] = mm.m21;
        doubles[index++] = mm.m22;
        doubles[index++] = mm.m23;
        doubles[index++] = mm.m30;
        doubles[index++] = mm.m31;
        doubles[index++] = mm.m32;
        doubles[index++] = mm.m33;
        doubles[index++] = isSelected() ? 1d : 0d;
        double materialIndex = getComponents().containsKey("ModelComponent") ?
                MaterialFactory.getInstance().indexOf(ModelComponent.class.cast(getComponents().get("ModelComponent")).getMaterial()) : 0;
        doubles[index++] = materialIndex;
        doubles[index++] = AppContext.getInstance().getScene().getEntityIndexOf(this);
        doubles[index++] = 0;
        return index;
    }

    public int getInstanceCount() {
        int instancesCount = instances.size() + 1;

        if(hasParent() && getParent().getInstanceCount() > 1) {
            instancesCount *= getParent().getInstanceCount();
        }
        return instancesCount;
    }

    public void addInstance(Transform instance) {
        if(getParent() != null) {
            instance.setParent(getParent().getTransform());
        }
        instances.add(instance);
    }
}
