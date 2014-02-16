package main;

import org.lwjgl.util.vector.Vector3f;


public interface IEntity {
	public void update();
	public void draw();
	public void destroy();
	public void drawShadow();
	public Vector3f getPosition();
}
