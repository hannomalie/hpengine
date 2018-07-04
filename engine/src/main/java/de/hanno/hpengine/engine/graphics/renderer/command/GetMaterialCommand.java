package de.hanno.hpengine.engine.graphics.renderer.command;

import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.graphics.renderer.command.InitMaterialCommand.MaterialResult;
import de.hanno.hpengine.engine.model.material.SimpleMaterial;
import de.hanno.hpengine.engine.model.material.SimpleMaterialInfo;

public class GetMaterialCommand implements Command<MaterialResult> {

	SimpleMaterialInfo materialInfo;
	
	public GetMaterialCommand(SimpleMaterialInfo materialInfo) {
		this.materialInfo = materialInfo;
	}
	
	@Override
	public MaterialResult execute(Engine engine) {
        SimpleMaterial material = engine.getScene().getMaterialManager().getMaterial(materialInfo);
		return new MaterialResult(material);
	}

}
