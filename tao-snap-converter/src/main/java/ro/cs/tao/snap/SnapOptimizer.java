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
import ro.cs.tao.persistence.PersistenceManager;
import ro.cs.tao.security.SystemPrincipal;
import ro.cs.tao.services.bridge.spring.SpringContextBridge;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Runtime optimizer class for SNAP processing components.
 *
 * @author Cosmin Cara
 */
public class SnapOptimizer extends BaseRuntimeOptimizer {

    private static final PersistenceManager persistenceManager;

    static {
        persistenceManager = SpringContextBridge.services().getService(PersistenceManager.class);
    }

    @Override
    public boolean isIntendedFor(String containerId) {
        return containerId != null && persistenceManager.getContainerById(containerId).getName().toLowerCase().contains("snap");
    }

    @Override
    public ProcessingComponent createAggregatedComponent(ProcessingComponent... sources) throws AggregationException {
        ProcessingComponent aggregator = new ProcessingComponent();
        final String newId = "snap-aggregated-component-" + UUID.randomUUID().toString();
        aggregator.setId(newId);
        aggregator.setLabel(newId);
        aggregator.setVersion("1.0");
        aggregator.setDescription("SNAP aggregated component");
        aggregator.setAuthors(SystemPrincipal.instance().getName());
        aggregator.setCopyright("(C)" + LocalDate.now().getYear());
        aggregator.setFileLocation(sources[0].getFileLocation());
        aggregator.setWorkingDirectory(sources[0].getWorkingDirectory());
        Template template = new BasicTemplate();
        template.setName(newId + ".xslt");
        template.setTemplateType(TemplateType.XSLT);
        aggregator.setTemplateType(TemplateType.XSLT);
        aggregator.setTemplate(template);
        aggregator.setVisibility(ProcessingComponentVisibility.USER);
        aggregator.setNodeAffinity("Any");
        aggregator.setMultiThread(true);
        aggregator.setActive(true);
        aggregator.setContainerId(sources[0].getContainerId());
        //Path graphPath = Paths.get(SystemVariable.USER_WORKSPACE.value(), newId + ".xml");

        sources[0].getSources().forEach((s) -> {
            SourceDescriptor source = s.clone();
            source.setParentId(aggregator.getId());
            aggregator.addSource(source);
        });

        sources[sources.length - 1].getTargets().forEach((t) -> {
            TargetDescriptor target = t.clone();
            target.setParentId(aggregator.getId());
            aggregator.addTarget(target);
        });

        final List<ParameterDescriptor> parameterDescriptors = new ArrayList<>();
        List<ParameterDescriptor> sourceParameterDescriptors;
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
        final String graph = DescriptorConverter.toSnapXml(sources);
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
