package main.model;

import org.lwjgl.util.vector.Vector3f;

import main.renderer.material.Material;

public class EntityFactory {
	public IEntity getEntity(Model model) {
		return getEntity(model, model.getMaterial());
	}
	
	public IEntity getEntity(Vector3f position, Model model) {
		return getEntity(position, model, model.getMaterial());
	}

	public IEntity getEntity(Model model, Material material) {
		return getEntity(new Vector3f(0, 0, 0), model, material);
	}
	
	public IEntity getEntity(Vector3f position, Model model, Material material) {
		IEntity entity = new Entity(position, model, material);
		return entity;
	}
}
