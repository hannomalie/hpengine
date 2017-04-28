package de.hanno.hpengine.engine.model;

import javax.vecmath.Vector2f;
import java.util.EnumSet;

public class QuadVertexBuffer extends VertexBuffer {

    private static final VertexBuffer fullscreenBuffer = new QuadVertexBuffer(true);
    private static final VertexBuffer debugBuffer = new QuadVertexBuffer(false);
	static {
		fullscreenBuffer.upload();
		debugBuffer.upload();
	}
    public static VertexBuffer getFullscreenBuffer() {
        return fullscreenBuffer;
    }
    public static VertexBuffer getDebugBuffer() {
        return debugBuffer;
    }

	public QuadVertexBuffer(boolean fullscreen) {
		super(getPositionsAndTexCoords(fullscreen), EnumSet.of(DataChannels.POSITION3, DataChannels.TEXCOORD));
	}
	public QuadVertexBuffer(Vector2f leftBottom, Vector2f rightUpper) {
		super(getPositionsAndTexCoords(leftBottom, rightUpper), EnumSet.of(DataChannels.POSITION3, DataChannels.TEXCOORD));
	}

	static float[] getPositionsAndTexCoords(boolean fullscreen) {
		if (fullscreen) {
			return new float[] {
					-1.0f, -1.0f, 0.0f,   0f, 0f,
					1.0f, -1.0f, 0.0f,    1f, 0f,
					-1.0f,  1.0f, 0.0f,   0f,  1.0f,
					-1.0f,  1.0f, 0.0f,   0f,  1.0f,
					1.0f, -1.0f, 0.0f,    1.0f, 0f,
					1.0f,  1.0f, 0.0f,    1.0f,  1.0f
			};
		} else {
//			return new float[] {
//					-1.0f, -1.0f, 0.0f, 0f, 0f,
//					0f, -1.0f, 0.0f,    1f, 0f,
//					-1.0f,  0f, 0.0f,   0f, 1.0f,
//					-1.0f,  0f, 0.0f,   0f, 1.0f,
//					0f, -1.0f, 0.0f,    1.0f, 0f,
//					0f,  0f, 0.0f,      1.0f, 1.0f
//			};
			return getPositionsAndTexCoords(new Vector2f(-1, -1), new Vector2f(0, 0));
		}
	}
	static float[] getPositionsAndTexCoords(Vector2f leftBottom, Vector2f rightUpper) {
		return new float[] {
				leftBottom.x, leftBottom.y, 0.0f,   0f, 0f,
				rightUpper.x, leftBottom.y, 0.0f,    1f, 0f,
				leftBottom.x,  rightUpper.y, 0.0f,   0f,  1.0f,
				leftBottom.x,  rightUpper.y, 0.0f,   0f,  1.0f,
				rightUpper.x, leftBottom.y, 0.0f,    1.0f, 0f,
				rightUpper.x,  rightUpper.y, 0.0f,    1.0f,  1.0f
		};
	}
}
