package engine.model;

import org.lwjgl.util.vector.Vector2f;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;
import renderer.material.Material;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL11.*;

public class Model implements Serializable {
	private static final long serialVersionUID = 1L;
	
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


    private transient Vector3f min;
    private transient Vector3f max;

    public Vector4f[] getMinMax() {
        if (min == null || max == null) {
            min = vertices.get(0);
            max = vertices.get(0);

            for (Vector3f position : vertices) {

                min.x = position.x < min.x ? position.x : min.x;
                min.y = position.y < min.y ? position.y : min.y;
                min.z = position.z < min.z ? position.z : min.z;

                max.x = position.x > max.x ? position.x : max.x;
                max.y = position.y > max.y ? position.y : max.y;
                max.z = position.z > max.z ? position.z : max.z;
            }

        }

        return new Vector4f[] {new Vector4f(min.x, min.y, min.z, 1), new Vector4f(max.x, max.y, max.z, 1)};
    }

    private transient Vector3f center;
    public Vector3f getCenter() {
        if(center == null) {
            center = (Vector3f) Vector3f.add(min, Vector3f.sub(max, min, null), null).scale(0.5f);
        }
        return new Vector3f(center);
    }
    public void setCenter(Vector3f center) {
        this.center = center;
    }
}