package de.hanno.hpengine.texture;

import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.TimeStepThread;
import de.hanno.hpengine.event.TexturesChangedEvent;
import de.hanno.hpengine.renderer.GraphicsContext;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.lwjgl.opengl.*;
import org.lwjgl.util.vector.Vector2f;
import de.hanno.hpengine.renderer.DeferredRenderer;
import de.hanno.hpengine.renderer.constants.GlTextureTarget;
import de.hanno.hpengine.shader.ComputeShaderProgram;
import de.hanno.hpengine.shader.ProgramFactory;
import de.hanno.hpengine.shader.define.Define;
import de.hanno.hpengine.util.commandqueue.CommandQueue;

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
import java.util.logging.Logger;

import static de.hanno.hpengine.renderer.constants.GlTextureTarget.*;

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
    private static final Logger LOGGER = Logger.getLogger(TextureFactory.class.getName());
    private static final int TEXTURE_FACTORY_THREAD_COUNT = 1;
    private static volatile TextureFactory instance = null;
    private static volatile BufferedImage defaultTextureAsBufferedImage = null;
    public static volatile long TEXTURE_UNLOAD_THRESHOLD_IN_MS = 10000;
    private static volatile boolean USE_TEXTURE_STREAMING = false;

    public CubeMap getCubeMap() {
        return cubeMap;
    }

    public CommandQueue getCommandQueue() {
        return commandQueue;
    }

    private CommandQueue commandQueue = new CommandQueue();

    public static Texture getLensFlareTexture() {
        return lensFlareTexture;
    }

    private static Texture lensFlareTexture;
    public static CubeMap cubeMap;
    private final ComputeShaderProgram blur2dProgramSeperableHorizontal;
    private final ComputeShaderProgram blur2dProgramSeperableVertical;

    public static TextureFactory getInstance() {
        if(instance == null) {
            throw new IllegalStateException("Call Engine.init() before using it");
        }
        return instance;
    }

    public static void init() {
        instance = new TextureFactory();
        instance.loadDefaultTexture();
        DeferredRenderer.exitOnGLError("After loadDefaultTexture");
        lensFlareTexture = instance.getTexture("hp/assets/textures/lens_flare_tex.jpg", true);
        DeferredRenderer.exitOnGLError("After load lensFlareTexture");
        try {
            cubeMap = instance.getCubeMap("hp/assets/textures/skybox.png");
            DeferredRenderer.exitOnGLError("After load cubemap");
            GraphicsContext.getInstance().activeTexture(0);
//            instance.generateMipMapsCubeMap(cubeMap.getTextureID());
        } catch (IOException e) {
            LOGGER.severe(e.getMessage());
        }
    }

    /** The table of textures that have been loaded in this loader */
    public Map<String, Texture> TEXTURES = new ConcurrentHashMap<>();

    private Set<String> loadingTextures = new HashSet<>();

    /** The colour model including alpha for the GL image */
    private ColorModel glAlphaColorModel;
    
    /** The colour model for the GL image */
    private ColorModel glColorModel;
    
    /** 
     * Create a new de.hanno.hpengine.texture loader based on the game panel
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

        ArrayList horizontalDefines = new ArrayList() {{
            add(Define.getDefine("HORIZONTAL", true));
        }};
        ArrayList verticalDefines = new ArrayList() {{
            add(Define.getDefine("VERTICAL", true));
        }};
        blur2dProgramSeperableHorizontal = ProgramFactory.getInstance().getComputeProgram("blur2D_seperable_vertical_or_horizontal_compute.glsl", horizontalDefines);
        blur2dProgramSeperableVertical = ProgramFactory.getInstance().getComputeProgram("blur2D_seperable_vertical_or_horizontal_compute.glsl", verticalDefines);

        DeferredRenderer.exitOnGLError("After TextureFactory constructor");

        if(USE_TEXTURE_STREAMING) {
            new TimeStepThread("TextureWatcher", 0.5f) {
                @Override
                public void update(float seconds) {
                    Iterator<Texture> iterator = TEXTURES.values().iterator();
                    while(iterator.hasNext()) {
                        Texture texture = iterator.next();
                        long notUsedSinceMs = System.currentTimeMillis() - texture.getLastUsedTimeStamp();
//                    System.out.println("Not used since " + notUsedSinceMs + ": " + de.hanno.hpengine.texture.getPath());
                        if(notUsedSinceMs > TEXTURE_UNLOAD_THRESHOLD_IN_MS && notUsedSinceMs < 20000) { // && de.hanno.hpengine.texture.getTarget().equals(TEXTURE_2D)) {
                            texture.unload();
                        }
                    }
                }
            }.start();
        }

        for(int i = 0; i < 1+TEXTURE_FACTORY_THREAD_COUNT; i++) {
            new TimeStepThread("TextureFactory" + i, 0.01f) {
                @Override
                public void update(float seconds) {
                    commandQueue.executeCommands();
                }
            }.start();
        }
    }

    public BufferedImage getDefaultTextureAsBufferedImage() {
        return defaultTextureAsBufferedImage;
    }

    public void loadDefaultTexture() {
        String defaultTexturePath = "hp/assets/models/textures/gi_flag.png";
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
     * Create a new de.hanno.hpengine.texture ID
     *
     * @return A new de.hanno.hpengine.texture ID
     */
    private int createTextureID()
    {
        return GraphicsContext.getInstance().genTextures();
    }
    
    /**
     * Load a de.hanno.hpengine.texture
     *
     * @param resourceName The location of the resource to load
     * @return The loaded de.hanno.hpengine.texture
     * @throws IOException Indicates a failure to access the resource
     */
    public Texture getTexture(String resourceName) {
    	return getTexture(resourceName, false);
    }
    public Texture getTexture(String resourceName, boolean srgba) {
        if(textureLoaded(resourceName)) {
            return TEXTURES.get(resourceName);
        }
        if(!loadingTextures.contains(resourceName)) {
            loadingTextures.add(resourceName);
        } else {
            while (!textureLoaded(resourceName)) {
                LOGGER.info("Waiting for de.hanno.hpengine.texture " + resourceName + " to become available...");
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            return TEXTURES.get(resourceName);
        }
        LOGGER.info(resourceName + " requested");
        if (Texture.COMPILED_TEXTURES && texturePreCompiled(resourceName)) {
            Texture texture = new Texture(resourceName, srgba);
            TEXTURES.put(resourceName, texture);
            Engine.getEventBus().post(new TexturesChangedEvent());
            texture.readAndUpload();
            return texture;
        }

        Texture texture = new Texture(resourceName, srgba);
        TEXTURES.put(resourceName, texture);
        LOGGER.severe("Adding " + resourceName);
        texture.convertAndUpload();
        return texture;
    }


    public static int getTextureId() {
        return GraphicsContext.getInstance().genTextures();
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

        if (Texture.COMPILED_TEXTURES && cubeMapPreCompiled(resourceName)) {
        	tex = CubeMap.read(resourceName, createTextureID());
        	if (tex != null) {
                TEXTURES.put(resourceName+ "_cube",tex);
                return tex;
            }
        }
        
        tex = getCubeMap(resourceName,
                         GL11.GL_RGBA,     // dst pixel format
                         GL11.GL_LINEAR_MIPMAP_LINEAR, // min filter (unused)
                         GL11.GL_LINEAR, false);

        TEXTURES.put(resourceName + "_cube",tex);
        if(Texture.COMPILED_TEXTURES) {
            LOGGER.info("Precompiled " + CubeMap.write(tex, resourceName));
        }
        return tex;
    }

    private CubeMap getCubeMap(String resourceName, 
					            int dstPixelFormat,
					            int minFilter, 
					            int magFilter, boolean asStream) throws IOException {

        GlTextureTarget target = TEXTURE_CUBE_MAP;

    	 int srcPixelFormat = 0;
         
         // create the de.hanno.hpengine.texture ID for this de.hanno.hpengine.texture
         int textureID = createTextureID(); 
         CubeMap cubeMap = new CubeMap(resourceName, target);
         
         // bind this de.hanno.hpengine.texture
        GraphicsContext.getInstance().bindTexture(target, textureID);

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
         
         // convert that image into a byte buffer of de.hanno.hpengine.texture data
         ByteBuffer[] textureBuffers = convertCubeMapData(bufferedImage,cubeMap);
         
         cubeMap.upload();
         
         return cubeMap; 
	}
    
    /**
     * Convert the buffered image to a de.hanno.hpengine.texture
     *
     * @param bufferedImage The image to convert to a de.hanno.hpengine.texture
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
//            	LOGGER.info("xoxoxoxo");
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
        GraphicsContext.getInstance().execute(() -> {
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
        GraphicsContext.getInstance().bindTexture(TEXTURE_2D, textureId);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, textureMagFilter);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, textureMinFilter);
		GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
    }
    public void enableMipMaps(int textureId, int textureMinFilter, int textureMagFilter) {
        GraphicsContext.getInstance().bindTexture(TEXTURE_2D, textureId);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, textureMagFilter);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, textureMinFilter);
    }
    
    public void generateMipMapsCubeMap(int textureId) {
        GraphicsContext.getInstance().execute(() -> {
            GraphicsContext.getInstance().bindTexture(TEXTURE_CUBE_MAP, textureId);
            GL30.glGenerateMipmap(GL13.GL_TEXTURE_CUBE_MAP);
        });
    }
    
    public static ByteBuffer getTextureData(int textureId, int mipLevel, int format, ByteBuffer pixels) {
        GraphicsContext.getInstance().bindTexture(TEXTURE_2D, textureId);
		GL11.glGetTexImage(GL11.GL_TEXTURE_2D, mipLevel, format, GL11.GL_UNSIGNED_BYTE, pixels);
		return pixels;
    }
    
    public static int copyCubeMap(int sourceTextureId, int width, int height, int internalFormat) {
    	int copyTextureId = getTextureId();
        GraphicsContext.getInstance().bindTexture(15, TEXTURE_CUBE_MAP, copyTextureId);

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

        GraphicsContext.getInstance().bindTexture(15, TEXTURE_CUBE_MAP, 0);
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
        GraphicsContext.getInstance().bindTexture(target, textureId);

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
        GL11.glTexParameteri(target.glTarget, GL12.GL_TEXTURE_WRAP_R, GL11.GL_REPEAT);
        GL11.glTexParameteri(target.glTarget, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
        GL11.glTexParameteri(target.glTarget, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
        GL11.glTexParameteri(target.glTarget, GL12.GL_TEXTURE_BASE_LEVEL, 0);
        GL11.glTexParameteri(target.glTarget, GL12.GL_TEXTURE_MAX_LEVEL, 0);
        GL30.glGenerateMipmap(target.glTarget);
    }

    // TODO return proper object
    public int getTexture3D(int gridSize, int gridTextureFormatSized, int filterMin, int filterMag, int wrapMode) {
        final int[] grid = new int[1];
        GraphicsContext.getInstance().execute(()-> {
            grid[0] = GL11.glGenTextures();
            GL11.glBindTexture(GL12.GL_TEXTURE_3D, grid[0]);
            GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_MIN_FILTER, filterMin);
            GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_MAG_FILTER,filterMag);
            GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_WRAP_S, wrapMode);
            GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_WRAP_T, wrapMode);
            GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL12.GL_TEXTURE_WRAP_R, wrapMode);
            GL42.glTexStorage3D(GL12.GL_TEXTURE_3D, de.hanno.hpengine.util.Util.calculateMipMapCount(gridSize), gridTextureFormatSized, gridSize, gridSize, gridSize);
            GL11.glBindTexture(GL12.GL_TEXTURE_3D, grid[0]);
            GL30.glGenerateMipmap(GL12.GL_TEXTURE_3D);
        });
        return grid[0];
    }

    public void blur2DTextureRGBA16F(int sourceTexture, int width, int height, int mipmapTarget, int mipmapSource) {
        for(int i = 0; i < mipmapSource; i++ ) {
            width /= 2;
            height /=2;
        }
        int finalWidth = width;
        int finalHeight = height;
        GraphicsContext.getInstance().execute(() -> {
            blur2dProgramSeperableHorizontal.use();
            GraphicsContext.getInstance().bindTexture(0, TEXTURE_2D, sourceTexture);
            GraphicsContext.getInstance().bindImageTexture(1,sourceTexture, mipmapTarget, false, mipmapTarget, GL15.GL_WRITE_ONLY, GL30.GL_RGBA16F);
            blur2dProgramSeperableHorizontal.setUniform("width", finalWidth);
            blur2dProgramSeperableHorizontal.setUniform("height", finalHeight);
            blur2dProgramSeperableHorizontal.setUniform("mipmapSource", mipmapSource);
            blur2dProgramSeperableHorizontal.setUniform("mipmapTarget", mipmapTarget);
            blur2dProgramSeperableHorizontal.dispatchCompute(finalWidth /8, finalHeight /8, 1);

            blur2dProgramSeperableVertical.use();
//            OpenGLContext.getInstance().bindTexture(0, TEXTURE_2D, sourceTexture);
//            OpenGLContext.getInstance().bindImageTexture(1,sourceTexture, mipmapTarget, false, mipmapTarget, GL15.GL_WRITE_ONLY, GL30.GL_RGBA16F);
            blur2dProgramSeperableVertical.setUniform("width", finalWidth);
            blur2dProgramSeperableVertical.setUniform("height", finalHeight);
            blur2dProgramSeperableVertical.setUniform("mipmapSource", mipmapSource);
            blur2dProgramSeperableVertical.setUniform("mipmapTarget", mipmapTarget);
            blur2dProgramSeperableVertical.dispatchCompute(finalWidth /8, finalHeight /8, 1);
        });
    }

    public void blurHorinzontal2DTextureRGBA16F(int sourceTexture, int width, int height, int mipmapTarget, int mipmapSource) {
        for(int i = 0; i < mipmapSource; i++ ) {
            width /= 2;
            height /=2;
        }
        int finalWidth = width;
        int finalHeight = height;
        GraphicsContext.getInstance().execute(() -> {
            blur2dProgramSeperableHorizontal.use();
            GraphicsContext.getInstance().bindTexture(0, TEXTURE_2D, sourceTexture);
            GraphicsContext.getInstance().bindImageTexture(1,sourceTexture, mipmapTarget, false, mipmapTarget, GL15.GL_WRITE_ONLY, GL30.GL_RGBA16F);
            blur2dProgramSeperableHorizontal.setUniform("width", finalWidth);
            blur2dProgramSeperableHorizontal.setUniform("height", finalHeight);
            blur2dProgramSeperableHorizontal.setUniform("mipmapSource", mipmapSource);
            blur2dProgramSeperableHorizontal.setUniform("mipmapTarget", mipmapTarget);
            int num_groups_x = Math.max(1, finalWidth / 8);
            int num_groups_y = Math.max(1, finalHeight / 8);
            blur2dProgramSeperableHorizontal.dispatchCompute(num_groups_x, num_groups_y, 1);
        });
    }
}
