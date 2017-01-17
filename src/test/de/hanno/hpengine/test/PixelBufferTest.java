package de.hanno.hpengine.test;

import de.hanno.hpengine.renderer.PixelBufferObject;
import de.hanno.hpengine.renderer.constants.GlTextureTarget;
import org.junit.Assert;
import org.junit.Test;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;

import java.nio.FloatBuffer;

public class PixelBufferTest extends TestWithRenderer {
	
	@Test
	public void downloadWorks() throws InterruptedException {
		
		FloatBuffer textureData = BufferUtils.createFloatBuffer(4);
		textureData.put(0.5f);
		textureData.put(0.4f);
		textureData.put(0.3f);
		textureData.put(0.2f);
		textureData.rewind();
		float[] xxx = new float[4];
		textureData.rewind();
		textureData.get(xxx);
		textureData.rewind();
		
    	int textureId = GL11.glGenTextures();
    	GL13.glActiveTexture(GL13.GL_TEXTURE0);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
		GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, 1, 1, 0, GL11.GL_RGBA, GL11.GL_FLOAT, textureData);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
		
		////
		FloatBuffer fBuffer = BufferUtils.createFloatBuffer(4);
		GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_FLOAT, fBuffer);
		float[] onePixel = new float[4];
		fBuffer.rewind();
		fBuffer.get(onePixel);
		Assert.assertEquals(0.5f, onePixel[0], 0.01f);
		Assert.assertEquals(0.4f, onePixel[1], 0.01f);
		Assert.assertEquals(0.3f, onePixel[2], 0.01f);
		////
		
		PixelBufferObject pixelBufferObject = new PixelBufferObject(1, 1);
		pixelBufferObject.readPixelsFromTexture(textureId, 0, GlTextureTarget.TEXTURE_2D, GL11.GL_RGBA, GL11.GL_FLOAT);

		float[] result = pixelBufferObject.mapBuffer();
		Assert.assertEquals(0.5f, result[0], 0.01f);
		Assert.assertEquals(0.4f, result[1], 0.01f);
		Assert.assertEquals(0.3f, result[2], 0.01f);
	}
}
