package main;

import java.nio.ByteBuffer;

public class TextureBuffer {

	private int height;
	private int width;
	private ByteBuffer buffer;

	public TextureBuffer(int tWidth, int tHeight, ByteBuffer buf) {
		this.width = tWidth;
		this.height = tHeight;
		this.buffer = buf;
	}

	public int getHeight() {
		return height;
	}

	public void setHeight(int height) {
		this.height = height;
	}

	public int getWidth() {
		return width;
	}

	public void setWidth(int width) {
		this.width = width;
	}

	public ByteBuffer getBuffer() {
		return buffer;
	}

	public void setBuffer(ByteBuffer buffer) {
		this.buffer = buffer;
	}

}
