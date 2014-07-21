package main.renderer.command;

import main.World;
import main.model.IEntity;
import main.renderer.Result;

public class RemoveEntityCommand implements Command {

	private IEntity entity;

	public RemoveEntityCommand(IEntity entity) {
		this.entity = entity;
	}
	
	@Override
	public Result execute(World world) {
		boolean result = world.getScene().removeEntity(entity);
		if (result) {
			return new Result() {
				@Override
				public boolean isSuccessful() {
					return true;
				}
			};
		} else {
			return new Result() {
				@Override
				public boolean isSuccessful() {
					return false;
				}
			};
		}
	}

}
