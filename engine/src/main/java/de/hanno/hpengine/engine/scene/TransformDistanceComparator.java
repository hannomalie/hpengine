package de.hanno.hpengine.engine.scene;

import java.util.Comparator;

import de.hanno.hpengine.engine.entity.Entity;

public class TransformDistanceComparator<T extends Entity> implements Comparator<T>{

	protected T reference;

	public T getReference() {
		return reference;
	}
	
	@Override
	public int compare(T o1, T o2) {
		if(reference == null) {
			return 0;
		}

		float distanceToFirst = reference.getTransform().getPosition().distanceSquared(o1.getTransform().getPosition());
		float distanceToSecond = reference.getTransform().getPosition().distanceSquared(o2.getTransform().getPosition());
		return Float.compare(distanceToFirst, distanceToSecond);
	}


	public void setReference(T reference) {
		this.reference = reference;
	}

	public TransformDistanceComparator(T reference) {
		this.reference = reference;
	}

}
