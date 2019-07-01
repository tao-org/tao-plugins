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

import ro.cs.tao.component.AggregationException;
import ro.cs.tao.component.ProcessingComponent;
import ro.cs.tao.component.RuntimeOptimizer;
import ro.cs.tao.component.SystemVariable;
import ro.cs.tao.component.enums.ProcessingComponentVisibility;
import ro.cs.tao.component.template.BasicTemplate;
import ro.cs.tao.component.template.Template;
import ro.cs.tao.component.template.TemplateType;
import ro.cs.tao.persistence.PersistenceManager;
import ro.cs.tao.security.SystemPrincipal;
import ro.cs.tao.services.bridge.spring.SpringContextBridge;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Runtime optimizer class for SNAP processing components.
 *
 * @author Cosmin Cara
 */
public class SnapOptimizer implements RuntimeOptimizer {

    private static final PersistenceManager persistenceManager;

    static {
        persistenceManager = SpringContextBridge.services().getService(PersistenceManager.class);
    }

    @Override
    public boolean isIntendedFor(String containerId) {
        return containerId != null && persistenceManager.getContainerById(containerId).getName().toLowerCase().contains("snap");
    }

    @Override
    public ProcessingComponent aggregate(ProcessingComponent... sources) throws AggregationException {
        ProcessingComponent aggregator = null;
        if (sources != null && sources.length > 0) {
            if (sources.length == 1) {
                aggregator = sources[0];
            } else {
                String containerId = null;
                for (ProcessingComponent source : sources) {
                    if (!isIntendedFor(source.getContainerId())) {
                        throw new AggregationException(String.format("This aggregator is not intended for components belonging to the container '%s'",
                                                                     source.getContainerId()));
                    } else if (containerId == null) {
                        containerId = source.getContainerId();
                    } else if (!containerId.equals(source.getContainerId())) {
                        throw new AggregationException("The components to be aggregated must belong to the same container");
                    }
                }
                ProcessingComponent component = new ProcessingComponent();
                final String newId = "snap-aggregated-component-" + UUID.randomUUID().toString();
                component.setId(newId);
                component.setLabel(newId);
                component.setVersion("1.0");
                component.setDescription("SNAP aggregated component");
                component.setAuthors(SystemPrincipal.instance().getName());
                component.setCopyright("(C)" + LocalDate.now().getYear());
                component.setFileLocation(sources[0].getFileLocation());
                component.setWorkingDirectory(sources[0].getWorkingDirectory());
                Template template = new BasicTemplate();
                template.setName(newId + " template");
                template.setTemplateType(TemplateType.VELOCITY);
                component.setTemplate(template);
                component.setTemplateType(TemplateType.VELOCITY);
                component.setVisibility(ProcessingComponentVisibility.SYSTEM);
                component.setNodeAffinity("Any");
                component.setMultiThread(true);
                component.setActive(true);
                component.setContainerId(containerId);
                Path graphPath = Paths.get(SystemVariable.USER_WORKSPACE.value(), newId + ".xml");
                component.setTemplateContents(graphPath.toString());
                component.setSources(sources[0].getSources());
                component.setTargets(sources[sources.length - 1].getTargets());
                final String graph = DescriptorConverter.toSnapXml(sources);
                if (graph == null) {
                    throw new AggregationException("Cannot produce aggregagted graph");
                }
                try {
                    Files.write(graphPath, graph.getBytes());
                } catch (IOException ex) {
                    throw new AggregationException(ex.getMessage());
                }
                return component;
            }
        }
        return aggregator;
    }
}
