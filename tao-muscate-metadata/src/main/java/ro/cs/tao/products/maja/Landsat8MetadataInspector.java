package ro.cs.tao.products.maja;

import ro.cs.tao.eodata.Polygon2D;
import ro.cs.tao.eodata.enums.PixelType;
import ro.cs.tao.eodata.enums.SensorType;
import ro.cs.tao.eodata.metadata.DecodeStatus;
import ro.cs.tao.eodata.metadata.XmlMetadataInspector;
import ro.cs.tao.utils.FileUtilities;
import ro.cs.tao.utils.executors.FileProcessFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class Landsat8MetadataInspector extends XmlMetadataInspector {

    @Override
    public DecodeStatus decodeQualification(Path productPath) {
        if (!Files.exists(productPath)) {
            return DecodeStatus.UNABLE;
        }
        Path productFolderPath = Files.isRegularFile(productPath) ?  productPath.getParent() : productPath;
        try {
            new MajaLandsat8ProductHelper(productFolderPath);
            return DecodeStatus.INTENDED;
        } catch (Exception e) {
            return DecodeStatus.UNABLE;
        }
    }

    @Override
    public Metadata getMetadata(Path productPath) throws IOException {
        if (!Files.exists(productPath)) {
            return null;
        }
        Path productFolderPath = Files.isRegularFile(productPath) ?  productPath.getParent() : productPath;
        MajaLandsat8ProductHelper helper = new MajaLandsat8ProductHelper(productFolderPath);
        Metadata metadata = new Metadata();

        String metadataFileName = helper.getMetadataFileName();
        metadata.setEntryPoint(metadataFileName);
        metadata.setPixelType(PixelType.INT16);
        metadata.setSensorType(SensorType.OPTICAL);
        metadata.setProductType("Landsat8");
        metadata.setAquisitionDate(LocalDate.parse(helper.getSensingDate(), DateTimeFormatter.ofPattern("yyyyMMdd")).atStartOfDay());
        metadata.setSize(FileUtilities.folderSize(productFolderPath));
        metadata.addAttribute("relativeOrbit", helper.getOrbit());
        try {
            readDocument(productFolderPath.resolve(metadataFileName));
        } catch (Exception e) {
            logger.warning(String.format("Cannot read metadata %s. Reason: %s", metadataFileName, e.getMessage()));
            throw new IOException(e);
        }
        String lines = getValue("/Earth_Explorer_Header/Variable_Header/Specific_Product_Header/Image_Information/Size/Lines/text()");
        String columns = getValue("/Earth_Explorer_Header/Variable_Header/Specific_Product_Header/Image_Information/Size/Columns/text()");
        String utmZone = getValue("/Earth_Explorer_Header/Variable_Header/Specific_Product_Header/Product_Information/Geometry/Projection/text()");
        String x = getValue("/Earth_Explorer_Header/Variable_Header/Specific_Product_Header/Product_Information/Image_Geo_Coverage/Upper_Left_Corner/Long/text()");
        String y = getValue("/Earth_Explorer_Header/Variable_Header/Specific_Product_Header/Product_Information/Image_Geo_Coverage/Upper_Left_Corner/Lat/text()");
        if (lines != null && columns != null && x != null && y != null && utmZone != null) {
            int l = Integer.parseInt(lines);
            int c = Integer.parseInt(columns);
            double px = Double.parseDouble(x);
            double py = Double.parseDouble(y);
            metadata.setWidth(l);
            metadata.setHeight(c);
            metadata.setCrs("EPSG:32" + (py > 0 ? "6" : "7") + utmZone);
            Polygon2D polygon2D = new Polygon2D();
            polygon2D.append(px, py);
            polygon2D.append(Double.parseDouble(getValue("/Earth_Explorer_Header/Variable_Header/Specific_Product_Header/Product_Information/Image_Geo_Coverage/Upper_Right_Corner/Long/text()")),
                             Double.parseDouble(getValue("/Earth_Explorer_Header/Variable_Header/Specific_Product_Header/Product_Information/Image_Geo_Coverage/Upper_Right_Corner/Lat/text()")));
            polygon2D.append(Double.parseDouble(getValue("/Earth_Explorer_Header/Variable_Header/Specific_Product_Header/Product_Information/Image_Geo_Coverage/Lower_Left_Corner/Long/text()")),
                             Double.parseDouble(getValue("/Earth_Explorer_Header/Variable_Header/Specific_Product_Header/Product_Information/Image_Geo_Coverage/Lower_Left_Corner/Lat/text()")));
            polygon2D.append(Double.parseDouble(getValue("/Earth_Explorer_Header/Variable_Header/Specific_Product_Header/Product_Information/Image_Geo_Coverage/Lower_Right_Corner/Long/text()")),
                             Double.parseDouble(getValue("/Earth_Explorer_Header/Variable_Header/Specific_Product_Header/Product_Information/Image_Geo_Coverage/Lower_Right_Corner/Lat/text()")));
            polygon2D.append(px, py);
            metadata.setFootprint(polygon2D.toWKT(8));
        }

        return metadata;
    }

    @Override
    public void setFileProcessFactory(FileProcessFactory factory) {

    }
}
