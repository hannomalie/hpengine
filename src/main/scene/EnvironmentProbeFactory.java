package main.scene;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import main.World;
import main.model.DataChannels;
import main.model.Entity;
import main.model.IEntity;
import main.model.VertexBuffer;
import main.octree.Octree;
import main.renderer.DeferredRenderer;
import main.renderer.Renderer;
import main.renderer.light.Spotlight;
import main.renderer.rendertarget.CubeRenderTarget;
import main.scene.EnvironmentProbe.Update;
import main.shader.Program;
import main.texture.CubeMapArray;

import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Vector3f;

public class EnvironmentProbeFactory {
	public static final int RESOLUTION = 512;
	
	public static Update DEFAULT_PROBE_UPDATE = Update.DYNAMIC;

	private Renderer renderer;
	
	private List<EnvironmentProbe> probes = new ArrayList<>();
	
	private CubeMapArray environmentMapsArray;

	public EnvironmentProbeFactory(Renderer renderer) {
		this.renderer = renderer;
		this.environmentMapsArray = new CubeMapArray(renderer, 192);
		

//		int cubeMapArray = GL11.glGenTextures();
//		GL11.glBindTexture(GL40.GL_TEXTURE_CUBE_MAP_ARRAY, cubeMapArray);
//		GL42.glTexStorage3D(GL40.GL_TEXTURE_CUBE_MAP_ARRAY, 1, GL11.GL_RGBA8, EnvironmentProbeFactory.RESOLUTION, EnvironmentProbeFactory.RESOLUTION, 10);
//		//glTexSubImage http://books.google.de/books?id=jG4LGmH5RuIC&pg=PT489&lpg=PT489&dq=GL_TEXTURE_CUBE_MAP_ARRAY&source=bl&ots=q2ARu8mNBX&sig=TCA9cvRh1NehCqZHaqHjYhmWfc8&hl=en&sa=X&ei=LU0CVNSWDc7VaoWIgbgM&ved=0CFAQ6AEwBA#v=onepage&q=GL_TEXTURE_CUBE_MAP_ARRAY&f=false

		DeferredRenderer.exitOnGLError("EnvironmentProbeFactory constructor");
	}

	public EnvironmentProbe getProbe(Vector3f center, float size) {
		return getProbe(center, size, DEFAULT_PROBE_UPDATE);
	}

	public EnvironmentProbe getProbe(Vector3f center, float size, Update update) {
		return getProbe(center, new Vector3f(size, size, size), update);
	}
	public EnvironmentProbe getProbe(Vector3f center, Vector3f size, Update update) {
		EnvironmentProbe probe = new EnvironmentProbe(renderer, center, size, RESOLUTION, update);
		probes.add(probe);
//		probe.bind(probe.getTextureUnitIndex());
		renderer.getEnvironmentProbeFactory().bindProbes();
		return probe;
	}
	
	public void bindProbes() {
		for (EnvironmentProbe environmentProbe : probes) {
			environmentProbe.bind(environmentProbe.getTextureUnitIndex());
		}
	}
	
	public void draw(Octree octree, Spotlight light) {
		if(!World.DRAW_PROBES) { return; }
		
		List<EnvironmentProbe> dynamicProbes = probes.stream().filter(probe -> { return probe.update == Update.DYNAMIC; }).collect(Collectors.toList());
		for (EnvironmentProbe environmentProbe : dynamicProbes) {
			environmentProbe.draw(octree, light);
		}
	}
	
	public void drawAlternating(Octree octree, Spotlight light, int frameCount) {
		if(!World.DRAW_PROBES) { return; }

		GL11.glDepthMask(true);
		GL11.glEnable(GL11.GL_DEPTH_TEST);
		
		List<EnvironmentProbe> dynamicProbes = probes.stream().filter(probe -> { return probe.update == Update.DYNAMIC; }).collect(Collectors.toList());
		
		for (int i = 1; i <= dynamicProbes.size(); i++) {
			if(frameCount%i == 0) {
				EnvironmentProbe environmentProbe = dynamicProbes.get(i-1);
				environmentProbe.draw(octree, light);
//				environmentMapsArray.copyCubeMapIntoIndex(environmentProbe.getEnvironmentMap().getTextureID(), i-1);
			};
		}
	}
	
	public void drawDebug(Program program, Octree octree) {
		List<float[]> arrays = new ArrayList<>();
		
		for (EnvironmentProbe probe : renderer.getEnvironmentProbeFactory().getProbes()) {
//			probe.drawDebug(program);
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
		program.setUniform("materialDiffuseColor", new Vector3f(0,1,0));
		buffer.drawDebug();
		
		octree.getEntities().stream().forEach(e -> {
			Optional<EnvironmentProbe> option = getProbeForEntity(e);
			option.ifPresent(probe -> {
				renderer.drawLine(probe.getCenter(), e.getPosition());
			});
		});
	}
	
	public<T extends IEntity> Optional<EnvironmentProbe> getProbeForEntity(T entity) {
		return probes.stream().filter(probe -> {
			return probe.contains(entity.getMinMaxWorld());
		}).sorted(new Comparator<EnvironmentProbe>() {
			@Override
			public int compare(EnvironmentProbe o1, EnvironmentProbe o2) {
				return (Float.compare(Vector3f.sub(entity.getCenter(), o1.getCenter(), null).length(), Vector3f.sub(entity.getCenter(), o2.getCenter(), null).length()));
			}
		}).findFirst();
		
//		for (EnvironmentProbe environmentProbe : probes) {
//			if(environmentProbe.contains(entity.getMinMaxWorld())) {
//				System.out.println("Returning " + environmentProbe.getPosition());
//				return Optional.of(environmentProbe);
//			}
//		}
//		return Optional.empty();
	}
	
	public List<EnvironmentProbe> getProbes() {
		return probes;
	}

	public void drawInitial(Octree octree) {
		List<EnvironmentProbe> staticProbes = probes.stream().filter(probe -> { return probe.update == Update.STATIC; }).collect(Collectors.toList());
		for (EnvironmentProbe environmentProbe : staticProbes) {
			environmentProbe.draw(octree, World.light);
		}
	}
	
	public CubeMapArray getEnvironmentMapsArray() {
		return environmentMapsArray;
	}

	public List<EnvironmentProbe> getProbesForEntity(Entity entity) {
		return probes.stream().filter(probe -> {
			return probe.contains(entity.getMinMaxWorld());
		}).sorted(new Comparator<EnvironmentProbe>() {
			@Override
			public int compare(EnvironmentProbe o1, EnvironmentProbe o2) {
				return (Float.compare(Vector3f.sub(entity.getCenter(), o1.getCenter(), null).length(), Vector3f.sub(entity.getCenter(), o2.getCenter(), null).length()));
			}
		}).collect(Collectors.toList());
	}

	public void remove(EnvironmentProbe probe) {
		probes.remove(probe);
	}
}
