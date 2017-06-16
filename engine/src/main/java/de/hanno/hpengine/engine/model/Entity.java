package de.hanno.hpengine.engine.model;

import de.hanno.hpengine.engine.camera.Camera;
import de.hanno.hpengine.engine.component.Component;
import de.hanno.hpengine.engine.component.ModelComponent;
import de.hanno.hpengine.engine.component.PhysicsComponent;
import de.hanno.hpengine.engine.DirectoryManager;
import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.Transform;
import de.hanno.hpengine.engine.lifecycle.LifeCycle;
import de.hanno.hpengine.engine.event.EntityAddedEvent;
import de.hanno.hpengine.engine.event.UpdateChangedEvent;
import de.hanno.hpengine.engine.model.material.Material;
import de.hanno.hpengine.engine.model.material.MaterialFactory;
import de.hanno.hpengine.engine.graphics.buffer.Bufferable;
import de.hanno.hpengine.util.Util;
import org.apache.commons.io.FilenameUtils;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

public class Entity extends Transform implements LifeCycle, Serializable, Bufferable {
	private static final long serialVersionUID = 1;
	public static int count = 0;

	public List<Instance> getInstances() {
        return instances;
    }

	public enum Update {
		STATIC(1),
		DYNAMIC(0);

		private final double d;
		Update(double d) {
			this.d = d;
		}

		public double getAsDouble() {
			return d;
		}
	}

	private Update update = Update.DYNAMIC;

    private List<Instance> instances = new CopyOnWriteArrayList<>();

	protected boolean initialized = false;

	protected String name = "Entity_" + System.currentTimeMillis();

	private Entity parent = null;
	private List<Entity> children = new ArrayList<>();

	private boolean selected = false;
	private boolean visible = true;
	
	public Map<String, Component> components = new HashMap<>();

	protected Entity() { }

	protected Entity(String name, Model model) {
		this(new Vector3f(0, 0, 0), name, model);
	}

	protected Entity(Vector3f position, String name, Model model) {
		addComponent(new ModelComponent(model));
		this.name = name;
		setTranslation(position);
		init();
	}

	@Override
	public void init() {
		LifeCycle.super.init();

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

//	public <T extends Component> T getComponent(Class<T> type) {
//		Component component = getComponents().get(type.getSimpleName());
//		return type.cast(component);
//	}
	public <T extends Component> T getComponent(Class<T> type, String key) {
		Component component = getComponents().get(key);
		return type.cast(component);
	}

//	public <T extends Component> Optional<T> getComponentOption(Class<T> type) {
//		Component component = getComponents().get(type.getSimpleName());
//		return Optional.ofNullable(type.cast(component));
//	}
	public <T extends Component> Optional<T> getComponentOption(Class<T> type, String key) {
		Component component = getComponents().get(key);
		return Optional.ofNullable(type.cast(component));
	}

    public boolean hasComponent(Class<? extends Component> type) {
        return getComponents().containsKey(type.getSimpleName());
    }
    public boolean hasComponent(String key) {
        return getComponents().containsKey(key);
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

	public List<Entity> getEntityChildren() {
		return children;
	}

	public void removeParent() {
		parent.getChildren().remove(this);
		parent = null;
	}
	public void setParent(Entity parent) {
		this.parent = parent;
		parent.addChild(this);
		super.setParent(parent);
		recalculate();
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
		recalculateIfDirty();
		for (Component c : components.values()) {
            if(!c.isInitialized()) { continue; }
			c.update(seconds);
		}
		for(int i = 0; i < getChildren().size(); i++) {
			getEntityChildren().get(i).update(seconds);
		}
	}

    public Map<String,Component> getComponents() {
		return components;
	}

	public FloatBuffer getViewMatrixAsBuffer() {
		return getViewMatrixAsBuffer(true);
	}
	public FloatBuffer getViewMatrixAsBuffer(boolean recalculateBefore) {
		return getTranslationRotationBuffer(recalculateBefore);
	}

	public String getName() {
		return name;
	}
	public void setName(String string) {
		this.name = string;
	}

	public boolean isInFrustum(Camera camera) {
		return isInFrustum(camera, getCenterWorld(), getMinMaxWorld()[0], getMinMaxWorld()[1]);
	}

	public static boolean isInFrustum(Camera camera, Vector3f centerWorld, Vector3f minWorld, Vector3f maxWorld) {
		Vector3f tempDistVector = new Vector3f();
		new Vector3f(minWorld).sub(maxWorld, tempDistVector);

//		if (de.hanno.hpengine.camera.getFrustum().pointInFrustum(minWorld.x, minWorld.y, minWorld.z) ||
//			de.hanno.hpengine.camera.getFrustum().pointInFrustum(maxWorld.x, maxWorld.y, maxWorld.z)) {
//		if (de.hanno.hpengine.camera.getFrustum().cubeInFrustum(cubeCenterX, cubeCenterY, cubeCenterZ, size)) {
//		if (de.hanno.hpengine.camera.getFrustum().pointInFrustum(minView.x, minView.y, minView.z)
//				|| de.hanno.hpengine.camera.getFrustum().pointInFrustum(maxView.x, maxView.y, maxView.z)) {
		if (camera.getFrustum().sphereInFrustum(centerWorld.x, centerWorld.y, centerWorld.z, tempDistVector.length()/2)) {
			return true;
		}
		return false;
	}

	private transient Vector3f centerWorld = null;
	public Vector3f getCenterWorld() {
		if(centerWorld != null) {
			return centerWorld;
		}
		Vector3f[] minMaxWorld = getMinMaxWorld();
		Vector3f minWorld = minMaxWorld[0];
		Vector3f maxWorld = minMaxWorld[1];

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

    private transient Vector3f[] minMax;
    private transient float boundingSphereRadius = -1;
    private transient Matrix4f lastUsedTransformationMatrix;
	public Vector3f[] getMinMaxWorld() {
		if(lastUsedTransformationMatrix != null && Util.equals(getTransformation(), lastUsedTransformationMatrix)) {
			return minMax;
		}
        centerWorld = null;
        if(hasComponent("ModelComponent")) {
			ModelComponent modelComponent = getComponent(ModelComponent.class, "ModelComponent");
			minMax = modelComponent.getMinMax(this);
		} else {
			minMax = new Vector3f[2];
			float amount = 5;
			Vector3f vectorMin = new Vector3f(getPosition().x-amount, getPosition().y-amount, getPosition().z-amount);
			Vector3f vectorMax = new Vector3f(getPosition().x+amount, getPosition().y+amount, getPosition().z);
			minMax[0] = vectorMin;
			minMax[1] = vectorMax;
            boundingSphereRadius = Mesh.getBoundingSphereRadius(vectorMin, vectorMax);
		}
        lastUsedTransformationMatrix = new Matrix4f(getTransformation());

		return minMax;
	}

	public float getBoundingSphereRadius() {
        return boundingSphereRadius;
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
		return DirectoryManager.WORKDIR_NAME + "/assets/entities/";
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

	@Override
	public boolean isInitialized() {
		return initialized;
	}

	public void setHasMoved(boolean value) {
        super.setHasMoved(value);
        for(Transform inst : instances) {
            inst.setHasMoved(value);
        }
    }

	public boolean hasMoved() {
	    if(isHasMoved()) { return true; }
	    if(getInstanceCount() <= 1) { return false; }

	    for(int i = 0; i < instances.size(); i++) {
	        if(instances.get(i).isHasMoved()) {
	            return true;
            }
        }
        return false;
	}

	public Update getUpdate() {
        if(hasComponent("PhysicsComponent") && getComponent(PhysicsComponent.class, "PhysicsComponent").isDynamic()) {
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
		Engine.getEventBus().post(new UpdateChangedEvent(this));
	}

	@Override
	public void putToBuffer(ByteBuffer buffer) {
		int meshIndex = 0;
		for(Mesh mesh : getComponent(ModelComponent.class, ModelComponent.COMPONENT_KEY).getMeshes()) {
			int materialIndex = MaterialFactory.getInstance().indexOf(mesh.getMaterial());
			{
				putValues(buffer, this, meshIndex, materialIndex);
			}

			for(int i = 0; i < instances.size(); i++) {
				Instance instance = instances.get(i);
				Matrix4f instanceMatrix = instance.getTransformation();
				int instanceMaterialIndex = MaterialFactory.getInstance().indexOf(instance.getMaterial());
				putValues(buffer, instanceMatrix, meshIndex, instanceMaterialIndex);
			}

			// TODO: This has to be the outer loop i think?
			if(hasParent()) {
				for(Instance instance : getParent().getInstances()) {
					Matrix4f instanceMatrix = instance.getTransformation();
					putValues(buffer, instanceMatrix, meshIndex, materialIndex);
				}
			}
			meshIndex++;
		}
	}

	@Override
	public int getBytesPerObject() {
		return (16 * Float.BYTES + 8 * Integer.BYTES) * getComponent(ModelComponent.class, ModelComponent.COMPONENT_KEY).getMeshes().size() * getInstanceCount();
	}

	private void putValues(ByteBuffer buffer, Matrix4f mm, int meshIndex, int materialIndex) {
		buffer.putFloat(mm.m00());
		buffer.putFloat(mm.m01());
		buffer.putFloat(mm.m02());
		buffer.putFloat(mm.m03());
		buffer.putFloat(mm.m10());
		buffer.putFloat(mm.m11());
		buffer.putFloat(mm.m12());
		buffer.putFloat(mm.m13());
		buffer.putFloat(mm.m20());
		buffer.putFloat(mm.m21());
		buffer.putFloat(mm.m22());
		buffer.putFloat(mm.m23());
		buffer.putFloat(mm.m30());
		buffer.putFloat(mm.m31());
		buffer.putFloat(mm.m32());
		buffer.putFloat(mm.m33());
		buffer.putInt(isSelected() ? 1 : 0);
		buffer.putInt(materialIndex);
		buffer.putInt((int) getUpdate().getAsDouble());
		int entityIndex = Engine.getInstance().getScene().getEntityBufferIndex(getComponent(ModelComponent.class, ModelComponent.COMPONENT_KEY));
		buffer.putInt(entityIndex + meshIndex);
		buffer.putInt(entityIndex);
		buffer.putInt(meshIndex);
		buffer.putInt(-1);
		buffer.putInt(-1);
	}

    public int getInstanceCount() {
        int instancesCount = instances.size() + 1;

        if(hasParent()) {
            instancesCount *= getParent().getInstanceCount();
        }
        return instancesCount;
    }

	public void addInstance(Transform instance) {
		if(getParent() != null) {
			instance.setParent(getParent());
		}
		instances.add(new Instance(instance, getComponent(ModelComponent.class, ModelComponent.COMPONENT_KEY).getMaterial()));
		Engine.getEventBus().post(new EntityAddedEvent());
	}
    public void addInstanceTransforms(List<Transform> instances) {
        if(getParent() != null) {
            for(Transform instance : instances) {
                instance.setParent(getParent());
            }
        }
        this.instances.addAll(instances.stream().map(trafo -> new Instance(trafo, getComponent(ModelComponent.class, ModelComponent.COMPONENT_KEY).getMaterial())).collect(Collectors.toList()));
        Engine.getEventBus().post(new EntityAddedEvent());
    }
    public void addInstances(List<Instance> instances) {
        if(getParent() != null) {
            for(Transform instance : instances) {
                instance.setParent(getParent());
            }
        }
        this.instances.addAll(instances);
        Engine.getEventBus().post(new EntityAddedEvent());
    }

	public static class Instance extends Transform {
		private Material material;

		public Instance(Transform transform, Material material) {
			set(transform);
			this.material = material;
		}

        public Material getMaterial() {
            return material;
        }

        public void setMaterial(Material material) {
            this.material = material;
        }
    }
}
