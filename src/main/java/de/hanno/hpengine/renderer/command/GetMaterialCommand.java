package de.hanno.hpengine.renderer.command;

import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.renderer.command.InitMaterialCommand.MaterialResult;
import de.hanno.hpengine.renderer.material.Material;
import de.hanno.hpengine.renderer.material.MaterialFactory;
import de.hanno.hpengine.renderer.material.MaterialInfo;

public class GetMaterialCommand implements Command<MaterialResult> {

	MaterialInfo materialInfo;
	
	public GetMaterialCommand(MaterialInfo materialInfo) {
		this.materialInfo = materialInfo;
	}
	
	@Override
	public MaterialResult execute(Engine engine) {
		Material material = MaterialFactory.getInstance().getMaterial(materialInfo);
		return new MaterialResult(material);
	}

}
