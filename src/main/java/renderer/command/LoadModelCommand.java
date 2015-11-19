package renderer.command;

import engine.AppContext;
import engine.model.Entity;
import engine.model.EntityFactory;
import engine.model.Model;
import engine.model.OBJLoader;
import renderer.command.LoadModelCommand.EntityListResult;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class LoadModelCommand implements Command<EntityListResult> {
    private final File file;
    private final String name;

    public LoadModelCommand(File file, String name) {
        this.file = file;
        this.name = name;
    }

    public EntityListResult execute(AppContext appContext) {
        EntityListResult result = new EntityListResult();
        try {
            List<Model> models = new OBJLoader().loadTexturedModel(file);
            List<Entity> entities = new ArrayList<>();
            entities.addAll(EntityFactory.getInstance().getEntity(name, models).getAllChildrenAndSelf());
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

        public List<Entity> entities;
        private boolean successFul = false;

        public EntityListResult() {
        }

        public EntityListResult(List<Entity> entities) {
            this.entities = entities;
            this.successFul = true;
        }

        public void setEntities(List<Entity> entities) {
            this.entities = entities;
            this.successFul = true;
        }

        @Override
        public boolean isSuccessful() {
            return successFul;
        }

    }
}
