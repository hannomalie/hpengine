package de.hanno.hpengine;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.lwjgl.BufferUtils;
import org.lwjgl.PointerBuffer;
import org.lwjgl.opencl.CL10;
import org.lwjgl.opencl.CLKernel;
import org.lwjgl.opencl.CLMem;
import org.lwjgl.opencl.CLProgram;
import de.hanno.hpengine.util.CLUtil;
import de.hanno.hpengine.util.Util;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

public class OpenCLTest {
	
	@BeforeClass
	public static void CLContextCreation() {
		CLUtil.initialize();
	}
	
	@Test
	public void CLProgramTest() {
		String kernel = Util.loadAsTextFile("/de/hanno/hpengine/test/sum.cls");
		CLProgram sumProgram = CL10.clCreateProgramWithSource(CLUtil.context, kernel, null);
		
		CLKernel sumKernel = CLUtil.build(sumProgram, "sum");
		
		final int size = 5;
		IntBuffer errorBuff = BufferUtils.createIntBuffer(1);

		FloatBuffer aBuff = BufferUtils.createFloatBuffer(size);
		float[] tempData = new float[size];
		for(int i = 0; i < size; i++) {
		    tempData[i] = i;
		}
		aBuff.put(tempData);
		aBuff.rewind();

		FloatBuffer bBuff = BufferUtils.createFloatBuffer(size);
		for(int j = 0, i = size-1; j < size; j++, i--) {
		    tempData[j] = i;
		}
		bBuff.put(tempData);
		bBuff.rewind();

		CLMem aMemory = CL10.clCreateBuffer(CLUtil.context, CL10.CL_MEM_WRITE_ONLY | CL10.CL_MEM_COPY_HOST_PTR, aBuff, errorBuff);
		org.lwjgl.opencl.Util.checkCLError(errorBuff.get(0));

		CLMem bMemory = CL10.clCreateBuffer(CLUtil.context, CL10.CL_MEM_WRITE_ONLY | CL10.CL_MEM_COPY_HOST_PTR, bBuff, errorBuff);
		org.lwjgl.opencl.Util.checkCLError(errorBuff.get(0));

		CLMem resultMemory = CL10.clCreateBuffer(CLUtil.context, CL10.CL_MEM_READ_ONLY, size*4, errorBuff);
		org.lwjgl.opencl.Util.checkCLError(errorBuff.get(0));
		
		sumKernel.setArg(0, aMemory);
		sumKernel.setArg(1, bMemory);
		sumKernel.setArg(2, resultMemory);
		sumKernel.setArg(3, size);
		
		final int dimensions = 1;
		PointerBuffer globalWorkSize = BufferUtils.createPointerBuffer(dimensions);
		globalWorkSize.put(0, size);

		CL10.clEnqueueNDRangeKernel(CLUtil.queue, sumKernel, dimensions, null, globalWorkSize, null, null, null);

		CL10.clFinish(CLUtil.queue);
		
		FloatBuffer writeTo = BufferUtils.createFloatBuffer(size);
		CL10.clEnqueueReadBuffer(CLUtil.queue, resultMemory, CL10.CL_TRUE, 0, writeTo, null, null);
		
		float[] result = new float[size];
		writeTo.get(result);
		Assert.assertArrayEquals(new float[]{4f,4f,4f,4f,4f}, result, 0.0f);
	}
	

}
