package ro.cs.tao.products.sentinels;

import ro.cs.tao.eodata.Polygon2D;
import ro.cs.tao.eodata.enums.OrbitDirection;
import ro.cs.tao.eodata.enums.PixelType;
import ro.cs.tao.eodata.enums.SensorType;
import ro.cs.tao.eodata.metadata.DecodeStatus;
import ro.cs.tao.eodata.metadata.MetadataInspector;
import ro.cs.tao.utils.FileUtilities;
import ro.cs.tao.utils.executors.FileProcessFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Logger;

public class Sentinel5PMetadataInspector implements MetadataInspector {
    protected Logger logger = Logger.getLogger(getClass().getName());

    public Sentinel5PMetadataInspector() {
    }

    @Override
    public DecodeStatus decodeQualification(Path productPath) {
        if (!Files.exists(productPath)) {
            return DecodeStatus.UNABLE;
        }
        Path productFolderPath = Files.isRegularFile(productPath) ?  productPath.getParent() : productPath;
        try {
            Sentinel5PProductHelper helper = new Sentinel5PProductHelper(productFolderPath.getFileName().toString());
            return Files.exists(productFolderPath.resolve(helper.getMetadataFileName())) ? DecodeStatus.INTENDED : DecodeStatus.UNABLE;
        } catch (Exception e) {
            return DecodeStatus.UNABLE;
        }
    }

    @Override
    public Metadata getMetadata(Path productPath) throws IOException {
        if (decodeQualification(productPath) == DecodeStatus.UNABLE) {
            return null;
        }
        Path productFolderPath = Files.isRegularFile(productPath) ? productPath.getParent() : productPath;
        Sentinel3ProductHelper helper = new Sentinel3ProductHelper(productFolderPath.getFileName().toString());
        Metadata metadata = new Metadata();

        String metadataFileName = helper.getMetadataFileName();
        metadata.setEntryPoint(metadataFileName);
        metadata.setPixelType(PixelType.FLOAT32);
        metadata.setSensorType(SensorType.ATMOSPHERIC);
        metadata.setProductType("Sentinel5P");
        metadata.setCrs("EPSG:4326");
        metadata.setAquisitionDate(LocalDateTime.parse(helper.getSensingDate(), DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")));
        metadata.setSize(FileUtilities.folderSize(productFolderPath));
        try {
            readDocument(productFolderPath.resolve(metadataFileName), metadata);
        } catch (Exception e) {
            logger.warning(String.format("Cannot read metadata %s. Reason: %s", metadataFileName, e.getMessage()));
            throw new IOException(e);
        }
        return metadata;
    }

    @Override
    public void setFileProcessFactory(FileProcessFactory factory) {
        //NOOP - only intended for local access
    }

    private void readDocument(Path metadataFile, Metadata metadata) {

    }
}
