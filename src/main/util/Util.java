package main.util;

import static main.log.ConsoleLogger.getLogger;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.imageio.ImageIO;

import main.Material;
import main.TextureBuffer;

import org.lwjgl.Sys;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Quaternion;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;
import org.newdawn.slick.opengl.Texture;
import org.newdawn.slick.opengl.TextureLoader;
import org.newdawn.slick.util.ResourceLoader;

import de.matthiasmann.twl.utils.PNGDecoder;
import de.matthiasmann.twl.utils.PNGDecoder.Format;

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

	public static Texture loadTexture(String filename) {
		return loadTexture(filename, Material.MIPMAP_DEFAULT);
	}
	
	private static Texture loadDefaultTexture() {
		return Util.loadTexture("assets/textures/stone_diffuse.png");
	}

	public static Texture loadTexture(String filename, boolean mipmap) {
		Texture texture = null;
		String extension = getFileExtension(filename).toUpperCase();
		try {
			texture = TextureLoader.getTexture(extension, ResourceLoader.getResourceAsStream(filename));
		} catch (IOException e) {
			try {
				texture = TextureLoader.getTexture(extension, ResourceLoader.class.getResourceAsStream(filename));
			} catch (IOException e1) {
				LOGGER.log(Level.WARNING, filename + " could not be loaded, default texture used instead");
				texture = loadDefaultTexture();
			} catch (NullPointerException np) {
				LOGGER.log(Level.WARNING, filename + " could not be loaded, default texture used instead");
				texture = loadDefaultTexture();
			}
		}

		texture.bind();
		if (mipmap) {
			GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
		}

		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL14.GL_MIRRORED_REPEAT);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL14.GL_MIRRORED_REPEAT);
		
		return texture;
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
	

}
