import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.deser.std.StdDeserializer;
import tools.jackson.databind.module.SimpleModule;
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
            module.addDeserializer(ParameterDescriptor.class, new StdDeserializer<>(ParameterDescriptor.class) {
                @Override
                public ParameterDescriptor deserialize(JsonParser p, DeserializationContext ctxt) throws JacksonException {
                    JsonNode node = p.readValueAsTree();
                    if (ParameterType.TEMPLATE.name().equals(node.get("type").textValue())) {
                        return innerMapper.treeToValue(node, TemplateParameterDescriptor.class);
                    } else {
                        return innerMapper.treeToValue(node, ParameterDescriptor.class);
                    }
                }
            });
            components = mapper.rebuild().addModule(module).build().readValue(str, ProcessingComponent[].class);
        }
        System.exit(0);
    }
}
