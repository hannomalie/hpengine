package de.hanno.hpengine.test;

import org.junit.Assert;
import org.junit.Test;
import org.lwjgl.BufferUtils;
import de.hanno.hpengine.shader.Bufferable;
import de.hanno.hpengine.shader.OpenGLBuffer;
import de.hanno.hpengine.shader.PersistentMappedBuffer;

import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;

import static org.lwjgl.opengl.ARBBufferStorage.glBufferStorage;
import static org.lwjgl.opengl.GL15.glGenBuffers;

public class StorageBufferTest extends TestWithEngine {

	@Test
	public void storageBuffersGetsCorrectValues() {

		DoubleBuffer data = BufferUtils.createDoubleBuffer(16);
		for (int i = 0; i < 16; i++) {
			data.put(i, i);
		}
        OpenGLBuffer buffer = new PersistentMappedBuffer(0);
        buffer.putValues(data);

        DoubleBuffer result = (DoubleBuffer) buffer.getValues();
		double[] dst = new double[result.capacity()];
		result.get(dst);

		for (int i = 0; i < 16; i++) {
			Assert.assertTrue(dst[i] == i);
		}
	}

	@Test
	public void storageBuffersGetsCorrectRangedValues() {

        DoubleBuffer data = BufferUtils.createDoubleBuffer(16);
		for (int i = 0; i < 16; i++) {
			data.put(i, i);
		}
        OpenGLBuffer buffer = new PersistentMappedBuffer(0);
        buffer.putValues(data);

        DoubleBuffer result = (DoubleBuffer) buffer.getValues(4, 11);
		double[] dst = new double[result.capacity()];
		result.get(dst);

		for (int i = 0; i < 8; i++) {
			Assert.assertTrue(dst[i] == i+4);
		}
	}

	@Test
	public void storageBufferBuffersCorrectly() {

        DoubleBuffer data = BufferUtils.createDoubleBuffer(16);
		for (int i = 0; i < 16; i++) {
			data.put(i, i);
		}

        OpenGLBuffer buffer = new PersistentMappedBuffer(0);
        buffer.putValues(data);

		buffer.putValues(data);

        DoubleBuffer result = (DoubleBuffer) buffer.getValues(0, 16);
		double[] dst = new double[result.capacity()];
		result.get(dst);

		for (int i = 0; i < 16; i++) {
			Assert.assertTrue(dst[i] == (double) i);
		}
	}

	@Test
	public void storageBufferBuffersCorrectlyWithOffset() {

        DoubleBuffer data = BufferUtils.createDoubleBuffer(12);
		for (int i = 0; i < 12; i++) {
			data.put(i, i+4);
		}

        OpenGLBuffer buffer = new PersistentMappedBuffer(0);
        buffer.putValues(data);

		buffer.putValues(4, data);

        DoubleBuffer result = (DoubleBuffer) buffer.getValues(4, 12);
		double[] dst = new double[result.capacity()];
		result.get(dst);

		for (int i = 0; i < 12; i++) {
			Assert.assertTrue(dst[i] == i+4);
		}
	}

	@Test
	public void storageBufferLayoutsCorrectly() {
		double[] array = new double[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13};

		Bufferable bufferable = new Bufferable() {
			@Override
			public double[] get() {
				return array;
			}
		};


        OpenGLBuffer buffer = new PersistentMappedBuffer(0);
		buffer.put(bufferable, bufferable, bufferable, bufferable);


		FloatBuffer result = buffer.getValuesAsFloats();
		float[] dst = new float[result.capacity()];
		result.get(dst);

		for (int i = 0; i < 4*bufferable.getElementsPerObject(); i++) {
			Assert.assertTrue(dst[i] == array[i%14]);
		}
	}
}