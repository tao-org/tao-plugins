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

import org.w3id.cwl.cwl1_2.utils.LoaderInstances;
import org.w3id.cwl.cwl1_2.utils.LoadingOptions;
import org.w3id.cwl.cwl1_2.utils.LoadingOptionsBuilder;
import org.w3id.cwl.cwl1_2.utils.SaveableImpl;
import org.w3id.cwl.cwl1_2.utils.ValidationException;

/**
* Auto-generated class implementation for <I>https://w3id.org/cwl/cwl#OutputArraySchema</I><BR>
 */
public class OutputArraySchemaImpl extends SaveableImpl implements OutputArraySchema {
  private LoadingOptions loadingOptions_ = new LoadingOptionsBuilder().build();
  private java.util.Map<String, Object> extensionFields_ =
      new java.util.HashMap<String, Object>();

  private java.util.Optional<String> name;

  /**
   * Getter for property <I>https://w3id.org/cwl/cwl#IOSchema/name</I><BR>
   * <BLOCKQUOTE>
   * The identifier for this type   * </BLOCKQUOTE>
   */

  public java.util.Optional<String> getName() {
    return this.name;
  }

  private Object items;

  /**
   * Getter for property <I>https://w3id.org/cwl/salad#items</I><BR>
   * <BLOCKQUOTE>
   * Defines the type of the array elements.   * </BLOCKQUOTE>
   */

  public Object getItems() {
    return this.items;
  }

  private enum_d062602be0b4b8fd33e69e29a841317b6ab665bc type;

  /**
   * Getter for property <I>https://w3id.org/cwl/salad#type</I><BR>
   * <BLOCKQUOTE>
   * Must be `array`   * </BLOCKQUOTE>
   */

  public enum_d062602be0b4b8fd33e69e29a841317b6ab665bc getType() {
    return this.type;
  }

  private java.util.Optional<String> label;

  /**
   * Getter for property <I>https://w3id.org/cwl/cwl#Labeled/label</I><BR>
   * <BLOCKQUOTE>
   * A short, human-readable label of this object.   * </BLOCKQUOTE>
   */

  public java.util.Optional<String> getLabel() {
    return this.label;
  }

  private Object doc;

  /**
   * Getter for property <I>https://w3id.org/cwl/salad#Documented/doc</I><BR>
   * <BLOCKQUOTE>
   * A documentation string for this object, or an array of strings which should be concatenated.   * </BLOCKQUOTE>
   */

  public Object getDoc() {
    return this.doc;
  }

  /**
   * Used by {@link org.w3id.cwl.cwl1_2.utils.RootLoader} to construct instances of OutputArraySchemaImpl.
   *
   * @param __doc_            Document fragment to load this record object from (presumably a
                              {@link java.util.Map}).
   * @param __baseUri_        Base URI to generate child document IDs against.
   * @param __loadingOptions  Context for loading URIs and populating objects.
   * @param __docRoot_        ID at this position in the document (if available) (maybe?)
   * @throws ValidationException If the document fragment is not a {@link java.util.Map}
   *                             or validation of fields fails.
   */
  public OutputArraySchemaImpl(
      final Object __doc_,
      final String __baseUri_,
      LoadingOptions __loadingOptions,
      final String __docRoot_) {
    super(__doc_, __baseUri_, __loadingOptions, __docRoot_);
    // Prefix plumbing variables with '__' to reduce likelihood of collision with
    // generated names.
    String __baseUri = __baseUri_;
    String __docRoot = __docRoot_;
    if (!(__doc_ instanceof java.util.Map)) {
      throw new ValidationException("OutputArraySchemaImpl called on non-map");
    }
    final java.util.Map<String, Object> __doc = (java.util.Map<String, Object>) __doc_;
    final java.util.List<ValidationException> __errors =
        new java.util.ArrayList<ValidationException>();
    if (__loadingOptions != null) {
      this.loadingOptions_ = __loadingOptions;
    }
    java.util.Optional<String> name;

    if (__doc.containsKey("name")) {
      try {
        name =
            LoaderInstances
                .uri_optional_StringInstance_True_False_None
                .loadField(__doc.get("name"), __baseUri, __loadingOptions);
      } catch (ValidationException e) {
        name = null; // won't be used but prevents compiler from complaining.
        final String __message = "the `name` field is not valid because:";
        __errors.add(new ValidationException(__message, e));
      }

    } else {
      name = null;
    }

    Boolean __original_is_null = name == null;
    if (name == null) {
      if (__docRoot != null) {
        name = java.util.Optional.of(__docRoot);
      } else {
        name = java.util.Optional.of("_:" + java.util.UUID.randomUUID().toString());
      }
    }
    if (__original_is_null) {
        __baseUri = __baseUri_;
    } else {
        __baseUri = (String) name.orElse(null);
    }
    Object items;
    try {
      items =
          LoaderInstances
              .typedsl_union_of_CWLType_or_OutputRecordSchema_or_OutputEnumSchema_or_OutputArraySchema_or_StringInstance_or_array_of_union_of_CWLType_or_OutputRecordSchema_or_OutputEnumSchema_or_OutputArraySchema_or_StringInstance_2
              .loadField(__doc.get("items"), __baseUri, __loadingOptions);
    } catch (ValidationException e) {
      items = null; // won't be used but prevents compiler from complaining.
      final String __message = "the `items` field is not valid because:";
      __errors.add(new ValidationException(__message, e));
    }
    enum_d062602be0b4b8fd33e69e29a841317b6ab665bc type;
    try {
      type =
          LoaderInstances
              .typedsl_enum_d062602be0b4b8fd33e69e29a841317b6ab665bc_2
              .loadField(__doc.get("type"), __baseUri, __loadingOptions);
    } catch (ValidationException e) {
      type = null; // won't be used but prevents compiler from complaining.
      final String __message = "the `type` field is not valid because:";
      __errors.add(new ValidationException(__message, e));
    }
    java.util.Optional<String> label;

    if (__doc.containsKey("label")) {
      try {
        label =
            LoaderInstances
                .optional_StringInstance
                .loadField(__doc.get("label"), __baseUri, __loadingOptions);
      } catch (ValidationException e) {
        label = null; // won't be used but prevents compiler from complaining.
        final String __message = "the `label` field is not valid because:";
        __errors.add(new ValidationException(__message, e));
      }

    } else {
      label = null;
    }
    Object doc;

    if (__doc.containsKey("doc")) {
      try {
        doc =
            LoaderInstances
                .union_of_NullInstance_or_StringInstance_or_array_of_StringInstance
                .loadField(__doc.get("doc"), __baseUri, __loadingOptions);
      } catch (ValidationException e) {
        doc = null; // won't be used but prevents compiler from complaining.
        final String __message = "the `doc` field is not valid because:";
        __errors.add(new ValidationException(__message, e));
      }

    } else {
      doc = null;
    }
    if (!__errors.isEmpty()) {
      throw new ValidationException("Trying 'RecordField'", __errors);
    }
    this.items = (Object) items;
    this.type = (enum_d062602be0b4b8fd33e69e29a841317b6ab665bc) type;
    this.label = (java.util.Optional<String>) label;
    this.doc = (Object) doc;
    this.name = (java.util.Optional<String>) name;
  }

  @Override
  public String toString() {
    return (this.items != null ? this.items.toString() : "") +
            ((this.type != null && this.type.toString().equalsIgnoreCase("array") ? "[]" : ""));
  }
}
