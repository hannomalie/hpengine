package main;

import org.lwjgl.util.vector.Vector2f;
import org.lwjgl.util.vector.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.lwjgl.opengl.GL11.*;

public class Model {

    private List<Vector3f> vertices = new ArrayList<Vector3f>();
    private List<Vector2f> texCoords = new ArrayList<Vector2f>();
    private List<Vector3f> normals = new ArrayList<Vector3f>();
    private List<Face> faces = new ArrayList<Face>();
    private HashMap<String, Material> materials = new HashMap<String, Material>();
    private boolean enableSmoothShading = true;
	private String name = "";

    public void enableStates() {
        if (hasTextureCoordinates()) {
            glEnable(GL_TEXTURE_2D);
        }
        if (isSmoothShadingEnabled()) {
            glShadeModel(GL_SMOOTH);
        } else {
            glShadeModel(GL_FLAT);
        }
    }

    public boolean hasTextureCoordinates() {
        return getTexCoords().size() > 0;
    }

    public boolean hasNormals() {
        return getNormals().size() > 0;
    }

    public List<Vector3f> getVertices() {
        return vertices;
    }

    public List<Vector2f> getTexCoords() {
        return texCoords;
    }

    public List<Vector3f> getNormals() {
        return normals;
    }

    public List<Face> getFaces() {
        return faces;
    }

    public boolean isSmoothShadingEnabled() {
        return enableSmoothShading;
    }

    public void setSmoothShadingEnabled(boolean smoothShadingEnabled) {
        this.enableSmoothShading = smoothShadingEnabled;
    }

    public HashMap<String, Material> getMaterials() {
        return materials;
    }

    public static class Material {

        @Override
        public String toString() {
            return "Material{" +
                    "specularCoefficient=" + specularCoefficient +
                    ", ambientColour=" + ambientColour +
                    ", diffuseColour=" + diffuseColour +
                    ", specularColour=" + specularColour +
                    '}';
        }

        /** Between 0 and 1000. */
        public float specularCoefficient = 100;
        public float[] ambientColour = {0.2f, 0.2f, 0.2f};
        public float[] diffuseColour = {0.3f, 1, 1};
        public float[] specularColour = {1, 1, 1};
        public org.newdawn.slick.opengl.Texture texture;
    }

	public void setName(String name) {
		this.name  = name;
	}

	public void setVertices(ArrayList<Vector3f> vertices) {
		this.vertices = vertices;
	}

	public void setNormals(ArrayList<Vector3f> normals) {
		this.normals = normals;
	}

	public void setTexCoords(ArrayList<Vector2f> texCoords) {
		this.texCoords = texCoords;
	}
}