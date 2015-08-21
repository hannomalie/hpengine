package scene;

import camera.Camera;
import engine.World;
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
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class Scene implements LifeCycle, Serializable {
	private static final long serialVersionUID = 1L;
	
	String name = "";
	List<ProbeData> probes = new ArrayList<>();

	List<PointLightSerializationProxy> pointlightProxies = new ArrayList<>();
	List<AreaLightSerializationProxy> arealightProxies = new ArrayList<>();
	DirectionalLight directionalLight = new DirectionalLight();
	
	private Octree octree = new Octree(new Vector3f(), 400, 6);
	transient boolean initialized = false;
	transient private World world;
	transient Renderer renderer;
	private ArrayList<Entity> entities = new ArrayList<>();

	public Scene() {
		octree = new Octree(new Vector3f(), 600, 5);
	}
	public Scene(String name) {
		this.name = name;
	}

	@Override
	public void init(World world) {
		LifeCycle.super.init(world);
		renderer = world.getRenderer();
		renderer.getEnvironmentProbeFactory().clearProbes();
		octree.init(world);
		entities.forEach(entity -> entity.init(world));
		addAll(entities);
		for (ProbeData data : probes) {
			world.getRenderer().addCommand(new Command<Result>() {
				@Override
				public Result execute(World world) {
					world.getRenderer().getEnvironmentProbeFactory().getProbe(data.getCenter(), data.getSize(), data.getUpdate(), data.getWeight()).draw(world);
					return new Result() { @Override public boolean isSuccessful() { return true; } };
				}
			});
		}
		initLights(renderer);
		initialized = true;
		renderer.init(octree);
	}
	private void initLights(Renderer renderer) {
		for(PointLightSerializationProxy pointLightSerializationProxy : pointlightProxies) {
			renderer.getLightFactory().getPointLight(pointLightSerializationProxy);
		}
		for(AreaLightSerializationProxy areaLightSerializationProxy : arealightProxies) {
			renderer.getLightFactory().getAreaLight(areaLightSerializationProxy);
		}
		
		directionalLight.init(world);
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
			gatherLights();
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

	private void gatherLights() {
		pointlightProxies.clear();
		pointlightProxies.addAll(renderer.getLightFactory().getPointLightProxies());
		arealightProxies.clear();
		arealightProxies.addAll(renderer.getLightFactory().getAreaLightProxies());
	}

    private static void handleEvolution(Scene scene, Renderer renderer) {
		if(scene.getPointlights() == null) {
			scene.setPointLights(new ArrayList<PointLightSerializationProxy>());
		}
		if(scene.getAreaLights() == null) {
			scene.setAreaLights(new ArrayList<AreaLightSerializationProxy>());
		}
    }

	public static String getDirectory() {
		return World.WORKDIR_NAME + "/assets/scenes/";
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
		for (Entity entity : octree.getEntities()) {
			entity.update(seconds);
		}
	}

	private void initializationWrapped (Supplier<Void> supplier) {
		initialized = false;
		supplier.get();
		initialized = true;
	}

	@Override
	public void setWorld(World world) {
		this.world = world;
	}

	@Override
	public World getWorld() {
		return world;
	}

	public void endFrame(Camera camera) {
		for (Entity entity : octree.getEntities()) {
			entity.setHasMoved(false);
		}

		camera.saveViewMatrixAsLastViewMatrix();
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

	public void setPointLights(List<PointLightSerializationProxy> list) {
		pointlightProxies = list;
	}
	public List<PointLightSerializationProxy> getPointlights() {
		return pointlightProxies;
	}
	
	public void setAreaLights(List<AreaLightSerializationProxy> list) {
		arealightProxies = list;
	}
	public List<AreaLightSerializationProxy> getAreaLights() {
		return arealightProxies;
	}

	public DirectionalLight getDirectionalLight() {
		return directionalLight;
	}
}
