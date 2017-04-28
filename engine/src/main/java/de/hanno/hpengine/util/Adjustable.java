package de.hanno.hpengine.util;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Adjustable {
	String group() default "Main";

	int minimum() default 0;

	int maximum() default 1000;

	int minorTickSpacing() default 250;

	int majorTickSpacing() default 500;

	float factor() default 100;

}
