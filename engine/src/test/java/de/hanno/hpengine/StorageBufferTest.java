package de.hanno.hpengine;

import org.junit.Assert;
import org.junit.Test;
import org.lwjgl.BufferUtils;
import de.hanno.hpengine.engine.graphics.buffer.Bufferable;
import de.hanno.hpengine.engine.graphics.buffer.GPUBuffer;
import de.hanno.hpengine.engine.graphics.buffer.PersistentMappedBuffer;

import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;

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
        GPUBuffer buffer = new PersistentMappedBuffer(0);
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

        GPUBuffer buffer = new PersistentMappedBuffer(0);
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

        GPUBuffer buffer = new PersistentMappedBuffer(0);

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


        GPUBuffer buffer = new PersistentMappedBuffer(0);
		buffer.put(bufferable, bufferable, bufferable, bufferable);


		FloatBuffer result = buffer.getBuffer().asFloatBuffer();
		float[] dst = new float[result.capacity()];
		result.get(dst);

//		TODO: Reimplement this
//		for (int i = 0; i < 4*bufferable.getElementsPerObject(); i++) {
//			Assert.assertTrue(dst[i] == array[i%14]);
//		}
	}

	@Test
	public void testBufferRetrieval() {
		class CustomBufferable implements Bufferable {
			private double x,y,z;

			public CustomBufferable(float x, float y, float z) {
				this.x = x;
				this.y = y;
				this.z = z;
			}

			@Override
			public void putToBuffer(ByteBuffer buffer) {
				buffer.putDouble(x);
				buffer.putDouble(y);
				buffer.putDouble(z);
			}

			@Override
			public void getFromBuffer(ByteBuffer buffer) {
				x = buffer.getDouble();
				y = buffer.getDouble();
				z = buffer.getDouble();
			}

			@Override
			public int getBytesPerObject() {
				return 3 * Double.BYTES;
			}
		}

        CustomBufferable customBufferable0 = new CustomBufferable(0, 0, 1);
        CustomBufferable customBufferable1 = new CustomBufferable(0, 1, 0);
        CustomBufferable customBufferable2 = new CustomBufferable(1, 0, 0);

        PersistentMappedBuffer<CustomBufferable> buffer = new PersistentMappedBuffer<>(3 * customBufferable0.getBytesPerObject());
        buffer.put(customBufferable0, customBufferable1, customBufferable2);
        CustomBufferable fromBuffer = new CustomBufferable(-1, -1, -1);
        buffer.get(0, fromBuffer);
        Assert.assertEquals(0, fromBuffer.x, Double.MIN_VALUE);
        Assert.assertEquals(0, fromBuffer.y, Double.MIN_VALUE);
        Assert.assertEquals(1, fromBuffer.z, Double.MIN_VALUE);
        buffer.get(2, fromBuffer);
        Assert.assertEquals(1, fromBuffer.x, Double.MIN_VALUE);
        Assert.assertEquals(0, fromBuffer.y, Double.MIN_VALUE);
        Assert.assertEquals(0, fromBuffer.z, Double.MIN_VALUE);

        buffer.putAtIndex(3, customBufferable0);
        fromBuffer.getFromIndex(3, buffer.getBuffer());
        Assert.assertEquals(0, fromBuffer.x, Double.MIN_VALUE);
        Assert.assertEquals(0, fromBuffer.y, Double.MIN_VALUE);
        Assert.assertEquals(1, fromBuffer.z, Double.MIN_VALUE);
	}
}
