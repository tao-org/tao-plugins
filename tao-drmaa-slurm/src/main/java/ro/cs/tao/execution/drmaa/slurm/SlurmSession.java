/*___INFO__MARK_BEGIN__*/
/*************************************************************************
 *
 *  The Contents of this file are made available subject to the terms of
 *  the Sun Industry Standards Source License Version 1.2
 *
 *  Sun Microsystems Inc., March, 2001
 *
 *
 *  Sun Industry Standards Source License Version 1.2
 *  =================================================
 *  The contents of this file are subject to the Sun Industry Standards
 *  Source License Version 1.2 (the "License"); You may not use this file
 *  except in compliance with the License. You may obtain a copy of the
 *  License at http://gridengine.sunsource.net/Gridengine_SISSL_license.html
 *
 *  Software provided under this License is provided on an "AS IS" basis,
 *  WITHOUT WARRANTY OF ANY KIND, EITHER EXPRESSED OR IMPLIED, INCLUDING,
 *  WITHOUT LIMITATION, WARRANTIES THAT THE SOFTWARE IS FREE OF DEFECTS,
 *  MERCHANTABLE, FIT FOR A PARTICULAR PURPOSE, OR NON-INFRINGING.
 *  See the License for the specific provisions governing your rights and
 *  obligations concerning the Software.
 *
 *   The Initial Developer of the Original Code is: Sun Microsystems, Inc.
 *
 *   Copyright: 2001 by Sun Microsystems, Inc.
 *
 *   All Rights Reserved.
 *
 ************************************************************************/
/*___INFO__MARK_END__*/
package ro.cs.tao.execution.drmaa.slurm;

import org.ggf.drmaa.Session;
import ro.cs.tao.execution.drmaa.AbstractSession;
import ro.cs.tao.execution.drmaa.DrmaaJobTemplate;

/**
 * The AbstractSession class provides a DRMAA interface to Grid Engine.  This
 * interface is built on top of the DRMAA C binding using JNI.  In order to keep
 * the native code as localized as possible, this class also provides native
 * DRMAA services to other classes, such as the DrmaaJobTemplate.
 *
 * <p>This class relies on the version 1.0 <i>drmaa</i> shared library.</p>
 *
 * @see Session
 * @see DrmaaJobTemplate
 * @see <a href="http://gridengine.sunsource.net/unbranded-source/browse/~checkout~/gridengine/doc/htmlman/manuals.html?content-type=text/html">Grid Engine Man Pages</a>
 * @author dan.templeton@sun.com
 * @since 0.5
 * @version 1.0
 */

class SlurmSession extends AbstractSession {

    /**
     * Creates a new instance of AbstractSession
     */
    SlurmSession() {
        super();
    }
}
