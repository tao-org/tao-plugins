package ro.cs.tao.eodata;

import ro.cs.eo.gdal.dataio.drivers.Dataset;
import ro.cs.eo.gdal.dataio.drivers.GDAL;
import ro.cs.eo.gdal.dataio.drivers.GDALConst;
import ro.cs.tao.configuration.ConfigurationManager;
import ro.cs.tao.configuration.ConfigurationProvider;
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
            metadata.setCrs(gdalDataset.getProjectionRef());
        }

        return metadata;
    }

    @Override
    public void setFileProcessFactory(FileProcessFactory factory) {

    }
}
