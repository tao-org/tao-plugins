package ro.cs.tao.datasource.remote.mundi.parsers;

import ro.cs.tao.datasource.param.CommonParameterNames;
import ro.cs.tao.eodata.enums.PixelType;
import ro.cs.tao.eodata.enums.SensorType;
import ro.cs.tao.products.sentinels.Sentinel2ProductHelper;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Sentinel2ResponseHandler extends XmlResponseHandler<Sentinel2ProductHelper> {

    public Sentinel2ResponseHandler() {
        super("entry");
    }

    public Sentinel2ResponseHandler(String countElement) {
        this();
        this.countElement = countElement;
    }

    @Override
    protected void initRecord() {
        this.current.setSensorType(SensorType.OPTICAL);
        this.current.setPixelType(PixelType.UINT16);
        this.current.setWidth(10980);
        this.current.setHeight(10980);
        this.current.setProductType("Sentinel2");
    }

    @Override
    protected Sentinel2ProductHelper createHelper(String productName) {
        return Sentinel2ProductHelper.createHelper(productName);
    }

    @Override
    protected String computeLocation() {
        StringBuilder builder = new StringBuilder();
        String sensingDate = this.helper.getSensingDate();
        String tileId = this.helper.getTileIdentifier();
        // Since around 05-2019, MUNDI split the buckets for S2 products in quarters, so we have to compute them
        LocalDateTime date = LocalDateTime.from(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss").parse(sensingDate));
        final int year = date.getYear();
        if (year < 2019) {
            builder.append("s2-l1c/");
        } else {
            builder.append("s2-l1c-").append(year).append("-q");
            final int month = date.getMonthValue();
            builder.append(month < 4 ? "1" : month < 7 ? "2" : month < 10 ? "3" : "4").append("/");
        }
        builder.append(tileId, 0, 2).append("/")
                .append(tileId, 2, 3).append("/")
                .append(tileId, 3, 5).append("/");
        builder.append(sensingDate, 0, 4).append("/")
                .append(sensingDate, 4, 6).append("/")
                .append(sensingDate, 6, 8).append("/")
                .append(this.current.getName());
        return builder.toString();
    }

    @Override
    protected void handleAdditionalElements(String qName, String text) {
        switch (qName) {
            case "cloudCover":
                this.current.addAttribute(CommonParameterNames.CLOUD_COVER, text);
                break;
            default:
                break;
        }
    }
}
