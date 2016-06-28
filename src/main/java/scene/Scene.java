package scene;

import camera.Camera;
import container.EntitiesContainer;
import container.SimpleContainer;
import engine.AppContext;
import engine.lifecycle.LifeCycle;
import engine.model.Entity;
import event.LightChangedEvent;
import event.SceneInitEvent;
import org.apache.commons.io.FilenameUtils;
import org.nustaq.serialization.FSTConfiguration;
import renderer.OpenGLContext;
import renderer.RenderExtract;
import renderer.light.AreaLight;
import renderer.light.DirectionalLight;
import renderer.light.PointLight;
import renderer.light.TubeLight;

import java.io.*;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class Scene implements LifeCycle, Serializable {
	private static final long serialVersionUID = 1L;
	
	String name = "";
	List<ProbeData> probes = new CopyOnWriteArrayList<>();

	private transient EntitiesContainer entityContainer;// = new Octree(new Vector3f(), 400, 6);
	transient boolean initialized = false;
	private List<Entity> entities = new CopyOnWriteArrayList<>();
	private List<PointLight> pointLights = new CopyOnWriteArrayList<>();
	private List<TubeLight> tubeLights = new CopyOnWriteArrayList<>();
	private List<AreaLight> areaLights = new CopyOnWriteArrayList<>();
	private DirectionalLight directionalLight = new DirectionalLight();

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
		addAll(entities);
		for (ProbeData data : probes) {
			OpenGLContext.getInstance().execute(() -> {
                try {
                    AppContext appContext = AppContext.getInstance();
                    EnvironmentProbeFactory.getInstance().getProbe(data.getCenter(), data.getSize(), data.getUpdate(), data.getWeight()).draw(new RenderExtract(appContext.getActiveCamera(), appContext.getScene().getEntities(), appContext.getScene().getDirectionalLight(),true,true,true,false));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
		}
		initLights();
		initialized = true;
		AppContext.getEventBus().post(new SceneInitEvent());
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
//			Scene scene = (Scene)newIn.readObject();
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

	static FSTConfiguration conf = FSTConfiguration.createDefaultConfiguration();

    private static void handleEvolution(Scene scene) {
    }

	public static String getDirectory() {
		return AppContext.WORKDIR_NAME + "/assets/scenes/";
	}
	public void addAll(List<Entity> entities) {
//		initializationWrapped(() -> {
			entityContainer.insert(entities);
//			return null;
//		});
	}
	public void add(Entity entity) {
//		initializationWrapped(() -> {
			entityContainer.insert(entity);
//			return null;
//		});
	}
	public void update(float seconds) {
		Iterator<PointLight> pointLightsIterator = pointLights.iterator();
		while (pointLightsIterator.hasNext()) {
			pointLightsIterator.next().update(seconds);
		}

		for (AreaLight areaLight : areaLights) {
			areaLight.update(seconds);
		}
		for (Entity entity : entityContainer.getEntities()) {
			entity.update(seconds);
		}
		directionalLight.update(seconds);
	}

	private void initializationWrapped (Supplier<Void> supplier) {
		initialized = false;
		supplier.get();
		initialized = true;
	}

    public void endFrame(Camera camera) {
		for (Entity entity : entityContainer.getEntities()) {
			entity.setHasMoved(false);
		}
       getDirectionalLight().setHasMoved(false);
	}
	public EntitiesContainer getEntitiesContainer() {
		return entityContainer;
	}
	public List<Entity> getEntities() {
		return entityContainer.getEntities();
	}
	public Optional<Entity> getEntity(String name) {
		List<Entity> candidates = getEntities().stream().filter(e -> { return e.getName().equals(name); }).collect(Collectors.toList());
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
		AppContext.getEventBus().post(new LightChangedEvent());
	}

	public void addTubeLight(TubeLight tubeLight) {
		tubeLights.add(tubeLight);
	}

}
