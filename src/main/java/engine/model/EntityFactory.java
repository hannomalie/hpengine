package engine.model;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.List;
import java.util.logging.Logger;

import engine.World;
import org.apache.commons.io.FilenameUtils;
import org.lwjgl.util.vector.Vector3f;

import engine.model.Entity.Update;
import renderer.Renderer;
import renderer.material.Material;

public class EntityFactory {
	private Renderer renderer;
	private World world;
	
	public EntityFactory(World world) {
		this.world = world;
		this.renderer = world.getRenderer();
	}

	public Entity getEntity(String name, List<Model> models) {
		if(models.size() > 1) {
			Entity entity = new Entity();
			entity.setName(name);
			for (Model model : models) {
				Entity child = getEntity(model);
				child.setParent(entity);
			}
			entity.init(world);
			return entity;
		} else {
			return getEntity(new Vector3f(), name, models.get(0), models.get(0).getMaterial());
		}
	}

	public Entity getEntity(Model model) {
		return getEntity(model, model.getMaterial());
	}

	public Entity getEntity(Vector3f position, Model model) {
		return getEntity(position, model.getName(), model, model.getMaterial());
	}

	public Entity getEntity(Model model, Material material) {
		return getEntity(new Vector3f(0, 0, 0), model.getName(), model, material);
	}
	public Entity getEntity(Vector3f position, String name, Model model, Material material) {
		Entity entity = null;
		try {
			entity = read(name);
		} catch (IOException e) {
			Logger.getGlobal().info(String.format("File not found for %s)", name));

			entity = new Entity(world, renderer.getMaterialFactory(), position, name, model, material.getName());
			entity.setPosition(position);
			entity.setName(name);
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}

		entity.init(world);
		return entity;
	}

	public Entity getEntity(String childName) throws IOException, ClassNotFoundException {
		Entity entity = read(childName);
		return entity;
	}

//	public Entity getEntity(Vector3f position, String name, Model model, Material material) {
//		Entity entity = read(model.getName());
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

    public Entity read(String resourceName) throws IOException, ClassNotFoundException {
		String fileName = FilenameUtils.getBaseName(resourceName);
		FileInputStream fis = null;
		ObjectInputStream in = null;
		fis = new FileInputStream(Entity.getDirectory() + fileName + ".hpentity");
		in = new ObjectInputStream(fis);
		Entity entity = (Entity) in.readObject();
		handleEvolution(entity);
		entity.init(world);
		in.close();

		return entity;
	}
    
    public Entity readWithoutInit(String resourceName) {
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
