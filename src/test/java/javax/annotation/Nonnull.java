package javax.annotation;

import java.lang.annotation.*;

@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.CLASS)
public @interface Nonnull {}
