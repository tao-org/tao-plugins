/*
 * Copyright (C) 2018 CS ROMANIA
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, see http://www.gnu.org/licenses/
 */

package ro.cs.tao.snap;

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
 * Runtime optimizer class for SNAP processing components.
 *
 * @author Cosmin Cara
 */
public class SnapOptimizer extends BaseRuntimeOptimizer {

    private static final ContainerProvider containerProvider;

    static {
        containerProvider = SpringContextBridge.services().getService(ContainerProvider.class);
    }

    @Override
    public boolean isIntendedFor(String containerId) {
        return containerId != null && containerProvider.get(containerId).getName().toLowerCase().contains("snap");
    }

    @Override
    public ProcessingComponent createAggregatedComponent(List<ProcessingComponent> sources,
                                                         Map<String, Map<String, String>> values) throws AggregationException {
        final ProcessingComponent first = sources.get(0);
        ProcessingComponent aggregator = new ProcessingComponent();
        final String newId = "snap-aggregated-component-" + UUID.randomUUID();
        aggregator.setId(newId);
        aggregator.setLabel(newId);
        aggregator.setVersion("1.0");
        aggregator.setDescription("SNAP aggregated component");
        aggregator.setAuthors(SystemPrincipal.instance().getName());
        aggregator.setCopyright("(C)" + LocalDate.now().getYear());
        aggregator.setFileLocation(first.getFileLocation());
        aggregator.setWorkingDirectory(first.getWorkingDirectory());
        Template template = new BasicTemplate();
        template.setName(newId + ".xslt");
        template.setTemplateType(TemplateType.XSLT);
        aggregator.setTemplateType(TemplateType.XSLT);
        aggregator.setTemplate(template);
        aggregator.setVisibility(ProcessingComponentVisibility.USER);
        aggregator.setNodeAffinity(NodeAffinity.Any);
        aggregator.setMultiThread(true);
        aggregator.setParallelism(sources.stream().mapToInt(ProcessingComponent::getParallelism).min().orElse(1));
        aggregator.setActive(true);
        aggregator.setContainerId(first.getContainerId());
        //Path graphPath = Paths.get(SystemVariable.USER_WORKSPACE.value(), newId + ".xml");
        final Set<ParameterDescriptor> params = first.getParameterDescriptors();
        first.getSources().forEach((s) -> {
            final ParameterDescriptor param = params.stream().filter(p -> p.getName().equals(s.getName())).findFirst().orElse(null);
            if (param == null) {
                SourceDescriptor source = s.clone();
                source.setParentId(aggregator.getId());
                aggregator.addSource(source);
            } else {
                param.setDefaultValue(s.getDataDescriptor().getLocation());
            }
        });

        sources.get(sources.size() - 1).getTargets().forEach((t) -> {
            TargetDescriptor target = t.clone();
            target.setParentId(aggregator.getId());
            aggregator.addTarget(target);
        });

        final Set<ParameterDescriptor> parameterDescriptors = new LinkedHashSet<>();
        Set<ParameterDescriptor> sourceParameterDescriptors;
        for (ProcessingComponent source : sources) {
            sourceParameterDescriptors = source.getParameterDescriptors();
            for (ParameterDescriptor descriptor : sourceParameterDescriptors) {
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
        aggregator.setParameterDescriptors(parameterDescriptors);
        final String graph = DescriptorConverter.toSnapXml(sources, values);
        if (graph == null) {
            throw new AggregationException("Cannot produce aggregated graph");
        }
        aggregator.setTemplateContents(graph);
        aggregator.setComponentType(ProcessingComponentType.AGGREGATE);
        /*try {
            Files.write(graphPath, graph.getBytes());
        } catch (IOException ex) {
            throw new AggregationException(ex.getMessage());
        }*/
        return aggregator;
    }
}
