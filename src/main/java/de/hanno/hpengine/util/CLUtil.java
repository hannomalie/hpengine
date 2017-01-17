package de.hanno.hpengine.util;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.List;
import java.util.logging.Logger;

import org.lwjgl.BufferUtils;
import org.lwjgl.LWJGLException;
import org.lwjgl.PointerBuffer;
import org.lwjgl.opencl.CL;
import org.lwjgl.opencl.CL10;
import org.lwjgl.opencl.CLCommandQueue;
import org.lwjgl.opencl.CLContext;
import org.lwjgl.opencl.CLDevice;
import org.lwjgl.opencl.CLKernel;
import org.lwjgl.opencl.CLPlatform;
import org.lwjgl.opencl.CLProgram;

public class CLUtil {

    private static final Logger LOGGER = Logger.getLogger(CLUtil.class.getName());

	public static boolean initialized = false;
	public static CLPlatform platform;
	public static List<CLDevice> devices;
	public static CLContext context;
	public static CLCommandQueue queue;
	
	public static IntBuffer errorBuffer = BufferUtils.createIntBuffer(1);
	
	public static void initialize() {
		try {
			CL.create();
			platform = CLPlatform.getPlatforms().get(0);
			devices = platform.getDevices(CL10.CL_DEVICE_TYPE_GPU);
			context = CLContext.create(platform, devices, null);
			queue = CL10.clCreateCommandQueue(context, devices.get(0), CL10.CL_QUEUE_PROFILING_ENABLE, errorBuffer);
			org.lwjgl.opencl.Util.checkCLError(errorBuffer.get(0));
			
			initialized = true;
		} catch (LWJGLException e) {
			e.printStackTrace();
		}
	}
	
	public static CLKernel build(CLProgram program, String kernelName) {
		try {
			int error = CL10.clBuildProgram(program, CLUtil.devices.get(0), "", null);
			org.lwjgl.opencl.Util.checkCLError(error);
			return CL10.clCreateKernel(program, kernelName, null);
		} catch (Exception e) {
			PointerBuffer pointerBuffer = BufferUtils.createPointerBuffer(1);
			CL10.clGetProgramBuildInfo(program, CLUtil.devices.get(0), CL10.CL_PROGRAM_BUILD_LOG, null, pointerBuffer);
			
			if (pointerBuffer.get(0) > 2) {
				ByteBuffer infoBuffer = BufferUtils.createByteBuffer((int)pointerBuffer.get(0));
				CL10.clGetProgramBuildInfo(program, CLUtil.devices.get(0), CL10.CL_PROGRAM_BUILD_LOG, infoBuffer, pointerBuffer);
				byte bytes[] = new byte[infoBuffer.capacity()];
				infoBuffer.get(bytes);
				LOGGER.fine(new String(bytes));
			}
			throw e;
		}
	}

}
