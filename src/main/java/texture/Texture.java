package texture;

import engine.AppContext;
import org.apache.commons.io.FilenameUtils;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import renderer.OpenGLThread;
import renderer.constants.GlTextureTarget;
import util.CompressionUtils;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.logging.Logger;
import java.util.zip.DataFormatException;

import static renderer.constants.GlTextureTarget.TEXTURE_2D;

public class Texture implements Serializable {
	private static final long serialVersionUID = 1L;
    public static final boolean COMPILED_TEXTURES = true;
    private transient boolean srgba;

    private String path = "";

    private volatile boolean mipmapsGenerated = false;

	protected GlTextureTarget target = TEXTURE_2D;
    transient protected int textureID;
    protected int height;
    protected int width;

    protected volatile byte[] data;
    protected int dstPixelFormat = GL11.GL_RGBA;
    protected int srcPixelFormat = GL11.GL_RGBA;
    protected int minFilter = GL11.GL_LINEAR;
    protected int magFilter = GL11.GL_LINEAR;
    private int mipmapCount = -1;

    protected Texture() {
    }
	
    /**
     * Create a new texture
     * @param textureID The GL texture ID
     */
    public Texture(String path, int textureID) {
        this(path, textureID, false);
    }

    public Texture(String path, int id, boolean srgba) {
        this(path, TEXTURE_2D, id, srgba);
    }

    public Texture(String path, GlTextureTarget target, int id, boolean srgba) {
        this.target = target;
        this.textureID = id;
        this.path = path;
        this.srgba = srgba;
    }

    /**
     * Bind the specified GL context to a texture
     *
     */
    public void bind() {
        AppContext.getInstance().getRenderer().getOpenGLContext().bindTexture(target, textureID);
    }
    public void bind(int unit) {
        AppContext.getInstance().getRenderer().getOpenGLContext().bindTexture(unit, target, textureID);
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
//		imageBuffer.order(ByteOrder.nativeOrder());
		imageBuffer.put(data, 0, data.length);
		imageBuffer.flip();
		return imageBuffer;
	}

    public void upload() {
        upload(false);
    }
	public void upload(boolean srgba) {
//		new OpenGLThread() {
//			@Override
//			public void doRun() {
//				upload(buffer());
//			}
//		}.start();

        AppContext.getInstance().getRenderer().getOpenGLContext().doWithOpenGLContext(() -> {
            upload(buffer(), srgba);
        });
	}
	
	public void upload(ByteBuffer textureBuffer) {
		upload(textureBuffer, false);
	}
	
	public void upload(ByteBuffer textureBuffer, boolean srgba) {
        new OpenGLThread() {
            @Override
            public void doRun() {
                AppContext.getInstance().getRenderer().getOpenGLContext().doWithOpenGLContext(() -> {
                    bind();
                    if (target == TEXTURE_2D)
                    {
                        GL11.glTexParameteri(target.glTarget, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
                        GL11.glTexParameteri(target.glTarget, GL11.GL_TEXTURE_MAG_FILTER, magFilter);
                        GL11.glTexParameteri(target.glTarget, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
                        GL11.glTexParameteri(target.glTarget, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
                        GL11.glTexParameteri(target.glTarget, GL12.GL_TEXTURE_BASE_LEVEL, 0);
                        GL11.glTexParameteri(target.glTarget, GL12.GL_TEXTURE_MAX_LEVEL, util.Util.calculateMipMapCount(Math.max(width,height)));
                    }

                    int internalformat = EXTTextureCompressionS3TC.GL_COMPRESSED_RGBA_S3TC_DXT5_EXT;
                    //internalformat = EXTTextureSRGB.GL_SRGB8_ALPHA8_EXT;
                    if(srgba) {
                        internalformat = EXTTextureSRGB.GL_COMPRESSED_SRGB_ALPHA_S3TC_DXT5_EXT;
                        //internalformat = EXTTextureSRGB.GL_SRGB8_ALPHA8_EXT;
                    }
                    synchronized (data) {
                        GL11.glTexImage2D(target.glTarget,
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
                }, false);
            }
        }.start();

//        AppContext.getInstance().getRenderer().doWithOpenGLContext(() -> {
//            bind();
//            if (target == TEXTURE_2D)
//            {
//                GL11.glTexParameteri(target.glTarget, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
//                GL11.glTexParameteri(target.glTarget, GL11.GL_TEXTURE_MAG_FILTER, magFilter);
//                GL11.glTexParameteri(target.glTarget, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
//                GL11.glTexParameteri(target.glTarget, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
//                GL11.glTexParameteri(target.glTarget, GL12.GL_TEXTURE_BASE_LEVEL, 0);
//                GL11.glTexParameteri(target.glTarget, GL12.GL_TEXTURE_MAX_LEVEL, util.Util.calculateMipMapCount(Math.max(width,height)));
//            }
//
//            int internalformat = EXTTextureCompressionS3TC.GL_COMPRESSED_RGBA_S3TC_DXT5_EXT;
//            //internalformat = EXTTextureSRGB.GL_SRGB8_ALPHA8_EXT;
//            if(srgba) {
//                internalformat = EXTTextureSRGB.GL_COMPRESSED_SRGB_ALPHA_S3TC_DXT5_EXT;
//                //internalformat = EXTTextureSRGB.GL_SRGB8_ALPHA8_EXT;
//            }
//            synchronized (data) {
//                GL11.glTexImage2D(target.glTarget,
//                        0,
//                        internalformat,
//                        getWidth(),
//                        getHeight(),
//                        0,
//                        srcPixelFormat,
//                        GL11.GL_UNSIGNED_BYTE,
//                        textureBuffer);
//            }
////        if(mipmapsGenerated) {
////            uploadMipMaps(internalformat);
////        } else {
////            GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
////            downloadMipMaps();
////        }
//            GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
//        }, false);
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
            GL11.glTexImage2D(target.glTarget,
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
        return read(resourceName, textureId, false);
    }
    public static Texture read(String resourceName, int textureId, boolean srgba) {
		String fileName = FilenameUtils.getBaseName(resourceName);
		FileInputStream fis = null;
		ObjectInputStream in = null;

		try {
			fis = new FileInputStream(getDirectory() + fileName + ".hptexture");
			in = new ObjectInputStream(fis);
			Texture texture = (Texture) in.readObject();
			in.close();
			texture.textureID = textureId;
			texture.upload(srgba);
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

    public void readAndUpload() {
        new Thread(() -> {
            try {
                System.out.println("Thread started ...");
                FileInputStream fis = new FileInputStream(getDirectory() + FilenameUtils.getBaseName(path) + ".hptexture");
                ObjectInputStream in = new ObjectInputStream(fis);
                Texture texture = (Texture) in.readObject();
                in.close();
                init(texture);
                System.out.println("Data: " + getData().length);
                upload();
                System.out.println("Uploaded");
            } catch (IOException | ClassNotFoundException | IllegalArgumentException e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void init(Texture texture) {
        setData(texture.getData());
        setWidth(texture.getWidth());
        setHeight(texture.getHeight());
        setTarget(texture.getTarget());
        setDstPixelFormat(texture.getDstPixelFormat());
        setSrcPixelFormat(texture.getSrcPixelFormat());
        setMinFilter(texture.getMinFilter());
        setMagFilter(texture.getMagFilter());
    }

    public void convertAndUpload() {
        new Thread(() -> {
            try {
                BufferedImage bufferedImage = AppContext.getInstance().getRenderer().getTextureFactory().loadImage(path);
                setWidth(bufferedImage.getWidth());
                setHeight(bufferedImage.getHeight());
                setMinFilter(minFilter);
                setMagFilter(magFilter);

                if (bufferedImage.getColorModel().hasAlpha()) {
                    srcPixelFormat = GL11.GL_RGBA;
                } else {
                    srcPixelFormat = GL11.GL_RGB;
                }

                setDstPixelFormat(dstPixelFormat);
                setSrcPixelFormat(srcPixelFormat);

                // convert that image into a byte buffer of texture data
                ByteBuffer textureBuffer = AppContext.getInstance().getRenderer().getTextureFactory().
                        convertImageData(bufferedImage, this);

                upload(textureBuffer, srgba);
            } catch (IOException | NullPointerException e) {
                e.printStackTrace();
                Logger.getGlobal().severe("Texture not found: " + path + ". Default texture returned...");
            }
        }).start();
    }

    public GlTextureTarget getTarget() {
        return target;
    }

    public void setTarget(GlTextureTarget target) {
        this.target = target;
    }

    public int getDstPixelFormat() {
        return dstPixelFormat;
    }

    public int getSrcPixelFormat() {
        return srcPixelFormat;
    }

    public int getMinFilter() {
        return minFilter;
    }

    public int getMagFilter() {
        return magFilter;
    }
}