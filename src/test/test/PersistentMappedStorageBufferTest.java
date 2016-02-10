package test;

import org.junit.Assert;
import org.junit.Test;
import org.lwjgl.BufferUtils;
import shader.Bufferable;
import shader.PersistentMappedStorageBuffer;
import shader.StorageBuffer;

import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;

public class PersistentMappedStorageBufferTest extends TestWithAppContext {

	@Test
	public void storageBuffersGetsCorrectValues() {
		
		DoubleBuffer data = BufferUtils.createDoubleBuffer(16);
		for (int i = 0; i < 16; i++) {
			data.put(i, i);
		}
		PersistentMappedStorageBuffer buffer = new PersistentMappedStorageBuffer(data.capacity());
		buffer.putValues(data);

        DoubleBuffer result = buffer.getValues();
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
		PersistentMappedStorageBuffer buffer = new PersistentMappedStorageBuffer(data.capacity());
		buffer.putValues(data);

        DoubleBuffer result = buffer.getValues(4, 11);
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
		
		PersistentMappedStorageBuffer buffer = new PersistentMappedStorageBuffer(16);
		
		buffer.putValues(data);

        DoubleBuffer result = buffer.getValues(0, 16);
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
		
		PersistentMappedStorageBuffer buffer = new PersistentMappedStorageBuffer(16);
		
		buffer.putValues(4, data);

        DoubleBuffer result = buffer.getValues(4, 12);
		double[] dst = new double[result.capacity()];
		result.get(dst);
		
		for (int i = 0; i < 12; i++) {
			Assert.assertTrue(dst[i] == i+4);
		}
	}

	@Test
	public void storageBufferLayoutsCorrectly() {
		double[] array = new double[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13};
		PersistentMappedStorageBuffer buffer = new PersistentMappedStorageBuffer(64);

		Bufferable bufferable = new Bufferable() {
			@Override
			public double[] get() {
				return array;
			}
		};

		buffer.put(bufferable, bufferable, bufferable, bufferable);


		FloatBuffer result = buffer.getValuesAsFloats();
		float[] dst = new float[result.capacity()];
		result.get(dst);

		for (int i = 0; i < 4*bufferable.getSizePerObject(); i++) {
			Assert.assertTrue(dst[i] == array[i%14]);
		}
	}

	@Test
	public void storageBufferLayoutsCorrectlyWithIndex() {
		double[] array = new double[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11};
		double[] secondArray = new double[]{1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1};
		PersistentMappedStorageBuffer buffer = new PersistentMappedStorageBuffer(48);

		Bufferable bufferable = new Bufferable() {
			@Override
			public double[] get() {
				return array;
			}
		};
		Bufferable secondBufferable = new Bufferable() {
			@Override
			public double[] get() {
				return secondArray;
			}
		};

		buffer.put(bufferable, bufferable, bufferable, bufferable);


		FloatBuffer result = buffer.getValuesAsFloats();
		float[] dst = new float[result.capacity()];
		result.get(dst);

		for (int i = 0; i < 4*bufferable.getSizePerObject(); i++) {
			Assert.assertTrue(dst[i] == array[i%bufferable.getSizePerObject()]);
		}

		buffer.put(bufferable.getSizePerObject()*2, secondBufferable);

		DoubleBuffer newValues = buffer.getValues();
		double[] newDst = new double[newValues.capacity()];
		newValues.get(newDst);
		for (int i = 0; i < bufferable.getSizePerObject(); i++) {
			Assert.assertEquals(secondArray[i], newDst[2*bufferable.getSizePerObject()+i], 0.01f);
		}
	}
}
