package main.util;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import javax.imageio.ImageIO;

import main.TextureBuffer;

import org.lwjgl.BufferUtils;
import org.lwjgl.Sys;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Vector2f;
import org.lwjgl.util.vector.Vector3f;

import de.matthiasmann.twl.utils.PNGDecoder;
import de.matthiasmann.twl.utils.PNGDecoder.Format;

public class Util {
	private static final double PI = 3.14159265358979323846;
	
	public static String loadAsTextFile(String path) {
		//InputStream in = Util.class.getClass().getResourceAsStream("/de/hanno/render/shader/vs.vs");
		InputStream in = Util.class.getClass().getResourceAsStream(path);
		String result = convertStreamToString(in);
		
		try {
			in.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return result;
	}
	
	//public static TextureBuffer loadPNGTexture(String filename, int textureUnit) {
	public static TextureBuffer loadPNGTexture(String filename) {
		ByteBuffer buf = null;
		int tWidth = 0;
		int tHeight = 0;
		
		try {
			// Open the PNG file as an InputStream
			InputStream in = Util.class.getClass().getResourceAsStream(filename);
//			InputStream in = new FileInputStream(filename);
			// Link the PNG decoder to this stream
			PNGDecoder decoder = new PNGDecoder(in);
			
			// Get the width and height of the texture
			tWidth = decoder.getWidth();
			tHeight = decoder.getHeight();
			
			
			// Decode the PNG file in a ByteBuffer
			buf = ByteBuffer.allocateDirect(
					4 * decoder.getWidth() * decoder.getHeight());
			decoder.decode(buf, decoder.getWidth() * 4, Format.RGBA);
			buf.flip();
			
			in.close();
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(-1);
		}
		
		return new TextureBuffer(tWidth, tHeight, buf);
		
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
}
