package ro.cs.tao.stac.core.parser;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import ro.cs.tao.stac.core.model.*;
import ro.cs.tao.stac.core.model.extensions.Extension;
import ro.cs.tao.stac.core.model.extensions.ExtensionType;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Deserialization from JSON class for an Item.
 *
 * @author Cosmin Cara
 */
public class ItemDeserializer extends StdDeserializer<Item> {
    public ItemDeserializer() {
        super(Item.class);
    }

    @Override
    public Item deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JacksonException {
        TreeNode root = p.readValueAsTree();
        final Item item = new Item();
        final Iterator<String> iterator = root.fieldNames();
        TreeNode child;
        List<String> extensions = JsonValueHelper.getStringArray(root, "stac_extensions");
        boolean hasExtensions = false;
        final Map<String, Extension<?>> itemExtensions = new HashMap<>();
        final Map<String, ExtensionType> assetExtensionTypes = new HashMap<>();
        if (extensions != null) {
            for (String extension : extensions) {
                ExtensionType extensionType = ExtensionType.fromURI(extension);
                if (extensionType != null) {
                    Extension<?> ext = item.registerExtension(extensionType);
                    itemExtensions.put(ext.getPrefix(), ext);
                    assetExtensionTypes.put(ext.getPrefix(), extensionType);
                    hasExtensions = true;
                }
            }
        }
        if (!hasExtensions) {
            for (ExtensionType type : ExtensionType.values()) {
                Extension<?> ext = item.registerExtension(type);
                itemExtensions.put(ext.getPrefix(), ext);
                assetExtensionTypes.put(ext.getPrefix(), type);
            }
        }
        final Map<String, Extension<?>> assetExtensions = new HashMap<>();
        while (iterator.hasNext()) {
            String field = iterator.next();
            child = root.get(field);
            switch (field) {
                case "stac_version":
                    item.setStac_version(JsonValueHelper.getString(root, field));
                    break;
                case "type":
                    item.setType(ItemType.valueOf(JsonValueHelper.getString(root, field)));
                    break;
                case "id":
                    item.setId(JsonValueHelper.getString(root, field));
                    break;
                case "bbox":
                    item.setBbox(JsonValueHelper.getDoubleArray1(root, field));
                    break;
                case "geometry":
                    item.setGeometry(Geometry.create(GeometryType.valueOf(JsonValueHelper.getString(child, "type"))));
                    Geometry geometry = item.getGeometry();
                    if (geometry != null) {
                        switch (geometry.getRank()) {
                            case 1:
                                geometry.setCoordinates(JsonValueHelper.getDoubleArray1(child, "coordinates"));
                                break;
                            case 2:
                                geometry.setCoordinates(JsonValueHelper.getDoubleArray2(child, "coordinates"));
                                break;
                            case 3:
                                geometry.setCoordinates(JsonValueHelper.getDoubleArray3(child, "coordinates"));
                                break;
                            case 4:
                                geometry.setCoordinates(JsonValueHelper.getDoubleArray4(child, "coordinates"));
                                break;
                        }
                    }
                    break;
                case "links":
                    if (child.isArray()) {
                        for (int i = 0; i < child.size(); i++) {
                            item.addLink(new STACParser().parseLink(child.get(i).toString()));
                        }
                    }
                    break;
                case "properties":
                    Iterator<String> namesIterator = child.fieldNames();
                    while (namesIterator.hasNext()) {
                        final String name = namesIterator.next();
                        if (Item.DATETIME.equals(name)) {
                            item.setDatetime(JsonValueHelper.getDateTime(child, name));
                        }
                        String prefix;
                        if (name.contains(":") &&
                                itemExtensions.containsKey((prefix = name.substring(0, name.indexOf(':') + 1)))) {
                            // If there is an associated extension, it can handle the field
                            itemExtensions.get(prefix).extractField(child, name);
                        } else { // Non-core fields from other extensions not known
                            setUnknownField(child, name, item);
                        }
                    }
                    break;
                case "assets":
                    namesIterator = child.fieldNames();
                    while (namesIterator.hasNext()) {
                        final String name = namesIterator.next();
                        final Asset asset = new Asset();
                        if (assetExtensions.isEmpty()){
                            assetExtensionTypes.values().forEach(e -> {
                                Extension<?> extension = asset.registerExtension(e);
                                assetExtensions.put(extension.getPrefix(), extension);
                            });
                        }
                        TreeNode assetNode = child.get(name);
                        asset.setHref(JsonValueHelper.getString(assetNode, "href"));
                        asset.setType(JsonValueHelper.getString(assetNode, "type"));
                        asset.setTitle(JsonValueHelper.getString(assetNode, "title"));
                        asset.setDescription(JsonValueHelper.getString(assetNode, "description"));
                        asset.setRoles(JsonValueHelper.getStringArray(assetNode, "roles"));
                        Iterator<String> assetFields = assetNode.fieldNames();
                        while (assetFields.hasNext()) {
                            final String assetField = assetFields.next();
                            if (!Asset.coreFieldNames().contains(assetField)) {
                                String prefix;
                                if (assetField.contains(":") &&
                                        assetExtensions.containsKey((prefix = assetField.substring(0, assetField.indexOf(':') + 1)))) {
                                    assetExtensions.get(prefix).extractField(assetNode, assetField);
                                } else {
                                    setUnknownField(assetNode, assetField, asset);
                                }
                            }
                        }
                        item.addAsset(name, asset);
                        assetExtensions.clear();
                    }
                    break;
                default:
                    item.addField(field, child.toString());
                    break;
            }
        }

        return item;
    }

    private void setUnknownField(TreeNode parent, String name, Extensible target) throws JsonProcessingException {
        TreeNode node = parent.get(name);
        if (node.numberType() != null) {
            switch (node.numberType()) {
                case INT:
                    target.addField(name, JsonValueHelper.getInt(parent, name));
                    break;
                case LONG:
                    target.addField(name, JsonValueHelper.getLong(parent, name));
                    break;
                case FLOAT:
                case DOUBLE:
                    target.addField(name, JsonValueHelper.getDouble(parent, name));
                    break;
            }
        } else {
            target.addField(name, JsonValueHelper.tryGuessTypedValue(node));
        }
    }
}
