package ro.cs.tao.products.maja;

import ro.cs.tao.eodata.Polygon2D;
import ro.cs.tao.eodata.enums.PixelType;
import ro.cs.tao.eodata.enums.SensorType;
import ro.cs.tao.eodata.metadata.DecodeStatus;
import ro.cs.tao.eodata.metadata.MetadataInspector;
import ro.cs.tao.eodata.metadata.XmlMetadataInspector;
import ro.cs.tao.utils.FileUtilities;
import ro.cs.tao.utils.executors.FileProcessFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Sentinel2MetadataInspector extends XmlMetadataInspector {
    @Override
    public DecodeStatus decodeQualification(Path productPath) {
        if (!Files.exists(productPath)) {
            return DecodeStatus.UNABLE;
        }
        Path productFolderPath = Files.isRegularFile(productPath) ?  productPath.getParent() : productPath;
        try {
            new MajaSentinel2ProductHelper(productFolderPath);
            return DecodeStatus.INTENDED;
        } catch (Exception e) {
            return DecodeStatus.UNABLE;
        }
    }

    @Override
    public MetadataInspector.Metadata getMetadata(Path productPath) throws IOException {
        if (!Files.exists(productPath)) {
            return null;
        }
        Path productFolderPath = Files.isRegularFile(productPath) ?  productPath.getParent() : productPath;
        MajaSentinel2ProductHelper helper = new MajaSentinel2ProductHelper(productFolderPath);
        MetadataInspector.Metadata metadata = new MetadataInspector.Metadata();

        String metadataFileName = helper.getMetadataFileName();
        metadata.setEntryPoint(metadataFileName);
        metadata.setPixelType(PixelType.UINT16);
        metadata.setSensorType(SensorType.OPTICAL);
        metadata.setProductType("Sentinel2");
        metadata.setAquisitionDate(LocalDateTime.parse(helper.getSensingDate(), DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")));
        metadata.setSize(FileUtilities.folderSize(productFolderPath));
        metadata.addAttribute("relativeOrbit", helper.getOrbit());
        Path metadataPath = productFolderPath.resolve(helper.getGranuleFolder(helper.getTileIdentifier())).resolve(metadataFileName);
        try {
            readDocument(metadataPath);
        } catch (Exception e) {
            logger.warning(String.format("Cannot read metadata %s. Reason: %s", metadataFileName, e.getMessage()));
            throw new IOException(e);
        }
        String lines = getValue("/Muscate_Metadata_Document/Geoposition_Informations/Geopositioning/Group_Geopositioning_List/Group_Geopositioning[@group_id = 'R1']/NROWS/text()");
        String columns = getValue("/Muscate_Metadata_Document/Geoposition_Informations/Geopositioning/Group_Geopositioning_List/Group_Geopositioning[@group_id = 'R1']/NCOLS/text()");
        String crs = getValue("/Muscate_Metadata_Document/Geoposition_Informations/Coordinate_Reference_System/Horizontal_Coordinate_System/HORIZONTAL_CS_CODE/text()");
        if (lines != null && columns != null && crs != null) {
            int l = Integer.parseInt(lines);
            int c = Integer.parseInt(columns);
            metadata.setCrs("EPSG:" + crs);
            metadata.setWidth(l);
            metadata.setHeight(c);
            Polygon2D polygon2D = new Polygon2D();
            double x = Double.parseDouble(getValue("/Muscate_Metadata_Document/Geoposition_Informations/Geopositioning/Global_Geopositioning/Point[@name = 'upperLeft']/LON/text()"));
            double y = Double.parseDouble(getValue("/Muscate_Metadata_Document/Geoposition_Informations/Geopositioning/Global_Geopositioning/Point[@name = 'upperLeft']/LAT/text()"));
            polygon2D.append(x, y);
            polygon2D.append(Double.parseDouble(getValue("/Muscate_Metadata_Document/Geoposition_Informations/Geopositioning/Global_Geopositioning/Point[@name = 'upperRight']/LON/text()")),
                             Double.parseDouble(getValue("/Muscate_Metadata_Document/Geoposition_Informations/Geopositioning/Global_Geopositioning/Point[@name = 'upperRight']/LAT/text()")));
            polygon2D.append(Double.parseDouble(getValue("/Muscate_Metadata_Document/Geoposition_Informations/Geopositioning/Global_Geopositioning/Point[@name = 'lowerRight']/LON/text()")),
                             Double.parseDouble(getValue("/Muscate_Metadata_Document/Geoposition_Informations/Geopositioning/Global_Geopositioning/Point[@name = 'lowerRight']/LAT/text()")));
            polygon2D.append(Double.parseDouble(getValue("/Muscate_Metadata_Document/Geoposition_Informations/Geopositioning/Global_Geopositioning/Point[@name = 'lowerLeft']/LON/text()")),
                             Double.parseDouble(getValue("/Muscate_Metadata_Document/Geoposition_Informations/Geopositioning/Global_Geopositioning/Point[@name = 'lowerLeft']/LAT/text()")));
            polygon2D.append(x, y);
            metadata.setFootprint(polygon2D.toWKT(8));
        }
        return metadata;
    }

    @Override
    public void setFileProcessFactory(FileProcessFactory factory) {

    }
}
