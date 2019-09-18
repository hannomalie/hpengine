package de.hanno.hpengine.util;

import de.hanno.hpengine.engine.component.ModelComponent;
import de.hanno.hpengine.engine.entity.Entity;
import de.hanno.hpengine.engine.transform.SimpleTransform;
import de.hanno.hpengine.engine.transform.Transform;
import de.hanno.hpengine.engine.camera.Camera;
import org.joml.*;

import javax.imageio.ImageIO;
import javax.vecmath.Quat4f;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.Math;
import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;

public class Util {
    private static final Logger LOGGER = Logger.getLogger(Util.class.getName());
	private static final double PI = 3.14159265358979323846;
	private static final String VECTOR_DELIMITER = "_";
	
	public static String loadAsTextFile(String path) {
		//InputStream in = Util.class.getClass().getResourceAsStream("/de/hanno/render/de.hanno.hpengine.shader/vs.vs");
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

    public static Matrix4f createPerspective(float fovInDegrees, float ratio, float near, float far) {
		return new Matrix4f().setPerspective((float) Math.toRadians(fovInDegrees), ratio, near, far);
    }
    
    public static Matrix4f createOrthogonal(float left, float right, float top, float bottom, float near, float far) {
		return new Matrix4f().setOrtho(left, right, bottom, top, near, far);
    }
	
	public static Matrix4f lookAt(Vector3f eye, Vector3f center, Vector3f up) {
		return new Matrix4f().lookAt(eye, center, up);
//        Vector3f f = new Vector3f();
//        Vector3f.sub(center, eye, f);
//        f.normalize();
//
//        Vector3f u = new Vector3f();
//        up.normalise(u);
//        Vector3f s = new Vector3f();
//        Vector3f.cross(f, u, s);
//        s.normalize();
//        Vector3f.cross(s, f, u);
//
//        Matrix4f mat = new Matrix4f();
//
//        mat.m00 = s.x;
//        mat.m10 = s.y;
//        mat.m20 = s.z;
//        mat.m01 = u.x;
//        mat.m11 = u.y;
//        mat.m21 = u.z;
//        mat.m02 = -f.x;
//        mat.m12 = -f.y;
//        mat.m22 = -f.z;
//
//        Matrix4f temp = new Matrix4f();
//        Matrix4f.translate(new Vector3f(-eye.x, -eye.y, -eye.z), mat, temp);
//
//        return mat;
    }
	
//	//http://molecularmusings.wordpress.com/2013/05/24/a-faster-quaternion-vector-multiplication/
//	public static Vector3f _mul(Vector3f a, Quaternionf b) {
//		Vector3f temp = (Vector3f) Vector3f.cross(new Vector3f(b.x, b.y, b.z), a, null).scale(2f);
//		Vector3f result = new Vector3f(a, (Vector3f) temp.scale(b.w), null);
//		result = new Vector3f(result, Vector3f.cross(new Vector3f(b.x,  b.y,  b.z), temp, null), null);
//		return result;
//	}
	public static Vector3f mul(Vector3f a, Quaternionf b) {
		return new Vector3f(a).rotate(b);
//		Quaternionf in = new Quaternionf(a.x,a.y,a.z,0);
//		Quaternionf temp = new Quaternionf((b, in, null);
//		temp = new Quaternionf((temp, conjugate(b), null);
//		return new Vector3f(temp.x, temp.y, temp.z);
	}
	
	public static Matrix4f toMatrix(Quaternionf q, Matrix4f out) {
		return q.get(out);
//		float qx = q.x();
//        float qy = q.y();
//        float qz = q.z();
//        float qw = q.w();
//
//        // just pretend these are superscripts.
//        float qx2 = qx * qx;
//        float qy2 = qy * qy;
//        float qz2 = qz * qz;
//
//        out.m00 = 1 - 2 * qy2 - 2 * qz2;
//        out.m01 = 2 * qx * qy + 2 * qz * qw;
//        out.m02 = 2 * qx * qz - 2 * qy * qw;
//        out.m03 = 0;
//
//        out.m10 = 2 * qx * qy - 2 * qz * qw;
//        out.m11 = 1 - 2 * qx2 - 2 * qz2;
//        out.m12 = 2 * qy * qz + 2 * qx * qw;
//        out.m13 = 0;
//
//        out.m20 = 2 * qx * qz + 2 * qy * qw;
//        out.m21 = 2 * qy * qz - 2 * qx * qw;
//        out.m22 = 1 - 2 * qx2 - 2 * qy2;
//        out.m23 = 0;
//
//        out.m30 = 0;
//        out.m31 = 0;
//        out.m32 = 0;
//
//        return out;
    }

//	public static Quaternionf slerp(Quaternionf q1, Quaternionf q2, float t) {
//		Quaternionf qInterpolated = new Quaternionf();
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

	public static Quaternionf conjugate(Quaternionf q) {
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
    public static Vector3f fromBullet(javax.vecmath.Vector3f in) {
        return new Vector3f(in.x, in.y, in.z);
    }
	public static com.bulletphysics.linearmath.Transform toBullet(Transform in) {
		Matrix4f out = in.getTransformation();
		return new com.bulletphysics.linearmath.Transform(toBullet(out));
	}

	public static javax.vecmath.Matrix4f toBullet(Matrix4f in) {
		javax.vecmath.Matrix4f out = new javax.vecmath.Matrix4f();
		out.m00 = in.m00();
		out.m01 = in.m01();
		out.m02 = in.m02();
		out.m03 = in.m03();
		out.m10 = in.m10();
		out.m11 = in.m11();
		out.m12 = in.m12();
		out.m13 = in.m13();
		out.m20 = in.m20();
		out.m21 = in.m21();
		out.m22 = in.m22();
		out.m23 = in.m23();
		out.m30 = in.m30();
		out.m31 = in.m31();
		out.m32 = in.m32();
		out.m33 = in.m33();
		
		out.transpose();
		return out;
	}
	public static Matrix4f fromBullet(javax.vecmath.Matrix4f in) {
		Matrix4f out = new Matrix4f();
		out.m00(in.m00);
		out.m01(in.m01);
		out.m02(in.m02);
		out.m03(in.m03);
		out.m10(in.m10);
		out.m11(in.m11);
		out.m12(in.m12);
		out.m13(in.m13);
		out.m20(in.m20);
		out.m21(in.m21);
		out.m22(in.m22);
		out.m23(in.m23);
		out.m30(in.m30);
		out.m31(in.m31);
		out.m32(in.m32);
		out.m33(in.m33);
		
		return out;
	}

	public static Transform fromBullet(com.bulletphysics.linearmath.Transform in) {
		javax.vecmath.Matrix4f out = new javax.vecmath.Matrix4f();
		in.getMatrix(out);
		Quat4f outRot = new Quat4f();

		Transform finalTransform = new SimpleTransform();
		finalTransform.setTranslation(new Vector3f(in.origin.x,in.origin.y,in.origin.z));
		finalTransform.setOrientation(fromBullet(in.getRotation(outRot)));
		return finalTransform;
	}

	private static Quaternionf fromBullet(Quat4f rotation) {
		return new Quaternionf(rotation.x, rotation.y, rotation.z, rotation.w);
	}


    public static int calculateMipMapCountPlusOne(int width, int height) {
        return calculateMipMapCount(Math.max(width, height)) + 1;
    }
    public static int calculateMipMapCount(int width, int height) {
        return calculateMipMapCount(Math.max(width, height));
    }
	public static int calculateMipMapCount(int size) {
		int maxLength = size;
		int count = 0;
		while(maxLength >= 1) {
			count++;
			maxLength /= 2;
		}
		return count;
	}

	public static void printFloatBuffer(FloatBuffer values) {
		printFloatBuffer(values, 4, 1000);
	}

	public static String printFloatBuffer(FloatBuffer buffer, int columns) {
		return printFloatBuffer(buffer, columns, 1000);
	}
	public static String printFloatBuffer(FloatBuffer buffer, int columns, int rows) {
		buffer.rewind();
		StringBuilder builder = new StringBuilder();
		int columnCounter = 1;
		int rowCounter = 0;
		while (buffer.hasRemaining() && rowCounter < rows) {
			builder.append(buffer.get());
			builder.append(" ");
			if(columnCounter%columns==0) { builder.append(System.lineSeparator()); rowCounter++; }
			columnCounter++;
		}
		buffer.rewind();
		String result = builder.toString();
		System.out.println(result);
		return result;
	}
	public static String printModelComponents(ByteBuffer buffer, int count) {
		buffer.rewind();
		StringBuilder builder = new StringBuilder();
		for(int i = 0; i < count; i++) {
			builder.append(ModelComponent.getDebugStringFromBuffer(buffer));
		}
		buffer.rewind();
		String result = builder.toString();
		System.out.println(result);
		return result;
	}
	public static String printIntBuffer(IntBuffer buffer, int columns) {
		return printIntBuffer(buffer, columns, 1000);
	}
	public static String printIntBuffer(IntBuffer buffer, int columns, int rows) {
        buffer.rewind();
        StringBuilder builder = new StringBuilder();
        int columnCounter = 1;
        int rowCounter = 0;
        while (columns > 0 && buffer.hasRemaining() && rowCounter < rows) {
            builder.append(buffer.get());
            builder.append(" ");
            if(columnCounter%columns==0) { builder.append(System.lineSeparator()); rowCounter++; }
            columnCounter++;
        }
        buffer.rewind();
		String result = builder.toString();
		System.out.println(result);
		return result;
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
		Camera camera = new Camera(new Entity());
		camera.setProjectionMatrix(Util.createPerspective(90, 1, 0.1f, 250f));
		Matrix4f projectionMatrix = camera.getProjectionMatrix();

		Matrix4f[] resultViewMatrices = new Matrix4f[6];
		Matrix4f[] resultProjectionMatrices = new Matrix4f[6];

			camera.getEntity().rotation(new Quaternionf().identity());
			camera.getEntity().rotate(new Vector3f(0f,0f,1f), 180);
			camera.getEntity().rotate(new Vector3f(0f, 1f, 0f), -90);
			camera.getEntity().translateLocal(position);
			camera.getEntity().recalculate();
		resultViewMatrices[1] = new Matrix4f(camera.getViewMatrix());
		resultProjectionMatrices[1] = new Matrix4f(projectionMatrix);

			camera.getEntity().rotation(new Quaternionf().identity());
			camera.getEntity().rotate(new Vector3f(0f,0f,1f), 180);
			camera.getEntity().rotate(new Vector3f(0f, 1f, 0f), 90);
			camera.getEntity().translateLocal(position);
			camera.getEntity().recalculate();
		resultViewMatrices[0] = new Matrix4f(camera.getViewMatrix());
		resultProjectionMatrices[0] = new Matrix4f(projectionMatrix);

			camera.getEntity().rotation(new Quaternionf().identity());
			camera.getEntity().rotate(new Vector3f(0f,0f,1f), 180);
			camera.getEntity().rotate(new Vector3f(1f, 0f, 0f), 90);
			camera.getEntity().rotate(new Vector3f(0f, 1f, 0f), 180);
			camera.getEntity().translateLocal(position);
			camera.getEntity().recalculate();
		resultViewMatrices[2] = new Matrix4f(camera.getViewMatrix());
		resultProjectionMatrices[2] = new Matrix4f(projectionMatrix);

			camera.getEntity().rotation(new Quaternionf().identity());
			camera.getEntity().rotate(new Vector3f(0f,0f,1f), 180);
			camera.getEntity().rotate(new Vector3f(1f, 0f, 0f), -90);
            camera.getEntity().rotate(new Vector3f(0f, 1f, 0f), 180);
			camera.getEntity().translateLocal(position);
			camera.getEntity().recalculate();
		resultViewMatrices[3] = new Matrix4f(camera.getViewMatrix());
		resultProjectionMatrices[3] = new Matrix4f(projectionMatrix);

			camera.getEntity().rotation(new Quaternionf().identity());
			camera.getEntity().rotate(new Vector3f(0f,0f,1f), 180);
			camera.getEntity().rotate(new Vector3f(0f, 1f, 0f), -180);
			camera.getEntity().translateLocal(position);
		resultViewMatrices[4] = new Matrix4f(camera.getViewMatrix());
		resultProjectionMatrices[4] = new Matrix4f(projectionMatrix);

			camera.getEntity().rotation(new Quaternionf().identity());
			camera.getEntity().rotate(new Vector3f(0f,0f,1f), 180);
			camera.getEntity().translateLocal(position);
		resultViewMatrices[5] = new Matrix4f(camera.getViewMatrix());
		resultProjectionMatrices[5] = new Matrix4f(projectionMatrix);

		return new TypedTuple<>(resultViewMatrices, resultProjectionMatrices);
	}

    public static boolean equals(Quaternionf a, Quaternionf b) {
        return a.x == b.x && a.y == b.y && a.x == b.x && a.w == b.w;
    }

	public static String translateGLErrorString(int error_code) {
		switch(error_code) {
			case 0:
				return "No error";
			case 1280:
				return "Invalid enum";
			case 1281:
				return "Invalid value";
			case 1282:
				return "Invalid operation";
			case 1283:
				return "Stack overflow";
			case 1284:
				return "Stack underflow";
			case 1285:
				return "Out of memory";
			case 1286:
				return "Invalid framebuffer operation";
			case 32817:
				return "Table too large";
			default:
				return null;
		}
	}

	public static boolean equals(Matrix4f a, Matrix4f b) {
      	if (a==null)
			return false;
		if (b == null)
			return false;
		if (a == b)
			return true;
		Matrix4fc other = b;
		if (Float.floatToIntBits(a.m00()) != Float.floatToIntBits(other.m00()))
			return false;
		if (Float.floatToIntBits(a.m01()) != Float.floatToIntBits(other.m01()))
			return false;
		if (Float.floatToIntBits(a.m02()) != Float.floatToIntBits(other.m02()))
			return false;
		if (Float.floatToIntBits(a.m03()) != Float.floatToIntBits(other.m03()))
			return false;
		if (Float.floatToIntBits(a.m10()) != Float.floatToIntBits(other.m10()))
			return false;
		if (Float.floatToIntBits(a.m11()) != Float.floatToIntBits(other.m11()))
			return false;
		if (Float.floatToIntBits(a.m12()) != Float.floatToIntBits(other.m12()))
			return false;
		if (Float.floatToIntBits(a.m13()) != Float.floatToIntBits(other.m13()))
			return false;
		if (Float.floatToIntBits(a.m20()) != Float.floatToIntBits(other.m20()))
			return false;
		if (Float.floatToIntBits(a.m21()) != Float.floatToIntBits(other.m21()))
			return false;
		if (Float.floatToIntBits(a.m22()) != Float.floatToIntBits(other.m22()))
			return false;
		if (Float.floatToIntBits(a.m23()) != Float.floatToIntBits(other.m23()))
			return false;
		if (Float.floatToIntBits(a.m30()) != Float.floatToIntBits(other.m30()))
			return false;
		if (Float.floatToIntBits(a.m31()) != Float.floatToIntBits(other.m31()))
			return false;
		if (Float.floatToIntBits(a.m32()) != Float.floatToIntBits(other.m32()))
			return false;
		if (Float.floatToIntBits(a.m33()) != Float.floatToIntBits(other.m33()))
			return false;
		return true;
	}
}
