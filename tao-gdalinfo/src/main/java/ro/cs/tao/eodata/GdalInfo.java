package ro.cs.tao.eodata;

import ro.cs.eo.gdal.dataio.drivers.*;
import ro.cs.tao.configuration.ConfigurationManager;
import ro.cs.tao.configuration.ConfigurationProvider;
import ro.cs.tao.eodata.enums.PixelType;
import ro.cs.tao.eodata.metadata.DecodeStatus;
import ro.cs.tao.eodata.metadata.MetadataInspector;
import ro.cs.tao.products.landsat.Landsat8MetadataInspector;
import ro.cs.tao.products.sentinels.Sentinel1MetadataInspector;
import ro.cs.tao.products.sentinels.Sentinel2MetadataInspector;
import ro.cs.tao.utils.executors.FileProcessFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;


public class GdalInfo implements MetadataInspector {
    private static final boolean extractStatistics;
    private static final boolean extractHistogram;

    static {
        final ConfigurationProvider configurationProvider = ConfigurationManager.getInstance();
        extractStatistics = Boolean.parseBoolean(configurationProvider.getValue("extract.statistics", "true"));
        extractHistogram = Boolean.parseBoolean(configurationProvider.getValue("extract.histogram", "false"));
    }

    @Override
    public DecodeStatus decodeQualification(Path productPath) {
        return productPath == null || Files.notExists(productPath) || productPath.toString().endsWith(".dim")
               ? DecodeStatus.UNABLE : DecodeStatus.SUITABLE;
    }

    @Override
    public Metadata getMetadata(Path productPath) throws IOException {
        if (productPath == null) {
            return null;
        }
        Metadata metadata = null;
        try {
            metadata = new Sentinel2MetadataInspector().getMetadata(productPath);
        } catch (Exception notS2) {
            try {
                metadata = new Sentinel1MetadataInspector().getMetadata(productPath);
            } catch (Exception notS1) {
                try {
                    metadata = new Landsat8MetadataInspector().getMetadata(productPath);
                } catch (Exception ignored) {}
            }
        }
        if (metadata != null) {
            return metadata;
        }
        if (Files.isDirectory(productPath)) {
            // gdalinfo would work only on files
            return null;
        }
        GDAL.useExceptions();
        final Dataset gdalDataset = GDAL.open(productPath.toAbsolutePath().toString(), GDALConst.gaReadonly());
        if (gdalDataset == null) {
            throw new IllegalStateException("Fail to open Dataset for file: " + productPath + ". " + GDAL.getLastErrorMsg());
        }
        final Metadata gdalMetadata = new Metadata();
        String productId = gdalDataset.getDescription();
        if (productId.contains(File.separator)) {
            productId = productId.substring(productId.lastIndexOf(File.separator) + 1, productId.lastIndexOf('.'));
        }
        gdalMetadata.setProductId(productId);
        gdalMetadata.setEntryPoint(productPath.getFileName().toString());
        gdalMetadata.setSize(Files.size(productPath));
        gdalMetadata.setProductType(gdalDataset.getDriver().getLongName());
        Integer width = gdalDataset.getRasterXSize();
        gdalMetadata.setWidth(width);
        Integer height = gdalDataset.getRasterYSize();
        gdalMetadata.setHeight(height);
        String projectionRef = gdalDataset.getProjectionRef();
        if (projectionRef != null) {
            double[] geoTransform = new double[6];
            gdalDataset.getGeoTransform(geoTransform);
            final Polygon2D footprint = new Polygon2D();
            footprint.append(geoTransform[0] + geoTransform[1] * 0.0 + geoTransform[2] * 0.0,
                             geoTransform[3] + geoTransform[4] * 0.0 + geoTransform[5] * 0.0);
            footprint.append(geoTransform[0] + geoTransform[1] * 0.0 + geoTransform[2] * height,
                             geoTransform[3] + geoTransform[4] * 0.0 + geoTransform[5] * height);
            footprint.append(geoTransform[0] + geoTransform[1] * width + geoTransform[2] * height,
                             geoTransform[3] + geoTransform[4] * width + geoTransform[5] * height);
            footprint.append(geoTransform[0] + geoTransform[1] * width + geoTransform[2] * 0.0,
                             geoTransform[3] + geoTransform[4] * width + geoTransform[5] * 0.0);
            footprint.append(geoTransform[0] + geoTransform[1] * 0.0 + geoTransform[2] * 0.0,
                             geoTransform[3] + geoTransform[4] * 0.0 + geoTransform[5] * 0.0);
            gdalMetadata.setFootprint(footprint.toWKT());
        }
        if (gdalDataset.getSpatialRef() != null) {
            gdalMetadata.setCrs(gdalDataset.getSpatialRef().getAuthorityName(null) + ":" + gdalDataset.getSpatialRef().getAuthorityCode(null));
        }
        if (gdalDataset.getRasterCount() > 0) {
            Band gdalBand = gdalDataset.getRasterBand(1);
            int gdalDataType = gdalBand.getDataType();
            if (GDALConstConstants.gdtByte() == gdalDataType) {
                gdalMetadata.setPixelType(PixelType.UINT8);
            } else if (GDALConstConstants.gdtUint16() == gdalDataType || GDALConstConstants.gdtCInt16() == gdalDataType) {
                gdalMetadata.setPixelType(PixelType.UINT16);
            } else if (GDALConstConstants.gdtInt16() == gdalDataType) {
                gdalMetadata.setPixelType(PixelType.INT16);
            } else if (GDALConstConstants.gdtUint32() == gdalDataType || GDALConstConstants.gdtCInt32() == gdalDataType) {
                gdalMetadata.setPixelType(PixelType.UINT32);
            } else if (GDALConstConstants.gdtInt32() == gdalDataType) {
                gdalMetadata.setPixelType(PixelType.INT32);
            } else if (GDALConstConstants.gdtFloat32() == gdalDataType || GDALConstConstants.gdtCFloat32() == gdalDataType) {
                gdalMetadata.setPixelType(PixelType.FLOAT32);
            } else if (GDALConstConstants.gdtFloat64() == gdalDataType || GDALConstConstants.gdtCFloat64() == gdalDataType) {
                gdalMetadata.setPixelType(PixelType.FLOAT64);
            }
            double[] min = new double[1];
            double[] max = new double[1];
            double[] mean = new double[1];
            double[] stddev = new double[1];
            if (extractStatistics && gdalBand.getStatistics(true, true, min, max, mean, stddev).equals(GDALConstConstants.ceNone())) {
                gdalMetadata.addStatistic("min", min[0]);
                gdalMetadata.addStatistic("max", max[0]);
                gdalMetadata.addStatistic("mean", mean[0]);
                gdalMetadata.addStatistic("stdDev", stddev[0]);
            }
            int[] buckets = new int[256];
            if (extractHistogram && gdalBand.getHistogram(buckets).equals(GDALConstConstants.ceNone())) {
                gdalMetadata.setHistogram(buckets);
            }
            RasterAttributeTable gdalRasterAttributeTable = gdalBand.getDefaultRAT();
            if (gdalRasterAttributeTable != null) {
                int nrCols = gdalRasterAttributeTable.getColumnCount();
                int nrRows = gdalRasterAttributeTable.getRowCount();
                if (nrCols == 2) {
                    for (int i = 0; i < nrRows; i++) {
                        gdalMetadata.addAttribute(gdalRasterAttributeTable.getValueAsString(i, 0), gdalRasterAttributeTable.getValueAsString(i, 1));
                    }
                }
            }
            for (int i = 1; i <= gdalDataset.getRasterCount(); i++) {
                gdalMetadata.addControlSum("" + gdalDataset.getRasterBand(i).checksum());
            }
        }
        for (Object domain : gdalDataset.getMetadataDomainList()) {
            gdalDataset.getMetadataDict(domain.toString()).forEach((key, value) -> {
                if (key.toString().contains("DATETIME")) {
                    gdalMetadata.setAquisitionDate(LocalDateTime.parse(value.toString(), DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss")));
                } else {
                    gdalMetadata.addAttribute(key.toString(), value.toString());
                }
            });
        }
        return gdalMetadata;
    }

    @Override
    public void setFileProcessFactory(FileProcessFactory factory) {

    }
}
