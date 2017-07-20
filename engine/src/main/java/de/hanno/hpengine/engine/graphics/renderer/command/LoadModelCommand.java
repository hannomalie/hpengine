package de.hanno.hpengine.engine.graphics.renderer.command;

import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.component.ModelComponent;
import de.hanno.hpengine.engine.model.*;
import de.hanno.hpengine.engine.graphics.renderer.command.LoadModelCommand.EntityListResult;
import de.hanno.hpengine.engine.model.loader.md5.*;
import org.joml.Vector4f;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class LoadModelCommand implements Command<EntityListResult> {
    private final File file;
    private final String name;

    public LoadModelCommand(File file, String name) {
        if(file == null) {
            throw new IllegalArgumentException("Passed file is null!");
        }
        if(!file.exists() || !file.isFile()) {
            throw new IllegalArgumentException("Passed file is nonexistent or a directory: " + file.getPath());
        }
        this.file = file;
        this.name = name;
    }

    public EntityListResult execute(Engine engine) {
        EntityListResult result = new EntityListResult();
        try {
            List<Entity> entities = new ArrayList<>();
            ModelComponent modelComponent;
            if(file.getAbsolutePath().endsWith("md5mesh")) {
                MD5Model parsedModel = MD5Model.parse(file.getAbsolutePath());
                MD5AnimModel parsedAnimModel = MD5AnimModel.parse(file.getAbsolutePath().replace("md5mesh", "md5anim"));
                modelComponent = new ModelComponent(MD5Loader.process(parsedModel, parsedAnimModel, new Vector4f()));
            } else {
                StaticModel model = new OBJLoader().loadTexturedModel(file);
                modelComponent = new ModelComponent(model);
            }
            List<Entity> allChildrenAndSelf = EntityFactory.getInstance().getEntity(name).getAllChildrenAndSelf();
            if(!allChildrenAndSelf.isEmpty()) {
                allChildrenAndSelf.get(0).addComponent(modelComponent);
                allChildrenAndSelf.get(0).init();
            }

            entities.addAll(allChildrenAndSelf);

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
