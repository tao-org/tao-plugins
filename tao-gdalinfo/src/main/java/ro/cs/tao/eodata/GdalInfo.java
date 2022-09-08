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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;


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
        return productPath == null || Files.notExists(productPath) ? DecodeStatus.UNABLE : DecodeStatus.SUITABLE;
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
        metadata = new Metadata();
        metadata.setEntryPoint(productPath.getFileName().toString());
        metadata.setSize(Files.size(productPath));
        metadata.setProductType(gdalDataset.getDriver().getLongName());
        metadata.setWidth(gdalDataset.getRasterXSize());
        metadata.setHeight(gdalDataset.getRasterYSize());
        if (gdalDataset.getProjectionRef() != null) {
            metadata.setFootprint(gdalDataset.getProjectionRef());
        }
        if (gdalDataset.getSpatialRef() != null) {
            metadata.setCrs("EPSG:" + gdalDataset.getSpatialRef().getAuthorityCode(null));
        }
        if (gdalDataset.getRasterCount() > 0) {
            Band gdalBand = gdalDataset.getRasterBand(1);
            int gdalDataType = gdalBand.getDataType();
            if (GDALConstConstants.gdtByte() == gdalDataType) {
                metadata.setPixelType(PixelType.UINT8);
            } else if (GDALConstConstants.gdtUint16() == gdalDataType || GDALConstConstants.gdtCInt16() == gdalDataType) {
                metadata.setPixelType(PixelType.UINT16);
            } else if (GDALConstConstants.gdtInt16() == gdalDataType) {
                metadata.setPixelType(PixelType.INT16);
            } else if (GDALConstConstants.gdtUint32() == gdalDataType || GDALConstConstants.gdtCInt32() == gdalDataType) {
                metadata.setPixelType(PixelType.UINT32);
            } else if (GDALConstConstants.gdtInt32() == gdalDataType) {
                metadata.setPixelType(PixelType.INT32);
            } else if (GDALConstConstants.gdtFloat32() == gdalDataType || GDALConstConstants.gdtCFloat32() == gdalDataType) {
                metadata.setPixelType(PixelType.FLOAT32);
            } else if (GDALConstConstants.gdtFloat64() == gdalDataType || GDALConstConstants.gdtCFloat64() == gdalDataType) {
                metadata.setPixelType(PixelType.FLOAT64);
            }
            double[] min = new double[1];
            double[] max = new double[1];
            double[] mean = new double[1];
            double[] stddev = new double[1];
            if (extractStatistics && gdalBand.getStatistics(true, false, min, max, mean, stddev).equals(GDALConstConstants.ceNone())) {
                metadata.addStatistic("min", min[0]);
                metadata.addStatistic("max", max[0]);
                metadata.addStatistic("mean", mean[0]);
                metadata.addStatistic("stdDev", stddev[0]);
            }
            int[] buckets = new int[256];
            if (extractHistogram && gdalBand.getHistogram(buckets).equals(GDALConstConstants.ceNone())) {
                metadata.setHistogram(buckets);
            }
            RasterAttributeTable gdalRasterAttributeTable = gdalBand.getDefaultRAT();
            if (gdalRasterAttributeTable != null) {
                int nrCols = gdalRasterAttributeTable.getColumnCount();
                int nrRows = gdalRasterAttributeTable.getRowCount();
                if (nrCols == 2) {
                    for (int i = 0; i < nrRows; i++) {
                        metadata.addAttribute(gdalRasterAttributeTable.getValueAsString(i, 0), gdalRasterAttributeTable.getValueAsString(i, 1));
                    }
                }
            }
            for (int i = 1; i <= gdalDataset.getRasterCount(); i++) {
                metadata.addControlSum("" + gdalDataset.getRasterBand(i).checksum());
            }
        }
        return metadata;
    }

    @Override
    public void setFileProcessFactory(FileProcessFactory factory) {

    }
}
