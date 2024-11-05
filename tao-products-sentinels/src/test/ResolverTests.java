import org.junit.Assert;
import org.junit.Test;
import ro.cs.tao.eodata.naming.TokenResolver;
import ro.cs.tao.products.sentinels.SentinelSubDatasetResolver;

public class ResolverTests {

    @Test
    public void testSubdatasetResolver() {
        final String expression = "S2SUBDATASET(/mnt/archive/S2B_MSIL2A_20230712T091559_N0509_R093_T34TGQ_20230712T105354.SAFE,10)";
        final String expected = "SENTINEL2_L2A:/mnt/archive/S2B_MSIL2A_20230712T091559_N0509_R093_T34TGQ_20230712T105354.SAFE/MTD_MSIL2A.xml:10m:EPSG_32634";
        final TokenResolver resolver = new SentinelSubDatasetResolver();
        final String resolved = resolver.resolve(expression);
        Assert.assertEquals(resolved, expected);
    }

}
