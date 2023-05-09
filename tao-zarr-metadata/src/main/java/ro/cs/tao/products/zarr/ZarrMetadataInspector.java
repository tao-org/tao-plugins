package ro.cs.tao.products.zarr;

import org.geotools.referencing.CRS;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import ro.cs.tao.eodata.Polygon2D;
import ro.cs.tao.eodata.enums.PixelType;
import ro.cs.tao.eodata.enums.SensorType;
import ro.cs.tao.eodata.metadata.DecodeStatus;
import ro.cs.tao.eodata.metadata.MetadataInspector;
import ro.cs.tao.utils.FileUtilities;
import ro.cs.tao.utils.executors.FileProcessFactory;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReader;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Logger;

public class ZarrMetadataInspector implements MetadataInspector {
    private final Logger logger = Logger.getLogger(ZarrMetadataInspector.class.getName());

    @Override
    public DecodeStatus decodeQualification(Path productPath) {
        return productPath != null && Files.isDirectory(productPath) && Files.exists(productPath.resolve(".zmetadata")) ?
                DecodeStatus.INTENDED : DecodeStatus.UNABLE;
    }

    @Override
    public Metadata getMetadata(Path productPath) throws IOException {
        if (decodeQualification(productPath) == DecodeStatus.UNABLE) {
            return null;
        }
        try (BufferedReader reader = Files.newBufferedReader(productPath.resolve(".zmetadata"));
             JsonReader jsonReader = Json.createReader(reader)) {
            JsonObject root = jsonReader.readObject();
            final Metadata metadata = new Metadata();
            JsonObject jsonObject = root.getJsonObject("metadata");
            if (jsonObject != null && (jsonObject = jsonObject.getJsonObject(".zattrs")) != null) {
                final String crsString = jsonObject.getString("geospatial_bounds_crs");
                CoordinateReferenceSystem crs = null;
                try {
                    crs = CRS.parseWKT(crsString);
                } catch (Exception e1) {
                    final int idx = crsString.lastIndexOf("ID[");
                    if (idx > 0) {
                        int idx1 = crsString.indexOf("\"", idx) + 1;
                        int idx2 = crsString.indexOf("\"", idx1);
                        try {
                            crs = CRS.decode(crsString.substring(idx1, idx2) + ":"
                                    + crsString.substring(idx2 + 2, crsString.indexOf("]", idx2)), true);
                        } catch (Exception e2) {
                            logger.warning("Cannot determine CRS");
                        }
                    }
                }
                if (crs != null && crs.getIdentifiers() != null && crs.getIdentifiers().size() > 0) {
                    final String bounds = jsonObject.getString("geospatial_bounds");
                    metadata.setFootprint(bounds);
                    crs.getIdentifiers().stream()
                            .findFirst().ifPresent(identifier -> metadata.setCrs(identifier.getCodeSpace() + ":" + identifier.getCode()));
                }
                final Polygon2D wgsPolygon = new Polygon2D();
                wgsPolygon.append(jsonObject.getJsonNumber("geospatial_lon_min").doubleValue(),
                                  jsonObject.getJsonNumber("geospatial_lat_max").doubleValue());
                wgsPolygon.append(jsonObject.getJsonNumber("geospatial_lon_max").doubleValue(),
                                  jsonObject.getJsonNumber("geospatial_lat_max").doubleValue());
                wgsPolygon.append(jsonObject.getJsonNumber("geospatial_lon_max").doubleValue(),
                                  jsonObject.getJsonNumber("geospatial_lat_min").doubleValue());
                wgsPolygon.append(jsonObject.getJsonNumber("geospatial_lon_min").doubleValue(),
                                  jsonObject.getJsonNumber("geospatial_lat_min").doubleValue());
                wgsPolygon.append(jsonObject.getJsonNumber("geospatial_lon_min").doubleValue(),
                                  jsonObject.getJsonNumber("geospatial_lat_max").doubleValue());
                metadata.setWgs84footprint(wgsPolygon.toWKT(8));
                metadata.setSize(FileUtilities.folderSize(productPath));
                metadata.setPixelType(PixelType.FLOAT32);
                metadata.setProductType("zarr");
                metadata.setSensorType(SensorType.UNKNOWN);
                metadata.setAquisitionDate(LocalDateTime.parse(jsonObject.getString("time_coverage_start"), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                JsonObject object = root.getJsonObject("metadata").getJsonObject("x/.zarray");
                if (object != null) {
                    final JsonArray array = object.getJsonArray("chunks");
                    metadata.setWidth(array.getJsonNumber(0).intValue());
                }
                object = root.getJsonObject("metadata").getJsonObject("y/.zarray");
                if (object != null) {
                    final JsonArray array = object.getJsonArray("chunks");
                    metadata.setHeight(array.getJsonNumber(0).intValue());
                }
                metadata.setProductId(productPath.getFileName().toString());
            }
            return metadata;
        } catch (Exception e) {
            logger.severe(e.getMessage());
        }
        return null;
    }

    @Override
    public void setFileProcessFactory(FileProcessFactory factory) {
        // NO-OP
    }
}
