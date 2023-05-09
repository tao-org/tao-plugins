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

import org.w3id.cwl.cwl1_2.utils.ValidationException;

public enum enum_d9cba076fca539106791a4f46d198c7fcfbdb779 {
  RECORD("record");

  private static String[] symbols = new String[] {"record"};
  private String docVal;

  private enum_d9cba076fca539106791a4f46d198c7fcfbdb779(final String docVal) {
    this.docVal = docVal;
  }

  public static enum_d9cba076fca539106791a4f46d198c7fcfbdb779 fromDocumentVal(final String docVal) {
    for(final enum_d9cba076fca539106791a4f46d198c7fcfbdb779 val : enum_d9cba076fca539106791a4f46d198c7fcfbdb779.values()) {
      if(val.docVal.equals(docVal)) {
        return val;
      }
    }
    throw new ValidationException(String.format("Expected one of %s", enum_d9cba076fca539106791a4f46d198c7fcfbdb779.symbols, docVal));
  }
}
