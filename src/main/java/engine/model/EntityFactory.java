package engine.model;

import engine.model.Entity.Update;
import org.apache.commons.io.FilenameUtils;
import org.lwjgl.util.vector.Vector3f;
import renderer.material.Material;
import renderer.material.MaterialFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.List;

public class EntityFactory {
    private static EntityFactory instance;

    private EntityFactory() {
	}


	public Entity getEntity() {
		Entity entity = new Entity();
		entity.init();
		return entity;
	}

	public Entity getEntity(String name, List<Model> models) {
		if(models.size() > 1) {
			Entity entity = new Entity();
			entity.setName(name);
			for (Model model : models) {
				Entity child = getEntity(model);
				child.setParent(entity);
			}
			entity.init();
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
//		try {
//			entity = read(name);
//		} catch (IOException e) {
//			Logger.getGlobal().info(String.format("File not found for %s", name));

			entity = new Entity(MaterialFactory.getInstance(), position, name, model, material.getName());
			entity.setPosition(position);
			entity.setName(name);
//		} catch (ClassNotFoundException e) {
//			e.printStackTrace();
//		}

		entity.init();
		return entity;
	}

	public Entity getEntity(String childName) throws IOException, ClassNotFoundException {
		Entity entity = read(childName);
		return entity;
	}

    public Entity read(String resourceName) throws IOException, ClassNotFoundException {
		String fileName = FilenameUtils.getBaseName(resourceName);
		FileInputStream fis = null;
		ObjectInputStream in = null;
		fis = new FileInputStream(Entity.getDirectory() + fileName + ".hpentity");
		in = new ObjectInputStream(fis);
		Entity entity = (Entity) in.readObject();
		handleEvolution(entity);
		entity.init();
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

    public static EntityFactory getInstance() {
        if(instance == null) {
            throw new IllegalStateException("EntityFactory not initialized. Init a renderer first.");
        }
        return instance;
    }

    public static void init() {
        instance = new EntityFactory();
    }
}
