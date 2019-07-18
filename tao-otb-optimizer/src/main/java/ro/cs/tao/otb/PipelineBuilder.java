package ro.cs.tao.otb;

import ro.cs.tao.component.ParameterDescriptor;
import ro.cs.tao.component.ProcessingComponent;
import ro.cs.tao.component.SourceDescriptor;

import java.util.List;

/**
 * Utility class that performs conversion from TAO processing component to OTB python pipeline.
 *
 * @author Alexandru Pirlea
 */
public class PipelineBuilder {

    /**
     * Converts a TAO Processing Component which corresponds to a OTB operator into part of a python script.
     *
     * @param component  The TAO processing component
     */
    public static String toPythonPipeline(ProcessingComponent component, boolean isFrist, boolean isLast) {
        if (component == null || component.getContainerId() == null
                || !component.getContainerId().contains("otb") || !component.getContainerId().contains("OTB")) {
            return null;
        }

        StringBuilder builder = new StringBuilder();

        builder.append("appY = otb.Registry.CreateApplication(\"").
                append(component.getContainerId()).
                append("\")\n");

        builder.append("apps.append(appY)\n");

        for (ParameterDescriptor param : component.getParameterDescriptors()) {
            builder.append("appY.SetParameterString(\"").
                    append(param.getName()).
                    append("\", \"").
                    append("$").append(component.getId()).append(".").append(param.getName()).
                    append("\")\n");
        }

        // set input to application
        if (isFrist) {
            // TODO - link input file to this app
        } else {
            builder.append("appY.SetParameterInputImage(\"in\", appX.GetParameterOutputImage(\"out\"))\n");
        }

        builder.append("appX = appY\n");

        // set whether to write output or not
        if(isLast) {
            // TODO - set output file for this app

            builder.append("appX.ExecuteAndWriteOutput()\n\n");
        } else {
            builder.append("appX.Execute()\n\n");
        }

        return builder.toString();
    }

    /**
     * Converts a list of TAO Processing Components which corresponds to several OTB operators into OTB pipeline in
     * python.
     *
     * @param components  The TAO processing components
     */
    public static String toPythonPipeline(ProcessingComponent... components) {
        if (components == null || components.length == 0) {
            return null;
        }

        if (components.length == 1) {
            return "import otbApplication as otb\n\n" +
                   "apps = []\n\n" +
                   toPythonPipeline(components[0], true, true);
        } else {
            final StringBuilder builder = new StringBuilder();

            builder.append("import otbApplication as otb\n\n")
                    .append("apps = []\n\n");

            builder.append(toPythonPipeline(components[0], true, false));

            for (int i = 1; i < components.length - 1; i++) {
                builder.append(toPythonPipeline(components[i], false, false));
            }

            builder.append(toPythonPipeline(components[components.length - 1], false, true));

            return builder.toString();
        }
    }

}
