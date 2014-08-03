package main.scene;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import main.model.DataChannels;
import main.model.IEntity;
import main.model.VertexBuffer;
import main.octree.Octree;
import main.renderer.Renderer;
import main.shader.Program;

import org.lwjgl.util.vector.Vector3f;

public class EnvironmentProbeFactory {
	
	private Renderer renderer;
	
	private List<EnvironmentProbe> probes = new ArrayList<>();

	public EnvironmentProbeFactory(Renderer renderer) {
		this.renderer = renderer;
	}

	public EnvironmentProbe getProbe(Vector3f center, float size) {
		EnvironmentProbe probe = new EnvironmentProbe(renderer, center, size);
		probes.add(probe);
		return probe;
	}
	
	public void draw(Octree octree) {
		for (EnvironmentProbe environmentProbe : probes) {
			environmentProbe.draw(octree);
		}
	}
	
	public void drawDebug(Program program) {
		List<float[]> arrays = new ArrayList<>();
		
		for (EnvironmentProbe probe : renderer.getEnvironmentProbeFactory().getProbes()) {
			probe.drawDebug(program);
			arrays.add(probe.getBox().getPointsAsArray());
		}
		
		// 72 floats per array
		float[] points = new float[arrays.size() * 72];
		for (int i = 0; i < arrays.size(); i++) {
			float[] array = arrays.get(i);
			for (int z = 0; z < 72; z++) {
				points[24*3*i + z] = array[z];
			}
		};
		VertexBuffer buffer = new VertexBuffer(points, EnumSet.of(DataChannels.POSITION3)).upload();
		buffer.drawDebug();
	}
	
	public<T extends IEntity> Optional<EnvironmentProbe> getProbeForEntity(T entity) {
//		return probes.stream().filter(probe -> {
//			return probe.contains(entity.getMinMaxWorld());
//		}).findFirst();
		
		for (EnvironmentProbe environmentProbe : probes) {
			if(environmentProbe.contains(entity.getMinMaxWorld())) {
				return Optional.of(environmentProbe);
			}
		}
		return Optional.empty();
	}
	
	public List<EnvironmentProbe> getProbes() {
		return probes;
	}
}
