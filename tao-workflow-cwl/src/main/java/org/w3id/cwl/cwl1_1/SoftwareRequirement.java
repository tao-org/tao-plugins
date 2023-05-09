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

package org.w3id.cwl.cwl1_1;

import org.w3id.cwl.cwl1_1.utils.Saveable;

/**
* Auto-generated interface for <I>https://w3id.org/cwl/cwl#SoftwareRequirement</I><BR>This interface is implemented by {@link SoftwareRequirementImpl}<BR> <BLOCKQUOTE>
 A list of software packages that should be configured in the environment of
 the defined process.
  </BLOCKQUOTE>
 */
public interface SoftwareRequirement extends ProcessRequirement, Saveable {
  /**
   * Getter for property <I>https://w3id.org/cwl/cwl#SoftwareRequirement/class</I><BR>
   * <BLOCKQUOTE>
   * Always 'SoftwareRequirement'   * </BLOCKQUOTE>
   */

  SoftwareRequirement_class getClass_();
  /**
   * Getter for property <I>https://w3id.org/cwl/cwl#SoftwareRequirement/packages</I><BR>
   * <BLOCKQUOTE>
   * The list of software to be configured.   * </BLOCKQUOTE>
   */

  java.util.List<Object> getPackages();
}
