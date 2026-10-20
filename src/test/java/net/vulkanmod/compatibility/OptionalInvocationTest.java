package net.vulkanmod.compatibility;

import java.lang.reflect.InvocationTargetException;

public final class OptionalInvocationTest {
    public static void main(String[] args) {
        RuntimeException runtime = new IllegalArgumentException("runtime");
        if (OptionalInvocation.propagate("context",new InvocationTargetException(runtime)) != runtime)
            throw new AssertionError("Runtime identity lost");
        Error error = new AssertionError("error");
        try { OptionalInvocation.propagate("context",new InvocationTargetException(error));
            throw new AssertionError("Error swallowed"); }
        catch (Error actual) { if (actual != error) throw new AssertionError("Error identity lost", actual); }
        Exception checked = new Exception("checked");
        RuntimeException wrapped = OptionalInvocation.propagate("context",new InvocationTargetException(checked));
        if (!(wrapped instanceof IllegalStateException) || wrapped.getCause()!=checked || !wrapped.getMessage().equals("context"))
            throw new AssertionError("Checked cause/context lost");
        System.out.println("Optional invocation cause isolation passed without Minecraft or optional mods");
    }
}
