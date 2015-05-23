package main.event;

import main.model.IEntity;

public class EntitySelectedEvent {

	private IEntity entity;

	public EntitySelectedEvent(IEntity entity) {
		this.setEntity(entity);
	}

	public IEntity getEntity() {
		return entity;
	}

	public void setEntity(IEntity entity) {
		this.entity = entity;
	}

}
