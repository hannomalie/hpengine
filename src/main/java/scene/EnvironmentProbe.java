package scene;

import java.util.List;
import java.util.Random;

import camera.Camera;
import engine.World;
import engine.model.Entity;
import octree.Octree;
import renderer.environmentsampler.EnvironmentSampler;
import renderer.Renderer;
import shader.Program;

import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;

public class EnvironmentProbe extends Entity {
	
	public enum Update {
		STATIC,
		DYNAMIC
	}
	
	private String name = "Probe_" + System.currentTimeMillis();
	private Renderer renderer;
	private AABB box;
	private EnvironmentSampler sampler;
	protected Update update;
	private float weight;
	

	protected EnvironmentProbe(World world, Vector3f center, Vector3f size, int resolution, Update update, int probeIndex, float weight) {
		this.renderer = world.getRenderer();
		this.update = update;
		box = new AABB(center, size.x, size.y, size.z);
		sampler = new EnvironmentSampler(world, this, center, resolution, resolution, probeIndex);
		sampler.init(world);
		this.setWeight(weight);
		super.init(world);
	}

	public void draw(Octree octree) {
		draw(octree, false);
	};
	public void draw(Octree octree, boolean urgent) {
		sampler.drawCubeMap(octree, urgent);
	};
	
	public void drawDebug(Program program) {
		List<Vector3f> points = box.getPoints();
		for (int i = 0; i < points.size() - 1; i++) {
			renderer.drawLine(points.get(i), points.get(i+1));
		}

		renderer.drawLine(points.get(3), points.get(0));
		renderer.drawLine(points.get(7), points.get(4));

		renderer.drawLine(points.get(0), points.get(6));
		renderer.drawLine(points.get(1), points.get(7));
		renderer.drawLine(points.get(2), points.get(4));
		renderer.drawLine(points.get(3), points.get(5));

		renderer.drawLine(getSampler().getPosition(), Vector3f.add(getSampler().getPosition(), new Vector3f(5, 0, 0), null));
		renderer.drawLine(getSampler().getPosition(), Vector3f.add(getSampler().getPosition(), new Vector3f(0, 5, 0), null));
		renderer.drawLine(getSampler().getPosition(), Vector3f.add(getSampler().getPosition(), new Vector3f(0, 0, -5), null));

		float temp = (float)getIndex()/10;
		program.setUniform("diffuseColor", new Vector3f(temp,1-temp,0));
	    renderer.drawLines(program);
		
//		renderer.drawLine(box.getBottomLeftBackCorner(), sampler.getCamera().getPosition());
	}

	@Override
	public void move(Vector3f amount) {
		super.move(amount);
		resetAllProbes();
		renderer.getEnvironmentProbeFactory().updateBuffers();
		box.move(amount);
	}
	
	@Override
	public void moveInWorld(Vector3f amount) {
		super.moveInWorld(amount);
		resetAllProbes();
		box.move(amount);
		renderer.getEnvironmentProbeFactory().updateBuffers();
	}
	
	@Override
	public void setPosition(Vector3f position) {
		super.setPosition(position);
		resetAllProbes();
		box.setCenter(position);
		renderer.getEnvironmentProbeFactory().updateBuffers();
	}

	private void resetAllProbes() {
		world.getRenderer().getEnvironmentProbeFactory().getProbes().forEach(probe -> {
			probe.getSampler().resetDrawing();
		});
	}

	@Override
	public Vector3f getCenter() { return getPosition(); }
	
	@Override
	public String getName() {
		return name ;
	}

	@Override
	public boolean isSelected() {
		return false;
	}

	@Override
	public void setSelected(boolean selected) {
	}

	public boolean contains(Vector4f min, Vector4f max) {
		return box.contains(min) && box.contains(max);
	}

	public boolean contains(Vector4f[] minMaxWorld) {
		return contains(minMaxWorld[0], minMaxWorld[1]);
	}

	public AABB getBox() {
		return box;
	}

	public void setUpdate(Update update) {
		this.update = update;
	}

	public Update getProbeUpdate() {
		return update;
	}

	public void setSize(float size) {
		resetAllProbes();
		box.setSize(size);
		renderer.getEnvironmentProbeFactory().updateBuffers();
	}
	public void setSize(float sizeX, float sizeY, float sizeZ) {
		resetAllProbes();
		box.setSize(sizeX, sizeY, sizeZ);
		renderer.getEnvironmentProbeFactory().updateBuffers();
	}

	public Vector3f getSize() {
		return new Vector3f(box.sizeX, box.sizeY, box.sizeZ);
	}

	public Camera getCamera(){
		return sampler;
	}

	public int getTextureUnitIndex() {
		int index = getIndex();
		return renderer.getMaxTextureUnits() - index - 1;
	}

	public int getIndex() {
		return renderer.getEnvironmentProbeFactory().getProbes().indexOf(this);
	}

	public Vector3f getDebugColor() {
		float colorHelper = (float)getIndex()/(float)renderer.getEnvironmentProbeFactory().getProbes().size();
		Random randomGenerator = new Random();
		randomGenerator.setSeed((long)colorHelper);
		float random = randomGenerator.nextFloat();
		return new Vector3f(1- colorHelper * colorHelper, (1-colorHelper) * (1 - colorHelper), colorHelper*colorHelper);
	}

	public EnvironmentSampler getSampler() {
		return sampler;
	}

	public float getWeight() {
		return weight;
	}

	public void setWeight(float weight) {
		this.weight = weight;
	}
}
