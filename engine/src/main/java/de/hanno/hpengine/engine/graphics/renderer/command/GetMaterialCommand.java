package de.hanno.hpengine.engine.graphics.renderer.command;

import de.hanno.hpengine.engine.graphics.renderer.command.InitMaterialCommand.MaterialResult;
import de.hanno.hpengine.engine.model.material.MaterialManager;
import de.hanno.hpengine.engine.model.material.SimpleMaterial;
import de.hanno.hpengine.engine.model.material.SimpleMaterialInfo;

public class GetMaterialCommand implements Command<MaterialResult> {

	SimpleMaterialInfo materialInfo;
	private MaterialManager materialManager;

	public GetMaterialCommand(SimpleMaterialInfo materialInfo, MaterialManager materialManager) {
		this.materialInfo = materialInfo;
		this.materialManager = materialManager;
	}
	
	@Override
	public MaterialResult execute() {
        SimpleMaterial material = materialManager.getMaterial(materialInfo);
		return new MaterialResult(material);
	}

}
