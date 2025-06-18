import org.junit.Before;
import org.junit.Test;
import org.locationtech.jts.util.Assert;
import ro.cs.tao.eodata.functions.RasterFunctionsResolver;

import java.nio.file.Path;

public class GdalInfoTest {

    private Path testPath;

    @Before
    public void setup() {
        this.testPath = Path.of("E:\\eodata\\SPECTRAL_IMAGE_ndvi.TIF.tif");
    }

    @Test
    public void testAllNoData() {
        final RasterFunctionsResolver resolver = new RasterFunctionsResolver();
        final String expression = "ALL_NODATA(" + this.testPath.toString() + ")";
        Assert.equals("false",resolver.resolve(expression));
    }

    @Test
    public void testCountPixelsAbove() {
        final RasterFunctionsResolver resolver = new RasterFunctionsResolver();
        final String expression = "COUNT_PIXELS_ABOVE(" + this.testPath.toString() + ",2.0)";
        Assert.equals("0",resolver.resolve(expression));
    }

    @Test
    public void testCountPixelsBelow() {
        final RasterFunctionsResolver resolver = new RasterFunctionsResolver();
        final String expression = "COUNT_PIXELS_BELOW(" + this.testPath.toString() + ",1.0)";
        Assert.isTrue(Integer.parseInt(resolver.resolve(expression)) > 0);
    }

}
