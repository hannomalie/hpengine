package de.hanno.hpengine.engine.model;

import de.hanno.hpengine.engine.graphics.GpuContext;

import javax.vecmath.Vector2f;
import java.util.EnumSet;

public class QuadVertexBuffer extends VertexBuffer {

	public QuadVertexBuffer(GpuContext gpuContext, boolean fullscreen) {
		super(gpuContext, EnumSet.of(DataChannels.POSITION3, DataChannels.TEXCOORD), getPositionsAndTexCoords(fullscreen));
	}
	public QuadVertexBuffer(GpuContext gpuContext, Vector2f leftBottom, Vector2f rightUpper) {
		super(gpuContext, EnumSet.of(DataChannels.POSITION3, DataChannels.TEXCOORD), getPositionsAndTexCoords(leftBottom, rightUpper));
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
