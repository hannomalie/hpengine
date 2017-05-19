package de.hanno.hpengine.scene;

import com.carrotsearch.hppc.IntArrayList;
import de.hanno.hpengine.camera.Camera;
import de.hanno.hpengine.component.Component;
import de.hanno.hpengine.component.ModelComponent;
import de.hanno.hpengine.config.Config;
import de.hanno.hpengine.container.EntitiesContainer;
import de.hanno.hpengine.container.SimpleContainer;
import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.PerMeshInfo;
import de.hanno.hpengine.engine.lifecycle.LifeCycle;
import de.hanno.hpengine.engine.model.Entity;
import de.hanno.hpengine.engine.model.Mesh;
import de.hanno.hpengine.event.*;
import de.hanno.hpengine.renderer.GraphicsContext;
import de.hanno.hpengine.renderer.Renderer;
import de.hanno.hpengine.renderer.light.AreaLight;
import de.hanno.hpengine.renderer.light.DirectionalLight;
import de.hanno.hpengine.renderer.light.PointLight;
import de.hanno.hpengine.renderer.light.TubeLight;
import de.hanno.hpengine.renderer.state.RenderState;
import de.hanno.hpengine.shader.Program;
import de.hanno.hpengine.shader.ProgramFactory;
import org.apache.commons.io.FilenameUtils;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;
import org.nustaq.serialization.FSTConfiguration;

import java.io.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static de.hanno.hpengine.engine.Engine.getEventBus;


public class Scene implements LifeCycle, Serializable {
	private static final long serialVersionUID = 1L;

	private static final Logger LOGGER = Logger.getLogger(Scene.class.getName());
	private final VertexIndexBuffer vertexIndexBuffer = new VertexIndexBuffer(10, 10);

	private String name = "";
	private List<ProbeData> probes = new CopyOnWriteArrayList<>();

	private transient EntitiesContainer entityContainer = new SimpleContainer();
	private transient boolean initialized = false;
	private List<Entity> entities = new CopyOnWriteArrayList<>();
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

	public Scene() {
        this("new-scene-" + System.currentTimeMillis());
	}
	public Scene(String name) {
		this.name = name;
	}

	@Override
	public void init() {
		LifeCycle.super.init();
		getEventBus().register(this);
		EnvironmentProbeFactory.getInstance().clearProbes();
        entityContainer = new SimpleContainer();
		entityContainer.init();
        entities.forEach(Entity::init);
        entities.forEach(entity -> entity.getComponents().values().forEach(c -> c.registerInScene(Scene.this)));
		addAll(entities);
		for (ProbeData data : probes) {
            GraphicsContext.getInstance().execute(() -> {
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
		getEventBus().post(new SceneInitEvent());
	}
	private void initLights() {
		for(PointLight pointLight : pointLights) {
			pointLight.init();
		}
		for(AreaLight areaLight : areaLights) {
			areaLight.init();
		}

		directionalLight.addInputController();
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
            Vector3f[] currentMinMax = entity.getMinMaxWorld();
            Vector3f currentMin = currentMinMax[0];
            Vector3f currentMax = currentMinMax[1];
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
		entities.forEach(e -> e.getComponents().values().forEach(c -> {
            c.registerInScene(Scene.this);
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

	List<ModelComponent> registeredModelComponents = new ArrayList();
    //TODO: Handle deregistration, or prohibit it
	private void register(Component c) {
		if(c instanceof ModelComponent) {
			registeredModelComponents.add(((ModelComponent)c));
		}
	}

	public int getEntityBufferIndex(ModelComponent modelComponent) {
		cacheEntityIndices();
		return entityIndices.get(getModelComponents().indexOf(modelComponent));
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

	public void update(float seconds) {
		cacheEntityIndices();
		Iterator<PointLight> pointLightsIterator = pointLights.iterator();
		while (pointLightsIterator.hasNext()) {
			pointLightsIterator.next().update(seconds);
		}

		for(int i = 0; i < areaLights.size(); i++) {
			areaLights.get(i).update(seconds);
		}
		List<Entity> entities = entityContainer.getEntities();
		for(int i = 0; i < entities.size(); i++) {
		    try {
                entities.get(i).update(seconds);
            } catch (Exception e) {
		        LOGGER.warning(e.getMessage());
            }
		}
		directionalLight.update(seconds);

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
		getEventBus().post(new LightChangedEvent());
	}

	public void addTubeLight(TubeLight tubeLight) {
		tubeLights.add(tubeLight);
	}

	private Vector3f tempDistVector = new Vector3f();
	public void addPerMeshInfos(Camera camera, RenderState currentWriteState) {
		Vector3f cameraWorldPosition = camera.getWorldPosition();

		Program firstpassDefaultProgram = ProgramFactory.getInstance().getFirstpassDefaultProgram();

		List<ModelComponent> modelComponents = Engine.getInstance().getScene().getModelComponents();

		for (ModelComponent modelComponent : modelComponents) {
			Entity entity = modelComponent.getEntity();
			Vector3f centerWorld = entity.getCenterWorld();
			Vector3f.sub(cameraWorldPosition, centerWorld, tempDistVector);
			float distanceToCamera = tempDistVector.length();
			boolean isInReachForTextureLoading = distanceToCamera < 50 || distanceToCamera < 2.5f * modelComponent.getBoundingSphereRadius();

			int entityIndexOf = Engine.getInstance().getScene().getEntityBufferIndex(entity.getComponent(ModelComponent.class, ModelComponent.COMPONENT_KEY));

			for(int i = 0; i < modelComponent.getMeshes().size(); i++) {
				Mesh mesh = modelComponent.getMeshes().get(i);
				boolean meshIsInFrustum = camera.getFrustum().sphereInFrustum(mesh.getCenter().x, mesh.getCenter().y, mesh.getCenter().z, mesh.getBoundingSphereRadius());
				boolean visibleForCamera = meshIsInFrustum || entity.getInstanceCount() > 1; // TODO: Better culling for instances

				mesh.getMaterial().setTexturesUsed();
				PerMeshInfo info = currentWriteState.entitiesState.cash.computeIfAbsent(mesh, k -> new PerMeshInfo());
				Vector3f[] meshMinMax = mesh.getMinMax(entity.getModelMatrix());
				int meshBufferIndex = entityIndexOf + i * entity.getInstanceCount();

				info.init(firstpassDefaultProgram, meshBufferIndex, entity.isVisible(), entity.isSelected(), Config.getInstance().isDrawLines(), cameraWorldPosition, isInReachForTextureLoading, entity.getInstanceCount(), visibleForCamera, entity.getUpdate(), meshMinMax[0], meshMinMax[1], meshMinMax[0], meshMinMax[1], mesh.getCenter(), modelComponent.getIndexCount(i), modelComponent.getIndexOffset(i), modelComponent.getBaseVertex(i));
				currentWriteState.add(info);
			}
		}
	}

	public void setUpdateCache(boolean updateCache) {
		this.updateCache = updateCache;
	}

	public VertexIndexBuffer getVertexIndexBuffer() {
		return vertexIndexBuffer;
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
}
