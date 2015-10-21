package util;

import camera.Camera;
import com.bulletphysics.linearmath.Transform;
import org.lwjgl.Sys;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Quaternion;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;
import renderer.Renderer;

import javax.imageio.ImageIO;
import javax.vecmath.Quat4f;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;

import static log.ConsoleLogger.getLogger;

public class Util {
	private static Logger LOGGER = getLogger();
	private static final double PI = 3.14159265358979323846;
	private static final String VECTOR_DELIMITER = "_";
	
	public static String loadAsTextFile(String path) {
		//InputStream in = Util.class.getClass().getResourceAsStream("/de/hanno/render/shader/vs.vs");
		InputStream in = Util.class.getClass().getResourceAsStream(path);
		String result = convertStreamToString(in);
		
		try {
			in.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return result;
	}

	public static String getFileExtension(String in) {
		String extension = "";
		int i = in.lastIndexOf('.');
		if (i > 0) {
		    extension = in.substring(i+1);
		}
		return extension;
	}
	
	public static BufferedImage toImage(ByteBuffer byteBuffer, int width, int height) {

		int[] pixels = new int[width*height];
		int index;
		
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		for (int i=0; i < pixels.length; i++) {
            index = i * 3;
            pixels[i] =
                ((byteBuffer.get(index) << 16))  +
                ((byteBuffer.get(index+1) << 8))  +
                ((byteBuffer.get(index+2) << 0));
        }
        //Allocate colored pixel to buffered Image
        image.setRGB(0, 0, width, height, pixels, 0 , width);
		
//		try {
//			image = ImageIO.read(in);
//		} catch (IOException e) {
//			e.printStackTrace();
//		}
		return image;
	}
	
	public static void saveImage(BufferedImage image, String path) {
		if (image == null) return;
		try {
			ImageIO.write(image, "png", new File(path));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private static String convertStreamToString(java.io.InputStream is) {
	    java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A");
	    return s.hasNext() ? s.next() : "";
	}
	

	public static float coTangent(float angle) {
		return (float)(1f / Math.tan(angle));
	}
	
	public static float degreesToRadians(float degrees) {
		return degrees * (float)(PI / 180d);
	}
	
	public static long getTime() {
		return (Sys.getTime() * 1000) / Sys.getTimerResolution();
	}

    public static Matrix4f createPerpective(float fov, float ratio, float near, float far) {
        Matrix4f projectionMatrix = new Matrix4f();
        float fieldOfView = fov;
        float aspectRatio = ratio;
        float near_plane = near;
        float far_plane = far;

        float y_scale = (float) (1f / Math.tan(Math.toRadians(fieldOfView / 2f)));
        float x_scale = y_scale / aspectRatio;
        float frustum_length = far_plane - near_plane;

        projectionMatrix.m00 = x_scale;
        projectionMatrix.m11 = y_scale;
        projectionMatrix.m22 = -((far_plane + near_plane) / frustum_length);
        projectionMatrix.m23 = -1;
        projectionMatrix.m32 = -((2 * near_plane * far_plane) / frustum_length);
        projectionMatrix.m33 = 0;

        return projectionMatrix;
    }
    
    public static Matrix4f createOrthogonal(float left, float right, float top, float bottom, float near, float far) {
    	Matrix4f projectionMatrix = new Matrix4f();
        projectionMatrix.m00 = 2f / (right - left);
        projectionMatrix.m11 = 2f / (top - bottom);
        projectionMatrix.m22 = -1f / (far - near);
        projectionMatrix.m33 = 1f;
        
        projectionMatrix.m30 = -((right + left) / (right - left));
        projectionMatrix.m31 = -((top + bottom) / (top - bottom));
        projectionMatrix.m32 = -((near) / (far - near));
        return projectionMatrix;
    }
	
	public static Matrix4f lookAt(Vector3f eye, Vector3f center, Vector3f up) {
        Vector3f f = new Vector3f();
        Vector3f.sub(center, eye, f);
        f.normalise();

        Vector3f u = new Vector3f();
        up.normalise(u);
        Vector3f s = new Vector3f();
        Vector3f.cross(f, u, s);
        s.normalise();
        Vector3f.cross(s, f, u);

        Matrix4f mat = new Matrix4f();

        mat.m00 = s.x;
        mat.m10 = s.y;
        mat.m20 = s.z;
        mat.m01 = u.x;
        mat.m11 = u.y;
        mat.m21 = u.z;
        mat.m02 = -f.x;
        mat.m12 = -f.y;
        mat.m22 = -f.z;

        Matrix4f temp = new Matrix4f();
        Matrix4f.translate(new Vector3f(-eye.x, -eye.y, -eye.z), mat, temp);

        return mat;
    }
	
	//http://molecularmusings.wordpress.com/2013/05/24/a-faster-quaternion-vector-multiplication/
	public static Vector3f _mul(Vector3f a, Quaternion b) {
		Vector3f temp = (Vector3f) Vector3f.cross(new Vector3f(b.x, b.y, b.z), a, null).scale(2f);
		Vector3f result = Vector3f.add(a, (Vector3f) temp.scale(b.w), null);
		result = Vector3f.add(result, Vector3f.cross(new Vector3f(b.x,  b.y,  b.z), temp, null), null);
		return result;
	}
	public static Vector3f mul(Vector3f a, Quaternion b) {
		Quaternion in = new Quaternion(a.x,a.y,a.z,0);
		Quaternion temp = Quaternion.mul(b, in, null);
		temp = Quaternion.mul(temp, conjugate(b), null);
		return new Vector3f(temp.x, temp.y, temp.z);
	}
	
	public static Matrix4f toMatrix(Quaternion q) {
		Matrix4f m = new Matrix4f();

		float qx = q.getX();
        float qy = q.getY();
        float qz = q.getZ();
        float qw = q.getW();

        // just pretend these are superscripts.
        float qx2 = qx * qx;
        float qy2 = qy * qy;
        float qz2 = qz * qz;

        m.m00 = 1 - 2 * qy2 - 2 * qz2;
        m.m01 = 2 * qx * qy + 2 * qz * qw;
        m.m02 = 2 * qx * qz - 2 * qy * qw;
        m.m03 = 0;

        m.m10 = 2 * qx * qy - 2 * qz * qw;
        m.m11 = 1 - 2 * qx2 - 2 * qz2;
        m.m12 = 2 * qy * qz + 2 * qx * qw;
        m.m13 = 0;

        m.m20 = 2 * qx * qz + 2 * qy * qw;
        m.m21 = 2 * qy * qz - 2 * qx * qw;
        m.m22 = 1 - 2 * qx2 - 2 * qy2;
        m.m23 = 0;

        m.m30 = 0;
        m.m31 = 0;
        m.m32 = 0;

        return m;
    }

//	public static Quaternion slerp(Quaternion q1, Quaternion q2, float t) {
//		Quaternion qInterpolated = new Quaternion();
//
//		if (q1.equals(q2)) {
//			return q1;
//		}
//
//		// Temporary array to hold second quaternion.
//
//		float cosTheta = q1.x * q2.x + q1.y * q2.y + q1.z * q2.z + q1.w * q2.w;
//
//		if (cosTheta < 0.0f) {
//			// Flip sigh if so.
//			q2 = conjugate(q2);
//			cosTheta = -cosTheta;
//		}
//
//		float beta = 1.0f - t;
//
//		// Set the first and second scale for the interpolation
//		float scale0 = 1.0f - t;
//		float scale1 = t;
//
//		if (1.0f - cosTheta > 0.1f) {
//			// We are using spherical interpolation.
//			float theta = (float) Math.acos(cosTheta);
//			float sinTheta = (float) Math.sin(theta);
//			scale0 = (float) Math.sin(theta * beta) / sinTheta;
//			scale1 = (float) Math.sin(theta * t) / sinTheta;
//		}
//
//		// Interpolation.
//		qInterpolated.x = scale0 * q1.x + scale1 * q2.x;
//		qInterpolated.y = scale0 * q1.y + scale1 * q2.y;
//		qInterpolated.z = scale0 * q1.z + scale1 * q2.z;
//		qInterpolated.w = scale0 * q1.w + scale1 * q2.w;
//
//		return qInterpolated;
//	}

	public static Quaternion conjugate(Quaternion q) {
		q.x = -q.x;
		q.y = -q.y;
		q.z = -q.z;
		return q;
	}

	public static String vectorToString(Vector3f in) {
		StringBuilder b = new StringBuilder();
		b.append(in.x);
		b.append(VECTOR_DELIMITER);
		b.append(in.y);
		b.append(VECTOR_DELIMITER);
		b.append(in.z);
		
		return b.toString();
	}
	public static String vectorToString(Vector4f in) {
		StringBuilder b = new StringBuilder();
		b.append(in.x);
		b.append(VECTOR_DELIMITER);
		b.append(in.y);
		b.append(VECTOR_DELIMITER);
		b.append(in.z);
		b.append(VECTOR_DELIMITER);
		b.append(in.w);
		
		return b.toString();
	}

	public static javax.vecmath.Vector3f toBullet(Vector3f in) {
		return new javax.vecmath.Vector3f(in.x, in.y, in.z);
	}
	public static Transform toBullet(engine.Transform in) {
		Matrix4f out = in.getTransformation();
		return new Transform(toBullet(out));
	}

	public static javax.vecmath.Matrix4f toBullet(Matrix4f in) {
		javax.vecmath.Matrix4f out = new javax.vecmath.Matrix4f();
		out.m00 = in.m00;
		out.m01 = in.m01;
		out.m02 = in.m02;
		out.m03 = in.m03;
		out.m10 = in.m10;
		out.m11 = in.m11;
		out.m12 = in.m12;
		out.m13 = in.m13;
		out.m20 = in.m20;
		out.m21 = in.m21;
		out.m22 = in.m22;
		out.m23 = in.m23;
		out.m30 = in.m30;
		out.m31 = in.m31;
		out.m32 = in.m32;
		out.m33 = in.m33;
		
		out.transpose();
		return out;
	}
	public static Matrix4f fromBullet(javax.vecmath.Matrix4f in) {
		Matrix4f out = new Matrix4f();
		out.m00 = in.m00;
		out.m01 = in.m01;
		out.m02 = in.m02;
		out.m03 = in.m03;
		out.m10 = in.m10;
		out.m11 = in.m11;
		out.m12 = in.m12;
		out.m13 = in.m13;
		out.m20 = in.m20;
		out.m21 = in.m21;
		out.m22 = in.m22;
		out.m23 = in.m23;
		out.m30 = in.m30;
		out.m31 = in.m31;
		out.m32 = in.m32;
		out.m33 = in.m33;
		
		return out;
	}

	public static engine.Transform fromBullet(Transform in) {
		javax.vecmath.Matrix4f out = new javax.vecmath.Matrix4f();
		in.getMatrix(out);
		Quat4f outRot = new Quat4f();
		
		engine.Transform finalTransform = new engine.Transform();
		finalTransform.setPosition(new Vector3f(in.origin.x,in.origin.y,in.origin.z));
		finalTransform.setOrientation(fromBullet(in.getRotation(outRot)));
		return finalTransform;
	}

	private static Quaternion fromBullet(Quat4f rotation) {
		return new Quaternion(rotation.x, rotation.y, rotation.z, rotation.w);
	}
	

	public static int calculateMipMapCount(int size) {
		int maxLength = size;
		int count = 0;
		while(maxLength > 1) {
			count++;
			maxLength /= 2;
		}
		return count;
	}

	public static void printFloatBuffer(FloatBuffer values) {
		printFloatBuffer(values, 4);
	}

	public static void printFloatBuffer(FloatBuffer buffer, int columns) {
		buffer.rewind();
		StringBuilder builder = new StringBuilder();
		int columnCounter = 1;
		while (buffer.hasRemaining()) {
			builder.append(buffer.get());
			builder.append(" ");
			if(columnCounter%columns==0) { builder.append(System.lineSeparator()); }
			columnCounter++;
		}
		buffer.rewind();
		System.out.println(builder.toString());
	}
	
	/**
	 *
	 * @author Aleksandr Dubinsky
	 */
      public static ByteBuffer asByteBuffer (FloatBuffer floatBuffer) {
    	  ByteBuffer byteBuffer = (ByteBuffer) ((sun.nio.ch.DirectBuffer)floatBuffer).attachment();
    	  return byteBuffer;
      }

	public static float[] getArray(FloatBuffer values) {
		float[] array = new float[values.capacity()];
		values.get(array);
		return array;
	}

	public static void getOverallMinMax(Vector4f[] minMax, List<Vector4f[]> minMaxFromChildren) {
		for (Vector4f[] candidate : minMaxFromChildren) {
			Vector4f min = candidate[0];
			Vector4f max = candidate[1];

			if(min.x  < minMax[0].x) { minMax[0].x = min.x; }
			if(min.y  < minMax[0].y) { minMax[0].y = min.y; }
			if(min.z  < minMax[0].z) { minMax[0].z = min.z; }

			if(max.x  > minMax[1].x) { minMax[1].x = min.x; }
			if(max.y  > minMax[1].y) { minMax[1].y = min.y; }
			if(max.z  > minMax[1].z) { minMax[1].z = min.z; }
		}
	}

	public static boolean isRenderThread() {
		return Thread.currentThread().getName().equals(Renderer.RENDER_THREAD_NAME);
	}

	public static <T> T[] toArray(Collection<T> values, Class<T> clazz) {
		T[] array = (T[]) Array.newInstance(clazz, values.size());

		Iterator<T> iterator = values.iterator();

		int counter = 0;
		while(iterator.hasNext()) {
			array[counter] = iterator.next();
			counter++;
		}
		return array;
	}

	public static int countNewLines(String content) {
		String findStr = "\n";
		int newlineCount = (content.split(findStr, -1).length-1);
		return newlineCount;
	}

	public static TypedTuple<Matrix4f[],Matrix4f[]> getCubeViewProjectionMatricesForPosition(Vector3f position) {
		Camera camera = new Camera();
		camera.setProjectionMatrix(Util.createPerpective(90, 1, 0.1f, 250f));
		camera.setPosition(position);
		Matrix4f projectionMatrix = camera.getProjectionMatrix();

		Matrix4f[] resultViewMatrices = new Matrix4f[6];
		Matrix4f[] resultProjectionMatrices = new Matrix4f[6];

			camera.setOrientation(new Quaternion().setIdentity());
			camera.rotateWorld(new Vector4f(0,0,1, 180));
			camera.rotateWorld(new Vector4f(0, 1, 0, -90));
			camera.update(0);
		resultViewMatrices[0] = camera.getViewMatrix();
		resultProjectionMatrices[0] = projectionMatrix;
			camera.setOrientation(new Quaternion().setIdentity());
			camera.rotateWorld(new Vector4f(0,0,1, 180));
			camera.rotateWorld(new Vector4f(0, 1, 0, 90));
			camera.update(0);
		resultViewMatrices[1] = camera.getViewMatrix();
		resultProjectionMatrices[1] = projectionMatrix;
			camera.setOrientation(new Quaternion().setIdentity());
			camera.rotateWorld(new Vector4f(0,0,1, 180));
			camera.rotateWorld(new Vector4f(1, 0, 0, 90));
			camera.rotateWorld(new Vector4f(0, 1, 0, 180));
			camera.update(0);
		resultViewMatrices[2] = camera.getViewMatrix();
		resultProjectionMatrices[2] = projectionMatrix;
			camera.setOrientation(new Quaternion().setIdentity());
			camera.rotateWorld(new Vector4f(0,0,1, 180));
			camera.rotateWorld(new Vector4f(1, 0, 0, -90));
            camera.rotateWorld(new Vector4f(0, 1, 0, 180));
			camera.update(0);
		resultViewMatrices[3] = camera.getViewMatrix();
		resultProjectionMatrices[3] = projectionMatrix;
			camera.setOrientation(new Quaternion().setIdentity());
			camera.rotateWorld(new Vector4f(0,0,1, 180));
			camera.rotateWorld(new Vector4f(0, 1, 0, -180));
			camera.update(0);
		resultViewMatrices[4] = camera.getViewMatrix();
		resultProjectionMatrices[4] = projectionMatrix;
			camera.setOrientation(new Quaternion().setIdentity());
			camera.rotateWorld(new Vector4f(0,1,0, 180));
			camera.update(0);
		resultViewMatrices[5] = camera.getViewMatrix();
		resultProjectionMatrices[5] = projectionMatrix;

		return new TypedTuple<>(resultViewMatrices, resultProjectionMatrices);
	}
}
