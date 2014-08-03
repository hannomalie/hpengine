package main.scene;

import java.util.List;

import javafx.scene.Camera;
import main.Transform;
import main.model.IEntity;
import main.octree.Box;
import main.octree.Octree;
import main.renderer.EnvironmentSampler;
import main.renderer.Renderer;
import main.renderer.material.Material;
import main.shader.Program;
import main.texture.CubeMap;
import main.texture.Texture;

import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;

public class EnvironmentProbe implements IEntity {

	private String name = "Probe_" + System.currentTimeMillis();
	private Renderer renderer;
	private Box box;
	private EnvironmentSampler sampler;

	protected EnvironmentProbe(Renderer renderer, Vector3f center, float size) {
		this.renderer = renderer;
		box = new Box(center, size);
		sampler = new EnvironmentSampler(renderer, center, (int)size, (int)size);
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
		sampler.getCamera().move(amount);
		box.move(amount);
	}
	
	@Override
	public void moveInWorld(Vector3f amount) {
		box.move(amount);
		sampler.getCamera().setPosition(box.center.negate(null)); // TODO: WTFFFFFF
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

}
