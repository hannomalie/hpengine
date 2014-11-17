package main.model;

import java.nio.FloatBuffer;
import java.util.EnumSet;

public class QuadVertexBuffer extends VertexBuffer{
	
//	public QuadVertexBuffer(ForwardRenderer renderer, boolean fullscreen) {
//		super(renderer, getPositionsAndTexCoords(fullscreen), EnumSet.of(DataChannels.POSITION3, DataChannels.TEXCOORD));
	public QuadVertexBuffer(boolean fullscreen) {
		super(getPositionsAndTexCoords(fullscreen), EnumSet.of(DataChannels.POSITION3, DataChannels.TEXCOORD));
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
			return new float[] {
			    -1.0f, -1.0f, 0.0f,   0f, 0f,
			    0f, -1.0f, 0.0f,    1f, 0f,
			    -1.0f,  0f, 0.0f,   0f, 1.0f,
			    -1.0f,  0f, 0.0f,   0f, 1.0f,
			    0f, -1.0f, 0.0f,    1.0f, 0f,
			    0f,  0f, 0.0f,    1.0f, 1.0f
			};
		}
	}
}
