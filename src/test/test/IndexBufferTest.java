package test;

import engine.model.IndexBuffer;
import org.junit.Assert;
import org.junit.Test;
import org.lwjgl.BufferUtils;

import java.nio.IntBuffer;

public class IndexBufferTest extends TestWithOpenGLContext {

    @Test
    public void testBuffersCorrectly() {
        int[] indexArray = new int[]{99,98,97,96,95,94,93,92,91,90};
        int[] indexArray2 = new int[]{1,2,3,4,5};
        IndexBuffer indexBuffer = new IndexBuffer();
        indexBuffer.put(indexArray);

        IntBuffer bufferedIndices = indexBuffer.getValues();
        int[] actualArray = new int[bufferedIndices.capacity()];
        bufferedIndices.get(actualArray);
        for(int i = 0; i < indexArray.length; i++) {
            Assert.assertTrue("Element " + i + " not equal", indexArray[i] == actualArray[i]);
        }

        indexBuffer.put(indexArray.length, indexArray2);
        bufferedIndices = indexBuffer.getValues();
        int[] actualArray2 = new int[bufferedIndices.capacity()];
        bufferedIndices.get(actualArray2);
        for(int i = 0; i < indexArray.length; i++) {
            Assert.assertTrue("Element " + i + " not equal", indexArray[i] == actualArray2[i]);
        }
        for(int i = 0; i < indexArray2.length; i++) {
            Assert.assertTrue("Element " + i + " not equal", indexArray2[i] == actualArray2[indexArray.length+i]);
        }

        indexBuffer.bind();
        Assert.assertTrue(true);
    }

    @Test
    public void testAppendsCorrectly() {
        int[] indexArray = new int[]{99,98,97,96,95,94,93,92,91,90};
        int[] indexArray2 = new int[]{1,2,3,4,5};
        int[] expectedAppended = new int[]{11,12,13,14,15};
        IndexBuffer indexBuffer = new IndexBuffer();
        indexBuffer.put(indexArray);

        IntBuffer bufferedIndices = indexBuffer.getValues();
        int[] actualArray = new int[bufferedIndices.capacity()];
        bufferedIndices.get(actualArray);
        for(int i = 0; i < indexArray.length; i++) {
            Assert.assertTrue("Element " + i + " not equal", indexArray[i] == actualArray[i]);
        }

        indexBuffer.appendIndices(indexArray.length, indexArray2);
        int[] actualArray2 = new int[bufferedIndices.capacity()];
        bufferedIndices.get(actualArray2);
        for(int i = 0; i < indexArray.length; i++) {
            Assert.assertTrue("Element " + i + " not equal", indexArray[i] == actualArray2[i]);
        }
        for(int i = 0; i < indexArray2.length; i++) {
            Assert.assertTrue("Element " + i + " not equal", expectedAppended[i] == actualArray2[indexArray.length+i]);
        }

        indexBuffer.bind();
        Assert.assertTrue(true);
    }
}
