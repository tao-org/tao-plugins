// Copyright Common Workflow Language project contributors
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.w3id.cwl.cwl1_2;

import org.apache.commons.lang3.StringUtils;
import org.w3id.cwl.cwl1_2.utils.Saveable;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
* Auto-generated interface for <I>https://w3id.org/cwl/cwl#ProcessRequirement</I><BR> <BLOCKQUOTE>
 A process requirement declares a prerequisite that may or must be fulfilled
 before executing a process.  See [`Process.hints`](#process) and
 [`Process.requirements`](#process).
 
 Process requirements are the primary mechanism for specifying extensions to
 the CWL core specification.
  </BLOCKQUOTE>
 */
public interface ProcessRequirement extends Saveable {

    @Override
    default Map<Object, Object> save() {
        Map<Object, Object> processRequirements = new LinkedHashMap<>();
        for (Field field : this.getClass().getDeclaredFields()) {
            if (!field.getName().equalsIgnoreCase("loadingOptions_") && !field.getName().equalsIgnoreCase("extensionFields_")) {
                field.setAccessible(true);
                try {
                    if (field.getName().equalsIgnoreCase("class_")) {
                        if (field.get(this) != null) {
                            processRequirements.put(field.getName().replace("_", ""),  field.get(this).toString());
                        }
                    } else {
                        if (field.get(this) != null) {
                            if (field.get(this) instanceof Optional<?>) {
                                processRequirements.put(field.getName(), ((Optional<?>) field.get(this)).get());
                            } else {
                                processRequirements.put(field.getName(), field.get(this));
                            }
                        }
                    }
                } catch (Exception e) {
                    System.out.println(e.getMessage() + ": " + e.getCause() );
                }
            }
        }
        System.out.println("ProcessRequirement Interface implementation of the save method!");
        return processRequirements;
    }
}
