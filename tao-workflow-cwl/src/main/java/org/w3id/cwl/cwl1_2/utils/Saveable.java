package org.w3id.cwl.cwl1_2.utils;

import org.apache.commons.lang3.StringUtils;
import org.w3id.cwl.cwl1_2.ArraySchema;
import org.w3id.cwl.cwl1_2.CommandOutputBinding;
import org.w3id.cwl.cwl1_2.InputArraySchema;
import org.w3id.cwl.cwl1_2.OutputArraySchema;

import java.lang.reflect.Field;
import java.util.*;

public interface Saveable {
    public default Map<Object, Object> save(boolean top, String baseUrl, boolean relativeUris) {
        Map<Object, Object> objectList = new LinkedHashMap<>();
        for (Field f : this.getClass().getDeclaredFields()) {
            if (!f.getName().equalsIgnoreCase("loadingOptions_") && !f.getName().equalsIgnoreCase("extensionFields_")) {
                f.setAccessible(true);
                try {
                    if (f.get(this) != null) {
                        if (f.get(this) instanceof Optional<?>) {
                            objectList.put(f.getName(), ((Optional<?>) f.get(this)).get());
                        } else if (f.get(this).equals(f.get(this).toString().toUpperCase())) {
                            objectList.put(f.getName(), StringUtils.capitalize(f.get(this).toString().toLowerCase()));
                        } else {
                            objectList.put(f.getName(), f.get(this).toString());
                        }
                    }
                } catch (Exception e) {
                    System.out.println(e.getMessage() + ": " + e.getCause() );
                }
            }
        }
        System.out.println("Saveable Interface implementation of the save method!");
        return objectList;
    }

    public default Map<Object, Object> save(boolean top, String baseUrl){
        return save(top, baseUrl, true);
    }

    public default Map<Object, Object> save(boolean top) {
        return save(top, "");
    }

    public default Map<Object, Object> save() {
        return save(false);
    }

    /**
     * write input and output elements in a LinkedHashMap
     */
    public default Map<String, Object> treatInOut(java.util.List<Object> objects, String wfId) {
        Map<String, Object> objectList = new LinkedHashMap<>();
        try {
            for (Object object : objects) {
                Field[] fields = object.getClass().getDeclaredFields();
                Map<String, Object> temp = new LinkedHashMap<>();
                String inputId = "";
                for (Field field : fields) {
                    if (!field.getName().equalsIgnoreCase("loadingOptions_") && !field.getName().equalsIgnoreCase("extensionFields_")) {
                        field.setAccessible(true);
                        if (field.getName().equalsIgnoreCase("id")) {
                            inputId = ((Optional)field.get(object)).get().toString().replaceAll(wfId + "/", "");
                            if (inputId.contains("#")) {
                                int chIdx = inputId.indexOf("#");
                                inputId = inputId.substring(chIdx + 1);
                            }
                            if (wfId == null || wfId.equals("")) {
                                wfId = inputId;
                            }
                        } else if (field.getName().equalsIgnoreCase("type")) {
                            if (field.get(object) instanceof InputArraySchema || field.get(object) instanceof OutputArraySchema) {
                                temp.put(field.getName(), field.get(object).toString());
                            } else {
                                temp.put(field.getName(), field.get(object).toString());
                            }
                        } else if (field.get(object) != null) {
                            if (field.get(object) instanceof Optional<?> && ((Optional) field.get(object)).get() instanceof Saveable) {
                                temp.put(field.getName(), ((Saveable) ((Optional) field.get(object)).get()).save());
                            } else if (field.get(object) instanceof Optional<?>) {
                                if (((Optional) field.get(object)).get().toString().contains("#")) {
                                    int chIdx = ((Optional) field.get(object)).get().toString().indexOf("#") + 1;
                                    temp.put(field.getName(), ((Optional) field.get(object)).get().toString().substring(chIdx));
                                } else if (field.get(object).toString().equals(field.get(object).toString().toUpperCase())) {
                                    temp.put(field.getName(), StringUtils.capitalize(field.get(object).toString().toLowerCase()));
                                } else {
                                    temp.put(field.getName(), ((Optional) field.get(object)).get());
                                }
                            } else {
                                if (field.get(object).toString().contains("#")) {
                                    int chIdx = field.get(object).toString().indexOf("#") + 1;
                                    temp.put(field.getName(), field.get(object).toString().substring(chIdx).replaceAll(wfId + "/|" + wfId, "")
                                            .replaceAll(inputId + "/|" + inputId, ""));
                                } else if (field.get(object).toString().equals(field.get(object).toString().toUpperCase())) {
                                    temp.put(field.getName(), StringUtils.capitalize(field.get(object).toString().toLowerCase()));
                                } else {
                                    temp.put(field.getName(), field.get(object));
                                }
                            }
                        }
                    }
                }
                if (inputId != "") {
                    objectList.put(inputId, temp);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return objectList;
    }
}
