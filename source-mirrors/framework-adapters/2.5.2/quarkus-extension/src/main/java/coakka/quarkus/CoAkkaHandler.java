package coakka.quarkus;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Associates a CDI local handler with its unique CoAkka runtime target. */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface CoAkkaHandler {
    /**
     * Returns the runtime target owned by the annotated handler.
     *
     * @return target name used for routing
     */
    String value();
}
