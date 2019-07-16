package ro.cs.tao.products.maccs;

import org.opengis.referencing.FactoryException;
import org.opengis.referencing.operation.TransformException;
import ro.cs.tao.eodata.Polygon2D;
import ro.cs.tao.eodata.enums.PixelType;
import ro.cs.tao.eodata.enums.SensorType;
import ro.cs.tao.eodata.metadata.DecodeStatus;
import ro.cs.tao.eodata.metadata.XmlMetadataInspector;
import ro.cs.tao.eodata.util.Conversions;
import ro.cs.tao.utils.FileUtilities;

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
            new MaccsSentinel2ProductHelper(productFolderPath);
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
        MaccsSentinel2ProductHelper helper = new MaccsSentinel2ProductHelper(productFolderPath);
        Metadata metadata = new Metadata();

        String metadataFileName = helper.getMetadataFileName();
        metadata.setEntryPoint(metadataFileName);
        metadata.setPixelType(PixelType.UINT16);
        metadata.setSensorType(SensorType.OPTICAL);
        metadata.setProductType("Sentinel2");
        metadata.setAquisitionDate(LocalDateTime.parse(helper.getSensingDate(), DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")));
        metadata.setSize(FileUtilities.folderSize(productFolderPath));
        try {
            readDocument(productFolderPath.resolve(metadataFileName));
        } catch (Exception e) {
            logger.warning(String.format("Cannot read metadata %s. Reason: %s", metadataFileName, e.getMessage()));
            throw new IOException(e);
        }
        String lines = getValue("/Earth_Explorer_Header/Variable_Header/Specific_Product_Header/Image_Information/List_of_Resolutions/Resolution[@r = '10']/Size/Lines/text()");
        String columns = getValue("/Earth_Explorer_Header/Variable_Header/Specific_Product_Header/Image_Information/List_of_Resolutions/Resolution[@r = '10']/Size/Columns/text()");
        String ulx = getValue("/Earth_Explorer_Header/Variable_Header/Specific_Product_Header/Image_Information/List_of_Resolutions/Resolution[@r = '10']/Geoposition/ULX/text()");
        String uly = getValue("/Earth_Explorer_Header/Variable_Header/Specific_Product_Header/Image_Information/List_of_Resolutions/Resolution[@r = '10']/Geoposition/ULY/text()");
        String xDim = getValue("/Earth_Explorer_Header/Variable_Header/Specific_Product_Header/Image_Information/List_of_Resolutions/Resolution[@r = '10']/Geoposition/XDIM/text()");
        String yDim = getValue("/Earth_Explorer_Header/Variable_Header/Specific_Product_Header/Image_Information/List_of_Resolutions/Resolution[@r = '10']/Geoposition/YDIM/text()");
        String crs = getValue("/Earth_Explorer_Header/Variable_Header/Specific_Product_Header/Geo_Referencing_Information/Product_Coverage/Geographic/Coordinate_Reference_System/Code/text()");
        if (lines != null && columns != null && ulx != null && uly != null && crs != null) {
            int l = Integer.parseInt(lines);
            int c = Integer.parseInt(columns);
            long x = Long.parseLong(ulx);
            long y = Long.parseLong(uly);
            int stepX = Integer.parseInt(xDim);
            int stepY = Integer.parseInt(yDim);
            metadata.setCrs(crs);
            metadata.setWidth(l);
            metadata.setHeight(c);
            try {
                Polygon2D polygon2D = new Polygon2D();
                double[] point = Conversions.utmToDegrees(crs, x, y);
                polygon2D.append(point[0], point[1]);
                point = Conversions.utmToDegrees(crs, x, y + c * stepY);
                polygon2D.append(point[0], point[1]);
                point = Conversions.utmToDegrees(crs, x + l * stepX, y + c * stepY);
                polygon2D.append(point[0], point[1]);
                point = Conversions.utmToDegrees(crs, x + l * stepX, y);
                polygon2D.append(point[0], point[1]);
                point = Conversions.utmToDegrees(crs, x, y);
                polygon2D.append(point[0], point[1]);
                metadata.setFootprint(polygon2D.toWKT(8));
            } catch (TransformException | FactoryException e) {
                e.printStackTrace();
            }
        }
        return metadata;
    }
}
