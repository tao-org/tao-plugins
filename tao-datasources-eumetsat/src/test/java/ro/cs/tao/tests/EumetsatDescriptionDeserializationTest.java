package ro.cs.tao.tests;

import org.junit.Assert;
import org.junit.Test;
import ro.cs.tao.datasource.remote.eumetsat.model.Description;
import ro.cs.tao.datasource.remote.eumetsat.model.Source;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class EumetsatDescriptionDeserializationTest {

    @Test
    public void testSampleDescriptionDeserialization() throws IOException {
        final String response = Files.readString(Path.of("sample.json"));
        final ObjectMapper mapper = new ObjectMapper().rebuild()
                                                       .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                                                       .build();

        final Description description = mapper.readValue(response, Description.class);
        final Source source = description.get_source();

        Assert.assertEquals("csw", description.get_index());
        Assert.assertEquals("EO:EUM:DAT:0959", description.get_id());
        Assert.assertTrue(description.isFound());
        Assert.assertNotNull(source);
        Assert.assertEquals("EO:EUM:DAT:0959", source.getId());
        Assert.assertEquals("Metop", source.getSatellites().get(0).getSeriesName());
        Assert.assertEquals("2007-07-10", source.getTemporalExtent().getBegin());
        Assert.assertEquals("2026-03-13T13:32:29.179Z", source.getModified());
        Assert.assertTrue(source.getMyabstract().startsWith("The IASI Carbon Monoxide"));
    }
}
