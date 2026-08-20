package coakka.spring;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Marks a one-argument Spring bean method as the local owner of a CoAkka target. */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface CoAkkaHandler {
    /**
     * Returns the unique runtime target handled by the annotated method.
     *
     * @return target name used for routing
     */
    String value();
}
