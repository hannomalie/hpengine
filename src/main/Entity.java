package main;

import static main.log.ConsoleLogger.getLogger;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import main.util.Util;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL20;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Quaternion;
import org.lwjgl.util.vector.Vector2f;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;

public class Entity implements IEntity {
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
	
	FloatBuffer matrix44Buffer;

	public Matrix4f modelMatrix = null;
	protected int modelMatrixLocation = 0;
	protected Vector3f position = null;
	protected Vector3f scale = new Vector3f(1,1,1);
	private Material material;

	protected VertexBuffer vertexBuffer;

	public boolean castsShadows = false;

	protected String name;

	private Quaternion orientation = new Quaternion();

	public Entity() {}
	
	public Entity(Renderer renderer, Model model) {
		this(renderer, model, new Vector3f(0, 0, 0),
				new Material(renderer, "", "stone_diffuse.png", "stone_normal.png",
				"stone_specular.png", "stone_occlusion.png",
				"stone_height.png"),
				false);
	}

	public Entity(Renderer renderer, Model model, Material material, boolean castsShadows) {
		this(renderer, model, new Vector3f(0, 0, 0), material, castsShadows);
	}

	public Entity(Renderer renderer, Model model, Vector3f position, Material material, boolean castsShadows) {
		modelMatrix = new Matrix4f();
		matrix44Buffer = BufferUtils.createFloatBuffer(16);
		
		this.castsShadows = castsShadows;

		this.position = position;
		//angle = new Vector3f(0, 0, 0);
		scale = new Vector3f(1, 1, 1);
		
		createVertexBuffer(model);
		
		this.material = material;
		this.name = model.getName();
	}
	
	public void createVertexBuffer(Model model) {

		List<Vector3f> verticesTemp = model.getVertices();
		List<Vector2f> texcoordsTemp = model.getTexCoords();
		List<Vector3f> normalsTemp = model.getNormals();
		List<Face> facesTemp = model.getFaces();
		
		LOGGER.log(Level.INFO, String.format("Faces: %d", facesTemp.size()));
		
		List<Float> values = new ArrayList<Float>();
		
		for (int i = 0; i < facesTemp.size(); i++) {
			Face face = facesTemp.get(i);

			int[] referencedVertices = face.getVertexIndices();
			int[] referencedNormals = face.getNormalIndices();
			int[] referencedTexcoords = face.getTextureCoordinateIndices();

//			// for normalmapping
//			Vector3f[] nm_vertives= new Vector3f[3];
//			Vector2f[] nm_tcoords= new Vector2f[3];
			
			
			for (int j = 0; j < 3; j++) {
				Vector3f referencedVertex = verticesTemp.get(referencedVertices[j]-1);
				Vector2f referencedTexcoord = texcoordsTemp.get(referencedTexcoords[j]-1);
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

//			
//			Vector3f D = null;
//			Vector3f E = null;
//			D = Vector3f.sub(nm_vertives[1], nm_vertives[0], D);
//			E = Vector3f.sub(nm_vertives[2], nm_vertives[0], E);
//			
//			Vector2f F = null;
//			Vector2f G = null;
//			F = Vector2f.sub(nm_tcoords[1], nm_tcoords[0], F);
//			G = Vector2f.sub(nm_tcoords[2], nm_tcoords[0], G);
//			
//			float coef = 1/(F.x * G.y - F.y * G.x);
//			
//			Vector3f tangent = new Vector3f();
//
//			tangent.x = (G.y * D.x - F.y * E.x) * coef;
//			tangent.y = (G.y * D.y - F.y * E.y) * coef;
//			tangent.z = (G.y * D.z - F.y * E.z) * coef;
//			tangent.normalise();
//			
//			vds[0].setTangent(new float[]{tangent.x, tangent.y, tangent.z});
//			vds[1].setTangent(new float[]{tangent.x, tangent.y, tangent.z});
//			vds[2].setTangent(new float[]{tangent.x, tangent.y, tangent.z});
//			
//			Vector3f biNormal = null;
//			biNormal = Vector3f.cross(tangent, new Vector3f(vds[0].getN()[0],vds[0].getN()[1],vds[0].getN()[1] ), biNormal);
//			vds[0].setBinormal(new float[]{biNormal.x, biNormal.y, biNormal.z});
//			vds[1].setBinormal(new float[]{biNormal.x, biNormal.y, biNormal.z});
//			vds[2].setBinormal(new float[]{biNormal.x, biNormal.y, biNormal.z});
			
		}

		LOGGER.log(Level.INFO, String.format("Values: %d", values.size()));
		
		FloatBuffer verticesFloatBuffer = BufferUtils.createFloatBuffer(values.size() * 4);
		float[] floatArray = new float[values.size()];

		for (int i = 0; i < values.size(); i++) {
		    floatArray[i] = values.get(i);
		}
		verticesFloatBuffer.rewind();
		verticesFloatBuffer.put(floatArray);
		verticesFloatBuffer.rewind();

		LOGGER.log(Level.INFO, String.format("Bytes: %d", verticesFloatBuffer.capacity()));

		vertexBuffer = new VertexBuffer( verticesFloatBuffer, DEFAULTCHANNELS).upload();
//		vertexBufferShadow = new VertexBuffer( verticesFloatBuffer, DEFAULTCHANNELS).upload();
		
	}

	@Override
	public void update() {
		modelMatrix = new Matrix4f();
		Matrix4f.scale(scale, modelMatrix, modelMatrix);
		Matrix4f.mul(Util.toMatrix(orientation), modelMatrix, modelMatrix);
		Matrix4f.translate(position, modelMatrix, modelMatrix);

		modelMatrix.store(matrix44Buffer);
		matrix44Buffer.flip();
	}

	@Override
	public void draw(Program program) {
		GL20.glUniformMatrix4(GL20.glGetUniformLocation(program.getId(),"modelMatrix"), false, matrix44Buffer);
		material.setTexturesActive(program);
		vertexBuffer.draw();

//		material.setTexturesInactive();
	}

	@Override
	public void drawDebug(Program program) {
		GL20.glUniformMatrix4(GL20.glGetUniformLocation(program.getId(),"modelMatrix"), false, matrix44Buffer);

		material.setTexturesActive(program);
		vertexBuffer.drawDebug();

//		material.setTexturesInactive();
	}

	@Override
	public Matrix4f getModelMatrix() {
		return modelMatrix;
	}

	public void setModelMatrix(Matrix4f modelMatrix) {
		this.modelMatrix = modelMatrix;
	}

	@Override
	public Vector3f getPosition() {
		return position;
	}

	public void setPosition(Vector3f position) {
		this.position = position;
	}
	
	public Vector3f getScale() {
		return scale;
	}

	@Override
	public void setScale(Vector3f scale) {
		this.scale = scale;
	}


	public int getModelMatrixLocation() {
		return modelMatrixLocation;
	}

	public void setModelMatrixLocation(int modelMatrixLocation) {
		this.modelMatrixLocation = modelMatrixLocation;
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
		material.destroy();
	}

	public VertexBuffer getVertexBuffer() {
		return vertexBuffer;
	}

	@Override
	public void move(Vector3f amount) {
		Vector3f.add(getPosition(), amount, getPosition());
	}
	@Override
	public String getName() {
		return name;
	}
	@Override
	public Material getMaterial() {
		return material;
	}
	@Override
	public Quaternion getOrientation() {
		return orientation;
	}
	@Override
	public void rotate(Vector3f axis, float degree) {
		rotate(new Vector4f(axis.x, axis.y, axis.z, degree));
	}
	@Override
	public void rotate(Vector4f axisAngle) {
		Quaternion rot = new Quaternion();
		rot.setFromAxisAngle(axisAngle);
		orientation = Quaternion.mul(orientation, rot, orientation);
	}
	@Override
	public void setOrientation(Quaternion orientation) {
		this.orientation = orientation;
	}

	@Override
	public void setScale(float scale) {
		setScale(new Vector3f(scale,scale,scale));
	}
	@Override
	public boolean isInFrustum(Camera camera) {
		Vector4f[] minMax = vertexBuffer.getMinMax();

		Vector4f minView = new Vector4f();
		Vector4f maxView = new Vector4f();
		Vector4f minMaxCenterView = new Vector4f();

		Matrix4f.transform(modelMatrix, minMax[0], minView);
		Matrix4f.transform(camera.getViewMatrix(), minView, minView);
		//trix4f.transform(camera.getProjectionMatrix(), minView, minView);

		Matrix4f.transform(modelMatrix, minMax[1], maxView);
		Matrix4f.transform(camera.getViewMatrix(), maxView, maxView);
		//Matrix4f.transform(camera.getProjectionMatrix(), maxView, maxView);

		Vector4f.add(minView, maxView, minMaxCenterView);
		minMaxCenterView.scale(0.5f);

		if (camera.getFrustum().pointInFrustum(minMaxCenterView.x, minMaxCenterView.y, minMaxCenterView.z)) {
		//if (camera.getFrustum().cubeInFrustum(cubeCenterX, cubeCenterY, cubeCenterZ, size)) {
		//if (camera.getFrustum().pointInFrustum(minView.x, minView.y, minView.z)
		//		|| camera.getFrustum().pointInFrustum(maxView.x, maxView.y, maxView.z)) {
			return true;
		}
		return false;
	}
}
