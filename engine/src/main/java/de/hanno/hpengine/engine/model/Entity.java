package de.hanno.hpengine.engine.model;

import de.hanno.hpengine.engine.model.material.Material;
import de.hanno.hpengine.engine.transform.*;
import de.hanno.hpengine.engine.camera.Camera;
import de.hanno.hpengine.engine.component.Component;
import de.hanno.hpengine.engine.component.ModelComponent;
import de.hanno.hpengine.engine.component.PhysicsComponent;
import de.hanno.hpengine.engine.DirectoryManager;
import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.lifecycle.LifeCycle;
import de.hanno.hpengine.engine.event.EntityAddedEvent;
import de.hanno.hpengine.engine.event.UpdateChangedEvent;
import de.hanno.hpengine.engine.model.loader.md5.AnimationController;
import de.hanno.hpengine.engine.model.material.MaterialFactory;
import de.hanno.hpengine.engine.graphics.buffer.Bufferable;
import org.apache.commons.io.FilenameUtils;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

public class Entity extends Transform<Entity> implements LifeCycle, Serializable, Bufferable {
	private static final long serialVersionUID = 1;
	public static int count = 0;
	private final SimpleSpatial spatial = new SimpleSpatial() {
		@Override
		public AABB getMinMax() {
			if (hasComponent(ModelComponent.COMPONENT_KEY)) {
				ModelComponent modelComponent = getComponent(ModelComponent.class, ModelComponent.COMPONENT_KEY);
				return modelComponent.getMinMax(modelComponent.getAnimationController());
			} else {
				return super.getMinMax();
			}
		}
	};

	private List<Instance> instancesTemp = new ArrayList();

	public Instance addInstance(Entity entity) {
		return addInstance(entity, new SimpleTransform());
	}
	public static Instance addInstance(Entity entity, Transform transform) {
        Optional<ModelComponent> componentOption = entity.getComponentOption(ModelComponent.class, ModelComponent.COMPONENT_KEY);
        Instance instance;
        if(componentOption.isPresent()) {
            List<Material> materials = componentOption.get().getMeshes().stream().map(Mesh::getMaterial).collect(Collectors.toList());
            InstanceSpatial spatial = componentOption.get().isStatic() ? new InstanceSpatial() : new AnimatedInstanceSpatial();
            AnimationController animationController = componentOption.get().isStatic() ? new AnimationController(0, 0) : new AnimationController(120, 24);
            instance = new Instance(entity, transform, materials, animationController, spatial);
            spatial.setInstance(instance);
            entity.addExistingInstance(instance);
        } else {
            instance = entity.addInstance(new SimpleTransform());
        }
        Engine.getEventBus().post(new EntityAddedEvent());
        return instance;
    }

	public List<Instance> getInstances() {
        return instancesTemp;
    }

	private void recalculateInstances() {
		instancesTemp.clear();
		for(Cluster cluster : clusters) {
            instancesTemp.addAll(cluster);
        }
	}

	public List<Cluster> getClusters() {
		return clusters;
	}

	public void addCluster(Cluster cluster) {
		clusters.add(cluster);
		recalculateInstances();
		Engine.getEventBus().post(new EntityAddedEvent());
	}

	private Update update = Update.DYNAMIC;

    private List<Cluster> clusters = new CopyOnWriteArrayList<>();

	protected boolean initialized = false;

	protected String name = "Entity_" + System.currentTimeMillis();

	private boolean selected = false;
	private boolean visible = true;
	
	public Map<String, Component> components = new HashMap<>();

	protected Entity() {
	}

	protected Entity(String name, StaticModel model) {
		this(new Vector3f(0, 0, 0), name, model);
	}

	protected Entity(Vector3f position, String name, Model model) {
	    this();
		addComponent(new ModelComponent(model));
		this.name = name;
		setTranslation(position);
		initialize();
	}

	@Override
	public void initialize() {
		LifeCycle.super.init();

		for(Component component : components.values()) {
			component.init();
		}
		for(Entity child: getChildren()) {
			child.initialize();
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

	public <T extends Component> T getComponent(Class<T> type, String key) {
		Component component = getComponents().get(key);
		return type.cast(component);
	}

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
			for (Entity child: getChildren()) {
				allChildrenAndSelf.addAll(child.getAllChildrenAndSelf());
			}
		}
		return allChildrenAndSelf;
	}

	@Override
	public void setParent(Entity node) {
		super.setParent(node);
		recalculate();
	}

	@Override
	public void update(float seconds) {
		recalculateIfDirty();
		for (Component c : components.values()) {
            if(!c.isInitialized()) { continue; }
			c.update(seconds);
		}
		for(int i = 0; i < clusters.size(); i++) {
			Cluster cluster = clusters.get(i);
			cluster.update(seconds);
		}
		for(int i = 0; i < getChildren().size(); i++) {
			getChildren().get(i).update(seconds);
		}
	}

    public Map<String,Component> getComponents() {
		return components;
	}

	public String getName() {
		return name;
	}
	public void setName(String string) {
		this.name = string;
	}

	public boolean isInFrustum(Camera camera) {
		return Spatial.isInFrustum(camera, spatial.getCenterWorld(this), spatial.getMinMaxWorld(this).getMin(), spatial.getMinMaxWorld(this).getMax());
	}

	public Vector3f getCenterWorld() {
		return spatial.getCenterWorld(this);
	}

	public boolean isVisible() {
		return visible;
	}
	public void setVisible(boolean visible) {
		this.visible = visible;
	}

	public AABB getMinMaxWorld() {
		return spatial.getMinMaxWorld(this);
	}
	public AABB getMinMax() {
		return spatial.getMinMax();
	}

	public float getBoundingSphereRadius() {
		return spatial.getBoundingSphereRadius(this);
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

	@Override
	public void setHasMoved(boolean value) {
        super.setHasMoved(value);
		Optional<ModelComponent> modelComponentOption = getComponentOption(ModelComponent.class, ModelComponent.COMPONENT_KEY);
		modelComponentOption.ifPresent(modelComponent -> modelComponent.setHasUpdated(value));
        for(Cluster cluster : clusters) {
            cluster.setHasMoved(value);
        }
    }

	public boolean hasMoved() {
		Optional<ModelComponent> modelComponentOption = getComponentOption(ModelComponent.class, ModelComponent.COMPONENT_KEY);
		if(modelComponentOption.isPresent()) {
			if(modelComponentOption.get().isHasUpdated()) {
				return true;
			}
		}

		if(isHasMoved()) { return true; }
	    if(getInstanceCount() <= 1) { return false; }

	    for(int i = 0; i < clusters.size(); i++) {
	        if(clusters.get(i).isHasMoved()) {
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
			for (Entity child : getChildren()) {
				child.setUpdate(update);
			}
		}
		Engine.getEventBus().post(new UpdateChangedEvent(this));
	}

	@Override
	public void putToBuffer(ByteBuffer buffer) {
		int meshIndex = 0;
		ModelComponent modelComponent = getComponent(ModelComponent.class, ModelComponent.COMPONENT_KEY);
		List<Mesh> meshes = modelComponent.getMeshes();
		for(Mesh mesh : meshes) {
			int materialIndex = MaterialFactory.getInstance().indexOf(mesh.getMaterial());
			{
				putValues(buffer, getTransformation(), meshIndex, materialIndex, modelComponent.getAnimationFrame0(), modelComponent.getMinMax(this, mesh));
			}

			for(Cluster cluster : clusters) {
                for(int i = 0; i < cluster.size(); i++) {
                    Instance instance = cluster.get(i);
                    Matrix4f instanceMatrix = instance.getTransformation();
                    int instanceMaterialIndex = MaterialFactory.getInstance().indexOf(instance.getMaterials().get(meshIndex));
                    putValues(buffer, instanceMatrix, meshIndex, instanceMaterialIndex, instance.getAnimationController().getCurrentFrameIndex(), instance.getMinMaxWorld());
                }
            }

			// TODO: This has to be the outer loop i think?
			if(hasParent()) {
				for(Instance instance : getParent().getInstances()) {
					Matrix4f instanceMatrix = instance.getTransformation();
					putValues(buffer, instanceMatrix, meshIndex, materialIndex, instance.getAnimationController().getCurrentFrameIndex(), getMinMaxWorld());
				}
			}
			meshIndex++;
		}
	}

	@Override
	public int getBytesPerObject() {
		return getBytesPerInstance() * getComponent(ModelComponent.class, ModelComponent.COMPONENT_KEY).getMeshes().size() * getInstanceCount();
	}

	public static int getBytesPerInstance() {
		return 16 * Float.BYTES + 16 * Integer.BYTES + 8 * Float.BYTES;
	}

	private void putValues(ByteBuffer buffer, Matrix4f mm, int meshIndex, int materialIndex, int animationFrame0, AABB minMaxWorld) {
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
		ModelComponent modelComponent = getComponent(ModelComponent.class, ModelComponent.COMPONENT_KEY);
		int entityBufferIndex = Engine.getInstance().getSceneManager().getScene().getEntityBufferIndex(modelComponent);
		buffer.putInt(entityBufferIndex + meshIndex);

		buffer.putInt(Engine.getInstance().getSceneManager().getScene().getEntities().indexOf(this));
		buffer.putInt(meshIndex);
		buffer.putInt(modelComponent.getBaseVertex(meshIndex));
		buffer.putInt(modelComponent.getBaseJointIndex());

		buffer.putInt(animationFrame0);
		buffer.putInt(0);
		buffer.putInt(0);
		buffer.putInt(0);

		buffer.putInt(modelComponent.isInvertTexCoordY() ? 1 : 0);
		buffer.putInt(0);
		buffer.putInt(0);
		buffer.putInt(0);

		buffer.putFloat(minMaxWorld.getMin().x);
		buffer.putFloat(minMaxWorld.getMin().y);
		buffer.putFloat(minMaxWorld.getMin().z);
		buffer.putFloat(1);

		buffer.putFloat(minMaxWorld.getMax().x);
		buffer.putFloat(minMaxWorld.getMax().y);
		buffer.putFloat(minMaxWorld.getMax().z);
		buffer.putFloat(1);
	}

	@Override
	public void getFromBuffer(ByteBuffer buffer) {
		m00(buffer.getFloat());
		m01(buffer.getFloat());
		m02(buffer.getFloat());
		m03(buffer.getFloat());
		m10(buffer.getFloat());
		m11(buffer.getFloat());
		m12(buffer.getFloat());
		m13(buffer.getFloat());
		m20(buffer.getFloat());
		m21(buffer.getFloat());
		m22(buffer.getFloat());
		m23(buffer.getFloat());
		m30(buffer.getFloat());
		m31(buffer.getFloat());
		m32(buffer.getFloat());
		m33(buffer.getFloat());

		setSelected(buffer.getInt() == 1);
		Material material = MaterialFactory.getInstance().getMaterialsAsList().get(buffer.getInt());
		System.out.println(material.getName());
		System.out.println(material);
		setUpdate(Update.values()[buffer.getInt()]);
		ModelComponent modelComponent = getComponent(ModelComponent.class, ModelComponent.COMPONENT_KEY);
		int entityBufferIndex = Engine.getInstance().getSceneManager().getScene().getEntityBufferIndex(modelComponent);
		buffer.getInt();

		System.out.println("Entity index " + buffer.getInt());
		System.out.println("Mesh index " + buffer.getInt());
		System.out.println("Base vertex " + buffer.getInt());
		System.out.println("Base joint index " + buffer.getInt());

		System.out.println("AnimationFrame0 " + buffer.getInt());
		buffer.getInt();
		buffer.getInt();
		buffer.getInt();

		System.out.println("InvertTexCoordY " + buffer.getInt());
		setVisible(buffer.getInt() == 1);
		buffer.getInt();
		buffer.getInt();

		System.out.println("minX " + buffer.getFloat());
		buffer.getFloat();
		buffer.getFloat();
		buffer.getFloat();

		System.out.println("maxX " + buffer.getFloat());
		buffer.getFloat();
		buffer.getFloat();
		buffer.getFloat();
	}

    public int getInstanceCount() {
        int instancesCount = 1;

        for(int i = 0; i < clusters.size(); i++) {
            instancesCount += clusters.get(i).size();
        }

        if(hasParent()) {
            instancesCount *= getParent().getInstanceCount();
        }
        return instancesCount;
    }

	public Instance addInstance(Transform instanceTransform) {
		if(getParent() != null) {
			instanceTransform.setParent(getParent());
		}
		addInstance(this, instanceTransform);
		ModelComponent modelComponent = getComponent(ModelComponent.class, ModelComponent.COMPONENT_KEY);
		List<Material> materials = modelComponent == null ? new ArrayList<>() : modelComponent.getMaterials();

		InstanceSpatial spatial = new InstanceSpatial() {
			@Override
			public AABB getMinMax() {
				return Entity.this.spatial.getMinMax();
			}
		};
		Instance instance = new Instance(this, instanceTransform, materials, new AnimationController(0, 0), spatial);
		spatial.setInstance(instance);
		addExistingInstance(instance);
		return instance;
	}


	public void addExistingInstance(Instance instance) {
		Cluster firstCluster = getOrCreateFirstCluster();
		firstCluster.add(instance);
		Engine.getEventBus().post(new EntityAddedEvent());
		recalculateInstances();
	}

	public void addInstanceTransforms(List<Transform<? extends Transform<?>>> instances) {
        if(getParent() != null) {
            for(Transform instance : instances) {
                instance.setParent(getParent());
            }
        }
		Cluster firstCluster = getOrCreateFirstCluster();
		ModelComponent modelComponent = getComponent(ModelComponent.class, ModelComponent.COMPONENT_KEY);
		List<Material> materials = modelComponent == null ? new ArrayList<>() : modelComponent.getMaterials();
		List<Instance> collect = instances.stream().map(trafo -> {
			InstanceSpatial spatial = new InstanceSpatial();
			Instance instance = new Instance(this, trafo, materials, new AnimationController(0, 0), spatial);
			spatial.setInstance(instance);
			return instance;
		}).collect(Collectors.toList());
		firstCluster.addAll(collect);
		recalculateInstances();
		Engine.getEventBus().post(new EntityAddedEvent());
    }
    public void addInstances(List<Instance> instances) {
        if(getParent() != null) {
            for(Transform instance : instances) {
                instance.setParent(getParent());
            }
        }
		Cluster firstCluster = getOrCreateFirstCluster();
		firstCluster.addAll(instances);
		recalculateInstances();
        Engine.getEventBus().post(new EntityAddedEvent());
    }

	private Cluster getOrCreateFirstCluster() {
		Cluster firstCluster = null;
		if(!this.clusters.isEmpty()) {
			firstCluster = this.clusters.get(0);
		}
		if(firstCluster == null) {
            firstCluster = new Cluster();
            clusters.add(firstCluster);
        }
		return firstCluster;
	}

	public List<AABB> getInstanceMinMaxWorlds() {
		return getInstances().stream().map(it -> it.getMinMaxWorld()).collect(Collectors.toList());
	}
}
