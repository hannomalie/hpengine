package de.hanno.hpengine.engine.model;

import de.hanno.hpengine.engine.event.bus.EventBus;
import org.apache.commons.io.FilenameUtils;
import org.joml.Vector3f;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;

public class EntityManager {
    public EntityManager(EventBus eventbus) {
        eventbus.register(this);
	}
	public Entity getEntity() {
		Entity entity = new Entity();
		entity.initialize();
		return entity;
	}

	public Entity getEntity(String name, Model model) {
		return getEntity(new Vector3f(), name, model);
	}

	public Entity getEntity(Vector3f position, String name, Model model) {
		Entity entity = new Entity(position, name, model);
		entity.setTranslation(position);
		entity.setName(name);
		entity.initialize();
		return entity;
	}

	public Entity getEntity(String name) throws IOException, ClassNotFoundException {
//		Entity entity = read(name);
		Entity entity = new Entity();
		entity.setName(name);
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
		entity.initialize();
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
