package ro.cs.tao.utils.executors;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.core.io.IOContext;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.dataformat.yaml.util.StringQuotingChecker;
import org.yaml.snakeyaml.DumperOptions;

import java.io.IOException;
import java.io.Writer;

public class MyYAMLGenerator extends YAMLGenerator {
    public MyYAMLGenerator(IOContext ctxt, int jsonFeatures, int yamlFeatures, StringQuotingChecker quotingChecker, ObjectCodec codec, Writer out, DumperOptions.Version version) throws IOException {
        super(ctxt, jsonFeatures, yamlFeatures, quotingChecker, codec, out, version);
    }

    public MyYAMLGenerator(IOContext ctxt, int jsonFeatures, int yamlFeatures, StringQuotingChecker quotingChecker, ObjectCodec codec, Writer out, DumperOptions dumperOptions) throws IOException {
        super(ctxt, jsonFeatures, yamlFeatures, quotingChecker, codec, out, dumperOptions);
    }

    @Override
    public void writeString(String text) throws IOException, JsonGenerationException
    {
        if (text == null) {
            writeNull();
            return;
        }
        _verifyValueWrite("write String value");

        // [dataformats-text#50]: Empty String always quoted
        if (text.isEmpty()) {
            _writeScalar(text, "string", DumperOptions.ScalarStyle.DOUBLE_QUOTED);
            return;
        }
        DumperOptions.ScalarStyle style;
        if (Feature.MINIMIZE_QUOTES.enabledIn(_formatFeatures)) {
            if (text.indexOf('\n') >= 0) {
                style = DumperOptions.ScalarStyle.LITERAL;
                // If one of reserved values ("true", "null"), or, number, preserve quoting:
            } else if (_quotingChecker.needToQuoteValue(text)
                    || (Feature.ALWAYS_QUOTE_NUMBERS_AS_STRINGS.enabledIn(_formatFeatures)
                    && PLAIN_NUMBER_P.matcher(text).matches())
            ) {
                style = DumperOptions.ScalarStyle.DOUBLE_QUOTED;
            } else if (text.matches("-?\\d+(\\.\\d+)?")) {
                style = DumperOptions.ScalarStyle.DOUBLE_QUOTED;
            } else {
                style = DumperOptions.ScalarStyle.PLAIN;
            }
        } else {
            if (Feature.LITERAL_BLOCK_STYLE.enabledIn(_formatFeatures)
                    && text.indexOf('\n') >= 0) {
                style = DumperOptions.ScalarStyle.LITERAL;
            } else {
                style = DumperOptions.ScalarStyle.DOUBLE_QUOTED;
            }
        }
        _writeScalar(text, "string", style);
    }
}
