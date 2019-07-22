package ro.cs.tao.otb;

import ro.cs.tao.component.ParameterDescriptor;
import ro.cs.tao.component.ProcessingComponent;
import ro.cs.tao.component.SourceDescriptor;
import ro.cs.tao.component.TargetDescriptor;


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
    public static String toPythonPipeline(ProcessingComponent component, boolean isFirst, boolean isLast) {
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
                    append("$").append(component.getId()).append("-").append(param.getName()).
                    append("\")\n");
        }

        // set input to application
        if (isFirst) {
            for (SourceDescriptor source : component.getSources()) {
                if (source.getCardinality() == 1) {
                    builder.append("appY.SetParameterString(\"");
                } else {
                    builder.append("appY.SetParameterStringList(\"");
                }

                builder.append(source.getName()).
                        append("\", \"").
                        append("$").append(component.getId()).append("-").append(source.getName()).
                        append("\")\n");
            }
        } else {
            builder.append("appY.SetParameterInputImage(\"in\", appX.GetParameterOutputImage(\"out\"))\n");
        }

        builder.append("appX = appY\n");

        // set whether to write output to file system or not
        if(isLast) {
            for (TargetDescriptor target : component.getTargets()) {
                if (target.getCardinality() == 1) {
                    builder.append("appY.SetParameterString(\"");
                } else {
                    builder.append("appY.SetParameterStringList(\"");
                }

                builder.append(target.getName()).
                        append("\", \"").
                        append("$").append(component.getId()).append("-").append(target.getName()).
                        append("\")\n");
            }

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
