package texture;

import engine.AppContext;
import engine.TimeStepThread;
import event.TexturesChangedEvent;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.lwjgl.opengl.*;
import org.lwjgl.util.vector.Vector2f;
import renderer.DeferredRenderer;
import renderer.OpenGLContext;
import renderer.constants.GlTextureTarget;
import renderer.material.Material;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.color.ColorSpace;
import java.awt.image.*;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import static renderer.constants.GlTextureTarget.*;

/**
 * A utility class to load textures for JOGL. This source is based
 * on a texture that can be found in the Java Gaming (www.javagaming.org)
 * Wiki. It has been simplified slightly for explicit 2D graphics use.
 * 
 * OpenGL uses a particular image format. Since the images that are 
 * loaded from disk may not match this format this loader introduces
 * a intermediate image which the source image is copied into. In turn,
 * this image is used as source for the OpenGL texture.
 *
 * @author Kevin Glass
 * @author Brian Matzon
 */
public class TextureFactory {
    private static volatile TextureFactory instance = null;
    private static volatile BufferedImage defaultTextureAsBufferedImage = null;
    public static volatile long TEXTURE_UNLOAD_THRESHOLD_IN_MS = 10000;
    private static volatile boolean USE_TEXTURE_STREAMING = false;

    public static TextureFactory getInstance() {
        if(instance == null) {
            throw new IllegalStateException("Call AppContext.init() before using it");
        }
        return instance;
    }

    public static void init() {
        instance = new TextureFactory();
        instance.loadDefaultTexture();
    }

    /** The table of textures that have been loaded in this loader */
    public Map<String, Texture> TEXTURES = new ConcurrentHashMap<String, Texture>();

    /** The colour model including alpha for the GL image */
    private ColorModel glAlphaColorModel;
    
    /** The colour model for the GL image */
    private ColorModel glColorModel;
    
    /** 
     * Create a new texture loader based on the game panel
     *
     */
    Texture defaultTexture = null;

    public TextureFactory() {
        DeferredRenderer.exitOnGLError("Begin TextureFactory constructor");
        glAlphaColorModel = new ComponentColorModel(ColorSpace.getInstance(ColorSpace.CS_sRGB),
                                            new int[] {8,8,8,8},
                                            true,
                                            false,
                                            ComponentColorModel.TRANSLUCENT,
                                            DataBuffer.TYPE_BYTE);
                                            
        glColorModel = new ComponentColorModel(ColorSpace.getInstance(ColorSpace.CS_sRGB),
                                            new int[] {8,8,8,0},
                                            false,
                                            false,
                                            ComponentColorModel.OPAQUE,
                                            DataBuffer.TYPE_BYTE);

//    	loadAllAvailableTextures();

        DeferredRenderer.exitOnGLError("After TextureFactory constructor");

        if(USE_TEXTURE_STREAMING) {
            new TimeStepThread("TextureWatcher", 0.5f) {
                @Override
                public void update(float seconds) {
                    Iterator<Texture> iterator = TEXTURES.values().iterator();
                    while(iterator.hasNext()) {
                        Texture texture = iterator.next();
                        long notUsedSinceMs = System.currentTimeMillis() - texture.getLastUsedTimeStamp();
//                    System.out.println("Not used since " + notUsedSinceMs + ": " + texture.getPath());
                        if(notUsedSinceMs > TEXTURE_UNLOAD_THRESHOLD_IN_MS && notUsedSinceMs < 20000) { // && texture.getTarget().equals(TEXTURE_2D)) {
                            texture.unload();
                        }
                    }
                    try {
                        sleep(500);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }.start();
        }
    }

    public BufferedImage getDefaultTextureAsBufferedImage() {
        return defaultTextureAsBufferedImage;
    }

    public void loadDefaultTexture() {
        String defaultTexturePath = "hp\\assets\\models\\textures\\gi_flag.png";
        defaultTexture = getTexture(defaultTexturePath, true);
        try {
            defaultTextureAsBufferedImage = loadImage(defaultTexturePath);
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(-1);
        }
    }

    private void loadAllAvailableTextures() {
    	File textureDir = new File(Texture.getDirectory());
    	List<File> files = (List<File>) FileUtils.listFiles(textureDir, TrueFileFilter.INSTANCE, TrueFileFilter.INSTANCE);
		DeferredRenderer.exitOnGLError("Before loadAllAvailableTextures");
		for (File file : files) {
			try {
				if(FilenameUtils.isExtension(file.getAbsolutePath(), "hptexture")) {
					getTexture(file.getAbsolutePath());
				} else {
					getCubeMap(file.getAbsolutePath());
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
    }

    public boolean removeTexture(String path) {
        if(TEXTURES.containsKey(path)) {
            TEXTURES.remove(path);
            return !TEXTURES.containsKey(path);
        }
        return true;
    }

    public Texture getDefaultTexture() {
        return defaultTexture;
    }

    /**
     * Create a new texture ID 
     *
     * @return A new texture ID
     */
    private int createTextureID()
    {
        return OpenGLContext.getInstance().calculate(() -> {
                    return getTextureId();
                }
        );
    }
    
    /**
     * Load a texture
     *
     * @param resourceName The location of the resource to load
     * @return The loaded texture
     * @throws IOException Indicates a failure to access the resource
     */
    public Texture getTexture(String resourceName) {
    	return getTexture(resourceName, false);
    }
    public Texture getTexture(String resourceName, boolean srgba) {
        if(textureLoaded(resourceName)) {
            return TEXTURES.get(resourceName);
        }
        if (Texture.COMPILED_TEXTURES && texturePreCompiled(resourceName)) {
            Texture texture = new Texture(resourceName, srgba);
            TEXTURES.put(resourceName, texture);
            AppContext.getEventBus().post(new TexturesChangedEvent());
            texture.readAndUpload();
            return texture;
        }

        Texture texture = new Texture(resourceName, srgba);
        TEXTURES.put(resourceName, texture);
        AppContext.getEventBus().post(new TexturesChangedEvent());
        texture.convertAndUpload();
        return texture;
    }


    public static int getTextureId() {
        return OpenGLContext.getInstance().calculate(() -> GL11.glGenTextures());
    }

    private boolean textureLoaded(String resourceName) {
        return TEXTURES.containsKey(resourceName);
    }

    public boolean texturePreCompiled(String resourceName) {
    	String fileName = FilenameUtils.getBaseName(resourceName);
    	File f = new File(Texture.getDirectory() + fileName + ".hptexture");
    	return f.exists();
	}
    

	private boolean cubeMapPreCompiled(String resourceName) {
    	String fileName = FilenameUtils.getBaseName(resourceName);
    	File f = new File(Texture.getDirectory() + fileName + ".hpcubemap");
    	return f.exists();
	}

	public CubeMap getCubeMap(String resourceName) throws IOException {
		CubeMap tex = (CubeMap) TEXTURES.get(resourceName+ "_cube");
        
        if (tex != null && tex instanceof CubeMap) {
            return tex;
        }

        if (cubeMapPreCompiled(resourceName)) {
        	tex = CubeMap.read(resourceName, createTextureID());
        	if (tex != null) {
                generateMipMapsCubeMap(tex.getTextureID());
                TEXTURES.put(resourceName+ "_cube",tex);
                return tex;
            }
        }
        
        tex = getCubeMap(resourceName,
                         GL11.GL_RGBA,     // dst pixel format
                         GL11.GL_LINEAR_MIPMAP_LINEAR, // min filter (unused)
                         GL11.GL_LINEAR, false);

        TEXTURES.put(resourceName + "_cube",tex);
        System.out.println("Precompiled " + CubeMap.write(tex, resourceName));
        return tex;
    }

    private CubeMap getCubeMap(String resourceName, 
					            int dstPixelFormat,
					            int minFilter, 
					            int magFilter, boolean asStream) throws IOException {

        GlTextureTarget target = TEXTURE_CUBE_MAP;

    	 int srcPixelFormat = 0;
         
         // create the texture ID for this texture 
         int textureID = createTextureID(); 
         CubeMap cubeMap = new CubeMap(resourceName, target, textureID); 
         
         // bind this texture
        OpenGLContext.getInstance().bindTexture(target, textureID);

         BufferedImage bufferedImage = null;
         if (asStream) {
             bufferedImage = loadImageAsStream(resourceName);
         } else {
             bufferedImage = loadImage(resourceName);
         } 
         cubeMap.setWidth(bufferedImage.getWidth());
         cubeMap.setHeight(bufferedImage.getHeight());
         cubeMap.setMinFilter(minFilter);
         cubeMap.setMagFilter(magFilter);
         
         if (bufferedImage.getColorModel().hasAlpha()) {
             srcPixelFormat = GL11.GL_RGBA;
         } else {
             srcPixelFormat = GL11.GL_RGB;
         }

         cubeMap.setDstPixelFormat(dstPixelFormat);
         cubeMap.setSrcPixelFormat(srcPixelFormat);
         
         // convert that image into a byte buffer of texture data 
         ByteBuffer[] textureBuffers = convertCubeMapData(bufferedImage,cubeMap);
         
         cubeMap.upload();
         
         return cubeMap; 
	}
    
    /**
     * Convert the buffered image to a texture
     *
     * @param bufferedImage The image to convert to a texture
     * @return A buffer containing the data
     */
    public byte[] convertImageData(BufferedImage bufferedImage) {
        WritableRaster raster;
        BufferedImage texImage;

        int width = bufferedImage.getWidth();
        int height = bufferedImage.getHeight();

        if (bufferedImage.getColorModel().hasAlpha()) {
            raster = Raster.createInterleavedRaster(DataBuffer.TYPE_BYTE,width,height,4,null);
            texImage = new BufferedImage(glAlphaColorModel,raster,false,new Hashtable());
        } else {
            raster = Raster.createInterleavedRaster(DataBuffer.TYPE_BYTE,width,height,3,null);
            texImage = new BufferedImage(glColorModel,raster,false,new Hashtable());
        }
            
        // copy the source image into the produced image
        Graphics g = texImage.getGraphics();
        g.setColor(new Color(0f,0f,0f,0f));
        g.fillRect(0,0,width,height);
        g.drawImage(bufferedImage, 0, 0, null);
        
        byte[] data = ((DataBufferByte) texImage.getRaster().getDataBuffer()).getData();
        return data;
    }
    
    private ByteBuffer[] convertCubeMapData(BufferedImage bufferedImage,CubeMap cubeMap) { 
        ByteBuffer imageBuffers[] = new ByteBuffer[6];
        List<byte[]> byteArrays = new ArrayList<>();
        
        WritableRaster raster;
        BufferedImage texImage;


        int width = cubeMap.getWidth();
        int height = cubeMap.getHeight();

        int tileWidth = (width /4);
        int tileHeight = (height /3);
    	
        for(int i = 0; i < 6; i++) {

        	Vector2f[] topLeftBottomRight = getRectForFaceIndex(i, bufferedImage.getWidth(), bufferedImage.getHeight());
            
            if (bufferedImage.getColorModel().hasAlpha()) {
                raster = Raster.createInterleavedRaster(DataBuffer.TYPE_BYTE,tileWidth,tileHeight,4,null);
                texImage = new BufferedImage(glAlphaColorModel,raster,false,new Hashtable());
            } else {
                raster = Raster.createInterleavedRaster(DataBuffer.TYPE_BYTE,tileWidth,tileHeight,3,null);
                texImage = new BufferedImage(glColorModel,raster,false,new Hashtable());
            }
            
            Graphics g = texImage.getGraphics();
            g.setColor(new Color(0f,0f,0f,0f));
            g.fillRect(0,0,tileWidth,tileHeight);
            
            g.drawImage(bufferedImage,0,0, tileWidth, tileHeight, (int)topLeftBottomRight[0].x,(int)topLeftBottomRight[0].y,
					  (int)topLeftBottomRight[1].x,(int)topLeftBottomRight[1].y, null);

//            try {
//                File outputfile = new File(i + ".png");
//                ImageIO.write(texImage, "png", outputfile);
//            } catch (IOException e) {
//            	System.out.println("xoxoxoxo");
//            }
            
            
            byte[] data = ((DataBufferByte) texImage.getRaster().getDataBuffer()).getData(); 
            byteArrays.add(data);
            
    		ByteBuffer tempBuffer = ByteBuffer.allocateDirect(data.length);
    		tempBuffer.order(ByteOrder.nativeOrder());
    		tempBuffer.put(data, 0, data.length);
    		tempBuffer.flip();
            
            imageBuffers[i] = tempBuffer;
            
		}
//        System.exit(0);
        cubeMap.setData(byteArrays);
        return imageBuffers;
    }
    
    private Vector2f[] getRectForFaceIndex(int index, int imageWidth, int imageHeight) {
    	Vector2f[] result = new Vector2f[2];
    	
    	switch (index) {
		case 0: // GL_TEXTURE_CUBE_MAP_POSITIVE_X
			result[0] = new Vector2f(imageWidth/2, imageHeight/3);
			result[1] = new Vector2f(3*imageWidth/4, 2*imageHeight/3);
			break;

		case 1: // GL_TEXTURE_CUBE_MAP_NEGATIVE_X
			result[0] = new Vector2f(0, imageHeight/3);
			result[1] = new Vector2f(imageWidth/4, 2*imageHeight/3);
			break;

		case 2: // GL_TEXTURE_CUBE_MAP_POSITIVE_Y
			result[1] = new Vector2f(imageWidth/4, 0);
			result[0] = new Vector2f(imageWidth/2, imageHeight/3);
			break;

		case 3: // GL_TEXTURE_CUBE_MAP_NEGATIVE_Y
			result[0] = new Vector2f(imageWidth/4, 2*imageHeight/3);
			result[1] = new Vector2f(imageWidth/2, imageHeight);
			break;

		case 4: // GL_TEXTURE_CUBE_MAP_POSITIVE_Z
			result[0] = new Vector2f(3*imageWidth/4, imageHeight/3);
			result[1] = new Vector2f(imageWidth, 2*imageHeight/3);
			break;

		case 5: // GL_TEXTURE_CUBE_MAP_NEGATIVE_Z
			result[0] = new Vector2f(imageWidth/4, imageHeight/3);
			result[1] = new Vector2f(imageWidth/2, 2*imageHeight/3);
			break;

		default:
			break;
		}
    	
    	return result;
	}

	/** 
     * Load a given resource as a buffered image
     * 
     * @param ref The location of the resource to load
     * @return The loaded buffered image
     * @throws IOException Indicates a failure to find a resource
     */
    public BufferedImage loadImage(String ref) throws IOException
    {
        URL url = TextureFactory.class.getClassLoader().getResource(ref);
        
        if (url == null) {
            return loadImageAsStream(ref);
        }

        File file = new File(ref);
        BufferedImage bufferedImage = ImageIO.read(file);//new BufferedInputStream(getClass().getClassLoader().getResourceAsStream(ref)));
        return bufferedImage;
    }

    public BufferedImage loadImageAsStream(String ref) throws IOException
    { 
        BufferedImage bufferedImage = null;
        File file = new File(ref);
		try {
            bufferedImage = ImageIO.read(file);
		} catch (Exception e) {
			System.err.println("Unable to read file " + ref);
		}
        return bufferedImage;
    }
    
    /**
     * Creates an integer buffer to hold specified ints
     * - strictly a utility method
     *
     * @param size how many int to contain
     * @return created IntBuffer
     */
    protected IntBuffer createIntBuffer(int size) {
      ByteBuffer temp = ByteBuffer.allocateDirect(4 * size);
      temp.order(ByteOrder.nativeOrder());

      return temp.asIntBuffer();
    }

    private void generateMipMaps(Texture texture, boolean mipmap) {
        OpenGLContext.getInstance().execute(() -> {
            texture.bind(15);
            if (mipmap) {
//                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
//                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
                GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
            }

            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL14.GL_MIRRORED_REPEAT);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL14.GL_MIRRORED_REPEAT);
            texture.unbind(15);
        });
    }
    public void generateMipMaps(int textureId) {
        generateMipMaps(textureId, GL11.GL_LINEAR_MIPMAP_LINEAR);
    }
    public void generateMipMaps(int textureId, int textureMinFilter) {
        generateMipMaps(textureId, textureMinFilter, GL11.GL_LINEAR);
    }
    public void generateMipMaps(int textureId, int textureMinFilter, int textureMagFilter) {
        OpenGLContext.getInstance().bindTexture(TEXTURE_2D, textureId);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, textureMagFilter);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, textureMinFilter);
		GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
    }
    public void enableMipMaps(int textureId, int textureMinFilter, int textureMagFilter) {
        OpenGLContext.getInstance().bindTexture(TEXTURE_2D, textureId);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, textureMagFilter);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, textureMinFilter);
    }
    
    public void generateMipMapsCubeMap(int textureId) {
        OpenGLContext.getInstance().execute(() -> {
            OpenGLContext.getInstance().bindTexture(TEXTURE_CUBE_MAP, textureId);
            GL11.glTexParameteri(GL13.GL_TEXTURE_CUBE_MAP, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
            GL11.glTexParameteri(GL13.GL_TEXTURE_CUBE_MAP, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL30.glGenerateMipmap(GL13.GL_TEXTURE_CUBE_MAP);
        });
    }
    
    public static ByteBuffer getTextureData(int textureId, int mipLevel, int format, ByteBuffer pixels) {
        OpenGLContext.getInstance().bindTexture(TEXTURE_2D, textureId);
		GL11.glGetTexImage(GL11.GL_TEXTURE_2D, mipLevel, format, GL11.GL_UNSIGNED_BYTE, pixels);
		return pixels;
    }
    
    public static int copyCubeMap(int sourceTextureId, int width, int height, int internalFormat) {
    	int copyTextureId = getTextureId();
        OpenGLContext.getInstance().bindTexture(15, TEXTURE_CUBE_MAP, copyTextureId);

        GL11.glTexParameteri(GL13.GL_TEXTURE_CUBE_MAP, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
        GL11.glTexParameteri(GL13.GL_TEXTURE_CUBE_MAP, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL13.GL_TEXTURE_CUBE_MAP, GL14.GL_GENERATE_MIPMAP, GL11.GL_TRUE);
        GL11.glTexParameteri(GL13.GL_TEXTURE_CUBE_MAP, GL12.GL_TEXTURE_WRAP_R, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL13.GL_TEXTURE_CUBE_MAP, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL13.GL_TEXTURE_CUBE_MAP, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);

//		for(int i = 0; i < 6; i++) {
//			GL11.glTexImage2D(GL13.GL_TEXTURE_CUBE_MAP_POSITIVE_X + i, 0, internalFormat, width, height, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (FloatBuffer) null);
//		}
        GL42.glTexStorage2D(TEXTURE_CUBE_MAP.glTarget, 1, internalFormat, width, height);
		
		GL43.glCopyImageSubData(sourceTextureId, GL13.GL_TEXTURE_CUBE_MAP, 0, 0, 0, 0,
				copyTextureId, GL13.GL_TEXTURE_CUBE_MAP, 0, 0, 0, 0,
				width, height, 6);

        OpenGLContext.getInstance().bindTexture(15, TEXTURE_CUBE_MAP, 0);
		return copyTextureId;
    }
    
    public static void deleteTexture(int id) {
		GL11.glDeleteTextures(id);
    }

    //TODO: Add texture filters as params
    public int getCubeMap(int width, int height, int format) {
        return getTexture(width, height, format, TEXTURE_CUBE_MAP);
    }

    public int getCubeMapArray(int width, int height, int format) {
        return getTexture(width, height, format, TEXTURE_CUBE_MAP_ARRAY, 1);
    }
    public int getCubeMapArray(int width, int height, int format, int depth) {
        return getTexture(width, height, format, TEXTURE_CUBE_MAP_ARRAY, depth);
    }

    public int getTexture(int width, int height, int format, GlTextureTarget target) {
        return getTexture(width, height, format, target, 1);
    }

    public int getTexture(int width, int height, int format, GlTextureTarget target, int depth) {
        int textureId = createTextureID();
        OpenGLContext.getInstance().bindTexture(target, textureId);

        setupTextureParameters(target);

        if(target == TEXTURE_CUBE_MAP_ARRAY) {
            GL42.glTexStorage3D(target.glTarget, 1, format, width, height, 6*depth);
        } else {
            GL42.glTexStorage2D(target.glTarget, 1, format, width, height);
        }

        return textureId;
    }

    private void setupTextureParameters(GlTextureTarget target) {
        GL11.glTexParameteri(target.glTarget, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
        GL11.glTexParameteri(target.glTarget, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(target.glTarget, GL12.GL_TEXTURE_WRAP_R, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(target.glTarget, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(target.glTarget, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(target.glTarget, GL12.GL_TEXTURE_BASE_LEVEL, 0);
        GL11.glTexParameteri(target.glTarget, GL12.GL_TEXTURE_MAX_LEVEL, 0);
        GL30.glGenerateMipmap(target.glTarget);
    }

    // TODO return proper object
    public int getTexture3D(int gridSize, int gridTextureFormatSized, int filterMin, int filterMag, int wrapMode) {
        final int[] grid = new int[1];
        OpenGLContext.getInstance().execute(()-> {
            grid[0] = GL11.glGenTextures();
            GL11.glBindTexture(GL12.GL_TEXTURE_3D, grid[0]);
            GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_MIN_FILTER, filterMin);
            GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_MAG_FILTER,filterMag);
            GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_WRAP_S, wrapMode);
            GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_WRAP_T, wrapMode);
            GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL12.GL_TEXTURE_WRAP_R, wrapMode);
            GL42.glTexStorage3D(GL12.GL_TEXTURE_3D, util.Util.calculateMipMapCount(gridSize), gridTextureFormatSized, gridSize, gridSize, gridSize);
            GL11.glBindTexture(GL12.GL_TEXTURE_3D, grid[0]);
            GL30.glGenerateMipmap(GL12.GL_TEXTURE_3D);
        });
        return grid[0];
    }
}
