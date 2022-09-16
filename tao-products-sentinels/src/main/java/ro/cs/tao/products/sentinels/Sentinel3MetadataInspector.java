package ro.cs.tao.products.sentinels;

import ro.cs.tao.eodata.Polygon2D;
import ro.cs.tao.eodata.enums.OrbitDirection;
import ro.cs.tao.eodata.enums.PixelType;
import ro.cs.tao.eodata.enums.SensorType;
import ro.cs.tao.eodata.metadata.DecodeStatus;
import ro.cs.tao.eodata.metadata.XmlMetadataInspector;
import ro.cs.tao.utils.FileUtilities;
import ro.cs.tao.utils.executors.FileProcessFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Sentinel3MetadataInspector extends XmlMetadataInspector {

    private static final String XPATH_ORBIT_DIRECTION = "/XFDU/metadataSection/metadataObject[@ID='measurementOrbitReference']/metadataWrap/xmlData/orbitReference/passNumber/@groundTrackDirection";
    private static final String XPATH_COORDINATES = "/XFDU/metadataSection/metadataObject[@ID='measurementFrameSet']/metadataWrap/xmlData/frameSet/footPrint/posList";
    private static final String XPATH_CHECKSUM = "/XFDU/dataObjectSection/dataObject/byteStream/checksum/text()";
    private static final String XPATH_FAMILY = "/XFDU/metadataSection/metadataObject[@ID='platform']/metadataWrap/xmlData/platform/instrument/familyName/@abbreviation";

    private static final Map<String, String> FAMILIES_XPATH_ROWS = new HashMap<>();
    private static final Map<String, String> FAMILIES_XPATH_COLUMNS = new HashMap<>();

    static {
        FAMILIES_XPATH_ROWS.put("SLSTR", "/XFDU/metadataSection/metadataObject[@ID='slstrProductInformation']/metadataWrap/xmlData/slstrProductInformation/nadirImageSize/rows/text()");
        FAMILIES_XPATH_ROWS.put("OLCI", "/XFDU/metadataSection/metadataObject[@ID='olciProductInformation']/metadataWrap/xmlData/olciProductInformation/imageSize/columns/text()");

        FAMILIES_XPATH_COLUMNS.put("SLSTR", "/XFDU/metadataSection/metadataObject[@ID='slstrProductInformation']/metadataWrap/xmlData/slstrProductInformation/nadirImageSize/columns/text()");
        FAMILIES_XPATH_COLUMNS.put("OLCI", "/XFDU/metadataSection/metadataObject[@ID='olciProductInformation']/metadataWrap/xmlData/olciProductInformation/imageSize/columns/text()");
    }

    @Override
    public void setFileProcessFactory(FileProcessFactory factory) {
        //NOOP - only intended for local access
    }

    @Override
    public DecodeStatus decodeQualification(Path productPath) {
        if (!Files.exists(productPath)) {
            return DecodeStatus.UNABLE;
        }
        Path productFolderPath = Files.isRegularFile(productPath) ?  productPath.getParent() : productPath;
        try {
            Sentinel3ProductHelper helper = new Sentinel3ProductHelper(productFolderPath.getFileName().toString());
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
        metadata.setPixelType(PixelType.UINT16);
        metadata.setSensorType(SensorType.OPTICAL);
        metadata.setProductType("Sentinel3");
        metadata.setCrs("EPSG:4326");
        metadata.setAquisitionDate(LocalDateTime.parse(helper.getSensingDate(), DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")));
        metadata.setSize(FileUtilities.folderSize(productFolderPath));
        try {
            readDocument(productFolderPath.resolve(metadataFileName));
        } catch (Exception e) {
            logger.warning(String.format("Cannot read metadata %s. Reason: %s", metadataFileName, e.getMessage()));
            throw new IOException(e);
        }
        metadata.setOrbitDirection(OrbitDirection.valueOf(getValue(XPATH_ORBIT_DIRECTION).toUpperCase()));
        String points = getValue(XPATH_COORDINATES);
        if (points != null) {
            String[] coords = points.trim().split(" ");
            Polygon2D polygon2D = new Polygon2D();
            for (int i = 0; i < coords.length - 1; i += 2) {
                polygon2D.append(Double.parseDouble(coords[i + 1]), Double.parseDouble(coords[i]));
            }
            metadata.setFootprint(polygon2D.toWKT());
        } else {
            logger.warning(String.format("Cannot extract product footprint from metadata [product=%s,xPath=%s]", productPath, XPATH_COORDINATES));
        }
        List<String> checkSums = getValues(XPATH_CHECKSUM);
        for (String checkSum : checkSums) {
            metadata.addControlSum(checkSum);
        }
        final String family = getValue(XPATH_FAMILY);
        if (family != null) {
            final String xpathColumns = FAMILIES_XPATH_COLUMNS.get(family);
            final String xpathRows = FAMILIES_XPATH_ROWS.get(family);
            if (xpathColumns != null) {
                metadata.setWidth(Integer.parseInt(getValue(xpathRows)));
            }
            if (xpathRows != null) {
                metadata.setHeight(Integer.parseInt(getValue(xpathRows)));
            }
        }

        return metadata;
    }
}
