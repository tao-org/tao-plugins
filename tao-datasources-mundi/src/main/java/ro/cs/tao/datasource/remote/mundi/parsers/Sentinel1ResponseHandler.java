package ro.cs.tao.datasource.remote.mundi.parsers;

import ro.cs.tao.eodata.enums.PixelType;
import ro.cs.tao.eodata.enums.SensorType;
import ro.cs.tao.products.sentinels.Sentinel1ProductHelper;

public class Sentinel1ResponseHandler extends XmlResponseHandler<Sentinel1ProductHelper> {

    public Sentinel1ResponseHandler() {
        super("entry");
    }

    public Sentinel1ResponseHandler(String countElement) {
        this();
        this.countElement = countElement;
    }

    @Override
    protected void initRecord() {
        this.current.setSensorType(SensorType.RADAR);
        this.current.setPixelType(PixelType.UINT16);
        this.current.setWidth(-1);
        this.current.setHeight(-1);
        this.current.setProductType("Sentinel1");
    }

    @Override
    protected Sentinel1ProductHelper createHelper(String productName) {
        return new Sentinel1ProductHelper(productName);
    }

    @Override
    protected String computeLocation() {
        StringBuilder builder = new StringBuilder();
        String sensingDate = this.helper.getSensingDate();
        builder.append(sensingDate.substring(0, 4)).append("/")
                .append(sensingDate.substring(4, 6)).append("/")
                .append(sensingDate.substring(6, 8)).append("/")
                .append(this.helper.getSensorMode().name()).append("/")
                .append(this.helper.getPolarisation().name()).append("/").append(this.current.getName());
        return builder.toString();
    }

    @Override
    protected void handleAdditionalElements(String qName, String text) {
        //NOOP
    }
}
