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
            long start = System.currentTimeMillis();
            List<Model> models = new OBJLoader().loadTexturedModel(file);
            System.out.println("Load model returned after " + (System.currentTimeMillis() - start));
            List<Entity> entities = new ArrayList<>();
            start = System.currentTimeMillis();
            entities.addAll(EntityFactory.getInstance().getEntity(name, models).getAllChildrenAndSelf());
            System.out.println("Get entities took " + (System.currentTimeMillis() - start));
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
