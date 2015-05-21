package main.scene;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import main.World;
import main.camera.Camera;
import main.model.Entity;
import main.model.IEntity;
import main.model.Entity.Update;
import main.octree.Octree;
import main.renderer.DeferredRenderer;
import main.renderer.Renderer;
import main.renderer.light.AreaLightSerializationProxy;
import main.renderer.light.DirectionalLight;
import main.renderer.light.DirectionalLightSerializationProxy;
import main.renderer.light.PointLight;
import main.renderer.light.PointLightSerializationProxy;

import org.apache.commons.io.FilenameUtils;
import org.lwjgl.util.vector.Vector3f;

public class Scene implements Serializable {
	private static final long serialVersionUID = 1L;
	
	String name = "";
	List<String> entitieNames = new ArrayList<>();
	List<ProbeData> probes = new ArrayList<>();

	List<PointLight> pointlights;
	List<PointLightSerializationProxy> pointlightProxies;
	List<AreaLightSerializationProxy> arealightProxies;
	DirectionalLightSerializationProxy directionalLight;
	
	transient Octree octree;// = new Octree(renderer, new Vector3f(), 400, 6);
	transient boolean initialized = false;
	transient Renderer renderer;

	public Scene(Renderer renderer) {
		octree = new Octree(renderer, new Vector3f(), 600, 5);
		this.renderer = renderer;
	}
	public Scene(String name) {
		this.name = name;
	}

	public void init(Renderer renderer) {
		List<IEntity> entities = new ArrayList<>();
		if (probes == null) { probes = new ArrayList<>(); }
		renderer.getEnvironmentProbeFactory().clearProbes();
		octree = new Octree(renderer, new Vector3f(), 600, 5);
		entitieNames.forEach(name -> {entities .add(renderer.getEntityFactory().read(name));});
		octree.insert(entities);
		for (ProbeData data : probes) {
			renderer.getEnvironmentProbeFactory().getProbe(data.getCenter(), data.getSize(), data.getUpdate(), data.getWeight());	
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
		
		renderer.getLightFactory().setDirectionalLight(directionalLight);
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
			entitieNames = octree.getEntities().stream().map(e -> e.getName()).collect(Collectors.toList());
			octree.getEntities().stream().forEach(e -> {
				Entity.write((Entity) e, e.getName());
			});
			probes.clear();
			for (EnvironmentProbe probe : renderer.getEnvironmentProbeFactory().getProbes()) {
				ProbeData probeData = new ProbeData(probe.getCenter(), probe.getSize(), probe.getProbeUpdate());
				if(probes.contains(probeData)) { continue; }
				probes.add(probeData);
			}
			gatherLights();
			out.writeObject(this);

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
	private void gatherLights() {
		pointlightProxies.clear();
		pointlightProxies.addAll(renderer.getLightFactory().getPointLightProxies());
		arealightProxies.clear();
		arealightProxies.addAll(renderer.getLightFactory().getAreaLightProxies());
		directionalLight = new DirectionalLightSerializationProxy(renderer.getLightFactory().getDirectionalLight());
	}
	
	public static Scene read(Renderer renderer, String name) {
		String fileName = FilenameUtils.getBaseName(name);
		FileInputStream fis = null;
		ObjectInputStream in = null;
		try {
			fis = new FileInputStream(getDirectory() + fileName + ".hpscene");
			in = new ObjectInputStream(fis);
			Scene scene = (Scene) in.readObject();
			handleEvolution(scene, renderer);
			in.close();
			fis.close();
			scene.renderer = renderer;
			scene.octree = new Octree(renderer, new Vector3f(), 400, 6);
			return scene;
		} catch (IOException | ClassNotFoundException e) {
			e.printStackTrace();
		}
		return null;
	}

    private static void handleEvolution(Scene scene, Renderer renderer) {
		if(scene.getPointlights() == null) {
			scene.setPointLights(new ArrayList<PointLightSerializationProxy>());
		}
		if(scene.getAreaLights() == null) {
			scene.setAreaLights(new ArrayList<AreaLightSerializationProxy>());
		}
		if(scene.directionalLight == null) {
			scene.directionalLight = new DirectionalLightSerializationProxy();
		}
    }

	public static String getDirectory() {
		return World.WORKDIR_NAME + "/assets/scenes/";
	}
	public void addAll(List<IEntity> entities) {
		octree.insert(entities);
	}
	public void update(float seconds) {
		if(!initialized) {
			init(renderer);
		}
		for (IEntity entity : octree.getEntities()) {
			entity.update(seconds);
		}
	}
	public void endFrame(Camera camera) {
		DirectionalLight light = renderer.getLightFactory().getDirectionalLight();
		
		for (IEntity entity : octree.getEntities()) {
			entity.setHasMoved(false);
		}
		for (IEntity entity : renderer.getLightFactory().getPointLights()) {
			entity.setHasMoved(false);
		}
		for (IEntity entity : renderer.getLightFactory().getAreaLights()) {
			entity.setHasMoved(false);
		}
		light.setHasMoved(false);
		camera.saveViewMatrixAsLastViewMatrix();
	}
	public Octree getOctree() {
		return octree;
	}
	public List<IEntity> getEntities() {
		return octree.getEntities();
	}
	public Optional<IEntity> getEntity(String name) {
		List<IEntity> candidates = getEntities().stream().filter(e -> { return e.getName().equals(name); }).collect(Collectors.toList());
		return candidates.size() > 0 ? Optional.of(candidates.get(0)) : Optional.of(null);
	}
	public boolean isInitialized() {
		return initialized;
	}
	public void setInitialized(boolean initialized) {
		this.initialized = initialized;
	}
	public boolean removeEntity(IEntity entity) {
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
}
