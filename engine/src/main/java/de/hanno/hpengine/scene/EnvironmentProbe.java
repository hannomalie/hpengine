package de.hanno.hpengine.scene;

import de.hanno.hpengine.camera.Camera;
import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.model.Entity;
import de.hanno.hpengine.renderer.GraphicsContext;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;
import de.hanno.hpengine.renderer.state.RenderState;
import de.hanno.hpengine.renderer.Renderer;
import de.hanno.hpengine.renderer.environmentsampler.EnvironmentSampler;
import de.hanno.hpengine.shader.Program;

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
	

	protected EnvironmentProbe(Engine engine, Vector3f center, Vector3f size, int resolution, Update update, int probeIndex, float weight) throws Exception {
        this.renderer = Renderer.getInstance();
		this.update = update;
		box = new AABB(center, size.x, size.y, size.z);
		sampler = new EnvironmentSampler(engine, this, center, resolution, resolution, probeIndex);
		sampler.init();
		this.setWeight(weight);
		super.init();
	}

	public void draw(RenderState extract) {
		draw(false, extract);
	}
	public void draw(boolean urgent, RenderState extract) {
		sampler.drawCubeMap(urgent, extract);
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
		EnvironmentProbeFactory.getInstance().updateBuffers();
		box.move(amount);
	}
	
	@Override
	public void moveInWorld(Vector3f amount) {
		super.moveInWorld(amount);
		resetAllProbes();
		box.move(amount);
		EnvironmentProbeFactory.getInstance().updateBuffers();
	}
	
	@Override
	public void setPosition(Vector3f position) {
		super.setPosition(position);
		resetAllProbes();
		box.setCenter(position);
		EnvironmentProbeFactory.getInstance().updateBuffers();
	}

	private void resetAllProbes() {
        EnvironmentProbeFactory.getInstance().getProbes().forEach(probe -> {
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

	public boolean contains(Vector3f min, Vector3f max) {
		return box.contains(min) && box.contains(max);
	}

	public boolean contains(Vector3f[] minMaxWorld) {
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
		EnvironmentProbeFactory.getInstance().updateBuffers();
	}
	public void setSize(float sizeX, float sizeY, float sizeZ) {
		resetAllProbes();
		box.setSize(sizeX, sizeY, sizeZ);
		EnvironmentProbeFactory.getInstance().updateBuffers();
	}

	public Vector3f getSize() {
		return new Vector3f(box.sizeX, box.sizeY, box.sizeZ);
	}

	public Camera getCamera(){
		return sampler;
	}

	public int getTextureUnitIndex() {
		int index = getIndex();
		return GraphicsContext.getInstance().getMaxTextureUnits() - index - 1;
	}

	public int getIndex() {
		return EnvironmentProbeFactory.getInstance().getProbes().indexOf(this);
	}

	public Vector3f getDebugColor() {
		float colorHelper = (float)getIndex()/(float)EnvironmentProbeFactory.getInstance().getProbes().size();
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
