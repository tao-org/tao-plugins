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

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
* Auto-generated class implementation for <I>https://w3id.org/cwl/cwl#CommandOutputBinding</I><BR> <BLOCKQUOTE>
 Describes how to generate an output parameter based on the files produced
 by a CommandLineTool.
 
 The output parameter value is generated by applying these operations in the
 following order:
 
   - glob
   - loadContents
   - outputEval
   - secondaryFiles
  </BLOCKQUOTE>
 */
public class CommandOutputBindingImpl extends SaveableImpl implements CommandOutputBinding {
  private LoadingOptions loadingOptions_ = new LoadingOptionsBuilder().build();
  private java.util.Map<String, Object> extensionFields_ =
      new java.util.HashMap<String, Object>();

  private java.util.Optional<Boolean> loadContents;

  /**
   * Getter for property <I>https://w3id.org/cwl/cwl#LoadContents/loadContents</I><BR>
   * <BLOCKQUOTE>
   * Only valid when `type: File` or is an array of `items: File`.
   * 
   * If true, the file (or each file in the array) must be a UTF-8
   * text file 64 KiB or smaller, and the implementation must read
   * the entire contents of the file (or file array) and place it
   * in the `contents` field of the File object for use by
   * expressions.  If the size of the file is greater than 64 KiB,
   * the implementation must raise a fatal error.
   *    * </BLOCKQUOTE>
   */

  public java.util.Optional<Boolean> getLoadContents() {
    return this.loadContents;
  }

  private java.util.Optional<LoadListingEnum> loadListing;

  /**
   * Getter for property <I>https://w3id.org/cwl/cwl#LoadContents/loadListing</I><BR>
   * <BLOCKQUOTE>
   * Only valid when `type: Directory` or is an array of `items: Directory`.
   * 
   * Specify the desired behavior for loading the `listing` field of
   * a Directory object for use by expressions.
   * 
   * The order of precedence for loadListing is:
   * 
   *   1. `loadListing` on an individual parameter
   *   2. Inherited from `LoadListingRequirement`
   *   3. By default: `no_listing`
   *    * </BLOCKQUOTE>
   */

  public java.util.Optional<LoadListingEnum> getLoadListing() {
    return this.loadListing;
  }

  private Object glob;

  /**
   * Getter for property <I>https://w3id.org/cwl/cwl#CommandOutputBinding/glob</I><BR>
   * <BLOCKQUOTE>
   * Find files or directories relative to the output directory, using POSIX
   * glob(3) pathname matching.  If an array is provided, find files or
   * directories that match any pattern in the array.  If an expression is
   * provided, the expression must return a string or an array of strings,
   * which will then be evaluated as one or more glob patterns.  Must only
   * match and return files/directories which actually exist.
   * 
   * If the value of glob is a relative path pattern (does not
   * begin with a slash '/') then it is resolved relative to the
   * output directory.  If the value of the glob is an absolute
   * path pattern (it does begin with a slash '/') then it must
   * refer to a path within the output directory.  It is an error
   * if any glob resolves to a path outside the output directory.
   * Specifically this means globs that resolve to paths outside the output
   * directory are illegal.
   * 
   * A glob may match a path within the output directory which is
   * actually a symlink to another file.  In this case, the
   * expected behavior is for the resulting File/Directory object to take the
   * `basename` (and corresponding `nameroot` and `nameext`) of the
   * symlink.  The `location` of the File/Directory is implementation
   * dependent, but logically the File/Directory should have the same content
   * as the symlink target.  Platforms may stage output files/directories to
   * cloud storage that lack the concept of a symlink.  In
   * this case file content and directories may be duplicated, or (to avoid
   * duplication) the File/Directory `location` may refer to the symlink
   * target.
   * 
   * It is an error if a symlink in the output directory (or any
   * symlink in a chain of links) refers to any file or directory
   * that is not under an input or output directory.
   * 
   * Implementations may shut down a container before globbing
   * output, so globs and expressions must not assume access to the
   * container filesystem except for declared input and output.
   *    * </BLOCKQUOTE>
   */

  public Object getGlob() {
    return this.glob;
  }

  private java.util.Optional<String> outputEval;

  /**
   * Getter for property <I>https://w3id.org/cwl/cwl#CommandOutputBinding/outputEval</I><BR>
   * <BLOCKQUOTE>
   * Evaluate an expression to generate the output value.  If
   * `glob` was specified, the value of `self` must be an array
   * containing file objects that were matched.  If no files were
   * matched, `self` must be a zero length array; if a single file
   * was matched, the value of `self` is an array of a single
   * element.  The exit code of the process is
   * available in the expression as `runtime.exitCode`.
   * 
   * Additionally, if `loadContents` is true, the file must be a
   * UTF-8 text file 64 KiB or smaller, and the implementation must
   * read the entire contents of the file (or file array) and place
   * it in the `contents` field of the File object for use in
   * `outputEval`.  If the size of the file is greater than 64 KiB,
   * the implementation must raise a fatal error.
   * 
   * If a tool needs to return a large amount of structured data to
   * the workflow, loading the output object from `cwl.output.json`
   * bypasses `outputEval` and is not subject to the 64 KiB
   * `loadContents` limit.
   *    * </BLOCKQUOTE>
   */

  public java.util.Optional<String> getOutputEval() {
    return this.outputEval;
  }

  public void setLoadContents(Optional<Boolean> loadContents) {
    this.loadContents = loadContents;
  }

  public void setLoadListing(Optional<LoadListingEnum> loadListing) {
    this.loadListing = loadListing;
  }

  public void setGlob(Object glob) {
    this.glob = glob;
  }

  public void setOutputEval(Optional<String> outputEval) {
    this.outputEval = outputEval;
  }

  public CommandOutputBindingImpl() {
    super();
  }

  /**
   * Used by {@link org.w3id.cwl.cwl1_2.utils.RootLoader} to construct instances of CommandOutputBindingImpl.
   *
   * @param __doc_            Document fragment to load this record object from (presumably a
                              {@link java.util.Map}).
   * @param __baseUri_        Base URI to generate child document IDs against.
   * @param __loadingOptions  Context for loading URIs and populating objects.
   * @param __docRoot_        ID at this position in the document (if available) (maybe?)
   * @throws ValidationException If the document fragment is not a {@link java.util.Map}
   *                             or validation of fields fails.
   */
  public CommandOutputBindingImpl(
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
      throw new ValidationException("CommandOutputBindingImpl called on non-map");
    }
    final java.util.Map<String, Object> __doc = (java.util.Map<String, Object>) __doc_;
    final java.util.List<ValidationException> __errors =
        new java.util.ArrayList<ValidationException>();
    if (__loadingOptions != null) {
      this.loadingOptions_ = __loadingOptions;
    }
    java.util.Optional<Boolean> loadContents;

    if (__doc.containsKey("loadContents")) {
      try {
        loadContents =
            LoaderInstances
                .optional_BooleanInstance
                .loadField(__doc.get("loadContents"), __baseUri, __loadingOptions);
      } catch (ValidationException e) {
        loadContents = null; // won't be used but prevents compiler from complaining.
        final String __message = "the `loadContents` field is not valid because:";
        __errors.add(new ValidationException(__message, e));
      }

    } else {
      loadContents = null;
    }
    java.util.Optional<LoadListingEnum> loadListing;

    if (__doc.containsKey("loadListing")) {
      try {
        loadListing =
            LoaderInstances
                .optional_LoadListingEnum
                .loadField(__doc.get("loadListing"), __baseUri, __loadingOptions);
      } catch (ValidationException e) {
        loadListing = null; // won't be used but prevents compiler from complaining.
        final String __message = "the `loadListing` field is not valid because:";
        __errors.add(new ValidationException(__message, e));
      }

    } else {
      loadListing = null;
    }
    Object glob;

    if (__doc.containsKey("glob")) {
      try {
        glob =
            LoaderInstances
                .union_of_NullInstance_or_StringInstance_or_ExpressionLoader_or_array_of_StringInstance
                .loadField(__doc.get("glob"), __baseUri, __loadingOptions);
      } catch (ValidationException e) {
        glob = null; // won't be used but prevents compiler from complaining.
        final String __message = "the `glob` field is not valid because:";
        __errors.add(new ValidationException(__message, e));
      }

    } else {
      glob = null;
    }
    java.util.Optional<String> outputEval;

    if (__doc.containsKey("outputEval")) {
      try {
        outputEval =
            LoaderInstances
                .optional_ExpressionLoader
                .loadField(__doc.get("outputEval"), __baseUri, __loadingOptions);
      } catch (ValidationException e) {
        outputEval = null; // won't be used but prevents compiler from complaining.
        final String __message = "the `outputEval` field is not valid because:";
        __errors.add(new ValidationException(__message, e));
      }

    } else {
      outputEval = null;
    }
    if (!__errors.isEmpty()) {
      throw new ValidationException("Trying 'RecordField'", __errors);
    }
    this.loadContents = (java.util.Optional<Boolean>) loadContents;
    this.loadListing = (java.util.Optional<LoadListingEnum>) loadListing;
    this.glob = (Object) glob;
    this.outputEval = (java.util.Optional<String>) outputEval;
  }

  public Map<Object, Object> save() {
    Map<Object, Object> outputBinding = new LinkedHashMap<>();
    for (Field f : this.getClass().getDeclaredFields()) {
      if (!f.getName().equalsIgnoreCase("loadingOptions_") && !f.getName().equalsIgnoreCase("extensionFields_")) {
        try {
          if (f.get(this) != null) {
            if (f.get(this) instanceof Optional<?>) {
              outputBinding.put(f.getName(), ((Optional<?>) f.get(this)).get());
            } else {
              outputBinding.put(f.getName(), f.get(this).toString());
            }
          }
        } catch (Exception e){
          System.out.println(e.getMessage() + ": " + e.getCause() );
        }
      }
    }
    return outputBinding;
  }
}
