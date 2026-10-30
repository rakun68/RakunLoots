package me.rakkoon69.rakunloots.server;

import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * No Bukkit world or block type crosses the server adapter. Item stacks may.
 */
class ServerAdapterTypesTest {

    @Test
    void noBukkitTypeButItemStackCrossesTheAdapter() {
        List<String> crossing = new ArrayList<>();
        List<Class<?>> seams = List.of(ServerAdapter.class, SharedInventory.class, TaskScheduler.class,
                TaskHandle.class);
        for (Class<?> seam : seams) {
            for (Method method : seam.getMethods()) {
                collectBukkit(method.getGenericReturnType(), seam.getSimpleName() + "#" + method.getName(), crossing);
                for (Type parameter : method.getGenericParameterTypes()) {
                    collectBukkit(parameter, seam.getSimpleName() + "#" + method.getName(), crossing);
                }
            }
        }

        assertEquals(List.of(), crossing);
    }

    /**
     * Adds each Bukkit type other than {@link ItemStack} found in the type, its type arguments, and its array
     * components.
     */
    private static void collectBukkit(Type type, String where, List<String> crossing) {
        if (type instanceof Class<?> raw) {
            Class<?> component = raw;
            while (component.isArray()) {
                component = component.getComponentType();
            }
            if (component != ItemStack.class && component.getName().startsWith("org.bukkit.")) {
                crossing.add(where + ": " + component.getName());
            }
        } else if (type instanceof ParameterizedType parameterized) {
            collectBukkit(parameterized.getRawType(), where, crossing);
            for (Type argument : parameterized.getActualTypeArguments()) {
                collectBukkit(argument, where, crossing);
            }
        } else if (type instanceof GenericArrayType array) {
            collectBukkit(array.getGenericComponentType(), where, crossing);
        } else if (type instanceof WildcardType wildcard) {
            for (Type bound : wildcard.getUpperBounds()) {
                collectBukkit(bound, where, crossing);
            }
            for (Type bound : wildcard.getLowerBounds()) {
                collectBukkit(bound, where, crossing);
            }
        }
    }
}
