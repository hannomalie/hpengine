package de.hanno.hpengine.engine.graphics.renderer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL21.*;

import java.nio.ByteBuffer;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;
import de.hanno.hpengine.engine.graphics.renderer.constants.GlTextureTarget;

public class PixelBufferObject {
	
	private int id;
	private int width;
	private int height;
	private ByteBuffer buffer;
	private float[] array;

	public PixelBufferObject(int width, int height) {
		id = glGenBuffers();
		this.width = width;
		this.height = height;
		this.buffer = BufferUtils.createByteBuffer(4*4*width*height); // 4 is byte size of float
		this.array = new float[4*width*height];
		bind();
		glBufferData(GL_PIXEL_UNPACK_BUFFER, 4*4*width*height, GL_DYNAMIC_READ);
		unbind();
	}

	private void unbind() {
		glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0);
	}
	
	public void bind() {
		glBindBuffer(GL_PIXEL_UNPACK_BUFFER, id);
	}

	public void readPixelsFromTexture(int textureId, int mipmapLevel, GlTextureTarget target, int format, int type) {
		bind();
        GraphicsContext.getInstance().bindTexture(target, textureId);
		glGetTexImage(target.glTarget, mipmapLevel, format, type, buffer);
		unbind();
	}
	public void glTexSubImage2D(int textureId, int mipmapLevel, GlTextureTarget target, int format, int type, int width, int height, ByteBuffer buffer) {
		glTexSubImage2D(textureId, mipmapLevel, target, format, type, 0, 0, width, height, buffer);
	}
	public void glTexSubImage2D(int textureId, int mipmapLevel, GlTextureTarget target, int format, int type, int offsetX, int offsetY, int width, int height, ByteBuffer buffer) {
		mapAndUnmap(offsetX, offsetY, width, height, buffer);
        GraphicsContext.getInstance().execute(() -> {
            GraphicsContext.getInstance().bindTexture(target, textureId);
			GL11.glTexSubImage2D(target.glTarget, mipmapLevel, offsetX, offsetY, width, height, GL_RGBA, GL_FLOAT, 0);
		});
		unbind();
	}

	public void glCompressedTexImage2D(int textureId, GlTextureTarget target, int level, int internalformat, int width, int height, int border, ByteBuffer textureBuffer) {
        GraphicsContext.getInstance().execute(() -> {
			mapAndUnmap(0, 0, width, height, buffer);
            GraphicsContext.getInstance().bindTexture(target, textureId);
			GL13.glCompressedTexImage2D(target.glTarget, level, internalformat, width, height, border, 0);
		});
		unbind();
	}

	private void mapAndUnmap(int offsetX, int offsetY, int width, int height, ByteBuffer buffer) {
		bind();
//		glBufferData(GL_PIXEL_UNPACK_BUFFER, 4*4*(width-offsetX)*(height*offsetY), GL_STREAM_COPY);
//		ByteBuffer result = glMapBuffer(GL_PIXEL_UNPACK_BUFFER, GL_READ_WRITE, buffer);
		ByteBuffer result = GL30.glMapBufferRange(GL_PIXEL_UNPACK_BUFFER, 0, 4*4*(width)*(height), GL30.GL_MAP_READ_BIT, buffer);
		result.put(buffer);
		glUnmapBuffer(GL_PIXEL_UNPACK_BUFFER);
	}

	public float[] mapBuffer() {
		glMapBuffer(GL_PIXEL_UNPACK_BUFFER, GL_READ_WRITE, buffer);
		buffer.rewind();
		buffer.asFloatBuffer().get(array);
		return array;
	}
}
