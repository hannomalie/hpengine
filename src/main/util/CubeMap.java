package main.util;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

import main.World;

import org.apache.commons.io.FilenameUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL32;

public class CubeMap extends Texture implements Serializable {

	private List<byte[]> dataList;

	public CubeMap(int target, int textureID) {
		super(target, textureID);
	}

	public void upload() {

        bind();
        if (target == GL13.GL_TEXTURE_CUBE_MAP)
        { 
            GL11.glTexParameteri(target, GL11.GL_TEXTURE_MIN_FILTER, minFilter); 
            GL11.glTexParameteri(target, GL11.GL_TEXTURE_MAG_FILTER, magFilter);
            GL11.glTexParameteri (target, GL12.GL_TEXTURE_WRAP_R, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri (target, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri (target, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        }


        ByteBuffer perFaceBuffer = ByteBuffer.allocateDirect(dataList.get(0).length);
        
        load(GL13.GL_TEXTURE_CUBE_MAP_POSITIVE_X, buffer(perFaceBuffer, dataList.get(1))); //1
		load(GL13.GL_TEXTURE_CUBE_MAP_NEGATIVE_X, buffer(perFaceBuffer, dataList.get(0))); //0
        load(GL13.GL_TEXTURE_CUBE_MAP_POSITIVE_Y, buffer(perFaceBuffer, dataList.get(2)));
        load(GL13.GL_TEXTURE_CUBE_MAP_NEGATIVE_Y, buffer(perFaceBuffer, dataList.get(3)));
        load(GL13.GL_TEXTURE_CUBE_MAP_POSITIVE_Z, buffer(perFaceBuffer, dataList.get(4)));
        load(GL13.GL_TEXTURE_CUBE_MAP_NEGATIVE_Z, buffer(perFaceBuffer, dataList.get(5)));
        GL11.glEnable(GL32.GL_TEXTURE_CUBE_MAP_SEAMLESS);
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
                dstPixelFormat, 
                get2Fold(getImageWidth()/4), 
                get2Fold(getImageHeight()/3), 
                0, 
                srcPixelFormat, 
                GL11.GL_UNSIGNED_BYTE, 
                buffer);
	}

	public void setData(List<byte[]> byteArrays) {
		dataList = byteArrays;
	}
	
	public static CubeMap read(String resourceName) {
		String fileName = FilenameUtils.getBaseName(resourceName);
		FileInputStream fis = null;
		ObjectInputStream in = null;
		try {
			fis = new FileInputStream(World.WORKDIR_NAME + "/" + fileName + ".hptexture");
			in = new ObjectInputStream(fis);
			CubeMap texture = (CubeMap) in.readObject();
			in.close();
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
			fos = new FileOutputStream(World.WORKDIR_NAME + "/" + fileName + ".hptexture");
			out = new ObjectOutputStream(fos);
			out.writeObject(texture);

		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				out.close();
				return true;
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return false;
	}
}