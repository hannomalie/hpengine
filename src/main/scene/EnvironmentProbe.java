package main.scene;

import java.io.Serializable;
import java.util.List;
import java.util.Random;

import main.Transform;
import main.camera.Camera;
import main.model.IEntity;
import main.octree.Octree;
import main.renderer.EnvironmentSampler;
import main.renderer.Renderer;
import main.renderer.light.AreaLight;
import main.renderer.light.DirectionalLight;
import main.renderer.material.Material;
import main.scene.EnvironmentProbe.Update;
import main.shader.Program;
import main.texture.CubeMap;

import org.lwjgl.opengl.GL13;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;

public class EnvironmentProbe implements IEntity {
	
	public enum Update {
		STATIC,
		DYNAMIC
	}
	
	private String name = "Probe_" + System.currentTimeMillis();
	private Renderer renderer;
	private AABB box;
	private EnvironmentSampler sampler;
	protected Update update;
	

	protected EnvironmentProbe(Renderer renderer, Vector3f center, Vector3f size, int resolution, Update update) {
		this.renderer = renderer;
		this.update = update;
		box = new AABB(center, size.x, size.y, size.z);
		sampler = new EnvironmentSampler(renderer, this, center, resolution, resolution);
	}
	
	public void draw(Octree octree, DirectionalLight light) {
		sampler.drawCubeMap(octree, light);
	};
	
	@Override
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
		
		float temp = (float)getIndex()/10;
		program.setUniform("diffuseColor", new Vector3f(temp,1-temp,0));
	    renderer.drawLines(program);
		
//		renderer.drawLine(box.getBottomLeftBackCorner(), sampler.getCamera().getPosition());
		
	}
	
	@Override
	public Transform getTransform() {
		return sampler.getCamera().getTransform();
	}
	@Override
	public void setTransform(Transform transform) {
		sampler.getCamera().setTransform(transform);
	}

	@Override
	public void move(Vector3f amount) {
		sampler.getCamera().moveInWorld(amount.negate(null));
		box.move(amount);
	}
	
	@Override
	public void moveInWorld(Vector3f amount) {
		box.move(amount);
		sampler.getCamera().moveInWorld(amount.negate(null));
	}
	
	@Override
	public void setPosition(Vector3f position) {
		box.setCenter(position);
		sampler.getCamera().setPosition(position.negate(null));
	}

	@Override
	public Vector3f getCenter() { return getPosition().negate(null); }
	
	@Override
	public String getName() {
		return name ;
	}

	@Override
	public Material getMaterial() {
		return null;
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

	public Update getUpdate() {
		return update;
	}

	public void setSize(float size) {
		box.setSize(size);
		renderer.getEnvironmentProbeFactory().updateBuffers();
	}
	public void setSize(float sizeX, float sizeY, float sizeZ) {
		box.setSize(sizeX, sizeY, sizeZ);
		renderer.getEnvironmentProbeFactory().updateBuffers();
	}

	public Vector3f getSize() {
		return new Vector3f(box.sizeX, box.sizeY, box.sizeZ);
	}

	public Camera getCamera(){
		return sampler.getCamera();
	}
	public void setCamera(Camera camera){
		sampler.setCamera(camera);
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
}
