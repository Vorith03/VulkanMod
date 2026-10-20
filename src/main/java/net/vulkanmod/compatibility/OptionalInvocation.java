package net.vulkanmod.compatibility;

import java.lang.reflect.InvocationTargetException;

/** Invocation mechanics only: each adapter retains its own discovery/absence policy. */
final class OptionalInvocation {
    private OptionalInvocation() {}
    static RuntimeException propagate(String message, InvocationTargetException exception) {
        Throwable cause = exception.getCause();
        if (cause instanceof RuntimeException runtime) return runtime;
        if (cause instanceof Error error) throw error;
        return new IllegalStateException(message, cause);
    }
}
