package scene;

import camera.Camera;
import engine.AppContext;
import engine.model.Entity;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;
import renderer.Renderer;
import renderer.environmentsampler.EnvironmentSampler;
import shader.Program;

import java.util.List;
import java.util.Random;

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
	

	protected EnvironmentProbe(AppContext appContext, Vector3f center, Vector3f size, int resolution, Update update, int probeIndex, float weight) throws Exception {
		this.renderer = appContext.getRenderer();
		this.update = update;
		box = new AABB(center, size.x, size.y, size.z);
		sampler = new EnvironmentSampler(appContext, this, center, resolution, resolution, probeIndex);
		sampler.init(appContext);
		this.setWeight(weight);
		super.init(appContext);
	}

	public void draw(AppContext appContext) {
		draw(appContext, false);
	}
	public void draw(AppContext appContext, boolean urgent) {
		sampler.drawCubeMap(appContext, urgent);
	}
	
	public void drawDebug(Program program) {
		List<Vector3f> points = box.getPoints();
		for (int i = 0; i < points.size() - 1; i++) {
			renderer.batchLine(points.get(i), points.get(i + 1));
		}

		renderer.batchLine(points.get(3), points.get(0));
		renderer.batchLine(points.get(7), points.get(4));

		renderer.batchLine(points.get(0), points.get(6));
		renderer.batchLine(points.get(1), points.get(7));
		renderer.batchLine(points.get(2), points.get(4));
		renderer.batchLine(points.get(3), points.get(5));

		renderer.batchLine(getSampler().getPosition(), Vector3f.add(getSampler().getPosition(), new Vector3f(5, 0, 0), null));
		renderer.batchLine(getSampler().getPosition(), Vector3f.add(getSampler().getPosition(), new Vector3f(0, 5, 0), null));
		renderer.batchLine(getSampler().getPosition(), Vector3f.add(getSampler().getPosition(), new Vector3f(0, 0, -5), null));

		float temp = (float)getIndex()/10;
		program.setUniform("diffuseColor", new Vector3f(temp,1-temp,0));
	    renderer.drawLines(program);
		
//		renderer.batchLine(box.getBottomLeftBackCorner(), sampler.getCamera().getPosition());
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
		appContext.getRenderer().getEnvironmentProbeFactory().getProbes().forEach(probe -> {
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
