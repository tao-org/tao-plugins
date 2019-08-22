package ro.cs.tao.otb;

import ro.cs.tao.component.*;


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
    private static String toPythonPipeline(ProcessingComponent component, boolean isFirst, boolean isLast, String outParamName) throws AggregationException {
        if (component == null || component.getContainerId() == null) {
            return null;
        }

        StringBuilder builder = new StringBuilder();

        builder.append("appY = otb.Registry.CreateApplication(\"").
                append(component.getId()).
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
                } else if (source.getCardinality() == 0) {
                    builder.append("appY.AddParameterStringList(\"");
                } else {
                    throw new AggregationException("Unknown way to treat cardinality.");
                }

                builder.append(source.getName()).
                        append("\", \"").
                        append("$").append(component.getId()).append("-").append(source.getName()).
                        append("\")\n");
            }
        } else {
            if (component.getSources().size() != 1) {
                throw new AggregationException("Other applications should only have one source.");
            }

            if (component.getSources().get(0).getCardinality() == 1) {
                builder.append("appY.SetParameterInputImage(\"");

            } else {
                throw new AggregationException("Source must have \"1\" as cardinality.");

            }

            builder.append(component.getSources().get(0).getName()).
                    append("\", appX.GetParameterOutputImage(\"").
                    append(outParamName).
                    append("\"))\n");
        }

        builder.append("appX = appY\n");

        // set whether to write output to file system or not
        if(isLast) {
            for (TargetDescriptor target : component.getTargets()) {
                if (target.getCardinality() == 1) {
                    builder.append("appY.SetParameterString(\"");
                } else {
                    builder.append("appY.AddParameterStringList(\"");
                }

                builder.append(target.getName()).
                        append("\", \"").
                        append("$").append(component.getId()).append("-").append(target.getName()).
                        append("\")\n");
            }

            builder.append("appX.ExecuteAndWriteOutput()\n\n");
        } else {

            if (component.getTargets().size() != 1) {
                throw new AggregationException("Application should have only one target.");
            }

            if (component.getTargets().get(0).getCardinality() != 1) {
                throw new AggregationException("Target must have \"1\" as cardinality.");
            }

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
    public static String toPythonPipeline(ProcessingComponent... components) throws AggregationException {
        if (components == null || components.length == 0) {
            return null;
        }

        if (components.length == 1) {
            return "import otbApplication as otb\n\n" +
                   "apps = []\n\n" +
                   toPythonPipeline(components[0], true, true, null);
        } else {
            final StringBuilder builder = new StringBuilder();

            builder.append("import otbApplication as otb\n\n")
                    .append("apps = []\n\n");

            builder.append(toPythonPipeline(components[0], true, false, null));
            String outParamName = components[0].getTargets().get(0).getName();

            for (int i = 1; i < components.length - 1; i++) {
                builder.append(toPythonPipeline(components[i], false, false, outParamName));
                outParamName = components[0].getTargets().get(0).getName();
            }

            builder.append(toPythonPipeline(components[components.length - 1], false, true, outParamName));

            return builder.toString();
        }
    }

}
