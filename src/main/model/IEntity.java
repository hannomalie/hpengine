package main.model;

import java.util.HashMap;

import main.Transform;
import main.camera.Camera;
import main.component.IGameComponent;
import main.component.IGameComponent.ComponentIdentifier;
import main.renderer.Renderer;
import main.renderer.material.Material;
import main.shader.Program;
import main.texture.CubeMap;

import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Quaternion;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;


public interface IEntity extends ITransformable {
	default public void update(float seconds) {};

	default public void draw(Renderer renderer, Camera camera) {};
	default public void draw(Renderer renderer, Camera camera, CubeMap environmentMap) {};
	default public void drawDebug(Program program) {};

	public String getName();
	default public void setName(String string) {}
	
	public Material getMaterial();
	default void setMaterial(String materialName) {};

	default public boolean isVisible() { return true; }
	default public void setVisible(boolean visible) {};
	
	public boolean isSelected();
	public void setSelected(boolean selected);
	
	public default VertexBuffer getVertexBuffer() { return null; }
	
	public default void destroy() {}

	public default HashMap<ComponentIdentifier, IGameComponent> getComponents() { return new HashMap<>(); }
}
