package scene;

import camera.Camera;
import com.google.common.eventbus.Subscribe;
import engine.AppContext;
import engine.lifecycle.LifeCycle;
import engine.model.Entity;
import event.*;
import net.engio.mbassy.listener.Handler;
import octree.Octree;
import org.apache.commons.io.FilenameUtils;
import org.lwjgl.util.vector.Vector3f;
import org.nustaq.serialization.FSTConfiguration;
import renderer.OpenGLContext;
import renderer.Renderer;
import renderer.light.AreaLight;
import renderer.light.DirectionalLight;
import renderer.light.PointLight;
import renderer.light.TubeLight;
import shader.OpenGLBuffer;
import shader.PersistentMappedStorageBuffer;
import shader.StorageBuffer;
import util.Util;

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

    // TODO: Move this to the factory maybe?
    private volatile transient OpenGLBuffer entitiesBuffer;
	
	private Octree octree = new Octree(new Vector3f(), 400, 6);
	transient boolean initialized = false;
	private List<Entity> entities = new CopyOnWriteArrayList<>();
	private List<PointLight> pointLights = new CopyOnWriteArrayList<>();
	private List<TubeLight> tubeLights = new CopyOnWriteArrayList<>();
	private List<AreaLight> areaLights = new CopyOnWriteArrayList<>();
	private DirectionalLight directionalLight = new DirectionalLight();

	public Scene() {
		octree = new Octree(new Vector3f(), 600, 5);
	}
	public Scene(String name) {
		this.name = name;
	}

	@Override
	public void init() {
		LifeCycle.super.init();
//        entitiesBuffer = new StorageBuffer(32000);
        entitiesBuffer = new PersistentMappedStorageBuffer(16000);
		EnvironmentProbeFactory.getInstance().clearProbes();
		octree.init();
		entities.forEach(entity -> entity.init());
		addAll(entities);
		for (ProbeData data : probes) {
			OpenGLContext.getInstance().execute(() -> {
                try {
                    EnvironmentProbeFactory.getInstance().getProbe(data.getCenter(), data.getSize(), data.getUpdate(), data.getWeight()).draw();
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
			entities.addAll(octree.getEntities());
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

	public static Scene read(Renderer renderer, String name) {
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

			handleEvolution(scene, renderer);
			in.close();
			fis.close();
			scene.octree = new Octree(new Vector3f(), 400, 6);
			return scene;
		} catch (IOException | ClassNotFoundException e) {
			e.printStackTrace();
		}
		return null;
	}

	static FSTConfiguration conf = FSTConfiguration.createDefaultConfiguration();

    private static void handleEvolution(Scene scene, Renderer renderer) {
    }

	public static String getDirectory() {
		return AppContext.WORKDIR_NAME + "/assets/scenes/";
	}
	public void addAll(List<Entity> entities) {
//		initializationWrapped(() -> {
			octree.insert(entities);
//			return null;
//		});
	}
	public void add(Entity entity) {
//		initializationWrapped(() -> {
			octree.insert(entity);
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
		for (Entity entity : octree.getEntities()) {
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
		for (Entity entity : octree.getEntities()) {
			entity.setHasMoved(false);
		}
       getDirectionalLight().setHasMoved(false);
	}
	public Octree getOctree() {
		return octree;
	}
	public List<Entity> getEntities() {
		return octree.getEntities();
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
		return octree.removeEntity(entity);
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

    public synchronized void bufferEntities() {
        entitiesBuffer.put(Util.toArray(getEntities(), Entity.class));
    }

    @Subscribe
    @Handler
    public void handle(MaterialChangedEvent event) {
        bufferEntities();
    }
    @Subscribe
    @Handler
    public void handle(MaterialAddedEvent event) {
        bufferEntities();
    }
    @Subscribe
    @Handler
    public void handle(EntityAddedEvent event) {
        bufferEntities();
    }

    public OpenGLBuffer getEntitiesBuffer() {
        return entitiesBuffer;
    }
}
