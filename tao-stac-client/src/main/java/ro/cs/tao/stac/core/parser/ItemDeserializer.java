package ro.cs.tao.stac.core.parser;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.databind.DeserializationContext;
import ro.cs.tao.stac.core.model.*;
import ro.cs.tao.stac.core.model.extensions.ExtensionType;
import ro.cs.tao.stac.core.model.extensions.eo.EOExtension;
import ro.cs.tao.stac.core.model.extensions.eo.EoFields;
import ro.cs.tao.stac.core.model.extensions.projection.Centroid;
import ro.cs.tao.stac.core.model.extensions.projection.ProjExtension;
import ro.cs.tao.stac.core.model.extensions.projection.ProjFields;
import ro.cs.tao.stac.core.model.extensions.view.ViewExtension;
import ro.cs.tao.stac.core.model.extensions.view.ViewFields;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;

public class ItemDeserializer extends BaseDeserializer<Item> {
    public ItemDeserializer() {
        super(Item.class);
    }

    @Override
    public Item deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JacksonException {
        TreeNode root = p.readValueAsTree();
        final Item item = new Item();
        final Iterator<String> iterator = root.fieldNames();
        TreeNode child;
        List<String> extensions = getStringArray(root, "stac_extensions");
        if (extensions != null) {
            for (String extension : extensions) {
                ExtensionType extensionType = ExtensionType.fromURI(extension);
                if (extensionType != null) {
                    item.registerExtension(extensionType);
                }
            }
        }
        final EOExtension<Item> eoExt = item.getExtension(ExtensionType.EO);
        final ProjExtension<Item> projExt = item.getExtension(ExtensionType.PROJ);
        final ViewExtension<Item> viewExt = item.getExtension(ExtensionType.VIEW);
        while (iterator.hasNext()) {
            String field = iterator.next();
            child = root.get(field);
            switch (field) {
                case "stac_version":
                    item.setStac_version(getString(root, field));
                    break;
                case "type":
                    item.setType(ItemType.valueOf(getString(root, field)));
                    break;
                case "id":
                    item.setId(getString(root, field));
                    break;
                case "bbox":
                    item.setBbox(getDoubleArray1(root, field));
                    break;
                case "geometry":
                    item.setGeometry(Geometry.create(GeometryType.valueOf(getString(child, "type"))));
                    Geometry geometry = item.getGeometry();
                    if (geometry != null) {
                        switch (geometry.getRank()) {
                            case 1:
                                geometry.setCoordinates(getDoubleArray1(child, "coordinates"));
                                break;
                            case 2:
                                geometry.setCoordinates(getDoubleArray2(child, "coordinates"));
                                break;
                            case 3:
                                geometry.setCoordinates(getDoubleArray3(child, "coordinates"));
                                break;
                            case 4:
                                geometry.setCoordinates(getDoubleArray4(child, "coordinates"));
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
                            item.setDatetime(getDateTime(child, name));
                        }
                        // EO-extension fields, if present
                        if (eoExt != null && eoExt.isOfThisType(name)) {
                            setEoExtensionField(child, name, eoExt);
                        } else if (projExt != null && projExt.isOfThisType(name)) { // PROJ-extension fields, if present
                            setProjExtensionField(child, name, projExt);
                        } else if (viewExt != null && viewExt.isOfThisType(name)) { // VIEW-extension fields, if present
                            setViewExtensionField(child, name, viewExt);
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
                        EOExtension<Asset> aEoExt = null;
                        ProjExtension<Asset> aProjExt = null;
                        ViewExtension<Asset> aViewExt = null;
                        if (eoExt != null) {
                            asset.registerExtension(ExtensionType.EO);
                            aEoExt = asset.getExtension(ExtensionType.EO);
                        }
                        if (projExt != null) {
                            asset.registerExtension(ExtensionType.PROJ);
                            aProjExt = asset.getExtension(ExtensionType.PROJ);
                        }
                        if (viewExt != null) {
                            asset.registerExtension(ExtensionType.VIEW);
                            aViewExt = asset.getExtension(ExtensionType.VIEW);
                        }
                        TreeNode assetNode = child.get(name);
                        asset.setHref(getString(assetNode, "href"));
                        asset.setType(getString(assetNode, "type"));
                        asset.setTitle(getString(assetNode, "title"));
                        asset.setDescription(getString(assetNode, "description"));
                        asset.setRoles(getStringArray(assetNode, "roles"));
                        Iterator<String> assetFields = assetNode.fieldNames();
                        while (assetFields.hasNext()) {
                            final String assetField = assetFields.next();
                            if (aEoExt != null && aEoExt.isOfThisType(assetField)) {
                                setEoExtensionField(assetNode, assetField, aEoExt);
                            } else if (aProjExt != null && aProjExt.isOfThisType(assetField)) {
                                setProjExtensionField(assetNode, assetField, aProjExt);
                            } else if (aViewExt != null && aViewExt.isOfThisType(assetField)) {
                                setViewExtensionField(assetNode, assetField, aViewExt);
                            } else {
                                setUnknownField(assetNode, assetField, asset);
                            }
                        }
                        item.addAsset(name, asset);
                    }
                    break;
                default:
                    item.addField(field, child.toString());
                    break;
            }
        }

        return item;
    }

    private void setEoExtensionField(TreeNode parent, String name, EOExtension<?> extension) throws JsonProcessingException {
        if (EoFields.BANDS.equals(name)) {
            TreeNode arrNode = parent.get(name);
            if (arrNode != null) {
                for (int i = 0; i < arrNode.size(); i++) {
                    extension.addBand(new STACParser().parseBand(arrNode.get(i).toString()));
                }
            }
        } else if (EoFields.CLOUD_COVER.equals(name)) {
            extension.setCloud_cover(Double.parseDouble(parent.get(name).toString()));
        }
    }

    private void setProjExtensionField(TreeNode parent, String name, ProjExtension<?> extension) {
        if (ProjFields.CENTROID.equals(name)) {
            TreeNode node = parent.get(name);
            Centroid centroid = new Centroid();
            centroid.setLat(getDouble(node, "lat"));
            centroid.setLon(getDouble(node, "lon"));
            extension.setCentroid(centroid);
        } else if (ProjFields.EPSG.equals(name)) {
            extension.setEpsg(getInt(parent, name));
        } else if (ProjFields.PROJJSON.equals(name)) {
            extension.setProjjson(getString(parent, name));
        } else if (ProjFields.SHAPE.equals(name)) {
            extension.setShape(getDoubleArray1(parent, name));
        } else if (ProjFields.TRANSFORM.equals(name)) {
            extension.setTransform(getDoubleArray1(parent, name));
        } else if (ProjFields.WKT_2.equals(name)) {
            extension.setWkt2(getString(parent, name));
        }
    }

    private void setViewExtensionField(TreeNode node, String name, ViewExtension<?> extension) {
        if (ViewFields.AZIMUTH.equals(name)) {
            extension.setAzimuth(getDouble(node, name));
        } else if (ViewFields.INCIDENCE_ANGLE.equals(name)) {
            extension.setIncidence_angle(getDouble(node, name));
        } else if (ViewFields.OFF_NADIR.equals(name)) {
            extension.setOff_nadir(getDouble(node, name));
        } else if (ViewFields.SUN_AZIMUTH.equals(name)) {
            extension.setSun_azimuth(getDouble(node, name));
        } else if (ViewFields.SUN_ELEVATION.equals(name)) {
            extension.setSun_elevation(getDouble(node, name));
        }
    }

    private void setUnknownField(TreeNode parent, String name, Extensible target) throws JsonProcessingException {
        TreeNode node = parent.get(name);
        if (node.numberType() != null) {
            switch (node.numberType()) {
                case INT:
                    target.addField(name, getInt(parent, name));
                    break;
                case LONG:
                    target.addField(name, getLong(parent, name));
                    break;
                case FLOAT:
                case DOUBLE:
                    target.addField(name, getDouble(parent, name));
                    break;
            }
        } else {
            target.addField(name, tryGuessTypedValue(node));
        }
    }
}
