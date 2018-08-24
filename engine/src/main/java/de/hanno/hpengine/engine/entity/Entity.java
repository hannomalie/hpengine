package de.hanno.hpengine.engine.entity;

import de.hanno.hpengine.engine.DirectoryManager;
import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.camera.Camera;
import de.hanno.hpengine.engine.component.Component;
import de.hanno.hpengine.engine.component.ModelComponent;
import de.hanno.hpengine.engine.component.PhysicsComponent;
import de.hanno.hpengine.engine.instancing.ClustersComponent;
import de.hanno.hpengine.engine.lifecycle.LifeCycle;
import de.hanno.hpengine.engine.model.Cluster;
import de.hanno.hpengine.engine.model.Update;
import de.hanno.hpengine.engine.transform.AABB;
import de.hanno.hpengine.engine.transform.SimpleSpatial;
import de.hanno.hpengine.engine.transform.Spatial;
import de.hanno.hpengine.engine.transform.Transform;
import org.joml.Vector3f;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

public class Entity extends Transform<Entity> implements LifeCycle {
	private static final long serialVersionUID = 1;
	public final SimpleSpatial spatial = new SimpleSpatial() {
		@Override
		public AABB getMinMax() {
			if (hasComponent(ModelComponent.class)) {
				ModelComponent modelComponent = getComponent(ModelComponent.class);
				return modelComponent.getMinMax(modelComponent.getAnimationController());
			} else {
				return super.getMinMax();
			}
		}
	};
    private int index = -1;

	private Update update = Update.DYNAMIC;

	protected String name = "Entity_" + System.currentTimeMillis();

	private boolean selected = false;
	private boolean visible = true;
	
	public Map<Class<Component>, Component> components = new HashMap<>();

	public Entity() {
	    this("Entity"  +String.valueOf(System.currentTimeMillis()));
	}

	public Entity(String name) {
		this(name, new Vector3f(0, 0, 0));
	}

	public Entity(String name, Vector3f position) {
		this.name = name;
		setTranslation(position);
    }

	@Override
	public void init(Engine engine) {
		for(Component component : components.values()) {
			component.init(engine);
		}
    }

	public Entity addComponent(Component component) {
		Class<Component> clazz = (Class<Component>) component.getClass();
		boolean isAnonymous = clazz.getEnclosingClass() != null;
		if(isAnonymous) {
			clazz = (Class<Component>) clazz.getSuperclass();
		}
		getComponents().put(clazz, component);
		return this;
	}

	public void removeComponent(Component component) {
		getComponents().remove(component.getIdentifier(), component);
	}
	public void removeComponent(String key) {
		getComponents().remove(key);
	}

    public <T extends Component> T getOrAddComponent(Class<T> type, Supplier<T> supplier) {
        if(!hasComponent(type)) {
            T component = supplier.get();
            addComponent(component);
            return component;
        }
        return getComponent(type);
    }
    public <T extends Component> T getOrAddComponentLegacy(Class<T> type, Callable<T> supplier) {
        if(!hasComponent(type)) {
            T component = null;
            try {
                component = supplier.call();
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
            addComponent(component);
            return component;
        }
        return getComponent(type);
    }

	public <T extends Component> T getComponent(Class<T> type) {
		Component component = getComponents().get(type);
		return type.cast(component);
	}

	public <T extends Component> Optional<T> getComponentOption(Class<T> type) {
		Component component = getComponents().get(type);
		return Optional.ofNullable(type.cast(component));
	}

    public boolean hasComponent(Class<? extends Component> type) {
        return getComponents().containsKey(type);
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
		if(hasParent()) {
			return;
		}
		recalculateIfDirty();
		for(int i = 0; i < getChildren().size(); i++) {
			getChildren().get(i).update(seconds);
		}
	}

    public Map<Class<Component>,Component> getComponents() {
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
	public void setHasMoved(boolean value) {
        super.setHasMoved(value);
		Optional<ModelComponent> modelComponentOption = getComponentOption(ModelComponent.class);
		modelComponentOption.ifPresent(modelComponent -> modelComponent.setHasUpdated(value));

		ClustersComponent clusters = getComponent(ClustersComponent.class);
		if(clusters != null) {
			for(Cluster cluster : clusters.getClusters()) {
				cluster.setHasMoved(value);
			}
		}
    }

	public boolean hasMoved() {
		Optional<ModelComponent> modelComponentOption = getComponentOption(ModelComponent.class);
		if(modelComponentOption.isPresent()) {
			if(modelComponentOption.get().isHasUpdated()) {
				return true;
			}
		}

		if(isHasMoved()) { return true; }
	    if(getComponent(ClustersComponent.class) == null) { return false; }

		ClustersComponent clusters = getComponent(ClustersComponent.class);
		if(clusters != null) {
			for(int i = 0; i < clusters.getClusters().size(); i++) {
				if(clusters.getClusters().get(i).isHasMoved()) {
					return true;
				}
			}
		}
        return false;
	}

	public Update getUpdate() {
        if((hasComponent("PhysicsComponent") && getComponent(PhysicsComponent.class).isDynamic())
				|| (hasComponent(ModelComponent.COMPONENT_KEY) && !getComponent(ModelComponent.class).getModel().isStatic())) {
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
	}

	private List<AABB> emptyList = new ArrayList<>();
	public List<AABB> getInstanceMinMaxWorlds() {
		ClustersComponent clusters = getComponent(ClustersComponent.class);
		if(clusters == null) { return emptyList; }
		return clusters.getInstancesMinMaxWorlds();
	}

    public int getIndex() {
	    return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

}
