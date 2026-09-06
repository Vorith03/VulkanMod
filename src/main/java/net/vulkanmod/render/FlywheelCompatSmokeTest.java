package net.vulkanmod.render;

import net.vulkanmod.Initializer;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Optional startup smoke coverage for legacy Flywheel 0.6. Uses reflection so
 * VulkanMod keeps no compile/runtime dependency on Flywheel.
 */
public final class FlywheelCompatSmokeTest {
    private FlywheelCompatSmokeTest() {
    }

    public static void verifyIfPresent() {
        final Class<?> backendClass;
        try {
            backendClass = Class.forName("com.jozufozu.flywheel.backend.Backend");
        } catch (ClassNotFoundException ignored) {
            return;
        }

        try {
            Method isOn = backendClass.getMethod("isOn");
            Method refresh = backendClass.getMethod("refresh");

            if ((boolean) isOn.invoke(null)) {
                throw new IllegalStateException("Flywheel backend was active before refresh under VulkanMod");
            }

            refresh.invoke(null);

            if ((boolean) isOn.invoke(null)) {
                throw new IllegalStateException("Flywheel backend became active after refresh under VulkanMod");
            }
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("Flywheel compatibility smoke test failed", cause);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not inspect Flywheel backend during compatibility smoke test", e);
        }

        Initializer.LOGGER.info("Flywheel 0.6 compatibility smoke test passed");
    }
}
