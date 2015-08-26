package texture;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.zip.DataFormatException;

import com.sun.org.apache.xpath.internal.SourceTree;
import engine.AppContext;
import org.apache.commons.lang.builder.ReflectionToStringBuilder;
import org.lwjgl.BufferUtils;
import org.lwjgl.Sys;
import org.lwjgl.opengl.*;
import renderer.DeferredRenderer;
import renderer.OpenGLThread;
import util.*;

import org.apache.commons.io.FilenameUtils;

import javax.sound.midi.SysexMessage;

public class Texture implements Serializable {
	private static final long serialVersionUID = 1L;
    public static final boolean COMPILED_TEXTURES = false;

    private String path = "";

    private volatile boolean mipmapsGenerated = false;

	protected int target;
    transient protected int textureID;
    protected int height;
    protected int width;

    protected volatile byte[] data;
    protected int dstPixelFormat;
    protected int srcPixelFormat;
    protected int minFilter;
    protected int magFilter;
    private int mipmapCount = -1;

    protected Texture() {
    }
	
    /**
     * Create a new texture
     *
     * @param target The GL target 
     * @param textureID The GL texture ID
     */
    public Texture(String path, int target,int textureID) {
        this.target = target;
        this.textureID = textureID;
        this.path = path;
    }
    
    /**
     * Bind the specified GL context to a texture
     *
     */
    public void bind() {
        AppContext.getInstance().getRenderer().doWithOpenGLContext(() -> {
            GL11.glBindTexture(target, textureID);
        });
    }

    public void setHeight(int height) {
        this.height = height;
    }
    public void setWidth(int width) {
        this.width = width;
    }
    
    public int getHeight() {
        return height;
    }
    public int getWidth() {
        return width;
    }
    

    public void setData(byte[] data) {
        this.data = data;
    }

	public void setDstPixelFormat(int dstPixelFormat) {
		this.dstPixelFormat = dstPixelFormat;
	}

	public void setSrcPixelFormat(int srcPixelFormat) {
		this.srcPixelFormat = srcPixelFormat;
	}
	
	public ByteBuffer buffer() {
		ByteBuffer imageBuffer = ByteBuffer.allocateDirect(data.length);
		imageBuffer.order(ByteOrder.nativeOrder());
		imageBuffer.put(data, 0, data.length);
		imageBuffer.flip();
		return imageBuffer;
	}

	public void upload() {
		new OpenGLThread() {
			@Override
			public void doRun() {
				upload(buffer());
			}
		}.start();

//        AppContext.getInstance().getRenderer().doWithOpenGLContext(() -> {
//            upload(buffer());
//        });
	}
	
	public void upload(ByteBuffer textureBuffer) {
		upload(textureBuffer, false);
	}
	
	public void upload(ByteBuffer textureBuffer, boolean srgba) {
        AppContext.getInstance().getRenderer().doWithOpenGLContext(() -> {

            bind();
            if (target == GL11.GL_TEXTURE_2D)
            {
                GL11.glTexParameteri(target, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
                GL11.glTexParameteri(target, GL11.GL_TEXTURE_MAG_FILTER, magFilter);
                GL11.glTexParameteri(target, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
                GL11.glTexParameteri(target, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
//            GL11.glTexParameteri(target, GL14.GL_GENERATE_MIPMAP, GL11.GL_TRUE);
            }

            int internalformat = EXTTextureCompressionS3TC.GL_COMPRESSED_RGBA_S3TC_DXT5_EXT;
            //internalformat = EXTTextureSRGB.GL_SRGB8_ALPHA8_EXT;
            if(srgba) {
                internalformat = EXTTextureSRGB.GL_COMPRESSED_SRGB_ALPHA_S3TC_DXT5_EXT;
                //internalformat = EXTTextureSRGB.GL_SRGB8_ALPHA8_EXT;
            }
            synchronized (data) {
                GL11.glTexImage2D(target,
                        0,
                        internalformat,
                        getWidth(),
                        getHeight(),
                        0,
                        srcPixelFormat,
                        GL11.GL_UNSIGNED_BYTE,
                        textureBuffer);
            }
//        if(mipmapsGenerated) {
//            uploadMipMaps(internalformat);
//        } else {
//            GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
//            downloadMipMaps();
//        }
            GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
        });
	}

    private void downloadMipMaps() {
        int currentWidth = width/2;
        int currentHeight = height/2;
        for(int i = 1; i < mipmapCount; i++) {
            ByteBuffer tempBuffer = BufferUtils.createByteBuffer(currentHeight * currentWidth * 4);
            tempBuffer.rewind();
            TextureFactory.getTextureData(textureID, i, GL11.GL_RGBA, tempBuffer);
            tempBuffer.get(data[i]);
            currentWidth /= 2;
            currentHeight /= 2;
        }
        mipmapsGenerated = true;
    }

    private void uploadMipMaps(int internalformat) {
        int currentWidth = width/2;
        int currentHeight = height/2;
        for(int i = 1; i < mipmapCount; i++) {
            ByteBuffer tempBuffer = BufferUtils.createByteBuffer(currentHeight * currentWidth * 4);
            tempBuffer.rewind();
            tempBuffer.put(data[i]);
            tempBuffer.rewind();
            GL11.glTexImage2D(target,
                    0,
                    internalformat,
                    currentWidth,
                    currentHeight,
                    0,
                    srcPixelFormat,
                    GL11.GL_UNSIGNED_BYTE,
                    tempBuffer);
            currentWidth /= 2;
            currentHeight /= 2;
        }
    }

	public void setMinFilter(int minFilter) {
		this.minFilter = minFilter;
	}

	public void setMagFilter(int magFilter) {
		this.magFilter = magFilter;
	}

    public byte[] getData() {
        return data;
    }

	public static Texture read(String resourceName, int textureId) {
		String fileName = FilenameUtils.getBaseName(resourceName);
		FileInputStream fis = null;
		ObjectInputStream in = null;

		try {
			fis = new FileInputStream(getDirectory() + fileName + ".hptexture");
			in = new ObjectInputStream(fis);
			Texture texture = (Texture) in.readObject();
			in.close();
			texture.textureID = textureId;
			texture.upload();
			return texture;
		} catch (IOException | ClassNotFoundException | IllegalArgumentException e) {
			e.printStackTrace();
		}
		
		return null;
	}
	
	public static boolean write(Texture texture, String resourceName) {
		String fileName = FilenameUtils.getBaseName(resourceName);
		FileOutputStream fos = null;
		ObjectOutputStream out = null;
		try {
			fos = new FileOutputStream(getDirectory() + fileName + ".hptexture");
			out = new ObjectOutputStream(fos);
			out.writeObject(texture);
			return true;

		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				out.close();
				fos.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return false;
	}
	

	private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
//		long start = System.currentTimeMillis();
		in.defaultReadObject();
	    decompress();
//		System.out.println("TEXTURE READ IN " +  (System.currentTimeMillis() - start) + " ms");
	}

	
	private void writeObject(ObjectOutputStream oos) throws IOException {
		compress();
        oos.defaultWriteObject();
    }

	protected void compress() throws IOException {
//    	long start = System.currentTimeMillis();
		synchronized (data) {
			setData(CompressionUtils.compress(getData()));
		}
//		System.out.println("Compression took " + (System.currentTimeMillis() - start));
	}

	protected void decompress() throws IOException {
		try {
//	    	long start = System.currentTimeMillis();
			synchronized (data) {
				setData(CompressionUtils.decompress(getData()));
			}
//			System.out.println("Decompression took " + (System.currentTimeMillis() - start));
		} catch (DataFormatException e) {
			e.printStackTrace();
		}
	}
	
	public static String getDirectory() {
		return AppContext.WORKDIR_NAME + "/assets/textures/";
	}
	
	public String getPath() {
		return path;
	}

	public void setPath(String path) {
		this.path = path;
	}

	@Override
	public String toString() {
		return "(Texture)" + path;
	}

	@Override
	public int hashCode() {
		return path.hashCode();
	}

    public int getTextureID() {
		return textureID;
	}

	public void setTextureID(int textureID) {
		this.textureID = textureID;
	}

	public static boolean filterRequiresMipmaps(int magTextureFilter) {
		return (magTextureFilter == GL11.GL_LINEAR_MIPMAP_LINEAR ||
				magTextureFilter == GL11.GL_LINEAR_MIPMAP_NEAREST ||
				magTextureFilter == GL11.GL_NEAREST_MIPMAP_LINEAR ||
				magTextureFilter == GL11.GL_NEAREST_MIPMAP_NEAREST);
	}
}