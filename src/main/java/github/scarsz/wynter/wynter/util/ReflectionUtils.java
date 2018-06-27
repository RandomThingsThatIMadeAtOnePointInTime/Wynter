package github.scarsz.wynter.wynter.util;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class ReflectionUtils {

    public static boolean hasMethod(Object o, String methodName) {
        return Arrays.stream(o.getClass().getMethods()).anyMatch(method -> method.getName().equals(methodName));
    }

    public static Map<String, Object> collectVariables(Object event) {
        return collectVariables(event, false);
    }

    public static Map<String, Object> collectVariables(Object event, boolean strict) {
        Map<String, Object> foundVariables = new HashMap<>();

//        if (event.getClass().getSuperclass() != null) {
//            Object superEvent = event.getClass().getSuperclass().cast(event);
//            collectVariables(superEvent).forEach(foundVariables::put);
//        }

        Method[] methods = !strict ? event.getClass().getMethods() : event.getClass().getDeclaredMethods();
        for (Method method : methods) {
            if (method.getParameterCount() != 0) continue;
            if (!method.getName().startsWith("get") && !method.getName().startsWith("is")) continue;

            try {
                foundVariables.put(method.getName().substring(3).toLowerCase(), method.invoke(event));
            } catch (IllegalAccessException | InvocationTargetException e) {
                e.printStackTrace();
            }
        }

        return foundVariables;
    }

}
