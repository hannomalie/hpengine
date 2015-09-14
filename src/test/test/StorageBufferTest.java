package test;

import org.junit.Assert;
import org.junit.Test;
import org.lwjgl.BufferUtils;
import shader.Bufferable;
import shader.StorageBuffer;

import java.nio.FloatBuffer;

public class StorageBufferTest extends TestWithAppContext {

	@Test
	public void storageBuffersGetsCorrectValues() {
		
		FloatBuffer data = BufferUtils.createFloatBuffer(16);
		for (int i = 0; i < 16; i++) {
			data.put(i, i);
		}
		StorageBuffer buffer = new StorageBuffer(data);
		
		FloatBuffer result = buffer.getValues();
		float[] dst = new float[result.capacity()];
		result.get(dst);
		
		for (int i = 0; i < 16; i++) {
			Assert.assertTrue(dst[i] == i);
		}
	}

	@Test
	public void storageBuffersGetsCorrectRangedValues() {
		
		FloatBuffer data = BufferUtils.createFloatBuffer(16);
		for (int i = 0; i < 16; i++) {
			data.put(i, i);	
		}
		StorageBuffer buffer = new StorageBuffer(data);
		
		FloatBuffer result = buffer.getValues(4, 11);
		float[] dst = new float[result.capacity()];
		result.get(dst);
		
		for (int i = 0; i < 8; i++) {
			Assert.assertTrue(dst[i] == i+4);
		}
	}

	@Test
	public void storageBufferBuffersCorrectly() {
		
		FloatBuffer data = BufferUtils.createFloatBuffer(16);
		for (int i = 0; i < 16; i++) {
			data.put(i, i);	
		}
		
		StorageBuffer buffer = new StorageBuffer(16);
		
		buffer.putValues(data);
		
		FloatBuffer result = buffer.getValues(0, 16);
		float[] dst = new float[result.capacity()];
		result.get(dst);
		
		for (int i = 0; i < 16; i++) {
			Assert.assertTrue(dst[i] == i);
		}
	}
	
	@Test
	public void storageBufferBuffersCorrectlyWithOffset() {
		
		FloatBuffer data = BufferUtils.createFloatBuffer(12);
		for (int i = 0; i < 12; i++) {
			data.put(i, i+4);
		}
		
		StorageBuffer buffer = new StorageBuffer(16);
		
		buffer.putValues(4, data);
		
		FloatBuffer result = buffer.getValues(4, 12);
		float[] dst = new float[result.capacity()];
		result.get(dst);
		
		for (int i = 0; i < 12; i++) {
			Assert.assertTrue(dst[i] == i+4);
		}
	}

	@Test
	public void storageBufferLayoutsCorrectly() {
		float[] array = new float[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13};
		StorageBuffer buffer = new StorageBuffer(64);

		Bufferable bufferable = new Bufferable() {
			@Override
			public float[] get() {
				return array;
			}
		};

		buffer.put(bufferable, bufferable, bufferable, bufferable);

		FloatBuffer result = buffer.getValues();
		float[] dst = new float[result.capacity()];
		result.get(dst);

		for (int i = 0; i < 4*bufferable.getSizePerObject(); i++) {
			Assert.assertTrue(dst[i] == array[i%14]);
		}
	}
}
