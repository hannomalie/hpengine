package main.renderer.command;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import main.World;
import main.model.IEntity;
import main.model.Model;
import main.renderer.Renderer;
import main.renderer.Result;
import main.renderer.command.LoadModelCommand.EntityListResult;

import org.lwjgl.util.vector.Vector3f;

public class LoadModelCommand implements Command<EntityListResult> {
	private final File file;
	private final String name;
	
	public LoadModelCommand(File file, String name) {
		this.file = file;
		this.name = name;
	}

	public EntityListResult execute(World world) {
		Renderer renderer = world.getRenderer();
		EntityListResult result = new EntityListResult();
		try {
			List<Model> models = renderer.getOBJLoader().loadTexturedModel(file);
			List<IEntity> entities = new ArrayList<IEntity>();
			
			for (int i = 0; i < models.size(); i++) {
				Model model = models.get(i);
				String counter = i == 0 ? "" : "_" +i + model.getName() ;
				entities.add(renderer.getEntityFactory().getEntity(new Vector3f(), name + counter, model, model.getMaterial()));
			}
			
			return new EntityListResult(entities);
			
		} catch (IOException e) {
			e.printStackTrace();
			return result;
		} catch (Exception e) {
			e.printStackTrace();
			return result;
		}
	}
	
	public static class EntityListResult extends Result {

		public List<IEntity> entities;
		private boolean successFul = false;

		public EntityListResult() {
		}
		public EntityListResult(List<IEntity> entities) {
			this.entities = entities;
			this.successFul = true;
		}
		
		public void setEntities(List<IEntity> entities) {
			this.entities = entities;
			this.successFul = true;
		}

		@Override
		public boolean isSuccessful() {
			return successFul;
		}
		
	}
}
