package ro.cs.tao.execution.wps;

import net.opengis.wps10.ProcessDescriptionType;
import org.geotools.data.wps.WebProcessingService;
import org.geotools.process.Process;

import java.net.URL;

public class WPSFactory extends org.geotools.data.wps.WPSFactory {
    public WPSFactory(ProcessDescriptionType pdt, URL serverUrl) {
        super(pdt, serverUrl);
    }

    public Process create(WebProcessingService webProcessingService) {
        return new WPSProcess(this, webProcessingService);
    }
}
