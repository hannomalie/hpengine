package scene;

import camera.Camera;
import engine.AppContext;
import engine.lifecycle.LifeCycle;
import engine.model.Entity;
import octree.Octree;
import org.apache.commons.io.FilenameUtils;
import org.lwjgl.util.vector.Vector3f;
import org.nustaq.serialization.FSTConfiguration;
import renderer.Renderer;
import renderer.command.Result;
import renderer.command.Command;
import renderer.light.*;

import java.io.*;
import java.util.ArrayList;
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

	
	private Octree octree = new Octree(new Vector3f(), 400, 6);
	transient boolean initialized = false;
	transient private AppContext appContext;
	transient Renderer renderer;
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
	public void init(AppContext appContext) {
		LifeCycle.super.init(appContext);
		renderer = appContext.getRenderer();
		renderer.getEnvironmentProbeFactory().clearProbes();
		octree.init(appContext);
		entities.forEach(entity -> entity.init(appContext));
		addAll(entities);
		for (ProbeData data : probes) {
			appContext.getRenderer().addCommand(new Command<Result>() {
				@Override
				public Result execute(AppContext appContext) {
					appContext.getRenderer().getEnvironmentProbeFactory().getProbe(data.getCenter(), data.getSize(), data.getUpdate(), data.getWeight()).draw(appContext);
					return new Result() {
						@Override
						public boolean isSuccessful() {
							return true;
						}
					};
				}
			});
		}
		initLights();
		initialized = true;
		renderer.init(octree);
	}
	private void initLights() {
		for(PointLight pointLight : pointLights) {
			pointLight.init(appContext);
		}
		for(AreaLight areaLight : areaLights) {
			areaLight.init(appContext);
		}
		
		directionalLight.init(appContext);
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
			for (EnvironmentProbe probe : renderer.getEnvironmentProbeFactory().getProbes()) {
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
			scene.renderer = renderer;
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

	@Override
	public void setAppContext(AppContext appContext) {
		this.appContext = appContext;
	}

	@Override
	public AppContext getAppContext() {
		return appContext;
	}

	public void endFrame(Camera camera) {
		for (Entity entity : octree.getEntities()) {
			entity.setHasMoved(false);
		}
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
	}

	public void addTubeLight(TubeLight tubeLight) {
		tubeLights.add(tubeLight);
	}
}
