package ro.cs.tao.datasource.remote.mundi.parsers;

import ro.cs.tao.datasource.param.CommonParameterNames;
import ro.cs.tao.eodata.enums.PixelType;
import ro.cs.tao.eodata.enums.SensorType;
import ro.cs.tao.products.sentinels.Sentinel2ProductHelper;

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
        builder.append(tileId.substring(0, 2)).append("/")
                .append(tileId.substring(2, 3)).append("/")
                .append(tileId.substring(3, 5)).append("/");
        builder.append(sensingDate.substring(0, 4)).append("/")
                .append(sensingDate.substring(4, 6)).append("/")
                .append(sensingDate.substring(6, 8)).append("/")
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
