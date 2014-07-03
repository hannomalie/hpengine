package main.renderer.material;

import java.io.Serializable;
import java.util.HashMap;

import main.renderer.material.Material.MAP;
import main.texture.Texture;


public class MaterialMap implements Serializable {
	
	public HashMap<MAP, Texture> textures = new HashMap<MAP, Texture>();
	
	@Override
	public boolean equals(Object other) {
		if (!(other instanceof MaterialMap)) {
			return false;
		}
		
		MaterialMap b = (MaterialMap) other;
		
		for (Object key : textures.keySet()) {
			if(!b.textures.containsKey(key)) {
				return false;
			} else {
				if (!((Texture) b.textures.get(key)).equals((Texture)textures.get(key))) {
					return false;
				}
			}
		}
		
		return true;
	}

	public Object get(Object key) {
		return textures.get(key);
	}

	public void put(MAP key, Texture value) {
		textures.put(key, value);
	}

	public int size() {
		return textures.size();
	}

}
