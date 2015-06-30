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

import engine.World;
import renderer.DeferredRenderer;
import util.CompressionUtils;
import util.OpenGLThread;

import org.apache.commons.io.FilenameUtils;
import org.lwjgl.opengl.EXTTextureCompressionS3TC;
import org.lwjgl.opengl.EXTTextureSRGB;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;

/**
 * A texture to be bound within JOGL. This object is responsible for 
 * keeping track of a given OpenGL texture and for calculating the
 * texturing mapping coordinates of the full image.
 * 
 * Since textures need to be powers of 2 the actual texture may be
 * considerably bigged that the source image and hence the texture
 * mapping coordinates need to be adjusted to matchup drawing the
 * sprite against the texture.
 *
 * @author Kevin Glass
 * @author Brian Matzon
 */
public class Texture implements Serializable {
	private static final long serialVersionUID = 1L;

	private String path = "";
	
    /** The GL target type */
	protected int target; 
    /** The GL texture ID */
    transient protected int textureID;
	/** The height of the image */
    protected int height;
    /** The width of the image */
    protected int width;
    /** The width of the texture */
    protected int texWidth;
    /** The height of the texture */
    protected int texHeight;
    /** The ratio of the width of the image to the texture */
    protected float widthRatio;
    /** The ratio of the height of the image to the texture */
    protected float heightRatio;
    
    protected byte[] data;
    protected int dstPixelFormat;
    protected int srcPixelFormat;
    protected int minFilter;
    protected int magFilter;
    
	protected Texture() {}
	
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
      GL11.glBindTexture(target, textureID); 
    }
    
    /**
     * Set the height of the image
     *
     * @param height The height of the image
     */
    public void setHeight(int height) {
        this.height = height;
        setHeight();
    }
    
    /**
     * Set the width of the image
     *
     * @param width The width of the image
     */
    public void setWidth(int width) {
        this.width = width;
        setWidth();
    }
    
    /**
     * Get the height of the original image
     *
     * @return The height of the original image
     */
    public int getImageHeight() {
        return height;
    }
    
    /** 
     * Get the width of the original image
     *
     * @return The width of the original image
     */
    public int getImageWidth() {
        return width;
    }
    
    /**
     * Get the height of the physical texture
     *
     * @return The height of physical texture
     */
    public float getHeight() {
        return heightRatio;
    }
    
    /**
     * Get the width of the physical texture
     *
     * @return The width of physical texture
     */
    public float getWidth() {
        return widthRatio;
    }
    
    /**
     * Set the height of this texture 
     *
     * @param texHeight The height of the texture
     */
    public void setTextureHeight(int texHeight) {
        this.texHeight = texHeight;
        setHeight();
    }
    
    /**
     * Set the width of this texture 
     *
     * @param texWidth The width of the texture
     */
    public void setTextureWidth(int texWidth) {
        this.texWidth = texWidth;
        setWidth();
    }
    
    /**
     * Set the height of the texture. This will update the
     * ratio also.
     */
    private void setHeight() {
        if (texHeight != 0) {
            heightRatio = ((float) height)/texHeight;
        }
    }
    
    /**
     * Set the width of the texture. This will update the
     * ratio also.
     */
    private void setWidth() {
        if (texWidth != 0) {
            widthRatio = ((float) width)/texWidth;
        }
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
	
    /**
     * Get the closest greater power of 2 to the fold number
     * 
     * @param fold The target number
     * @return The power of 2
     */
    protected int get2Fold(int fold) {
        int ret = 2;
        while (ret < fold) {
            ret *= 2;
        }
        return ret;
    }

	public ByteBuffer buffer() {
		ByteBuffer imageBuffer = ByteBuffer.allocateDirect(data.length);
		imageBuffer = ByteBuffer.allocateDirect(data.length);
		imageBuffer.order(ByteOrder.nativeOrder());
		imageBuffer.put(data, 0, data.length);
		imageBuffer.flip();
		return imageBuffer;
	}

	public void upload() {
//		new OpenGLThread() {
//			@Override
//			public void doRun() {
//				upload(buffer());
//			}
//		}.start();
		upload(buffer());
	}
	
	public void upload(ByteBuffer textureBuffer) {
		upload(textureBuffer, false);
	}
	
	public void upload(ByteBuffer textureBuffer, boolean srgba) {

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
		GL11.glTexImage2D(target, 
                      0, 
                      internalformat,
                      get2Fold(getImageWidth()), 
                      get2Fold(getImageHeight()), 
                      0, 
                      srcPixelFormat, 
                      GL11.GL_UNSIGNED_BYTE, 
                      textureBuffer);
		GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
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
			DeferredRenderer.exitOnGLError("XXX");
			texture.upload();
			DeferredRenderer.exitOnGLError("YYY");
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
		setData(CompressionUtils.compress(getData()));
//		System.out.println("Compression took " + (System.currentTimeMillis() - start));
	}

	protected void decompress() throws IOException {
		try {
//	    	long start = System.currentTimeMillis();
			setData(CompressionUtils.decompress(getData()));
//			System.out.println("Decompression took " + (System.currentTimeMillis() - start));
		} catch (DataFormatException e) {
			e.printStackTrace();
		}
	}
	
	public static String getDirectory() {
		return World.WORKDIR_NAME + "/assets/textures/";
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