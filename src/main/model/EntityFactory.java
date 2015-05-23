package main.model;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;

import org.apache.commons.io.FilenameUtils;
import org.lwjgl.util.vector.Vector3f;

import main.World;
import main.model.Entity.Update;
import main.renderer.Renderer;
import main.renderer.material.Material;
import main.renderer.material.MaterialFactory;

public class EntityFactory {
	private Renderer renderer;
	private World world;
	
	public EntityFactory(World world) {
		this.world = world;
		this.renderer = world.getRenderer();
	}

	public IEntity getEntity(Model model) {
		return getEntity(model, model.getMaterial());
	}
	
	public IEntity getEntity(Vector3f position, Model model) {
		return getEntity(position, model.getName(), model, model.getMaterial());
	}

	public IEntity getEntity(Model model, Material material) {
		return getEntity(new Vector3f(0, 0, 0), model.getName(), model, material);
	}

	public IEntity getEntity(Vector3f position, String name, Model model, Material material) {
		IEntity entity = read(name);
		
		if(entity == null) {
			entity = new Entity(world, renderer.getMaterialFactory(), position, name, model, material.getName());	
			entity.setPosition(position);
			entity.setName(name);
			Entity.write((Entity) entity, name);
		}
		
		return entity;
	}
//	public IEntity getEntity(Vector3f position, String name, Model model, Material material) {
//		IEntity entity = read(model.getName());
//		
//		if(entity != null) {
//			entity.setPosition(position);
//			entity.setName(name);
//		} else {
//			entity = read(name);
//			if(entity != null) {
//				entity.setPosition(position);
//				return entity;
//			} else {
//				entity = new Entity(renderer.getMaterialFactory(), position, name, model, material.getName());	
//			}
//		}
//		Entity.write((Entity) entity, name);
//		
//		return entity;
//	}

    public IEntity read(String resourceName) {
		String fileName = FilenameUtils.getBaseName(resourceName);
		FileInputStream fis = null;
		ObjectInputStream in = null;
		try {
			fis = new FileInputStream(Entity.getDirectory() + fileName + ".hpentity");
			in = new ObjectInputStream(fis);
			Entity entity = (Entity) in.readObject();
			handleEvolution(entity);
			entity.init(world);
			in.close();
			
			return entity;
		} catch (IOException | ClassNotFoundException e) {
			e.printStackTrace();
		}
		return null;
	}
    
    public IEntity readWithoutInit(String resourceName) {
		String fileName = FilenameUtils.getBaseName(resourceName);
		FileInputStream fis = null;
		ObjectInputStream in = null;
		try {
			fis = new FileInputStream(Entity.getDirectory() + fileName + ".hpentity");
			in = new ObjectInputStream(fis);
			Entity entity = (Entity) in.readObject();
			handleEvolution(entity);
			in.close();
			
			return entity;
		} catch (IOException | ClassNotFoundException e) {
			e.printStackTrace();
		}
		return null;
	}
    
    private void handleEvolution(Entity entity) {
		if(entity.getUpdate() == null) {
			entity.setUpdate(Update.DYNAMIC);
		}
    }
}
