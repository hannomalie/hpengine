package de.hanno.hpengine.engine.model;

import com.google.common.eventbus.Subscribe;
import de.hanno.hpengine.component.ModelComponent;
import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.model.Entity.Update;
import de.hanno.hpengine.event.EntityAddedEvent;
import de.hanno.hpengine.event.EntityChangedMaterialEvent;
import de.hanno.hpengine.event.SceneInitEvent;
import net.engio.mbassy.listener.Handler;
import org.apache.commons.io.FilenameUtils;
import org.lwjgl.util.vector.Vector3f;
import de.hanno.hpengine.renderer.OpenGLContext;
import de.hanno.hpengine.renderer.material.Material;
import de.hanno.hpengine.shader.OpenGLBuffer;
import de.hanno.hpengine.shader.PersistentMappedBuffer;
import de.hanno.hpengine.util.Util;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.List;
import java.util.stream.Collectors;

public class EntityFactory {
    private static EntityFactory instance;
    private volatile transient OpenGLBuffer entitiesBuffer;

    private EntityFactory() {
        Engine.getEventBus().register(this);
	}
	public Entity getEntity() {
		Entity entity = new Entity();
		entity.init();
		return entity;
	}

	public Entity getEntity(String name, List<Model> models) {
		if(models.size() > 1) {
            long start = System.currentTimeMillis();
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

			entity = new Entity(position, name, model, material.getName());
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

    public static void create() {
        instance = new EntityFactory();
    }
    public static void init() {
        instance.entitiesBuffer = new PersistentMappedBuffer(16000);
    }

    public OpenGLBuffer getEntitiesBuffer() {
        return entitiesBuffer;
    }

    public void bufferEntities(List<Entity> entities) {
        entitiesBuffer.put(Util.toArray(entities, Entity.class));
//        for(int i = 0; i < entities.size(); i++) {
//            ModelComponent.getGlobalEntityOffsetBuffer().put(i, Engine.getInstance().getScene().getEntityIndexOf(entities.get(i)));
//        }
    }

    public void bufferEntities() {
        if(Engine.getInstance().getScene() != null) {
//            TODO: Execute this outside of the renderloop
            OpenGLContext.getInstance().execute(() -> {
                bufferEntities(Engine.getInstance().getScene().getEntities().stream().filter(e -> e.hasComponent(ModelComponent.class)).collect(Collectors.toList()));
            });
        }
    }

    @Subscribe
    @Handler
    public void handle(EntityAddedEvent event) {
        bufferEntities();
    }
    @Subscribe
    @Handler
    public void handle(SceneInitEvent event) {
        bufferEntities();
    }
    @Subscribe
    @Handler
    public void handle(EntityChangedMaterialEvent event) {
        if(Engine.getInstance().getScene() != null) {
            Entity entity = event.getEntity();
//            buffer(entity);
            bufferEntities();
        }
    }

    public void buffer(Entity entity) {
        int offset = 0;
        for(Entity current : Engine.getInstance().getScene().getEntitiesWithModelComponent().keySet()) {
            if(current.equals(entity)) {
                break;
            }
            offset += entity.getElementsPerObject();
        }
//        entity.getElementsPerObject() * Engine.getInstance().getScene().getEntities().indexOf(entity);
        entitiesBuffer.put(offset, entity);
    }
}
