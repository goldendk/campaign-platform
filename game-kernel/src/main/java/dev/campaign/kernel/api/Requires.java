package dev.campaign.kernel.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Optional hint on a command record component: the referenced entity must carry this tag. The UI uses it to
 * highlight eligible targets before the player has submitted anything.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.RECORD_COMPONENT, ElementType.PARAMETER, ElementType.FIELD, ElementType.METHOD})
public @interface Requires {
    String value();
}
