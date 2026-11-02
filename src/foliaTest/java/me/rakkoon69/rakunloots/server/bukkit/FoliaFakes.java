package me.rakkoon69.rakunloots.server.bukkit;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Builds hand fakes of Folia and Bukkit interfaces. MockBukkit 1.18 does not match folia-api 1.20.1, so the Folia
 * tests fake only the calls the adapter makes.
 */
public final class FoliaFakes {

    /**
     * Answers one call on a fake, or returns {@link #UNHANDLED} to fall back to the interface's default method.
     */
    @FunctionalInterface
    public interface Calls {
        Object answer(Object proxy, Method method, Object[] args) throws Throwable;
    }

    public static final Object UNHANDLED = new Object();

    private FoliaFakes() {
    }

    /**
     * A fake of the interface. Calls the answer leaves unhandled run the interface's default method, and throw for an
     * abstract method, so an unexpected call fails the test.
     */
    public static <T> T fake(Class<T> type, String name, Calls calls) {
        InvocationHandler handler = (proxy, method, args) -> {
            Object[] arguments = args == null ? new Object[0] : args;
            switch (method.getName()) {
                case "hashCode":
                    if (arguments.length == 0) {
                        return System.identityHashCode(proxy);
                    }
                    break;
                case "equals":
                    if (arguments.length == 1) {
                        return proxy == arguments[0];
                    }
                    break;
                case "toString":
                    if (arguments.length == 0) {
                        return name;
                    }
                    break;
                default:
                    break;
            }
            Object answer = calls.answer(proxy, method, arguments);
            if (answer != UNHANDLED) {
                return answer;
            }
            if (method.isDefault()) {
                return InvocationHandler.invokeDefault(proxy, method, arguments);
            }
            throw new UnsupportedOperationException(name + " does not fake " + method);
        };
        return type.cast(Proxy.newProxyInstance(FoliaFakes.class.getClassLoader(), new Class<?>[]{type}, handler));
    }
}
