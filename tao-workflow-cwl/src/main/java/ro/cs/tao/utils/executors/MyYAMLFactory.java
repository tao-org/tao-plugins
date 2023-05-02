package ro.cs.tao.utils.executors;

import com.fasterxml.jackson.core.io.IOContext;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;

import java.io.IOException;
import java.io.Writer;

public class MyYAMLFactory extends YAMLFactory {
    @Override
    protected MyYAMLGenerator _createGenerator(Writer out, IOContext ctxt) throws IOException {
        int feats = _yamlGeneratorFeatures;
        if (_dumperOptions == null) {
            return new MyYAMLGenerator(ctxt, _generatorFeatures, feats,
                    _quotingChecker, _objectCodec, out, _version);
        } else {
            return new MyYAMLGenerator(ctxt, _generatorFeatures, feats,
                    _quotingChecker, _objectCodec, out, _dumperOptions);
        }
    }
}
