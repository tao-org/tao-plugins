package ro.cs.tao.utils.yaml;

import org.snakeyaml.engine.v2.api.DumpSettings;
import org.snakeyaml.engine.v2.common.ScalarStyle;
import org.snakeyaml.engine.v2.common.SpecVersion;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.ObjectWriteContext;
import tools.jackson.core.io.IOContext;
import tools.jackson.dataformat.yaml.YAMLGenerator;
import tools.jackson.dataformat.yaml.YAMLWriteFeature;
import tools.jackson.dataformat.yaml.util.StringQuotingChecker;

import java.io.Writer;

public class CWLYAMLGenerator extends YAMLGenerator {
    public CWLYAMLGenerator(ObjectWriteContext writeContext, IOContext ctxt, int jsonFeatures, int yamlFeatures,
                            StringQuotingChecker quotingChecker, Writer out, SpecVersion version, DumpSettings dumperOptions) {
        super(writeContext, ctxt, jsonFeatures, yamlFeatures, quotingChecker, out, version, dumperOptions);
    }

    @Override
    public JsonGenerator writeString(String text) throws JacksonException
    {
        if (text == null) {
            return writeNull();
        }
        _verifyValueWrite("write String value");

        // [dataformats-text#50]: Empty String always quoted
        if (text.isEmpty()) {
            _writeScalar(text, "string", ScalarStyle.DOUBLE_QUOTED);
            return this;
        }
        ScalarStyle style;
        if (YAMLWriteFeature.MINIMIZE_QUOTES.enabledIn(_formatWriteFeatures)) {
            if (text.indexOf('\n') >= 0) {
                style = ScalarStyle.LITERAL;
                // If one of reserved values ("true", "null"), or, number, preserve quoting:
            } else if (_quotingChecker.needToQuoteValue(text)
                    || (YAMLWriteFeature.ALWAYS_QUOTE_NUMBERS_AS_STRINGS.enabledIn(_formatWriteFeatures)
                    && PLAIN_NUMBER_P.matcher(text).matches())
            ) {
                if (_quotingChecker.needToQuoteValue(text) && text.matches("^\\w+\\[\\]$")) {
                    style = ScalarStyle.PLAIN;
                } else {
                    style = ScalarStyle.DOUBLE_QUOTED;
                }
            } else if (text.matches("-?\\d+(\\.\\d+)?") || text.startsWith("#")) {
                style = ScalarStyle.DOUBLE_QUOTED;
            } else {
                style = ScalarStyle.PLAIN;
            }
        } else {
            if (YAMLWriteFeature.LITERAL_BLOCK_STYLE.enabledIn(_formatWriteFeatures)
                    && text.indexOf('\n') >= 0) {
                style = ScalarStyle.LITERAL;
            } else {
                style = ScalarStyle.DOUBLE_QUOTED;
            }
        }
        _writeScalar(text, "string", style);
        return this;
    }
}
