package de.hanno.hpengine.engine.scene;

import com.carrotsearch.hppc.IntArrayList;
import de.hanno.hpengine.engine.BufferableMatrix4f;
import de.hanno.hpengine.engine.camera.Camera;
import de.hanno.hpengine.engine.component.Component;
import de.hanno.hpengine.engine.component.ModelComponent;
import de.hanno.hpengine.engine.config.Config;
import de.hanno.hpengine.engine.container.EntitiesContainer;
import de.hanno.hpengine.engine.container.SimpleContainer;
import de.hanno.hpengine.engine.DirectoryManager;
import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.graphics.light.AreaLight;
import de.hanno.hpengine.engine.graphics.light.DirectionalLight;
import de.hanno.hpengine.engine.graphics.light.PointLight;
import de.hanno.hpengine.engine.graphics.light.TubeLight;
import de.hanno.hpengine.engine.graphics.renderer.RenderBatch;
import de.hanno.hpengine.engine.lifecycle.LifeCycle;
import de.hanno.hpengine.engine.model.Entity;
import de.hanno.hpengine.engine.model.Mesh;
import de.hanno.hpengine.engine.event.*;
import de.hanno.hpengine.engine.graphics.state.RenderState;
import de.hanno.hpengine.engine.graphics.shader.Program;
import de.hanno.hpengine.engine.transform.AABB;
import org.apache.commons.io.FilenameUtils;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.nustaq.serialization.FSTConfiguration;

import java.io.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static de.hanno.hpengine.engine.Engine.getEventBus;


public class Scene implements LifeCycle, Serializable {
	private static final long serialVersionUID = 1L;

	private static final Logger LOGGER = Logger.getLogger(Scene.class.getName());

	private String name = "";
	private List<ProbeData> probes = new CopyOnWriteArrayList<>();

	private transient EntitiesContainer entityContainer = new SimpleContainer();
	private transient boolean initialized = false;
	private List<Entity> entities = new CopyOnWriteArrayList<>();
	private List<BufferableMatrix4f> joints = new CopyOnWriteArrayList<>();
	private List<PointLight> pointLights = new CopyOnWriteArrayList<>();
	private List<TubeLight> tubeLights = new CopyOnWriteArrayList<>();
	private List<AreaLight> areaLights = new CopyOnWriteArrayList<>();
	private DirectionalLight directionalLight = new DirectionalLight();

	private transient volatile boolean updateCache = true;
	private transient volatile long entityMovedInCycle;
	private transient volatile long entityAddedInCycle;
	private transient volatile long directionalLightMovedInCycle;
	private transient volatile long pointLightMovedInCycle;
	private transient volatile long currentCycle;
	private transient volatile boolean initiallyDrawn;
	private Engine engine;

	public Scene() {
        this("new-scene-" + System.currentTimeMillis());
	}
	public Scene(String name) {
		this.name = name;
	}

	@Override
	public void init(Engine engine) {
		LifeCycle.super.init(engine);
		this.engine = engine;
		getEventBus().register(this);
        entityContainer = new SimpleContainer();
        entities.forEach(Entity::initialize);
        entities.forEach(entity -> entity.getComponents().values().forEach(c -> c.registerInScene(Scene.this, engine)));
		addAll(entities);
		for (ProbeData data : probes) {
            engine.getGpuContext().execute(() -> {
                try {
					// TODO: Remove this f***
					EnvironmentProbe probe = engine.getEnvironmentProbeFactory().getProbe(data.getCenter(), data.getSize(), data.getUpdate(), data.getWeight());
					engine.getRenderer().addRenderProbeCommand(probe);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
		}
		initLights();
		initialized = true;
		getEventBus().post(new SceneInitEvent());
	}
	private void initLights() {
		for(PointLight pointLight : pointLights) {
			pointLight.initialize();
		}
		for(AreaLight areaLight : areaLights) {
			areaLight.initialize();
		}

		directionalLight.addInputController();
		directionalLight.initialize();
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
//			for (EnvironmentProbe probe : Engine.getInstance().getEnvironmentProbeFactory().getProbes()) {
//				ProbeData probeData = new ProbeData(probe.getCenter(), probe.getSize(), probe.getProbeUpdate());
//				if(probes.contains(probeData)) { continue; }
//				probes.add(probeData);
//			}
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

    private static final Vector4f absoluteMaximum = new Vector4f(Float.MAX_VALUE,Float.MAX_VALUE,Float.MAX_VALUE,Float.MAX_VALUE);
    private static final Vector4f absoluteMinimum = new Vector4f(-Float.MAX_VALUE,-Float.MAX_VALUE,-Float.MAX_VALUE,-Float.MAX_VALUE);
    private Vector4f min = new Vector4f();
    private Vector4f max = new Vector4f();
    private Vector4f[] minMax = new Vector4f[]{min, max};

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
            AABB currentMinMax = entity.getMinMaxWorld();
            Vector3f currentMin = currentMinMax.getMin();
            Vector3f currentMax = currentMinMax.getMax();
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
		return DirectoryManager.WORKDIR_NAME + "/assets/scenes/";
	}
	public void addAll(List<Entity> entities) {
		entityContainer.insert(entities);
		entities.forEach(e -> e.getComponents().values().forEach(c -> {
			c.registerInScene(Scene.this, engine);
			Scene.this.register(c);
		}));
		calculateMinMax(entities);
		updateCache = true;
		entityAddedInCycle = currentCycle;
		getEventBus().post(new MaterialAddedEvent());
		getEventBus().post(new EntityAddedEvent());
	}
	public void add(Entity entity) {
        addAll(new ArrayList() {{add(entity);}});
	}

	List<ModelComponent> registeredModelComponents = new CopyOnWriteArrayList<>();
    //TODO: Handle deregistration, or prohibit it
	private void register(Component c) {
		if(c instanceof ModelComponent) {
			ModelComponent modelComponent = (ModelComponent) c;
			registeredModelComponents.add(modelComponent);
		}
	}

	public int getEntityBufferIndex(ModelComponent modelComponent) {
		cacheEntityIndices();
		int index = getModelComponents().indexOf(modelComponent);
		if(index < 0 || index > entityIndices.size()) { return -1; }
		return entityIndices.get(index);
	}


	public List<ModelComponent> getModelComponents() {
		return registeredModelComponents;
	}

	private IntArrayList entityIndices = new IntArrayList();
	private void cacheEntityIndices() {
		if(updateCache)
		{
			updateCache = false;
			entityIndices.clear();
			int index = 0;
			for(Entity current : entityContainer.getEntities()) {
				if(!current.hasComponent(ModelComponent.class)) { continue; }
				entityIndices.add(index);
				index += current.getInstanceCount() * current.getComponent(ModelComponent.class, ModelComponent.COMPONENT_KEY).getMeshes().size();
			}
		}
	}

	public void update(Engine engine, float seconds) {
		cacheEntityIndices();
		Iterator<PointLight> pointLightsIterator = pointLights.iterator();
		while (pointLightsIterator.hasNext()) {
			pointLightsIterator.next().update(engine, seconds);
		}

		for(int i = 0; i < areaLights.size(); i++) {
			areaLights.get(i).update(engine, seconds);
		}
		List<Entity> entities = entityContainer.getEntities();

		for(int i = 0; i < entities.size(); i++) {
		    try {
                entities.get(i).update(engine, seconds);
            } catch (Exception e) {
		        LOGGER.warning(e.getMessage());
            }
		}
		directionalLight.update(engine, seconds);

		for(int i = 0; i < getPointLights().size(); i++) {
			PointLight pointLight = getPointLights().get(i);
			if(!pointLight.hasMoved()) { continue; }
			pointLightMovedInCycle = currentCycle;
			getEventBus().post(new PointLightMovedEvent());
			pointLight.setHasMoved(false);
		}

		for(int i = 0; i < getEntities().size(); i++) {
			Entity entity = getEntities().get(i);
			if(!entity.hasMoved()) { continue; }
			calculateMinMax();
			entity.setHasMoved(false);
			entityMovedInCycle = currentCycle;
		}

		if(directionalLight.hasMoved()) {
			directionalLightMovedInCycle = currentCycle;
			directionalLight.setHasMoved(false);
		}
	}

	public EntitiesContainer getEntitiesContainer() {
		return entityContainer;
	}
	public List<Entity> getEntities() {
		return entityContainer.getEntities();
	}
	public Optional<Entity> getEntity(String name) {
		List<Entity> candidates = getEntities().stream().filter(e -> e.getName().equals(name)).collect(Collectors.toList());
		return candidates.size() > 0 ? Optional.of(candidates.get(0)) : Optional.ofNullable(null);
	}
	public boolean isInitialized() {
		return initialized;
	}
	public void setInitialized(boolean initialized) {
		this.initialized = initialized;
	}
	public boolean removeEntity(Entity entity) {
		return entityContainer.remove(entity);
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
		getEventBus().post(new LightChangedEvent());
	}

	public void addTubeLight(TubeLight tubeLight) {
		tubeLights.add(tubeLight);
	}

	private Vector3f tempDistVector = new Vector3f();
	public void addRenderBatches(Engine engine, Camera camera, RenderState currentWriteState) {
		Vector3f cameraWorldPosition = camera.getPosition();

		Program firstpassDefaultProgram = engine.getProgramFactory().getFirstpassDefaultProgram();

		List<ModelComponent> modelComponentsStatic = engine.getSceneManager().getScene().getModelComponents();

		addBatches(engine, camera, currentWriteState, cameraWorldPosition, firstpassDefaultProgram, modelComponentsStatic);

	}

	public void addBatches(Engine engine, Camera camera, RenderState currentWriteState, Vector3f cameraWorldPosition, Program program, List<ModelComponent> modelComponentsStatic) {
		addBatches(engine, camera, currentWriteState, cameraWorldPosition, program, modelComponentsStatic, (batch) -> {
			if(batch.isStatic()) {
				currentWriteState.addStatic(batch);
			} else {
				currentWriteState.addAnimated(batch);
			}
		});
	}

	public void addBatches(Engine engine, Camera camera, RenderState currentWriteState, Vector3f cameraWorldPosition, Program firstpassDefaultProgram, List<ModelComponent> modelComponents, Consumer<RenderBatch> addToRenderStateRunnable) {
		for (ModelComponent modelComponent : modelComponents) {
			Entity entity = modelComponent.getEntity();
			float distanceToCamera = tempDistVector.length();
			boolean isInReachForTextureLoading = distanceToCamera < 50 || distanceToCamera < 2.5f * modelComponent.getBoundingSphereRadius();

			int entityIndexOf = engine.getSceneManager().getScene().getEntityBufferIndex(entity.getComponent(ModelComponent.class, ModelComponent.COMPONENT_KEY));

			List<Mesh> meshes = modelComponent.getMeshes();
			for(int i = 0; i < meshes.size(); i++) {
				Mesh mesh = meshes.get(i);
				Vector3f meshCenter = mesh.getCenter(entity);
				float boundingSphereRadius = modelComponent.getBoundingSphereRadius(mesh);
				boolean meshIsInFrustum = camera.getFrustum().sphereInFrustum(meshCenter.x, meshCenter.y, meshCenter.z, boundingSphereRadius);//TODO: Fix this
				boolean visibleForCamera = meshIsInFrustum || entity.getInstanceCount() > 1; // TODO: Better culling for instances

				mesh.getMaterial().setTexturesUsed();
				AABB meshMinMax = modelComponent.getMinMax(entity, mesh);
				int meshBufferIndex = entityIndexOf + i * entity.getInstanceCount();

				RenderBatch batch = currentWriteState.entitiesState.cash.computeIfAbsent(new BatchKey(mesh, -1), k -> new RenderBatch());
				batch.init(firstpassDefaultProgram, meshBufferIndex, entity.isVisible(), entity.isSelected(), Config.getInstance().isDrawLines(), cameraWorldPosition, isInReachForTextureLoading, entity.getInstanceCount(), visibleForCamera, entity.getUpdate(), meshMinMax.getMin(), meshMinMax.getMax(), meshCenter, boundingSphereRadius, modelComponent.getIndexCount(i), modelComponent.getIndexOffset(i), modelComponent.getBaseVertex(i), !modelComponent.getModel().isStatic(), entity.getInstanceMinMaxWorlds());
				addToRenderStateRunnable.accept(batch);
			}
		}
	}

	public void setUpdateCache(boolean updateCache) {
		this.updateCache = updateCache;
	}

	public long entityMovedInCycle() {
		return entityMovedInCycle;
	}

	public void setCurrentCycle(long currentCycle) {
		this.currentCycle = currentCycle;
	}

	public long directionalLightMovedInCycle() {
		return directionalLightMovedInCycle;
	}

	public long pointLightMovedInCycle() {
		return pointLightMovedInCycle;
	}

	public boolean isInitiallyDrawn() {
		return initiallyDrawn;
	}

	public void setInitiallyDrawn(boolean initiallyDrawn) {
		this.initiallyDrawn = initiallyDrawn;
	}

	public long getEntityAddedInCycle() {
		return entityAddedInCycle;
	}

	public List<BufferableMatrix4f> getJoints() {
		return joints;
	}
}
