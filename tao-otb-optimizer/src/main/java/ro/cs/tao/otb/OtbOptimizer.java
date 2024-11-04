package ro.cs.tao.otb;

import ro.cs.tao.BaseRuntimeOptimizer;
import ro.cs.tao.component.*;
import ro.cs.tao.component.enums.ProcessingComponentType;
import ro.cs.tao.component.enums.ProcessingComponentVisibility;
import ro.cs.tao.component.template.BasicTemplate;
import ro.cs.tao.component.template.Template;
import ro.cs.tao.component.template.TemplateType;
import ro.cs.tao.persistence.ContainerProvider;
import ro.cs.tao.security.SystemPrincipal;
import ro.cs.tao.services.bridge.spring.SpringContextBridge;

import java.time.LocalDate;
import java.util.*;

/**
 * Runtime optimizer class for OTB processing components.
 *
 * @author Alexandru Pirlea
 */
public class OtbOptimizer extends BaseRuntimeOptimizer {
    private static final ContainerProvider containerProvider;

    static {
        containerProvider = SpringContextBridge.services().getService(ContainerProvider.class);
    }

    @Override
    public boolean isIntendedFor(String containerId) {
        return containerId != null && containerProvider.get(containerId).getName().toLowerCase().contains("otb");
    }

    @Override
    protected ProcessingComponent createAggregatedComponent(List<ProcessingComponent> sources,
                                                            Map<String, Map<String, String>> values) throws AggregationException {
        final ProcessingComponent first = sources.get(0);
        ProcessingComponent component = new ProcessingComponent();

        final String newId = "otb-aggregated-component-" + UUID.randomUUID();

        component.setId(newId);
        component.setLabel(newId);
        component.setVersion("1.0");
        component.setDescription("OTB aggregated component");
        component.setAuthors(SystemPrincipal.instance().getName());
        component.setCopyright("(C)" + LocalDate.now().getYear());
        component.setFileLocation("python");
        component.setWorkingDirectory(first.getWorkingDirectory());

        Template template = new BasicTemplate();
        template.setName(newId + "_template");
        template.setTemplateType(TemplateType.VELOCITY);

        component.setTemplate(template);
        component.setTemplateType(TemplateType.VELOCITY);
        component.setVisibility(ProcessingComponentVisibility.USER);
        component.setNodeAffinity(NodeAffinity.Any);
        component.setMultiThread(true);
        component.setActive(true);
        component.setContainerId(first.getContainerId());

        first.getSources().forEach((s) -> {
            SourceDescriptor source = s.clone();
            source.setParentId(component.getId());
            component.addSource(source);
        });

        sources.get(sources.size() - 1).getTargets().forEach((t) -> {
            TargetDescriptor target = t.clone();
            target.setParentId(component.getId());
            component.addTarget(target);
        });

        Set<ParameterDescriptor> parameterDescriptors = new LinkedHashSet<>();

        for (ProcessingComponent source : sources) {
            for (ParameterDescriptor descriptor : source.getParameterDescriptors()) {
                parameterDescriptors.add(new ParameterDescriptor(UUID.randomUUID().toString(),
                        source.getId() + "-" + descriptor.getName(),
                        descriptor.getType(),
                        descriptor.getDataType(),
                        descriptor.getDefaultValue(),
                        descriptor.getDescription(),
                        descriptor.getLabel(),
                        descriptor.getUnit(),
                        descriptor.getValueSet(),
                        descriptor.getFormat(),
                        descriptor.isNotNull(),
                        descriptor.getExpansionRule()));
            }
        }

        component.setParameterDescriptors(parameterDescriptors);

        String pipeline = PipelineBuilder.toPythonPipeline(sources);

        if (pipeline == null) {
            throw new AggregationException("Cannot produce aggregated graph.");
        }

        component.setTemplateContents(pipeline);
        component.setComponentType(ProcessingComponentType.AGGREGATE);

        return component;
    }
}
