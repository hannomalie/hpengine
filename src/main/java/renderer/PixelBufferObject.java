package renderer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL21.*;

import java.nio.ByteBuffer;

import engine.AppContext;
import org.lwjgl.BufferUtils;
import renderer.constants.GlTextureTarget;

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
		glBufferData(GL_PIXEL_UNPACK_BUFFER, 4*4*width*height, GL_STREAM_READ);
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
		AppContext.getInstance().getRenderer().getOpenGLContext().bindTexture(target, textureId);
		glGetTexImage(target.glTarget, mipmapLevel, format, type, buffer);
		unbind();
	}
	
	public float[] mapBuffer() {
		glMapBuffer(GL_PIXEL_UNPACK_BUFFER, GL_READ_WRITE, buffer);
		buffer.rewind();
		buffer.asFloatBuffer().get(array);
		glUnmapBuffer(GL_PIXEL_PACK_BUFFER);
		return array;
	}
}
