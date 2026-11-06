package ro.cs.tao.utils.yaml;

import tools.jackson.core.JacksonException;
import tools.jackson.core.ObjectWriteContext;
import tools.jackson.core.io.IOContext;
import tools.jackson.dataformat.yaml.YAMLFactory;
import tools.jackson.dataformat.yaml.YAMLWriteFeature;

import java.io.Writer;

public class ArgoWorkflowYAMLFactory extends YAMLFactory {
    @Override
    protected ArgoWorkflowYAMLGenerator _createGenerator(ObjectWriteContext writeContext, IOContext ctxt, Writer out) throws JacksonException {
        int formatWriteFeatures = writeContext.getFormatWriteFeatures(_formatWriteFeatures);
        formatWriteFeatures |= YAMLWriteFeature.MINIMIZE_QUOTES.getMask();
        formatWriteFeatures &= ~YAMLWriteFeature.WRITE_DOC_START_MARKER.getMask();
        return new ArgoWorkflowYAMLGenerator(
                writeContext,
                ctxt,
                writeContext.getStreamWriteFeatures(_streamWriteFeatures),
                formatWriteFeatures,
                _quotingChecker,
                out,
                _version,
                _dumpSettings
        );
    }
}
