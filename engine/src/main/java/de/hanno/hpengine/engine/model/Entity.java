package de.hanno.hpengine.engine.model;

import com.carrotsearch.hppc.DoubleArrayList;
import de.hanno.hpengine.camera.Camera;
import de.hanno.hpengine.component.Component;
import de.hanno.hpengine.component.ModelComponent;
import de.hanno.hpengine.component.PhysicsComponent;
import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.Transform;
import de.hanno.hpengine.engine.lifecycle.LifeCycle;
import de.hanno.hpengine.event.EntityAddedEvent;
import de.hanno.hpengine.event.UpdateChangedEvent;
import de.hanno.hpengine.renderer.material.Material;
import de.hanno.hpengine.renderer.material.MaterialFactory;
import de.hanno.hpengine.shader.Bufferable;
import de.hanno.hpengine.util.Util;
import org.apache.commons.io.FilenameUtils;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Vector3f;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.FloatBuffer;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

public class Entity implements Transformable, LifeCycle, Serializable, Bufferable {
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

	private Transform transform = new Transform();

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
		transform.init();
		addComponent(new ModelComponent(model));
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
		Component component = getComponents().get(type.getSimpleName());
		return type.cast(component);
	}
	public <T extends Component> T getComponent(Class<T> type, String key) {
		Component component = getComponents().get(key);
		return type.cast(component);
	}

	public <T extends Component> Optional<T> getComponentOption(Class<T> type) {
		Component component = getComponents().get(type.getSimpleName());
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
		for(int i = 0; i < getChildren().size(); i++) {
			getChildren().get(i).update(seconds);
		}
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
		return isInFrustum(camera, getCenterWorld(), getMinMaxWorld()[0], getMinMaxWorld()[1]);
	}

	public static boolean isInFrustum(Camera camera, Vector3f centerWorld, Vector3f minWorld, Vector3f maxWorld) {
		Vector3f tempDistVector = new Vector3f();
		Vector3f.sub(minWorld, maxWorld, tempDistVector);

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
        if(!getTransform().isDirty() && minMax != null) {
            if(lastUsedTransformationMatrix != null && Util.equals(getTransform().getTransformation(), lastUsedTransformationMatrix)) {
                return minMax;
            }
        }
        centerWorld = null;
        if(hasComponent("ModelComponent")) {
			ModelComponent modelComponent = getComponent(ModelComponent.class, "ModelComponent");
			minMax = modelComponent.getMinMax(getModelMatrix());
		} else {
			minMax = new Vector3f[2];
			float amount = 5;
			Vector3f vectorMin = new Vector3f(getPosition().x-amount, getPosition().y-amount, getPosition().z-amount);
			Vector3f vectorMax = new Vector3f(getPosition().x+amount, getPosition().y+amount, getPosition().z);
			minMax[0] = vectorMin;
			minMax[1] = vectorMax;
            boundingSphereRadius = Mesh.getBoundingSphereRadius(vectorMin, vectorMax);
		}
        lastUsedTransformationMatrix = new Matrix4f(getTransform().getTransformation());

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
		return Engine.WORKDIR_NAME + "/assets/entities/";
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
        transform.setHasMoved(value);
        for(Transform inst : instances) {
            inst.setHasMoved(value);
        }
    }

	public boolean hasMoved() {
	    if(transform.isHasMoved()) { return true; }
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
    public int getElementsPerObject() {
        return getInstanceCount() * (hasComponent(ModelComponent.class) ? getComponent(ModelComponent.class).getMeshes().size() : 0) * (20+4);
    }


    private double[] doubles = null;
    @Override
    public double[] get() {

		int elementsPerObject = getElementsPerObject();
    	if(doubles == null || doubles.length < elementsPerObject) {
    		doubles = new double[elementsPerObject];
		}

        int index = 0;
        int meshIndex = 0;
        for(Mesh mesh : getComponent(ModelComponent.class).getMeshes()) {
			int materialIndex = MaterialFactory.getInstance().indexOf(mesh.getMaterial());
			{
				Matrix4f mm = getModelMatrix();
				index = getValues(doubles, index, mm, meshIndex, materialIndex);
			}

			List<Future<Matrix4f>> instanceMatrices = new ArrayList<>();
            for(int i = 0; i < instances.size(); i++) {
                Instance instance = instances.get(i);
                instanceMatrices.add(CompletableFuture.supplyAsync(instance::getTransformation));
            }

            for(int i = 0; i < instances.size(); i++) {
                Instance instance = instances.get(i);
                Matrix4f instanceMatrix = null;
                try {
                    instanceMatrix = instanceMatrices.get(i).get();
                } catch (InterruptedException | ExecutionException e) {
                    e.printStackTrace();
                }
				int instanceMaterialIndex = MaterialFactory.getInstance().indexOf(instance.getMaterial());
                index = getValues(doubles, index, instanceMatrix, meshIndex, instanceMaterialIndex);
            }
			if(hasParent()) {
				for(Instance instance : getParent().getInstances()) {
					Matrix4f instanceMatrix = instance.getTransformation();
                    index = getValues(doubles, index, instanceMatrix, meshIndex, materialIndex);
				}
			}
			meshIndex++;
		}
        return doubles;
    }

	private int getValues(double[] doubles, int index, Matrix4f mm, int meshIndex, double materialIndex) {
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
		doubles[index++] = materialIndex;
		doubles[index++] = getUpdate().getAsDouble();
		int entityIndex = Engine.getInstance().getScene().getEntityBufferIndex(getComponent(ModelComponent.class)); //getEntities().stream().filter(e -> e.hasComponent(ModelComponent.class)).collect(Collectors.toList()).indexOf(this);
		doubles[index++] = entityIndex + meshIndex;
		doubles[index++] = entityIndex;
		doubles[index++] = meshIndex;
		doubles[index++] = -1;
		doubles[index++] = -1;
		return index;
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
			instance.setParent(getParent().getTransform());
		}
		instances.add(new Instance(instance, getComponent(ModelComponent.class).getMaterial()));
		Engine.getEventBus().post(new EntityAddedEvent());
	}
    public void addInstanceTransforms(List<Transform> instances) {
        if(getParent() != null) {
            for(Transform instance : instances) {
                instance.setParent(getParent().getTransform());
            }
        }
        this.instances.addAll(instances.stream().map(trafo -> new Instance(trafo, getComponent(ModelComponent.class).getMaterial())).collect(Collectors.toList()));
        Engine.getEventBus().post(new EntityAddedEvent());
    }
    public void addInstances(List<Instance> instances) {
        if(getParent() != null) {
            for(Transform instance : instances) {
                instance.setParent(getParent().getTransform());
            }
        }
        this.instances.addAll(instances);
        Engine.getEventBus().post(new EntityAddedEvent());
    }

	public static class Instance extends Transform {
		private Material material;

		public Instance(Transform transform, Material material) {
			setTransform(transform);
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
