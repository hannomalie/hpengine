package main.model;

import main.renderer.material.Material;

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
    private boolean enableSmoothShading = true;
	private String name = "";
	private Material material;

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

	public void setMaterial(Material material) {
		this.material = material;
	}

    public Material getMaterial() {
		return material;
	}

	public String getName() {
		return name;
	}
}