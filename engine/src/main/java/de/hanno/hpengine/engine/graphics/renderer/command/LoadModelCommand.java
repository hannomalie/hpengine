package de.hanno.hpengine.engine.graphics.renderer.command;

import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.component.ModelComponent;
import de.hanno.hpengine.engine.entity.Entity;
import de.hanno.hpengine.engine.model.*;
import de.hanno.hpengine.engine.graphics.renderer.command.LoadModelCommand.EntityListResult;
import de.hanno.hpengine.engine.model.loader.md5.*;
import de.hanno.hpengine.engine.model.material.MaterialManager;

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
            Model model = getModel(engine.getMaterialManager());
            List<Entity> allChildrenAndSelf = engine.getEntityManager().create(name).getAllChildrenAndSelf();
            if(!allChildrenAndSelf.isEmpty()) {
                Entity entity = allChildrenAndSelf.get(0);
                ModelComponent modelComponent = new ModelComponent(entity, model);
                entity.addComponent(modelComponent);
            }

            entities.addAll(allChildrenAndSelf);

            return new EntityListResult(entities);

        } catch (Exception e) {
            e.printStackTrace();
            return result;
        }
    }

    protected Model getModel(MaterialManager materialManager) throws Exception {
        Model model;
        if(file.getAbsolutePath().endsWith("md5mesh")) {
            MD5Model parsedModel = MD5Model.parse(file.getAbsolutePath());
            MD5AnimModel parsedAnimModel = MD5AnimModel.parse(file.getAbsolutePath().replace("md5mesh", "md5anim"));
            model = MD5Loader.process(materialManager, parsedModel, parsedAnimModel);
        } else {
            model = new OBJLoader().loadTexturedModel(materialManager, file);
        }
        return model;
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
