package main.scene;

import java.util.List;

import main.Transform;
import main.camera.Camera;
import main.model.IEntity;
import main.octree.Box;
import main.octree.Octree;
import main.renderer.EnvironmentSampler;
import main.renderer.Renderer;
import main.renderer.material.Material;
import main.scene.EnvironmentProbe.Update;
import main.shader.Program;
import main.texture.CubeMap;
import main.texture.Texture;

import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;

public class EnvironmentProbe implements IEntity {
	
	public enum Update {
		STATIC,
		DYNAMIC
	}
	
	private String name = "Probe_" + System.currentTimeMillis();
	private Renderer renderer;
	private Box box;
	private EnvironmentSampler sampler;
	protected Update update;

	protected EnvironmentProbe(Renderer renderer, Vector3f center, Vector3f size, int resolution, Update update) {
		this.renderer = renderer;
		this.update = update;
		box = new Box(center, size.x, size.y, size.z);
		sampler = new EnvironmentSampler(renderer, center, resolution, resolution);
	}
	
	public void draw(Octree octree) {
		sampler.drawCubeMap(octree);
	};
	
	@Override
	public void drawDebug(Program program) {
		List<Vector3f> points = box.getPoints();
		for (int i = 0; i < points.size() - 1; i++) {
			renderer.drawLine(points.get(i), points.get(i+1));
		}
		renderer.drawLine(box.getBottomLeftBackCorner(), sampler.getCamera().getPosition());
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
		sampler.getCamera().moveInWorld(amount);
		box.move(amount);
	}
	
	@Override
	public void moveInWorld(Vector3f amount) {
		box.move(amount);
		amount.y *= -1;
		sampler.getCamera().moveInWorld(amount);
	}
	
	@Override
	public void setPosition(Vector3f position) {
		box.setCenter(position);
		position.y *= -1;
		sampler.getCamera().setPosition(position);
	}
	
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

	public CubeMap getEnvironmentMap() {
		return sampler.getEnvironmentMap();
	}

	public Box getBox() {
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
	}
	public void setSize(float sizeX, float sizeY, float sizeZ) {
		box.setSize(sizeX, sizeY, sizeZ);
	}

	public Vector3f getSize() {
		return new Vector3f(box.sizeX, box.sizeY, box.sizeZ);
	}
	
	public Camera getCamera(){
		return sampler.getCamera();
	}
}
