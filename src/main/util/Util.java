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
	
	public static TextureBuffer loadPNGTexture(String filename, int textureUnit) {
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
}
