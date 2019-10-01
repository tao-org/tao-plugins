package ro.cs.tao.otb;

import ro.cs.tao.BaseRuntimeOptimizer;
import ro.cs.tao.component.*;
import ro.cs.tao.component.enums.ProcessingComponentType;
import ro.cs.tao.component.enums.ProcessingComponentVisibility;
import ro.cs.tao.component.template.BasicTemplate;
import ro.cs.tao.component.template.Template;
import ro.cs.tao.component.template.TemplateType;
import ro.cs.tao.persistence.PersistenceManager;
import ro.cs.tao.security.SystemPrincipal;
import ro.cs.tao.services.bridge.spring.SpringContextBridge;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Runtime optimizer class for OTB processing components.
 *
 * @author Alexandru Pirlea
 */
public class OtbOptimizer extends BaseRuntimeOptimizer {
    private static final PersistenceManager persistenceManager;

    static {
        persistenceManager = SpringContextBridge.services().getService(PersistenceManager.class);
    }

    @Override
    public boolean isIntendedFor(String containerId) {
        return containerId != null && persistenceManager.getContainerById(containerId).getName().toLowerCase().contains("otb");
    }

    @Override
    protected ProcessingComponent createAggregatedComponent(ProcessingComponent... sources) throws AggregationException {
        ProcessingComponent component = new ProcessingComponent();

        final String newId = "otb-aggregated-component-" + UUID.randomUUID().toString();

        component.setId(newId);
        component.setLabel(newId);
        component.setVersion("1.0");
        component.setDescription("OTB aggregated component");
        component.setAuthors(SystemPrincipal.instance().getName());
        component.setCopyright("(C)" + LocalDate.now().getYear());
        component.setFileLocation("python");
        component.setWorkingDirectory(sources[0].getWorkingDirectory());

        Template template = new BasicTemplate();
        template.setName(newId + " template");
        template.setTemplateType(TemplateType.VELOCITY);

        component.setTemplate(template);
        component.setTemplateType(TemplateType.VELOCITY);
        component.setVisibility(ProcessingComponentVisibility.USER);
        component.setNodeAffinity("Any");
        component.setMultiThread(true);
        component.setActive(true);
        component.setContainerId(sources[0].getContainerId());

        sources[0].getSources().forEach((s) -> {
            SourceDescriptor source = s.clone();
            source.setParentId(component.getId());
            component.addSource(source);
        });

        sources[sources.length - 1].getTargets().forEach((t) -> {
            TargetDescriptor target = t.clone();
            target.setParentId(component.getId());
            component.addTarget(target);
        });

        List<ParameterDescriptor> parameterDescriptors = new ArrayList<>();

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
