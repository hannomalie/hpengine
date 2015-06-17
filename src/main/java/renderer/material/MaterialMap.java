package renderer.material;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

import renderer.material.Material.MAP;
import texture.Texture;


public class MaterialMap implements Serializable {
	private static final long serialVersionUID = 1L;
	
	private transient ConcurrentHashMap<MAP, Texture> textures = new ConcurrentHashMap<MAP, Texture>();
	private HashMap<MAP, String> textureNames = new HashMap<MAP, String>();
	
	
	public MaterialMap() {
		setTextures(new ConcurrentHashMap<MAP, Texture>());
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

	public ConcurrentHashMap<MAP, Texture> getTextures() {
		return textures;
	}

	public void setTextures(ConcurrentHashMap<MAP, Texture> textures) {
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
	    setTextures(new ConcurrentHashMap<MAP, Texture>());
	}
	
	private void writeObject(ObjectOutputStream oos) throws IOException {
		textureNames = new HashMap<Material.MAP, String>();
		for (MAP map : textures.keySet()) {
			textureNames.put(map, textures.get(map).getPath());
		}
        oos.defaultWriteObject();
    }
}
