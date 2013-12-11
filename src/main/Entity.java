package main;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

import main.Model.Face;
import main.util.Util;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Vector2f;
import org.lwjgl.util.vector.Vector3f;

public class Entity {
	protected int vaoId = 0;
	protected int vboId = 0;
	protected int vboiId = 0;
	
	FloatBuffer matrix44Buffer;

	protected VertexData[] vertices = null;
	protected ByteBuffer verticesByteBuffer = null;
	protected int indicesCount = 0;
	ByteBuffer indicesBuffer = null;
	
	protected Matrix4f modelMatrix = null;
	protected int modelMatrixLocation = 0;
	protected Vector3f position = null;
	protected Vector3f angle = null;
	protected Vector3f scale = null;
	
//	public Entity(Vector3f position) {
//		modelMatrix = new Matrix4f();
//		matrix44Buffer = BufferUtils.createFloatBuffer(16);
//
//		this.position = position;
//		angle = new Vector3f(0, 0, 0);
//		scale = new Vector3f(1, 1, 1);
//	}
	
	public Entity(Model model) {
		this(model, new Vector3f(0, 0, 0));
	}
	
	public Entity(Model model, Vector3f position) {
		modelMatrix = new Matrix4f();
		matrix44Buffer = BufferUtils.createFloatBuffer(16);

		this.position = position;
		angle = new Vector3f(0, 0, 0);
		scale = new Vector3f(1, 1, 1);
		
		
		List<Vector3f> verticesTemp = model.getVertices();
		List<Vector3f> normalsTemp = model.getNormals();
		List<Vector2f> texcoordsTemp = model.getTextureCoordinates();
		List<Face> facesTemp = model.getFaces();
		
		List<VertexData> verticesConverted = new ArrayList<>();
		
		for (int i = 0; i < facesTemp.size(); i++) {
			Face face = facesTemp.get(i);

			int[] referencedVertices = face.getVertexIndices();
			int[] referencedNormals = face.getNormalIndices();
			int[] referencedTexcoords = face.getTextureCoordinateIndices();

			// for normalmapping
			Vector3f[] nm_vertives= new Vector3f[3];
			Vector2f[] nm_tcoords= new Vector2f[3];
			VertexData[] vds = new VertexData[3];
			
			for (int j = 0; j < 3; j++) {
				VertexData vd = new VertexData();
				Vector3f referencedVertex = verticesTemp.get(Math.abs(referencedVertices[j])-1);
				Vector3f referencedNormal = normalsTemp.get(Math.abs(referencedNormals[j])-1);
				Vector2f referencedTexcoord = texcoordsTemp.get(Math.abs(referencedTexcoords[j])-1);
				vd.setST(referencedTexcoord.x, referencedTexcoord.y);
				vd.setXYZ(referencedVertex.x, referencedVertex.y, referencedVertex.z);
				vd.setN(referencedNormal.x, referencedNormal.y, referencedNormal.z);
				verticesConverted.add(vd);

				nm_vertives[j] = referencedVertex;
				nm_tcoords[j] = referencedTexcoord;
				vds[j] = vd;
			}


			Vector3f D = null;
			Vector3f E = null;
			D = Vector3f.sub(nm_vertives[1], nm_vertives[0], D);
			E = Vector3f.sub(nm_vertives[2], nm_vertives[0], E);
			
			Vector2f F = null;
			Vector2f G = null;
			F = Vector2f.sub(nm_tcoords[1], nm_tcoords[0], F);
			G = Vector2f.sub(nm_tcoords[2], nm_tcoords[0], G);
			
			float coef = 1/(F.x * G.y - F.y * G.x);
			
			Vector3f tangent = new Vector3f();

			tangent.x = (G.y * D.x - F.y * E.x) * coef;
			tangent.y = (G.y * D.y - F.y * E.y) * coef;
			tangent.z = (G.y * D.z - F.y * E.z) * coef;
			tangent.normalise();
			vds[0].setTangent(new float[]{tangent.x, tangent.y, tangent.z});
			vds[1].setTangent(new float[]{tangent.x, tangent.y, tangent.z});
			vds[2].setTangent(new float[]{tangent.x, tangent.y, tangent.z});
			
			Vector3f biNormal = null;
			biNormal = Vector3f.cross(tangent, new Vector3f(vds[0].getN()[0],vds[0].getN()[1],vds[0].getN()[1] ), biNormal);
			vds[0].setBinormal(new float[]{biNormal.x, biNormal.y, biNormal.z});
			vds[1].setBinormal(new float[]{biNormal.x, biNormal.y, biNormal.z});
			vds[2].setBinormal(new float[]{biNormal.x, biNormal.y, biNormal.z});
		}
		
		vertices = new VertexData[verticesConverted.size()];
		verticesConverted.toArray(vertices);
		
		verticesByteBuffer = BufferUtils.createByteBuffer(vertices.length * 
				VertexData.stride);	
		
		GL20.glEnableVertexAttribArray(0);
		GL20.glEnableVertexAttribArray(1);
		GL20.glEnableVertexAttribArray(2);
		GL20.glEnableVertexAttribArray(3);
		GL20.glEnableVertexAttribArray(4);
		GL20.glEnableVertexAttribArray(5);
		
		//ByteBuffer 
		verticesByteBuffer = BufferUtils.createByteBuffer(vertices.length * VertexData.stride);				
		FloatBuffer verticesFloatBuffer = verticesByteBuffer.asFloatBuffer();
		for (int i = 0; i < vertices.length; i++) {
			// Add position, color and texture floats to the buffer
			verticesFloatBuffer.put(vertices[i].getElements());
		}
		verticesFloatBuffer.flip();
		
		vaoId = GL30.glGenVertexArrays();
		GL30.glBindVertexArray(vaoId);
		
		// Create a new Vertex Buffer Object in memory and select it (bind)
		vboId = GL15.glGenBuffers();
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboId);
		GL15.glBufferData(GL15.GL_ARRAY_BUFFER, verticesFloatBuffer, GL15.GL_STREAM_DRAW);
		
		GL20.glVertexAttribPointer(0, VertexData.positionElementCount, GL11.GL_FLOAT, 
				false, VertexData.stride, VertexData.positionByteOffset);
		GL20.glVertexAttribPointer(1, VertexData.colorElementCount, GL11.GL_FLOAT, 
				false, VertexData.stride, VertexData.colorByteOffset);
		GL20.glVertexAttribPointer(2, VertexData.textureElementCount, GL11.GL_FLOAT, 
				false, VertexData.stride, VertexData.textureByteOffset);
		GL20.glVertexAttribPointer(3, VertexData.normalElementCount, GL11.GL_FLOAT, 
				false, VertexData.stride, VertexData.normalByteOffset);
		GL20.glVertexAttribPointer(4, VertexData.binormalElementCount, GL11.GL_FLOAT, 
				false, VertexData.stride, VertexData.binormalByteOffset);
		GL20.glVertexAttribPointer(5, VertexData.tangentElementCount, GL11.GL_FLOAT, 
				false, VertexData.stride, VertexData.tangentByteOffset);
		
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
		
		// Deselect (bind to 0) the VAO
		GL30.glBindVertexArray(0);
		
		// Create a new VBO for the indices and select it (bind) - INDICES
		GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
		
	}

	public void transform() {
		modelMatrix = new Matrix4f();
		Matrix4f.scale(scale, modelMatrix, modelMatrix);
		Matrix4f.translate(position, modelMatrix, modelMatrix);
		Matrix4f.rotate(Util.degreesToRadians(angle.z), new Vector3f(0, 0, 1), 
				modelMatrix, modelMatrix);
		Matrix4f.rotate(Util.degreesToRadians(angle.y), new Vector3f(0, 1, 0), 
				modelMatrix, modelMatrix);
		Matrix4f.rotate(Util.degreesToRadians(angle.x), new Vector3f(1, 0, 0), 
				modelMatrix, modelMatrix);
	}

	public void flipBuffers() {
		modelMatrix.store(matrix44Buffer);
		matrix44Buffer.flip();
		GL20.glUniformMatrix4(TheQuadExampleMoving.modelMatrixLocation, false, matrix44Buffer);
	}
	

	public void draw() {
		// Bind to the VAO that has all the information about the vertices
		GL30.glBindVertexArray(vaoId);
		GL20.glEnableVertexAttribArray(0);
		GL20.glEnableVertexAttribArray(1);
		GL20.glEnableVertexAttribArray(2);
		GL20.glEnableVertexAttribArray(3);
		GL20.glEnableVertexAttribArray(4);
		GL20.glEnableVertexAttribArray(5);
		GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, vboiId);
		TheQuadExampleMoving.exitOnGLError("bindbuffer in entity");
		
		flipBuffers();
		TheQuadExampleMoving.exitOnGLError("flipbuffers in entity");
		//		GL11.glDrawElements(GL11.GL_TRIANGLES, indicesCount, GL11.GL_UNSIGNED_BYTE, 0);
		GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, vertices.length);

		TheQuadExampleMoving.exitOnGLError("drawArrays in entity");
		GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
		GL20.glDisableVertexAttribArray(0);
		GL20.glDisableVertexAttribArray(1);
		GL20.glDisableVertexAttribArray(2);
		GL20.glDisableVertexAttribArray(3);
		GL20.glDisableVertexAttribArray(4);
		GL20.glDisableVertexAttribArray(5);
		GL30.glBindVertexArray(0);
	}
	
	public Matrix4f getModelMatrix() {
		return modelMatrix;
	}

	public void setModelMatrix(Matrix4f modelMatrix) {
		this.modelMatrix = modelMatrix;
	}

	public Vector3f getPosition() {
		return position;
	}

	public void setPosition(Vector3f position) {
		this.position = position;
	}

	public Vector3f getAngle() {
		return angle;
	}

	public void setAngle(Vector3f angle) {
		this.angle = angle;
	}

	public Vector3f getScale() {
		return scale;
	}

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

		// Select the VAO
		GL30.glBindVertexArray(vaoId);
		
		// Disable the VBO index from the VAO attributes list
		GL20.glDisableVertexAttribArray(0);
		GL20.glDisableVertexAttribArray(1);
		
		// Delete the vertex VBO
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
		GL15.glDeleteBuffers(vboId);
		
		// Delete the index VBO
		GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
		GL15.glDeleteBuffers(vboiId);
		
		// Delete the VAO
		GL30.glBindVertexArray(0);
		GL30.glDeleteVertexArrays(vaoId);
	}

}
