package test;

import main.Spotlight;
import main.ForwardRenderer;
import main.QuadVertexBuffer;
import main.VertexBuffer;

import org.lwjgl.opengl.Display;

public class TestWorld {
	VertexBuffer quad;
	public TestWorld() {
		quad = null;//new QuadVertexBuffer(new ForwardRenderer(new DirectionalLight(false)), false);
		
		while (!Display.isCloseRequested()) {
			this.loopCycle();
			Display.sync(60);
			Display.update();
		}
	}

	private void loopCycle() {
		quad.draw();
	}

public static void main(String[] args) {
	new TestWorld();
}
}
