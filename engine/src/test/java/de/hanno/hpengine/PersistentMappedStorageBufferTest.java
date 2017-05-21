package de.hanno.hpengine;

import de.hanno.hpengine.shader.Bufferable;
import de.hanno.hpengine.shader.PersistentMappedBuffer;
import org.junit.Assert;
import org.junit.Test;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;

public class PersistentMappedStorageBufferTest extends TestWithEngine {

	@Test
	public void storageBuffersGetsCorrectValues() {

		ByteBuffer byteBuffer = BufferUtils.createByteBuffer(16 * Double.BYTES);
		DoubleBuffer data = byteBuffer.asDoubleBuffer();
		for (int i = 0; i < 16; i++) {
			data.put(i, i);
		}
		PersistentMappedBuffer buffer = new PersistentMappedBuffer(data.capacity() * 8);
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
		ByteBuffer data = BufferUtils.createByteBuffer(16*Double.BYTES);

		for (int i = 0; i < 16; i++) {
			data.putDouble(i*Double.BYTES, i);
		}

		PersistentMappedBuffer buffer = new PersistentMappedBuffer(16*8);
        buffer.putValues(data);

        DoubleBuffer result = buffer.getBuffer().asDoubleBuffer();
		double[] dst = new double[result.capacity()];
		result.get(dst);
		
		for (int i = 0; i < 16; i++) {
			Assert.assertTrue("Failed index: " + i, dst[i] == (double) i);
		}
	}
	
	@Test
	public void storageBufferBuffersCorrectlyWithOffset() {

		ByteBuffer byteBuffer = BufferUtils.createByteBuffer(12 * Double.BYTES);
		DoubleBuffer data = byteBuffer.asDoubleBuffer();
		for (int i = 0; i < 12; i++) {
			data.put(i, i);
		}
		
		PersistentMappedBuffer buffer = new PersistentMappedBuffer(16);
		
		buffer.putValues(4*Double.BYTES, byteBuffer);

        DoubleBuffer result = buffer.getBuffer().asDoubleBuffer();
		double[] dst = new double[result.capacity()];
		result.get(dst);
		
		for (int i = 4; i < 16; i++) {
			Assert.assertTrue(dst[i] == i);
		}
	}

	@Test
	public void storageBufferLayoutsCorrectly() {
		double[] array = new double[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13};
		PersistentMappedBuffer buffer = new PersistentMappedBuffer(64*8);

		Bufferable bufferable = new Bufferable() {
			@Override
			public void putToBuffer(ByteBuffer buffer) {
				buffer.asDoubleBuffer().put(array);
			}

			@Override
			public int getBytesPerObject() {
				return 14*Double.BYTES;
			}
		};

		buffer.put(bufferable, bufferable, bufferable, bufferable);


		FloatBuffer result = buffer.getBuffer().asFloatBuffer();
		float[] dst = new float[result.capacity()];
		result.get(dst);

//		TODO Reimplement this
//		for (int i = 0; i < 4*bufferable.getElementsPerObject(); i++) {
//			Assert.assertTrue(dst[i] == array[i%14]);
//		}
	}

	@Test
	public void storageBufferLayoutsCorrectlyWithIndex() {
		double[] array = new double[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11};
		double[] secondArray = new double[]{1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1};
		PersistentMappedBuffer buffer = new PersistentMappedBuffer(48*8);

		Bufferable bufferable = new Bufferable() {
			@Override
			public void putToBuffer(ByteBuffer buffer) {
				buffer.asDoubleBuffer().put(array);
			}

			@Override
			public int getBytesPerObject() {
				return 14*Double.BYTES;
			}
		};
		Bufferable secondBufferable = new Bufferable() {
			@Override
			public void putToBuffer(ByteBuffer buffer) {
				buffer.asDoubleBuffer().put(array);
			}

			@Override
			public int getBytesPerObject() {
				return 14*Double.BYTES;
			}
		};

		buffer.put(bufferable, bufferable, bufferable, bufferable);


		FloatBuffer result = buffer.getBuffer().asFloatBuffer();
		float[] dst = new float[result.capacity()];
		result.get(dst);

//		TODO: Reimplement this
//		for (int i = 0; i < 4*bufferable.getElementsPerObject(); i++) {
//			Assert.assertTrue(dst[i] == array[i%bufferable.getElementsPerObject()]);
//		}
//
//		buffer.put(bufferable.getElementsPerObject()*2, secondBufferable);
//
//		DoubleBuffer newValues = buffer.getValues();
//		double[] newDst = new double[newValues.capacity()];
//		newValues.get(newDst);
//		for (int i = 0; i < bufferable.getElementsPerObject(); i++) {
//			Assert.assertEquals(secondArray[i], newDst[2*bufferable.getElementsPerObject()+i], 0.01f);
//		}
	}
}
