package com.example.simplejson.mapper;

import com.example.simplejson.exception.BeanMapperException;
import com.example.simplejson.exception.UnsupportedMapTypeException;
import com.example.simplejson.parser.JsonArray;
import com.example.simplejson.parser.JsonObject;

import java.lang.reflect.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Mapper {
    private static final Class<?>[] supportType = new Class[]{
            Integer.class,
            Long.class,
            Float.class,
            Double.class,
            Boolean.class,
            Character.class,
            String.class,

    };

    private Mapper() {
    }

    public static JsonObject toJsonObject(Object object) {
        JsonObject jsonObject = new JsonObject();
        Map<String, ObjMethod> properties = getProperties(object.getClass());
        for (Map.Entry<String, ObjMethod> entry : properties.entrySet()) {
            String key = entry.getKey();
            Method getMethod = entry.getValue().getMethod;
            Object value = null;
            try {
                value = getMethod.invoke(object);
            } catch (IllegalAccessException | InvocationTargetException e) {
                e.printStackTrace();
            }
            if (value == null) {
                jsonObject.put(key, null);
            } else if (value instanceof Map) {
                JsonObject obj = toJsonObject(value);
                jsonObject.put(key, obj);
            } else if (value instanceof List) {
                JsonArray arr = toJsonArray((List<?>) value);
                jsonObject.put(key, arr);
            } else if (isSupportType(value.getClass())) {
                jsonObject.put(key, value);
            }
        }
        return jsonObject;
    }

    public static JsonArray toJsonArray(List<?> list) {
        JsonArray jsonArray = new JsonArray();
        for (Object o : list) {
            if (o == null) {
                jsonArray.add(null);
            } else if (o instanceof Map) {
                JsonObject obj = toJsonObject(o);
                jsonArray.add(obj);
            } else if (o instanceof List) {
                JsonArray arr = toJsonArray((List<?>) o);
                jsonArray.add(arr);
            } else if (isSupportType(o.getClass())) {
                jsonArray.add(o);
            } else {
                jsonArray.add(toJsonObject(o));
            }
        }
        return jsonArray;
    }


    public static <T> T toBean(JsonObject jsonObject, Class<T> clazz) {
        try {
            T bean = clazz.getDeclaredConstructor().newInstance();
            Map<String, ObjMethod> properties = getProperties(clazz);

            for (Map.Entry<String, Object> entry : jsonObject.entrySet()) {
                String key = entry.getKey();
                if (!properties.containsKey(key)) throw new BeanMapperException();
                Field field = clazz.getDeclaredField(key);
                Object value = entry.getValue();
                Method setMethod = properties.get(key).setMethod;
                if (value instanceof JsonObject) {
                    Object o = toBean((JsonObject) value, field.getType());
                    setMethod.invoke(bean, o);
                } else if (value instanceof JsonArray) {
                    String typename = ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[0].getTypeName();
                    List<?> l = toList((JsonArray) value, Class.forName(typename));
                    setMethod.invoke(bean, l);
                } else if (value == null) {
                    //nothing to do
                } else if (value instanceof String) {
                    Class<?> fieldType = field.getType();
                    if (fieldType == String.class) {
                        setMethod.invoke(bean, value);
                    } else throw new UnsupportedMapTypeException();
                } else if (value instanceof Boolean) {
                    setMethod.invoke(bean, value);
                } else if (value instanceof Long) {
                    Class<?> fieldType = field.getType();
                    if (fieldType == Integer.class || fieldType == int.class) {
                        setMethod.invoke(bean, ((Long) value).intValue());
                    } else if (fieldType == Long.class || fieldType == long.class) {
                        setMethod.invoke(bean, value);
                    } else throw new UnsupportedMapTypeException();
                } else if (value instanceof Double) {
                    Class<?> fieldType = field.getType();
                    if (fieldType == Float.class || fieldType == float.class) {
                        setMethod.invoke(bean, ((Double) value).floatValue());
                    } else if (fieldType == Double.class || fieldType == double.class) {
                        setMethod.invoke(bean, value);
                    } else throw new UnsupportedMapTypeException();
                }
            }
            return bean;
        } catch (InstantiationException | InvocationTargetException | NoSuchMethodException | IllegalAccessException | NoSuchFieldException | ClassNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static <T> List<T> toList(JsonArray jsonArray, Class<T> clazz) {
        List<T> list = new ArrayList<>();
        for (Object value : jsonArray) {
            if (value instanceof JsonObject) {
                T o = toBean((JsonObject) value, clazz);
                list.add(o);
            } else if (value instanceof JsonArray) {
                // todo
            } else if (value == null) {
                list.add(null);
            } else {
                if (clazz == Integer.class || clazz == int.class) {
                    Integer v = ((Long) value).intValue();
                    list.add((T) v);
                } else if (clazz == Long.class || clazz == long.class) {
                    list.add((T) value);
                } else {
                    list.add((T) value);
                }
            }
        }
        return list;
    }

    private static Map<String, ObjMethod> getProperties(Class<?> clazz) {
        Map<String, ObjMethod> properties = new HashMap<>();
        for (Field field : clazz.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            String fieldName = field.getName();
            Class<?> fieldType = field.getType();
            String fieldNameHeaderUpper = fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);
            String getMethodName = (fieldType == boolean.class || fieldType == Boolean.class ? "is" : "get")
                    + fieldNameHeaderUpper;
            String setMethodName = "set" + fieldNameHeaderUpper;

            Method getMethod;
            Method setMethod;
            try {
                setMethod = clazz.getMethod(setMethodName, fieldType);
                getMethod = clazz.getMethod(getMethodName, (Class<?>[]) null);
            } catch (NoSuchMethodException e) {
                continue;
            }
            if (Modifier.isStatic(getMethod.getModifiers())) continue;
            if (!Modifier.isPublic(setMethod.getModifiers())) continue;
            properties.put(fieldName, new ObjMethod(getMethod, setMethod));
        }
        return properties;
    }

    private static boolean isSupportType(Class<?> clazz) {
        if (clazz.isPrimitive()) return true;
        for (Class<?> t : supportType) {
            if (clazz == t) return true;
        }
        if (clazz == List.class) return true;
        if (clazz == Map.class) return true;
        return false;
    }

    static class ObjMethod {
        Method getMethod;
        Method setMethod;

        public ObjMethod(Method getMethod, Method setMethod) {
            this.getMethod = getMethod;
            this.setMethod = setMethod;
        }
    }

}
