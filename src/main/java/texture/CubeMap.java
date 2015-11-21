package texture;

import org.apache.commons.io.FilenameUtils;
import org.lwjgl.opengl.EXTTextureSRGB;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import renderer.OpenGLContext;
import renderer.constants.GlTextureTarget;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

import static renderer.constants.GlTextureTarget.TEXTURE_CUBE_MAP;

public class CubeMap extends Texture implements Serializable {
	private static final long serialVersionUID = 1L;
	
	private List<byte[]> dataList;

	protected CubeMap() {}
	
	public CubeMap(String path, GlTextureTarget target, int textureID) {
		super(path, target, false);
	}
	
	public void upload() {

		OpenGLContext.getInstance().doWithOpenGLContext(() -> {
			bind();
//        if (target == GL13.GL_TEXTURE_CUBE_MAP)
			{
				GL11.glTexParameteri(target.glTarget, GL11.GL_TEXTURE_MIN_FILTER, minFilter);
				GL11.glTexParameteri(target.glTarget, GL11.GL_TEXTURE_MAG_FILTER, magFilter);
				GL11.glTexParameteri (target.glTarget, GL12.GL_TEXTURE_WRAP_R, GL12.GL_CLAMP_TO_EDGE);
				GL11.glTexParameteri (target.glTarget, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
				GL11.glTexParameteri (target.glTarget, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
			}


			ByteBuffer perFaceBuffer = ByteBuffer.allocateDirect(dataList.get(0).length);

			load(GL13.GL_TEXTURE_CUBE_MAP_POSITIVE_X, buffer(perFaceBuffer, dataList.get(1))); //1
			load(GL13.GL_TEXTURE_CUBE_MAP_NEGATIVE_X, buffer(perFaceBuffer, dataList.get(0))); //0
			load(GL13.GL_TEXTURE_CUBE_MAP_POSITIVE_Y, buffer(perFaceBuffer, dataList.get(2)));
			load(GL13.GL_TEXTURE_CUBE_MAP_NEGATIVE_Y, buffer(perFaceBuffer, dataList.get(3)));
			load(GL13.GL_TEXTURE_CUBE_MAP_POSITIVE_Z, buffer(perFaceBuffer, dataList.get(4)));
			load(GL13.GL_TEXTURE_CUBE_MAP_NEGATIVE_Z, buffer(perFaceBuffer, dataList.get(5)));
		});
	}
	
	public ByteBuffer buffer(ByteBuffer buffer, byte[] values) {
		buffer.order(ByteOrder.nativeOrder());
		buffer.put(values, 0, values.length);
		buffer.flip();
		return buffer;
	}
	
	private void load(int cubemapFace, ByteBuffer buffer) {
        GL11.glTexImage2D(cubemapFace,
                0, 
                //EXTTextureCompressionS3TC.GL_COMPRESSED_RGBA_S3TC_DXT5_EXT, 
                EXTTextureSRGB.GL_COMPRESSED_SRGB_ALPHA_S3TC_DXT5_EXT, 
				(getWidth()/4),
				(getHeight()/3),
                0, 
                srcPixelFormat, 
                GL11.GL_UNSIGNED_BYTE, 
                buffer);
	}

	public void setData(List<byte[]> byteArrays) {
		dataList = byteArrays;
	}
	
	public static CubeMap read(String resourceName, int textureId) {
		String fileName = FilenameUtils.getBaseName(resourceName);
		FileInputStream fis = null;
		ObjectInputStream in = null;
		try {
			fis = new FileInputStream(getDirectory() + fileName + ".hpcubemap");
			in = new ObjectInputStream(fis);
			CubeMap texture = (CubeMap) in.readObject();
			in.close();
			texture.textureID = textureId;
			texture.upload();
			return texture;
		} catch (IOException | ClassNotFoundException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	public static boolean write(CubeMap texture, String resourceName) {
		String fileName = FilenameUtils.getBaseName(resourceName);
		FileOutputStream fos = null;
		ObjectOutputStream out = null;
		try {
			fos = new FileOutputStream(getDirectory() + fileName + ".hpcubemap");
			out = new ObjectOutputStream(fos);
			out.writeObject(texture);

		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				out.close();
				fos.close();
				return true;
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return false;
	}
	
	// TODO: Implement compression for cubemaps
	@Override
	protected void compress() throws IOException {
//		long start = System.currentTimeMillis();
//    	for (int i = 0; i < dataList.size(); i++) {
//    		byte[] compressed = CompressionUtils.compress(dataList.get(i));
//    		System.out.println("Compressed " +  compressed.length);
//    		dataList.set(i, compressed);
//		}
//		System.out.println("CubeMap compression took " + (System.currentTimeMillis() - start));
	}

	@Override
	protected void decompress() throws IOException {
//		try {
//	    	long start = System.currentTimeMillis();
//	    	for (int i = 0; i < dataList.size(); i++) {
//	    		dataList.set(i, CompressionUtils.decompress(dataList.get(i)));
//			}
//			System.out.println("CubeMap decompression took " + (System.currentTimeMillis() - start));
//		} catch (DataFormatException e) {
//			e.printStackTrace();
//		}
	}
	
	@Override
	public String toString() {
		return "(Cubemap)" + getPath();
	}

	public void bind(int unit) {
		OpenGLContext.getInstance().bindTexture(unit, TEXTURE_CUBE_MAP, textureID);
	}
}