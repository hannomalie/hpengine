package renderer.command;

import engine.Engine;
import renderer.command.InitMaterialCommand.MaterialResult;
import renderer.material.Material;
import renderer.material.MaterialFactory;
import renderer.material.MaterialInfo;

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
