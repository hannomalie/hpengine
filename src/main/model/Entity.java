package main.model;

import static main.log.ConsoleLogger.getLogger;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Logger;

import javafx.beans.DefaultProperty;
import main.Transform;
import main.World;
import main.camera.Camera;
import main.component.IGameComponent;
import main.component.IGameComponent.ComponentIdentifier;
import main.renderer.Renderer;
import main.renderer.material.Material;
import main.renderer.material.MaterialFactory;
import main.shader.Program;
import main.texture.CubeMap;
import main.util.Util;

import org.apache.commons.io.FilenameUtils;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL13;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Vector2f;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;

public class Entity implements IEntity, Serializable {
	private static final long serialVersionUID = 1;
	public static int count = 0;
	private static Logger LOGGER = getLogger();

	public static EnumSet<DataChannels> DEFAULTCHANNELS = EnumSet.of(
		DataChannels.POSITION3,
		DataChannels.TEXCOORD,
		DataChannels.NORMAL
	);
	public static EnumSet<DataChannels> DEPTHCHANNELS = EnumSet.of(
		DataChannels.POSITION3,
		DataChannels.NORMAL
	);
	public static EnumSet<DataChannels> SHADOWCHANNELS = EnumSet.of(
			DataChannels.POSITION3);
	public static EnumSet<DataChannels> POSITIONCHANNEL = EnumSet.of(
			DataChannels.POSITION3);


	transient protected VertexBuffer vertexBuffer;
	private float[] floatArray;
	transient public Matrix4f modelMatrix = new Matrix4f();
	
	private Transform transform = new Transform();
	transient protected FloatBuffer matrix44Buffer = BufferUtils.createFloatBuffer(16);
	
	protected transient MaterialFactory materialFactory;
	protected String materialName = "";

	protected String name = "Entity_" + System.currentTimeMillis();

	private boolean selected = false;
	private boolean visible = true;
	
	transient public HashMap<ComponentIdentifier, IGameComponent> components = new HashMap<ComponentIdentifier, IGameComponent>();

	protected Entity() {
	}

	protected Entity(MaterialFactory materialFactory, Model model, String materialName) {
		this(materialFactory, new Vector3f(0, 0, 0), model.getName(), model, materialName);
	}

	protected Entity(MaterialFactory materialFactory, Vector3f position, String name, Model model, String materialName) {
		modelMatrix = new Matrix4f();
		matrix44Buffer = BufferUtils.createFloatBuffer(16);
		
		transform.setPosition(position);
		createFloatArray(model);
		createVertexBuffer();
		
		this.materialFactory = materialFactory;
		this.materialName = materialName;
		this.name = name;
	}
	
	public void init(Renderer renderer) {
		matrix44Buffer = BufferUtils.createFloatBuffer(16);
		createVertexBuffer();
		components = new HashMap<ComponentIdentifier, IGameComponent>();
		this.materialFactory = renderer.getMaterialFactory();
	}
	
	public void createFloatArray(Model model) {

		List<Vector3f> verticesTemp = model.getVertices();
		List<Vector2f> texcoordsTemp = model.getTexCoords();
		List<Vector3f> normalsTemp = model.getNormals();
		List<Face> facesTemp = model.getFaces();
		
//		LOGGER.log(Level.INFO, String.format("Faces: %d", facesTemp.size()));
		
		List<Float> values = new ArrayList<Float>();
		
		for (int i = 0; i < facesTemp.size(); i++) {
			Face face = facesTemp.get(i);

			int[] referencedVertices = face.getVertexIndices();
			int[] referencedNormals = face.getNormalIndices();
			int[] referencedTexcoords = face.getTextureCoordinateIndices();

			for (int j = 0; j < 3; j++) {
				Vector3f referencedVertex = verticesTemp.get(referencedVertices[j]-1);
				Vector2f referencedTexcoord = new Vector2f(0,0);
				try {
					referencedTexcoord = texcoordsTemp.get(referencedTexcoords[j]-1);
				} catch (Exception e) {
					
				}
				Vector3f referencedNormal = normalsTemp.get(referencedNormals[j]-1);

				values.add(referencedVertex.x);
				values.add(referencedVertex.y);
				values.add(referencedVertex.z);
				values.add(referencedTexcoord.x);
				values.add(referencedTexcoord.y);
				values.add(referencedNormal.x);
				values.add(referencedNormal.y);
				values.add(referencedNormal.z);
			}
		}

//		LOGGER.log(Level.INFO, String.format("Values: %d", values.size()));
		
		floatArray = new float[values.size()];

		for (int i = 0; i < values.size(); i++) {
		    floatArray[i] = values.get(i);
		}
		
		verticesTemp = null;
		texcoordsTemp = null;
		normalsTemp = null;
		facesTemp = null;
		System.gc();
	}
	
	public void createVertexBuffer() {

		FloatBuffer verticesFloatBuffer = BufferUtils.createFloatBuffer(floatArray.length * 4);
		verticesFloatBuffer.rewind();
		verticesFloatBuffer.put(floatArray);
		verticesFloatBuffer.rewind();
//		LOGGER.log(Level.INFO, String.format("Bytes: %d", verticesFloatBuffer.capacity()));
		
		vertexBuffer = new VertexBuffer( verticesFloatBuffer, DEFAULTCHANNELS).upload();
//		vertexBufferShadow = new VertexBuffer( verticesFloatBuffer, DEFAULTCHANNELS).upload();
	}

	@Override
	public void update(float seconds) {
		for (IGameComponent c : components.values()) {
			c.update(seconds);
		}
		modelMatrix = calculateCurrentModelMatrix();
		modelMatrix.store(matrix44Buffer);
		matrix44Buffer.flip();
	}
	
	@Override
	public HashMap<ComponentIdentifier,IGameComponent> getComponents() {
		return components;
	};

	@Override
	public void draw(Renderer renderer, Camera camera) {
		if(!isVisible()) {
			return;
		}
		
		Program firstPassProgram = getMaterial().getFirstPassProgram();
		if (firstPassProgram == null) {
			return;
		}
		Program currentProgram = firstPassProgram;
//		if (!firstPassProgram.equals(renderer.getLastUsedProgram())) {
//			currentProgram = firstPassProgram;
//			renderer.setLastUsedProgram(currentProgram);
//			currentProgram.use();
//		}
//		currentProgram = renderer.getLastUsedProgram();
		currentProgram.use();
		currentProgram.setUniform("useParallax", World.useParallax);
		currentProgram.setUniform("useSteepParallax", World.useSteepParallax);
		currentProgram.setUniformAsMatrix4("viewMatrix", camera.getViewMatrixAsBuffer());
		currentProgram.setUniformAsMatrix4("projectionMatrix", camera.getProjectionMatrixAsBuffer());
		currentProgram.setUniform("eyePosition", camera.getPosition());
		currentProgram.setUniform("near", camera.getNear());
		currentProgram.setUniform("far", camera.getFar());
		currentProgram.setUniform("time", (int)System.currentTimeMillis());
		
		currentProgram.setUniformAsMatrix4("modelMatrix", matrix44Buffer);
		getMaterial().setTexturesActive(this, currentProgram);
		
//		GL13.glActiveTexture(GL13.GL_TEXTURE0 + 6);
//		renderer.getEnvironmentMap().bind();
		
		vertexBuffer.draw();

//		material.setTexturesInactive();
	}
	
	@Override
	public void draw(Renderer renderer, Camera camera, CubeMap environmentMap) {
		if(!isVisible()) {
			return;
		}
		
		Program firstPassProgram = getMaterial().getFirstPassProgram();
		if (firstPassProgram == null) {
			return;
		}
		Program currentProgram = firstPassProgram;
//		if (!firstPassProgram.equals(renderer.getLastUsedProgram())) {
//			currentProgram = firstPassProgram;
//			renderer.setLastUsedProgram(currentProgram);
//			currentProgram.use();
//		}
//		currentProgram = renderer.getLastUsedProgram();
		currentProgram.use();
		currentProgram.setUniform("useParallax", World.useParallax);
		currentProgram.setUniform("useSteepParallax", World.useSteepParallax);
		currentProgram.setUniformAsMatrix4("viewMatrix", camera.getViewMatrixAsBuffer());
		currentProgram.setUniformAsMatrix4("projectionMatrix", camera.getProjectionMatrixAsBuffer());
		currentProgram.setUniform("eyePosition", camera.getPosition());
		currentProgram.setUniform("near", camera.getNear());
		currentProgram.setUniform("far", camera.getFar());
		currentProgram.setUniform("time", (int)System.currentTimeMillis());
		
		currentProgram.setUniformAsMatrix4("modelMatrix", matrix44Buffer);

		getMaterial().setTexturesActive(this, currentProgram);
		
//		GL13.glActiveTexture(GL13.GL_TEXTURE0 + 6);
//		environmentMap.bind();
		
		vertexBuffer.draw();

//		material.setTexturesInactive();
	}

	@Override
	public void drawDebug(Program program) {
		program.setUniformAsMatrix4("modelMatrix", matrix44Buffer);

		getMaterial().setTexturesActive(this, program);
		vertexBuffer.drawDebug();

//		material.setTexturesInactive();
	}

	protected Matrix4f calculateCurrentModelMatrix() {
		modelMatrix = transform.getTransformation();
		
		return modelMatrix;
	}
	
	@Override
	public Matrix4f getModelMatrix() {
		return calculateCurrentModelMatrix();
	}

	@Override
	public void setModelMatrix(Matrix4f modelMatrix) {
		this.modelMatrix = modelMatrix;
	}

	@Override
	public void setTransform(Transform transform) {
		this.transform = transform;
	}
	@Override
	public Transform getTransform() {
		return transform;
	}

	@Override
	public VertexBuffer getVertexBuffer() {
		return vertexBuffer;
	}
	@Override
	public String getName() {
		return name;
	}
	@Override
	public void setName(String string) {
		this.name = string;
	}
	@Override
	public Material getMaterial() {
		Material material = materialFactory.get(materialName);
		if(material == null) {
			return materialFactory.getDefaultMaterial();
		}
		return material;
	}
	@Override
	public void setMaterial(String materialName) {
		this.materialName = materialName;
	};

	@Override
	public boolean isInFrustum(Camera camera) {
		Vector4f[] minMaxWorld = getMinMaxWorld();
		Vector4f minWorld = minMaxWorld[0];
		Vector4f maxWorld = minMaxWorld[1];
		
		Vector3f centerWorld = new Vector3f();
		centerWorld.x = (maxWorld.x + minWorld.x)/2;
		centerWorld.y = (maxWorld.y + minWorld.y)/2;
		centerWorld.z = (maxWorld.z + minWorld.z)/2;
		
		Vector3f distVector = new Vector3f();
		Vector3f.sub(new Vector3f(maxWorld.x, maxWorld.y, maxWorld.z),
						new Vector3f(minWorld.x, minWorld.y, minWorld.z), distVector);

//		if (camera.getFrustum().pointInFrustum(minWorld.x, minWorld.y, minWorld.z) ||
//			camera.getFrustum().pointInFrustum(maxWorld.x, maxWorld.y, maxWorld.z)) {
//		if (camera.getFrustum().cubeInFrustum(cubeCenterX, cubeCenterY, cubeCenterZ, size)) {
//		if (camera.getFrustum().pointInFrustum(minView.x, minView.y, minView.z)
//				|| camera.getFrustum().pointInFrustum(maxView.x, maxView.y, maxView.z)) {
		if (camera.getFrustum().sphereInFrustum(centerWorld.x, centerWorld.y, centerWorld.z, distVector.length()/2)) {
			return true;
		}
		return false;
	}

	@Override
	public boolean isVisible() {
		return visible;
	}
	@Override
	public void setVisible(boolean visible) {
		this.visible = visible;
	}
	
	@Override
	public Vector4f[] getMinMaxWorld() {
		Vector4f[] minMax = vertexBuffer.getMinMax();
		
		Vector4f minView = new Vector4f(0,0,0,1);
		Vector4f maxView = new Vector4f(0,0,0,1);
		
		Matrix4f modelMatrix = getModelMatrix();
		
		Matrix4f.transform(modelMatrix, minMax[0], minView);
		Matrix4f.transform(getModelMatrix(), minMax[1], maxView);

		minView.w = 0;
		maxView.w = 0;
		minMax = new Vector4f[] {minView, maxView};
		
		return minMax;
	}
	
	@Override
	public Vector3f getCenter() {
		Vector4f[] minMax = vertexBuffer.getMinMax();
		
		Vector4f center = Vector4f.sub(minMax[1], minMax[0], null);
		center.w = 1;
		
		Matrix4f modelMatrix = getModelMatrix();
		
		Matrix4f.transform(modelMatrix, center, center);

		return new Vector3f(center.x, center.y, center.z);
	}

	@Override
	public boolean isSelected() {
		return selected;
	}
	@Override
	public void setSelected(boolean selected) {
		this.selected = selected;
	}
	
	@Override
	public String toString() {
		return name;
	}
	
	public static boolean write(Entity entity, String resourceName) {
		String fileName = FilenameUtils.getBaseName(resourceName);
		FileOutputStream fos = null;
		ObjectOutputStream out = null;
		try {
			fos = new FileOutputStream(getDirectory() + fileName + ".hpentity");
			out = new ObjectOutputStream(fos);
			out.writeObject(entity);

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

	public static String getDirectory() {
		return World.WORKDIR_NAME + "/assets/entities/";
	}
	public boolean equals(Object other) {
		if (!(other instanceof Entity)) {
			return false;
		}
		
		Entity b = (Entity) other;
		
		return b.getName().equals(getName());
	}

	public void destroy() {

//		// Select the VAO
//		GL30.glBindVertexArray(vaoId);
//		
//		// Disable the VBO index from the VAO attributes list
//		GL20.glDisableVertexAttribArray(0);
//		GL20.glDisableVertexAttribArray(1);
//		
//		// Delete the vertex VBO
//		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
//		GL15.glDeleteBuffers(vboId);
//		
//		// Delete the index VBO
//		GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
//		GL15.glDeleteBuffers(vboiId);
//		
//		// Delete the VAO
//		GL30.glBindVertexArray(0);
//		GL30.glDeleteVertexArrays(vaoId);
//		
	}
}
