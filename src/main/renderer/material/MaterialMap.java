package main.renderer.material;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.HashMap;

import main.renderer.material.Material.MAP;
import main.texture.Texture;


public class MaterialMap implements Serializable {
	private static final long serialVersionUID = 1L;
	
	private transient HashMap<MAP, Texture> textures = new HashMap<MAP, Texture>();
	private HashMap<MAP, String> textureNames = new HashMap<MAP, String>();
	
	
	public MaterialMap() {
		setTextures(new HashMap<MAP, Texture>());
		setTextureNames(new HashMap<MAP, String>());
	}
	
	@Override
	public boolean equals(Object other) {
		if (!(other instanceof MaterialMap)) {
			return false;
		}
		
		MaterialMap b = (MaterialMap) other;
		
		for (Object key : getTextures().keySet()) {
			if(!b.getTextures().containsKey(key)) {
				return false;
			} else {
				if (!((Texture) b.getTextures().get(key)).equals((Texture)getTextures().get(key))) {
					return false;
				}
			}
		}
		
		return true;
	}

	public Object get(Object key) {
		return getTextures().get(key);
	}

	public void put(MAP key, Texture value) {
		getTextures().put(key, value);
	}

	public HashMap<MAP, Texture> getTextures() {
		return textures;
	}

	public void setTextures(HashMap<MAP, Texture> textures) {
		this.textures = textures;
	}

	public HashMap<MAP, String> getTextureNames() {
		return textureNames;
	}

	public void setTextureNames(HashMap<MAP, String> textureNames) {
		this.textureNames = textureNames;
	}

	public int size() {
		return getTextures().size();
	}
	
	private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
	    in.defaultReadObject();
	    setTextures(new HashMap<MAP, Texture>());
	}
	
	private void writeObject(ObjectOutputStream oos) throws IOException {
		textureNames = new HashMap<Material.MAP, String>();
		for (MAP map : textures.keySet()) {
			textureNames.put(map, textures.get(map).getPath());
		}
        oos.defaultWriteObject();
    }
}
