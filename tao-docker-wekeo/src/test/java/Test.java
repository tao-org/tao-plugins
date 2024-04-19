import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import ro.cs.tao.component.ParameterDescriptor;
import ro.cs.tao.component.ProcessingComponent;
import ro.cs.tao.component.TemplateParameterDescriptor;
import ro.cs.tao.component.enums.ParameterType;
import ro.cs.tao.docker.wekeo.WekeoImageInstaller;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.stream.Collectors;

public class Test {

    public static void main(String[] args) throws IOException {
        ProcessingComponent[] components;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(WekeoImageInstaller.class.getResourceAsStream("wekeo_applications.json")))) {
            final String str = reader.lines().collect(Collectors.joining(""));
            final ObjectMapper mapper = new ObjectMapper();
            final ObjectMapper innerMapper = new ObjectMapper();
            SimpleModule module = new SimpleModule();
            module.addDeserializer(ParameterDescriptor.class, new JsonDeserializer<>() {
                @Override
                public ParameterDescriptor deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JacksonException {
                    JsonNode node = p.readValueAsTree();
                    try (JsonParser parser = innerMapper.createParser(node.toString())) {
                        final ObjectCodec codec = parser.getCodec();
                        JsonNode innerNode = parser.readValueAsTree();
                        if (ParameterType.TEMPLATE.name().equals(innerNode.get("type").textValue())) {
                            return codec.treeToValue(innerNode, TemplateParameterDescriptor.class);
                        } else {
                            return codec.treeToValue(innerNode, ParameterDescriptor.class);
                        }
                    }
                }
            });
            mapper.registerModule(module);
            components = mapper.readValue(str, ProcessingComponent[].class);
        }
        System.exit(0);
    }
}
