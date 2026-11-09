package ro.cs.tao.stac.core.metadata;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.locationtech.jts.geom.Coordinate;
import ro.cs.tao.eodata.Attribute;
import ro.cs.tao.eodata.DataHandlingException;
import ro.cs.tao.eodata.EOProduct;
import ro.cs.tao.eodata.OutputDataHandler;
import ro.cs.tao.serialization.GeometryAdapter;
import ro.cs.tao.serialization.JsonMapper;
import ro.cs.tao.stac.core.model.*;
import ro.cs.tao.stac.core.model.extensions.Extension;
import ro.cs.tao.stac.core.model.extensions.ExtensionType;
import ro.cs.tao.stac.core.model.extensions.fileinfo.FileInfoExtension;
import ro.cs.tao.stac.core.model.extensions.processing.Expression;
import ro.cs.tao.stac.core.model.extensions.processing.Level;
import ro.cs.tao.stac.core.model.extensions.processing.ProcessingExtension;
import ro.cs.tao.stac.core.model.extensions.processing.ProcessingFields;
import ro.cs.tao.stac.core.model.extensions.projection.ProjExtension;
import ro.cs.tao.stac.core.parser.STACParser;
import ro.cs.tao.utils.DateUtils;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.Logger;

/**
 * Specialization of an output data handler that creates STAC item descriptors for EOProducts.
 *
 * @author Cosmin Cara
 * @since 1.4.3
 */
public class ProductMetadataWriter implements OutputDataHandler<EOProduct> {
    private final Logger logger = Logger.getLogger(getClass().getName());

    @Override
    public Class<EOProduct> isIntendedFor() { return EOProduct.class; }

    @Override
    public int getPriority() { return 1; }

    @Override
    public List<EOProduct> handle(List<EOProduct> list) throws DataHandlingException {
        if (list != null) {
            final GeometryAdapter adapter = new GeometryAdapter();
            for (EOProduct product : list) {
                Item item = new Item();
                item.setId(product.getId());
                try {
                    final org.locationtech.jts.geom.Geometry productFootprint = adapter.marshal(product.getGeometry());
                    Geometry geometry;
                    final int geoCount = productFootprint.getNumGeometries();
                    if (geoCount > 1) {
                        geometry = Geometry.create(GeometryType.MultiPolygon);
                    } else {
                        geometry = Geometry.create(GeometryType.Polygon);
                    }
                    final double[][][] coords = new double[geoCount][][];
                    double [][] points;
                    for (int i = 0; i <geoCount; i++) {
                        final org.locationtech.jts.geom.Geometry geometryN = productFootprint.getGeometryN(i);
                        final Coordinate[] coordinates = geometryN.getCoordinates();
                        points = new double[coordinates.length][2];
                        for (int j = 0; j < coordinates.length; j++) {
                            points[j][0] = coordinates[j].x;
                            points[j][1] = coordinates[j].y;
                        }
                        coords[i] = points;
                    }
                    geometry.setCoordinates(coords);
                    item.setGeometry(geometry);
                } catch (Exception e) {
                    logger.warning(e.getMessage());
                }
                item.setDatetime(product.getAcquisitionDate());
                final String crs = product.getCrs();
                if (crs != null) {
                    final Extension<?> extension = item.registerExtension(ExtensionType.PROJ);
                    if (extension != null) {
                        ProjExtension<?> proj = ((ProjExtension<?>) extension);
                        proj.setEpsg(Integer.parseInt(crs.substring(crs.indexOf(':') + 1)));
                    }
                }
                final Map<String, Object> map = new LinkedHashMap<>();
                map.put("name", product.getName());
                map.put("sensor", product.getSatelliteName());
                map.put("type", product.getProductType());
                final List<Attribute> attributes = product.excludeAttributes("processing:");
                if (attributes != null) {
                    attributes.sort(Comparator.comparing(Attribute::getName));
                    for (Attribute attr : attributes) {
                        map.put(attr.getName(), attr.getValue());
                    }
                }
                item.setFields(map);
                final List<Attribute> processingAttributes = product.filterAttributes("processing:");
                if (processingAttributes != null) {
                    processingAttributes.sort(Comparator.comparing(Attribute::getName));
                    final Extension<?> extension = item.registerExtension(ExtensionType.PROCESSING);
                    for (Attribute attr : processingAttributes) {
                        try {
                            ProcessingExtension<?> processing = ((ProcessingExtension<?>) extension);
                            switch (attr.getName()) {
                                case ProcessingFields.PROCESSING_EXPRESSION:
                                    processing.setExpression(new STACParser().parse(attr.getValue(), Expression.class));
                                    break;
                                case ProcessingFields.PROCESSING_LINEAGE:
                                    processing.setLineage(attr.getValue());
                                    break;
                                case ProcessingFields.PROCESSING_LEVEL:
                                    processing.setLevel(Level.valueOf(attr.getValue()));
                                    break;
                                case ProcessingFields.PROCESSING_FACILITY:
                                    processing.setFacility(attr.getValue());
                                    break;
                                case ProcessingFields.PROCESSING_DATETIME:
                                    processing.setDateTime(DateUtils.parseDateTime(attr.getValue()));
                                    break;
                                case ProcessingFields.PROCESSING_VERSION:
                                    processing.setVersion(attr.getValue());
                                    break;
                                case ProcessingFields.PROCESSING_SOFTWARE:
                                    processing.setSoftware(JsonMapper.instance()
                                                                     .readerFor(LinkedHashMap.class)
                                                                     .readValue(attr.getValue()));
                                    break;
                            }
                        } catch (Exception e) {
                            logger.warning(e.getMessage());
                        }
                    }
                }
                final List<Link> links = new ArrayList<>();
                final String location = product.getLocation();
                Path path;
                try {
                    path = Paths.get(URI.create(location));
                } catch (Exception e) {
                    path = Paths.get(location);
                }
                Extension<?> extension = item.registerExtension(ExtensionType.FILE);
                FileInfoExtension<?> fileInfo = ((FileInfoExtension<?>) extension);
                fileInfo.setSize(product.getApproximateSize());
                fileInfo.setLocalPath(location);
                Path metadataPath = (Files.isDirectory(path) ? path : path.getParent()).resolve(product.getName() + ".json");
                links.add(createLink(path.toUri().toString(), "self"));
                links.add(createLink(metadataPath.toUri().toString(), "cannonical", "application/json"));
                links.add(createLink(path.getParent().toUri().toString(), "parent"));
                item.setLinks(links);
                final Map<String, Asset> assets = new LinkedHashMap<>();
                Asset asset = new Asset();
                asset.setTitle("PRODUCT");
                asset.setHref(path.toUri().toString());
                asset.setDescription(product.getName());
                assets.put(asset.getTitle(), asset);
                if (product.getQuicklookLocation() != null) {
                    asset = new Asset();
                    asset.setTitle("QUICKLOOK");
                    asset.setHref(product.getQuicklookLocation());
                    assets.put(asset.getTitle(), asset);
                }
                item.setAssets(assets);
                item.setStac_version("1.0.0");
                item.setType(ItemType.Feature);
                try {
                    final ObjectMapper mapper = new ObjectMapper();
                    mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
                    Files.write(metadataPath, mapper.writerFor(Item.class).writeValueAsBytes(item));
                } catch (IOException e) {
                    logger.severe(e.getMessage());
                }
            }
        }
        return list;
    }

    private Link createLink(String href, String rel) {
        final Link link = new Link();
        link.setRel(rel);
        link.setHref(href);
        return link;
    }

    private Link createLink(String href, String rel, String type) {
        final Link link = new Link();
        link.setRel(rel);
        link.setHref(href);
        link.setType(type);
        return link;
    }
}
