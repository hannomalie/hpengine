package renderer.command;

import engine.AppContext;
import renderer.command.InitMaterialCommand.MaterialResult;
import renderer.material.Material;
import renderer.material.MaterialFactory.MaterialInfo;

public class GetMaterialCommand implements Command<MaterialResult> {

	MaterialInfo materialInfo;
	
	public GetMaterialCommand(MaterialInfo materialInfo) {
		this.materialInfo = materialInfo;
	}
	
	@Override
	public MaterialResult execute(AppContext appContext) {
		Material material = appContext.getRenderer().getMaterialFactory().getMaterial(materialInfo);
		return new MaterialResult(material);
	}

}
