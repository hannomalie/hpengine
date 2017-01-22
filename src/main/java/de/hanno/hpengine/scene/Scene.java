package de.hanno.hpengine.scene;

import de.hanno.hpengine.component.ModelComponent;
import de.hanno.hpengine.container.EntitiesContainer;
import de.hanno.hpengine.container.SimpleContainer;
import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.lifecycle.LifeCycle;
import de.hanno.hpengine.engine.model.Entity;
import de.hanno.hpengine.engine.model.IndexBuffer;
import de.hanno.hpengine.engine.model.VertexBuffer;
import de.hanno.hpengine.event.EntityAddedEvent;
import de.hanno.hpengine.event.LightChangedEvent;
import de.hanno.hpengine.event.MaterialAddedEvent;
import de.hanno.hpengine.event.SceneInitEvent;
import org.apache.commons.io.FilenameUtils;
import org.lwjgl.BufferUtils;
import org.lwjgl.util.vector.Vector4f;
import org.nustaq.serialization.FSTConfiguration;
import de.hanno.hpengine.renderer.OpenGLContext;
import de.hanno.hpengine.renderer.Renderer;
import de.hanno.hpengine.renderer.light.AreaLight;
import de.hanno.hpengine.renderer.light.DirectionalLight;
import de.hanno.hpengine.renderer.light.PointLight;
import de.hanno.hpengine.renderer.light.TubeLight;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static de.hanno.hpengine.component.ModelComponent.DEFAULTCHANNELS;

public class Scene implements LifeCycle, Serializable {
	private static final long serialVersionUID = 1L;

	private static final Logger LOGGER = Logger.getLogger(Scene.class.getName());

	public volatile VertexBuffer vertexBuffer = new VertexBuffer(BufferUtils.createFloatBuffer(100000), DEFAULTCHANNELS);;
	public volatile IndexBuffer indexBuffer = new IndexBuffer(BufferUtils.createIntBuffer(100000));
	public volatile AtomicInteger currentBaseVertex = new AtomicInteger();
	public volatile AtomicInteger currentIndexOffset = new AtomicInteger();

	String name = "";
	List<ProbeData> probes = new CopyOnWriteArrayList<>();

	private transient EntitiesContainer entityContainer = new SimpleContainer();
	transient boolean initialized = false;
	private List<Entity> entities = new CopyOnWriteArrayList<>();
	private List<PointLight> pointLights = new CopyOnWriteArrayList<>();
	private List<TubeLight> tubeLights = new CopyOnWriteArrayList<>();
	private List<AreaLight> areaLights = new CopyOnWriteArrayList<>();
	private DirectionalLight directionalLight = new DirectionalLight();
	private boolean updateCache = true;

	public Scene() {
        this("new-scene-" + System.currentTimeMillis());
	}
	public Scene(String name) {
		this.name = name;
	}

	@Override
	public void init() {
		LifeCycle.super.init();
		EnvironmentProbeFactory.getInstance().clearProbes();
//        entityContainer = new Octree(new Vector3f(), 600, 5);
        entityContainer = new SimpleContainer();
		entityContainer.init();
        entities.forEach(entity -> entity.init());
        entities.forEach(entity -> entity.getComponents().values().forEach(c -> c.registerInScene(Scene.this)));
		addAll(entities);
		for (ProbeData data : probes) {
			OpenGLContext.getInstance().execute(() -> {
                try {
					// TODO: Remove this f***
                    EnvironmentProbe probe = EnvironmentProbeFactory.getInstance().getProbe(data.getCenter(), data.getSize(), data.getUpdate(), data.getWeight());
                    Renderer.getInstance().addRenderProbeCommand(probe);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
		}
		initLights();
		initialized = true;
		Engine.getEventBus().post(new SceneInitEvent());
	}
	private void initLights() {
		for(PointLight pointLight : pointLights) {
			pointLight.init();
		}
		for(AreaLight areaLight : areaLights) {
			areaLight.init();
		}
		
		directionalLight.init();
	}
	
	public void write() {
		write(name);
	}

	public boolean write(String name) {
		String fileName = FilenameUtils.getBaseName(name);
		this.name = fileName;
		FileOutputStream fos = null;
		ObjectOutputStream out = null;
		try {
			fos = new FileOutputStream(getDirectory() + fileName + ".hpscene");
			out = new ObjectOutputStream(fos);
			entities.clear();
			entities.addAll(entityContainer.getEntities());
			probes.clear();
			for (EnvironmentProbe probe : EnvironmentProbeFactory.getInstance().getProbes()) {
				ProbeData probeData = new ProbeData(probe.getCenter(), probe.getSize(), probe.getProbeUpdate());
				if(probes.contains(probeData)) { continue; }
				probes.add(probeData);
			}
			out.writeObject(this);
//			FSTObjectOutput newOut = new FSTObjectOutput(out);
//			newOut.writeObject(this);
//			newOut.close();

		} catch (Exception e) {
			e.printStackTrace();
			return false;
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

	public static Scene read(String name) {
		String fileName = FilenameUtils.getBaseName(name);
		FileInputStream fis = null;
		ObjectInputStream in = null;
		try {
			fis = new FileInputStream(getDirectory() + fileName + ".hpscene");
			in = new ObjectInputStream(fis);
			Scene scene = (Scene) in.readObject();

//			FSTObjectInput newIn = new FSTObjectInput(in);
//			Scene de.hanno.hpengine.scene = (Scene)newIn.readObject();
//			newIn.close();

			handleEvolution(scene);
			in.close();
			fis.close();
			scene.entityContainer = new SimpleContainer();//new Octree(new Vector3f(), 400, 6);
			return scene;
		} catch (IOException | ClassNotFoundException e) {
			e.printStackTrace();
		}
		return null;
	}

    static final Vector4f absoluteMaximum = new Vector4f(Float.MAX_VALUE,Float.MAX_VALUE,Float.MAX_VALUE,Float.MAX_VALUE);
    static final Vector4f absoluteMinimum = new Vector4f(-Float.MAX_VALUE,-Float.MAX_VALUE,-Float.MAX_VALUE,-Float.MAX_VALUE);
    Vector4f min = new Vector4f();
    Vector4f max = new Vector4f();
    Vector4f[] minMax = new Vector4f[]{min, max};

    public Vector4f[] getMinMax() {
        return minMax;
    }

    public void calculateMinMax() {
        calculateMinMax(entityContainer.getEntities());
    }
    private void calculateMinMax(List<Entity> entities) {
        if(entities.size() == 0) { min.set(-1,-1,-1,-1); max.set(1,1,1,1); return;}

        min.set(absoluteMaximum);
        max.set(absoluteMinimum);

        for(Entity entity : entities) {
            Vector4f[] currentMinMax = entity.getMinMaxWorld();
            Vector4f currentMin = currentMinMax[0];
            Vector4f currentMax = currentMinMax[1];
            min.x = currentMin.x < min.x ? currentMin.x : min.x;
            min.y = currentMin.y < min.y ? currentMin.y : min.y;
            min.z = currentMin.z < min.z ? currentMin.z : min.z;

            max.x = currentMax.x > max.x ? currentMax.x : max.x;
            max.y = currentMax.y > max.y ? currentMax.y : max.y;
            max.z = currentMax.z > max.z ? currentMax.z : max.z;
        }
    }

    static FSTConfiguration conf = FSTConfiguration.createDefaultConfiguration();

    private static void handleEvolution(Scene scene) {
    }

	public static String getDirectory() {
		return Engine.WORKDIR_NAME + "/assets/scenes/";
	}
	public void addAll(List<Entity> entities) {
		entityContainer.insert(entities);
		entities.forEach(e -> e.getComponents().values().forEach(c -> c.registerInScene(Scene.this)));
        calculateMinMax(entities);
		updateCache = true;
		Engine.getEventBus().post(new MaterialAddedEvent());
		Engine.getEventBus().post(new EntityAddedEvent());
	}
	public void add(Entity entity) {
		entityContainer.insert(entity.getAllChildrenAndSelf());
		entity.getComponents().values().forEach(c -> {
			c.registerInScene(Scene.this);
		});
        calculateMinMax(entities);
		updateCache = true;
		Engine.getEventBus().post(new MaterialAddedEvent());
		Engine.getEventBus().post(new EntityAddedEvent());
	}
	public void update(float seconds) {
		Iterator<PointLight> pointLightsIterator = pointLights.iterator();
		while (pointLightsIterator.hasNext()) {
			pointLightsIterator.next().update(seconds);
		}

		for(int i = 0; i < areaLights.size(); i++) {
			areaLights.get(i).update(seconds);
		}
		List<Entity> entities = entityContainer.getEntities();
		for(int i = 0; i < entities.size(); i++) {
			entities.get(i).update(seconds);
		}
		directionalLight.update(seconds);
	}

	private void initializationWrapped (Supplier<Void> supplier) {
		initialized = false;
		supplier.get();
		initialized = true;
	}

    public void endFrame() {
	}
	public EntitiesContainer getEntitiesContainer() {
		return entityContainer;
	}
	public List<Entity> getEntities() {
		return entityContainer.getEntities();
	}
	public Optional<Entity> getEntity(String name) {
		List<Entity> candidates = getEntities().stream().filter(e -> e.getName().equals(name)).collect(Collectors.toList());
		return candidates.size() > 0 ? Optional.of(candidates.get(0)) : Optional.of(null);
	}
	public boolean isInitialized() {
		return initialized;
	}
	public void setInitialized(boolean initialized) {
		this.initialized = initialized;
	}
	public boolean removeEntity(Entity entity) {
		return entityContainer.removeEntity(entity);
	}
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}

	public List<AreaLight> getAreaLights() {
		return areaLights;
	}

	public DirectionalLight getDirectionalLight() {
		return directionalLight;
	}

	public List<PointLight> getPointLights() {
		return pointLights;
	}

	public List<TubeLight> getTubeLights() {
		return tubeLights;
	}

	public void addPointLight(PointLight pointLight) {
		pointLights.add(pointLight);
		Engine.getEventBus().post(new LightChangedEvent());
	}

	public void addTubeLight(TubeLight tubeLight) {
		tubeLights.add(tubeLight);
	}


	private volatile List<Integer> cachedEntityIndices = new ArrayList();
    public int getEntityBufferIndex(Entity entity) {
    	cacheEntityIndices();
        Integer index = entitiesWithModelComponent.get(entity);
        if(index == null) { return -1; }
		Integer fromCache = cachedEntityIndices.get(index);
        return fromCache == null ? -1 : fromCache;
    }


    public Map<Entity, Integer> getEntitiesWithModelComponent() {
        return entitiesWithModelComponent;
    }
    public List<ModelComponent> getModelComponents() {
        return modelComponents;
    }

    private final Map<Entity, Integer> entitiesWithModelComponent = new ConcurrentHashMap<>();
    private final List<ModelComponent> modelComponents = new CopyOnWriteArrayList<>();
	private void cacheEntityIndices() {
		if(updateCache)
		{
			entitiesWithModelComponent.clear();
            modelComponents.clear();
            cachedEntityIndices.clear();
			int index = 0;
			int i = 0;
			for(Entity current : entityContainer.getEntities()) {
				if(!current.hasComponent(ModelComponent.class)) { continue; }
                entitiesWithModelComponent.put(current, i);
                modelComponents.add(current.getComponent(ModelComponent.class, "ModelComponent"));
				cachedEntityIndices.add(i, index);
				index += current.getInstanceCount();
				i++;
			}
			updateCache = false;
		}
	}

	public void setUpdateCache(boolean updateCache) {
		this.updateCache = updateCache;
	}
	public AtomicInteger getCurrentBaseVertex() {
		return currentBaseVertex;
	}

	public AtomicInteger getCurrentIndexOffset() {
		return currentIndexOffset;
	}

	public VertexBuffer getVertexBuffer() {
		return vertexBuffer;
	}

	public IndexBuffer getIndexBuffer() {
		return indexBuffer;
	}
}
