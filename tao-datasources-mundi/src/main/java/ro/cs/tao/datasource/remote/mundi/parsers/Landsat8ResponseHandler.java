package ro.cs.tao.datasource.remote.mundi.parsers;

import ro.cs.tao.datasource.param.CommonParameterNames;
import ro.cs.tao.eodata.enums.PixelType;
import ro.cs.tao.eodata.enums.SensorType;
import ro.cs.tao.products.landsat.Landsat8ProductHelper;

public class Landsat8ResponseHandler extends XmlResponseHandler<Landsat8ProductHelper> {

    public Landsat8ResponseHandler() {
        super("entry");
    }

    public Landsat8ResponseHandler(String countElement) {
        this();
        this.countElement = countElement;
    }

    @Override
    protected void initRecord() {
        this.current.setSensorType(SensorType.RADAR);
        this.current.setPixelType(PixelType.UINT16);
        this.current.setWidth(-1);
        this.current.setHeight(-1);
        this.current.setProductType("Landsat8");
    }

    @Override
    protected Landsat8ProductHelper createHelper(String productName) {
        return new Landsat8ProductHelper(productName);
    }

    @Override
    protected String computeLocation() {
        StringBuilder builder = new StringBuilder();
        String sensingDate = this.helper.getSensingDate();
        builder.append(this.helper.getPath()).append("/")
                .append(this.helper.getRow()).append("/");
        builder.append(sensingDate.substring(0, 4)).append("/")
                .append(sensingDate.substring(4, 6)).append("/")
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
        if (this.helper != null && this.current.getAttributeValue("tiles") == null) {
            this.current.addAttribute("tiles", helper.getPath() + helper.getRow());
        }
    }
}
