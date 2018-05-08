/*
 * Copyright (C) 2017 CS ROMANIA
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

import ro.cs.tao.workflow.WorkflowNodeDescriptor;

import java.util.List;

/**
 * Utility class that performs bidirectional conversion between TAO nodes and SNAP operator XML descriptors.
 *
 * @author Cosmin Cara
 */
public class DescriptorConverter {
    /**
     * Converts a SNAP Graph into a collection of linked TAO workflow nodes
     *
     * @param graphXml The graph XML
     */
    public static List<WorkflowNodeDescriptor> fromSnapGraphXml(String graphXml) {
        return null;
    }
    /**
     * Converts a collection of linked TAO workflow nodes into a SNAP Graph.
     * The nodes have to be linked and all of them should have been created from SNAP processing components
     *
     * @param nodes     The collection of nodes
     */
    public static String toSnapGraphXml(List<WorkflowNodeDescriptor> nodes) {
        return null;
    }
    /**
     * Converts a SNAP operator XML into a TAO workflow node
     *
     * @param operatorXml   The operator XML
     */
    public static WorkflowNodeDescriptor fromXml(String operatorXml) {
        return null;
    }
    /**
     * Converts a TAO workflow node which corresponds to a SNAP operator into the respective's operator SNAP XML
     *
     * @param node  The TAO workflow node created from a SNAP operator
     */
    public static String toSnapXml(WorkflowNodeDescriptor node) {
        return null;
    }

}
