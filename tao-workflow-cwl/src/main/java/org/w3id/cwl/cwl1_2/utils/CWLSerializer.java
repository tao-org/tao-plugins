package org.w3id.cwl.cwl1_2.utils;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;

/** Serializes CWL fields without converting data values or unions to strings. */
public final class CWLSerializer {
    private CWLSerializer() { }

    public static Map<Object, Object> serialize(Object object) {
        Map<Object, Object> result = new LinkedHashMap<>();
        for (Field field : object.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || field.getName().equals("loadingOptions_")) continue;
            field.setAccessible(true);
            try {
                Object raw = unwrap(field.get(object));
                if (raw == null) continue;
                if (field.getName().equals("extensionFields_")) {
                    result.putAll((Map<?, ?>) raw);
                    continue;
                }
                String key = field.getName().endsWith("_")
                        ? field.getName().substring(0, field.getName().length() - 1) : field.getName();
                Object value = value(raw);
                // A run that names another element of a packed graph is a fragment reference.
                if (key.equals("run") && raw instanceof String reference
                        && !reference.contains(":") && !reference.contains("#") && !reference.contains(".cwl")) {
                    value = "#" + reference;
                }
                result.put(key, value);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Cannot serialize CWL field " + field.getName(), e);
            }
        }
        return result;
    }

    public static Object unwrap(Object value) {
        return value instanceof Optional<?> optional ? optional.orElse(null) : value;
    }

    public static Object value(Object value) {
        value = unwrap(value);
        if (value instanceof Saveable) return serialize(value);
        if (value instanceof Enum<?>) {
            // Some generated enums have no toString override; their Java name is not a CWL symbol.
            try {
                Field symbol = value.getClass().getDeclaredField("docVal");
                symbol.setAccessible(true);
                return symbol.get(value);
            } catch (NoSuchFieldException e) {
                return value.toString();
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Cannot serialize CWL enum", e);
            }
        }
        if (value instanceof Collection<?> collection) {
            List<Object> result = new ArrayList<>();
            for (Object item : collection) result.add(value(item));
            return result;
        }
        if (value instanceof Map<?, ?> map) {
            Map<Object, Object> result = new LinkedHashMap<>();
            map.forEach((key, item) -> result.put(key, value(item)));
            return result;
        }
        return value;
    }
}
