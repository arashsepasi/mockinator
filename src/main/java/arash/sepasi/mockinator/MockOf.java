package arash.sepasi.mockinator;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation signals to {@link Mockinator} that this class is a mock implementation of the {@link #value()} class.
 *
 * @author Arash Sepasi
 *
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface MockOf {
    /**
     * The class or interface that this class is a mock implementation of.
     * @return
     */
    Class<?> value();
}