package ro.cs.tao.utils.yaml;

import com.fasterxml.jackson.core.io.IOContext;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.io.Writer;

public class ArgoWorkflowYAMLFactory extends YAMLFactory {
    @Override
    protected ArgoWorkflowYAMLGenerator _createGenerator(Writer out, IOContext ctxt) throws IOException {
        int feats = _yamlGeneratorFeatures;
        if (_dumperOptions == null) {
            return new ArgoWorkflowYAMLGenerator(ctxt, _generatorFeatures, feats,
                    _quotingChecker, _objectCodec, out, _version);
        } else {
            return new ArgoWorkflowYAMLGenerator(ctxt, _generatorFeatures, feats,
                    _quotingChecker, _objectCodec, out, _dumperOptions);
        }
    }
}
