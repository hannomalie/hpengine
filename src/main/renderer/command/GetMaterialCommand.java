package main.renderer.command;

import main.World;
import main.renderer.Result;
import main.renderer.command.InitMaterialCommand.MaterialResult;
import main.renderer.material.Material;
import main.renderer.material.MaterialFactory.MaterialInfo;

public class GetMaterialCommand implements Command<MaterialResult> {

	MaterialInfo materialInfo;
	
	public GetMaterialCommand(MaterialInfo materialInfo) {
		this.materialInfo = materialInfo;
	}
	
	@Override
	public MaterialResult execute(World world) {
		Material material = world.getRenderer().getMaterialFactory().getMaterial(materialInfo);
		return new MaterialResult(material);
	}

}
