package de.hanno.hpengine;

import org.junit.Assert;
import org.junit.Test;
import org.lwjgl.BufferUtils;
import de.hanno.hpengine.shader.Bufferable;
import de.hanno.hpengine.shader.OpenGLBuffer;
import de.hanno.hpengine.shader.PersistentMappedBuffer;

import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.util.DoubleSummaryStatistics;

import static org.lwjgl.opengl.ARBBufferStorage.glBufferStorage;
import static org.lwjgl.opengl.GL15.glGenBuffers;

public class StorageBufferTest extends TestWithEngine {

	@Test
	public void storageBuffersGetsCorrectValues() {

		ByteBuffer byteBuffer = BufferUtils.createByteBuffer(16 * Double.BYTES);
		DoubleBuffer data = byteBuffer.asDoubleBuffer();
		for (int i = 0; i < 16; i++) {
			data.put(i, i);
		}
        OpenGLBuffer buffer = new PersistentMappedBuffer(0);
        buffer.putValues(byteBuffer);

        DoubleBuffer result = buffer.getBuffer().asDoubleBuffer();
		double[] dst = new double[result.capacity()];
		result.get(dst);

		for (int i = 0; i < 16; i++) {
			Assert.assertTrue(dst[i] == i);
		}
	}

	@Test
	public void storageBufferBuffersCorrectly() {

		ByteBuffer byteBuffer = BufferUtils.createByteBuffer(16 * Double.BYTES);
		DoubleBuffer data = byteBuffer.asDoubleBuffer();
		for (int i = 0; i < 16; i++) {
			data.put(i, i);
		}

        OpenGLBuffer buffer = new PersistentMappedBuffer(0);
        buffer.putValues(byteBuffer);

		buffer.putValues(byteBuffer);

        DoubleBuffer result = buffer.getBuffer().asDoubleBuffer();
		double[] dst = new double[result.capacity()];
		result.get(dst);

		for (int i = 0; i < 16; i++) {
			Assert.assertTrue(dst[i] == (double) i);
		}
	}

	@Test
	public void storageBufferBuffersCorrectlyWithOffset() {
		ByteBuffer byteBuffer = BufferUtils.createByteBuffer(12 * Double.BYTES);
		DoubleBuffer data = byteBuffer.asDoubleBuffer();
		for (int i = 0; i < 12; i++) {
			data.put(i, i);
		}

        OpenGLBuffer buffer = new PersistentMappedBuffer(0);

		buffer.putValues(4*Double.BYTES, byteBuffer);

        DoubleBuffer result = buffer.getBuffer().asDoubleBuffer();
        result.position(4);
		double[] dst = new double[result.remaining()];
		result.get(dst);

		for (int i = 0; i < 12; i++) {
			Assert.assertTrue(dst[i] == i);
		}
	}

	@Test
	public void storageBufferLayoutsCorrectly() {
		double[] array = new double[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13};

		Bufferable bufferable = new Bufferable() {
			@Override
			public void putToBuffer(ByteBuffer buffer) {
				buffer.asDoubleBuffer().put(array);
			}

			@Override
			public int getBytesPerObject() {
				return 14 * Double.BYTES;
			}
		};


        OpenGLBuffer buffer = new PersistentMappedBuffer(0);
		buffer.put(bufferable, bufferable, bufferable, bufferable);


		FloatBuffer result = buffer.getBuffer().asFloatBuffer();
		float[] dst = new float[result.capacity()];
		result.get(dst);

//		TODO: Reimplement this
//		for (int i = 0; i < 4*bufferable.getElementsPerObject(); i++) {
//			Assert.assertTrue(dst[i] == array[i%14]);
//		}
	}
}
