package de.hanno.hpengine.engine.graphics.renderer.command;

import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.model.Entity;
import de.hanno.hpengine.engine.model.EntityFactory;
import de.hanno.hpengine.engine.model.Model;
import de.hanno.hpengine.engine.model.OBJLoader;
import de.hanno.hpengine.engine.graphics.renderer.command.LoadModelCommand.EntityListResult;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class LoadModelCommand implements Command<EntityListResult> {
    private final File file;
    private final String name;

    public LoadModelCommand(File file, String name) {
        this.file = file;
        this.name = name;
    }

    public EntityListResult execute(Engine engine) {
        EntityListResult result = new EntityListResult();
        try {
            Model model = new OBJLoader().loadTexturedModel(file);
            List<Entity> entities = new ArrayList<>();
            entities.addAll(EntityFactory.getInstance().getEntity(name, model).getAllChildrenAndSelf());

            return new EntityListResult(entities);

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
