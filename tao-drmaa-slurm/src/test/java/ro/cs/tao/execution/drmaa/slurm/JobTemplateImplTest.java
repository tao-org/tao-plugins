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
/*
 * JobTemplateImplTest.java
 * JUnit based test
 *
 * Created on November 15, 2004, 10:41 AM
 */

package ro.cs.tao.execution.drmaa.slurm;

import org.ggf.drmaa.DrmaaException;
import org.ggf.drmaa.FileTransferMode;
import org.ggf.drmaa.InvalidJobTemplateException;
import org.ggf.drmaa.JobTemplate;
import org.ggf.drmaa.PartialTimestamp;
import org.ggf.drmaa.Session;
import org.ggf.drmaa.SessionFactory;
import org.ggf.drmaa.UnsupportedAttributeException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import ro.cs.tao.execution.drmaa.DrmaaJobTemplate;

import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 *
 * @author dan.templeton@sun.com
 */


public class JobTemplateImplTest {
    private Session session = null;
    private JobTemplate jt = null;

    private static Map nullIfEmpty(Map env) {
        if (env == null || env.isEmpty()) {
            return null;
        }

        return env;
    }

    private static Set nullIfEmpty(Set s) {
        if (s == null || s.isEmpty()) {
            return null;
        }

        return s;
    }

    private static List nullIfEmpty(List l) {
        if (l == null || l.isEmpty()) {
            return null;
        }

        return l;
    }

    @Before
    public void setUp() throws DrmaaException {
        session = SessionFactory.getFactory().getSession();
        session.init(null);
        jt = session.createJobTemplate();
    }
    
    @After
    public void tearDown() throws DrmaaException {
        session.deleteJobTemplate(jt);
        session.exit();
        session = null;
    }
    
    /** Test of getId method, of class com.sun.grid.drmaa.DrmaaJobTemplate. */
    @Test
    public void testGetId() throws DrmaaException {
        System.out.println("testGetId");
        SlurmSession session = (SlurmSession)this.session;
        DrmaaJobTemplate jt = (DrmaaJobTemplate)this.jt;
        
        try {
            String[] names = session.nativeGetAttributeNames(jt.getId());
        } catch (InvalidJobTemplateException e) {
            fail("Id returned from getId() not recognised by session");
        }
    }
    
    /** Test of g|setRemoteCommand method, of class org.ggf.drmaa.JobTemplate. */
    @Test
    public void testRemoteCommand() throws DrmaaException {
        System.out.println("testRemoteCommand");
        
        jt.setRemoteCommand("MyRemoteCommand");
        assertEquals("MyRemoteCommand", jt.getRemoteCommand());
    }
    
    /** Test of g|setArgs method, of class org.ggf.drmaa.JobTemplate. */
    @Test
    public void testArgs() throws DrmaaException {
        System.out.println("testArgs");
        
        List args = Arrays.asList(new String[] {"arg1", "arg2", "arg3"});
        
        jt.setArgs(args);
        
        List retArgs = jt.getArgs();
        
        assertNotSame(args, retArgs);
        
        for (int count = 0; count < Math.min(args.size(), retArgs.size()); count++) {
            assertEquals(args.get(count), retArgs.get(count));
        }
    }
    
    /** Test of g|setJobSubmissionState method, of class org.ggf.drmaa.JobTemplate. */
    @Test
    public void testJobSubmissionState() throws DrmaaException {
        System.out.println("testJobSubmissionState");
        
        jt.setJobSubmissionState(jt.HOLD_STATE);
        assertEquals(jt.HOLD_STATE, jt.getJobSubmissionState());
        jt.setJobSubmissionState(jt.ACTIVE_STATE);
        assertEquals(jt.ACTIVE_STATE, jt.getJobSubmissionState());
    }
    
    /** Test of g|setJobEnvironment method, of class org.ggf.drmaa.JobTemplate. */
    @Test
    public void testJobEnvironment() throws DrmaaException {
        System.out.println("testJobEnvironment");
        
        HashMap env = new HashMap();
        env.put("PATH", "/usr/bin");
        env.put("LD_LIBRARY_PATH", "/usr/lib");
        
        jt.setJobEnvironment(env);
        
        Map retEnv = jt.getJobEnvironment();
        
        assertNotSame(env, retEnv);
        assertEquals(env, retEnv);
    }
    
    /** Test of g|setWorkingDirectory method, of class org.ggf.drmaa.JobTemplate. */
    @Test
    public void testWorkingDirectory() throws DrmaaException {
        System.out.println("testWorkingDirectory");
        
        jt.setWorkingDirectory("/home/me");
        assertEquals("/home/me", jt.getWorkingDirectory());
    }
    
    /** Test of g|setJobCategory method, of class org.ggf.drmaa.JobTemplate. */
    @Test
    public void testJobCategory() throws DrmaaException {
        System.out.println("testJobCategory");
        
        jt.setJobCategory("mycat");
        assertEquals("mycat", jt.getJobCategory());
    }
    
    /** Test of g|setNativeSpecification method, of class org.ggf.drmaa.JobTemplate. */
    @Test
    public void testNativeSpecification() throws DrmaaException {
        System.out.println("testNativeSpecification");
        
        jt.setNativeSpecification("-shell yes");
        assertEquals("-shell yes", jt.getNativeSpecification());
    }
    
    /** Test of g|setEmail method, of class org.ggf.drmaa.JobTemplate. */
    @Test
    public void testEmail() throws DrmaaException {
        System.out.println("testEmail");
        
        HashSet email = new HashSet(Arrays.asList(new String[] {"dant@germany", "admin"}));
        
        jt.setEmail(email);
        
        Set retEmail = jt.getEmail();
        
        assertNotSame(email, retEmail);
        assertEquals(email, retEmail);
    }
    
    /** Test of g|setBlockEmail method, of class org.ggf.drmaa.JobTemplate. */
    @Test
    public void testBlockEmail() throws DrmaaException {
        System.out.println("testBlockEmail");
        
        jt.setBlockEmail(true);
        assertTrue(jt.getBlockEmail());
        jt.setBlockEmail(false);
        assertFalse(jt.getBlockEmail());
    }
    
    /** Test of g|setStartTime method, of class org.ggf.drmaa.JobTemplate. */
    @Test
    public void testStartTime() throws DrmaaException {
        System.out.println("testStartTime");
        
        PartialTimestamp pt = new PartialTimestamp();
        Calendar cal = Calendar.getInstance();
        
        pt.set(pt.HOUR_OF_DAY, cal.get(cal.HOUR_OF_DAY));
        pt.set(pt.MINUTE, cal.get(cal.MINUTE) + 1);
        jt.setStartTime(pt);
        
        PartialTimestamp retPt = jt.getStartTime();
        
        assertNotSame(pt, retPt);
        assertEquals(pt.getTime(), retPt.getTime());
    }
    
    /** Test of g|setJobName method, of class org.ggf.drmaa.JobTemplate. */
    @Test
    public void testJobName() throws DrmaaException {
        System.out.println("testJobName");
        
        jt.setJobName("MyJob");
        assertEquals("MyJob", jt.getJobName());
    }
    
    /** Test of g|setInputPath method, of class org.ggf.drmaa.JobTemplate. */
    @Test
    public void testInputPath() throws DrmaaException {
        System.out.println("testInputPath");
        
        jt.setInputPath("/tmp");
        assertEquals("/tmp", jt.getInputPath());
    }
    
    /** Test of g|setOutputPath method, of class org.ggf.drmaa.JobTemplate. */
    @Test
    public void testOutputPath() throws DrmaaException {
        System.out.println("testOutputPath");
        
        jt.setOutputPath("/tmp");
        assertEquals("/tmp", jt.getOutputPath());
    }
    
    /** Test of g|setErrorPath method, of class org.ggf.drmaa.JobTemplate. */
    @Test
    public void testErrorPath() throws DrmaaException {
        System.out.println("testErrorPath");
        
        jt.setErrorPath("/tmp");
        assertEquals("/tmp", jt.getErrorPath());
    }
    
    /** Test of g|setJoinFiles method, of class org.ggf.drmaa.JobTemplate. */
    @Test
    public void testJoinFiles() throws DrmaaException {
        System.out.println("testJoinFiles");
        
        jt.setJoinFiles(true);
        assertTrue(jt.getJoinFiles());
        jt.setJoinFiles(false);
        assertFalse(jt.getJoinFiles());
    }
    
    /** Test of setTransferFiles method, of class org.ggf.drmaa.JobTemplate. */
    @Test
    public void testTransferFiles() throws DrmaaException {
        System.out.println("testTransferFiles");
        
        FileTransferMode mode = new FileTransferMode(true, true, true);
        
        jt.setTransferFiles(mode);
        
        FileTransferMode retMode = jt.getTransferFiles();
        
        assertNotSame(mode, retMode);
        assertEquals(mode, retMode);
        
        mode = new FileTransferMode(false, false, false);
        
        jt.setTransferFiles(mode);
        
        retMode = jt.getTransferFiles();
        
        assertNotSame(mode, retMode);
        assertEquals(mode, retMode);
    }
    
    /** Test of g|setDeadlineTime method, of class org.ggf.drmaa.JobTemplate. */
    @Test
    public void testDeadlineTime() throws DrmaaException {
        System.out.println("testDeadlineTime");
        
        PartialTimestamp pt = new PartialTimestamp();
        
        try {
            jt.setDeadlineTime(pt);
            fail("Allowed unsupported deadlineTime attribute");
        } catch (UnsupportedAttributeException e) {
            /* Don't care */
        }
        
        try {
            jt.getDeadlineTime();
            fail("Allowed unsupported deadlineTime attribute");
        } catch (UnsupportedAttributeException e) {
            /* Don't care */
        }
    }
    
    /** Test of g|setHardWallclockTimeLimit method, of class org.ggf.drmaa.JobTemplate. */
    @Test
    public void testHardWallclockTimeLimit() throws DrmaaException {
        System.out.println("testHardWallclockTimeLimit");
        
        try {
            jt.setHardWallclockTimeLimit(101L);
            fail("Allowed unsupported hardWallclockTimeLimit attribute");
        } catch (UnsupportedAttributeException e) {
            /* Don't care */
        }
        
        try {
            jt.getHardWallclockTimeLimit();
            fail("Allowed unsupported hardWallclockTimeLimit attribute");
        } catch (UnsupportedAttributeException e) {
            /* Don't care */
        }
    }
    
    /** Test of g|setSoftWallclockTimeLimit method, of class org.ggf.drmaa.JobTemplate. */
    @Test
    public void testSoftWallclockTimeLimit() throws DrmaaException {
        System.out.println("testSoftWallclockTimeLimit");
        
        try {
            jt.setSoftWallclockTimeLimit(101L);
            fail("Allowed unsupported softWallclockTimeLimit attribute");
        } catch (UnsupportedAttributeException e) {
            /* Don't care */
        }
        
        try {
            jt.getSoftWallclockTimeLimit();
            fail("Allowed unsupported softWallclockTimeLimit attribute");
        } catch (UnsupportedAttributeException e) {
            /* Don't care */
        }
    }
    
    /** Test of g|setHardRunDurationLimit method, of class org.ggf.drmaa.JobTemplate. */
    @Test
    public void testHardRunDurationLimit() throws DrmaaException {
        System.out.println("testHardRunDurationLimit");
        
        try {
            jt.setHardRunDurationLimit(101L);
            fail("Allowed unsupported hardRunDurationLimit attribute");
        } catch (UnsupportedAttributeException e) {
            /* Don't care */
        }
        
        try {
            jt.getHardRunDurationLimit();
            fail("Allowed unsupported hardRunDurationLimit attribute");
        } catch (UnsupportedAttributeException e) {
            /* Don't care */
        }
    }
    
    /** Test of g|setSoftRunDurationLimit method, of class org.ggf.drmaa.JobTemplate. */
    @Test
    public void testSoftRunDurationLimit() throws DrmaaException {
        System.out.println("testSoftRunDurationLimit");
        
        try {
            jt.setSoftRunDurationLimit(101L);
            fail("Allowed unsupported softRunDurationLimit attribute");
        } catch (UnsupportedAttributeException e) {
            /* Don't care */
        }
        
        try {
            jt.getSoftRunDurationLimit();
            fail("Allowed unsupported softRunDurationLimit attribute");
        } catch (UnsupportedAttributeException e) {
            /* Don't care */
        }
    }
    
    /** Test of equals method, of class org.ggf.drmaa.JobTemplate. */
    @Test
    public void testEquals() throws DrmaaException {
        System.out.println("testEquals");
        
        JobTemplate jt2 = session.createJobTemplate();
        
        assertFalse(jt.equals(jt2));
        assertFalse(jt2.equals(jt));
        jt.setBlockEmail(true);
        jt2.setBlockEmail(true);
        assertFalse(jt.equals(jt2));
        assertFalse(jt2.equals(jt));
        jt2.setStartTime(new PartialTimestamp(10, 21, 01));
        jt.setStartTime(new PartialTimestamp(10, 21, 01));
        assertFalse(jt.equals(jt2));
        assertFalse(jt2.equals(jt));
        
        session.deleteJobTemplate(jt2);
    }
    
    /** Test of hashCode method, of class org.ggf.drmaa.JobTemplate. */
    @Test
    public void testHashCode() throws DrmaaException {
        System.out.println("testHashCode");
        
        JobTemplate jt2 = session.createJobTemplate();
        
        assertFalse(jt.hashCode() == jt2.hashCode());
        jt.setBlockEmail(true);
        jt2.setBlockEmail(true);
        assertFalse(jt.hashCode() == jt2.hashCode());
        jt2.setStartTime(new PartialTimestamp(10, 21, 01));
        jt.setStartTime(new PartialTimestamp(10, 21, 01));
        assertFalse(jt.hashCode() == jt2.hashCode());
        
        session.deleteJobTemplate(jt2);
    }

    @Test
    public void testUnsetProperties() throws DrmaaException {
        System.out.println("testUnsetProperties");
        session.deleteJobTemplate(jt);
        jt = session.createJobTemplate();

        assertNull(nullIfEmpty(jt.getArgs()));
        assertNull(nullIfEmpty(jt.getEmail()));
        assertNull(nullIfEmpty(jt.getErrorPath()));
        assertNull(nullIfEmpty(jt.getInputPath()));
        assertNull(nullIfEmpty(jt.getJobCategory()));
        assertNull(nullIfEmpty(jt.getJobEnvironment()));
        assertNull(nullIfEmpty(jt.getJobName()));
        assertNull(nullIfEmpty(jt.getNativeSpecification()));
        assertNull(nullIfEmpty(jt.getOutputPath()));
        assertNull(nullIfEmpty(jt.getRemoteCommand()));
        assertNull(jt.getStartTime());
        assertNull(nullIfEmpty(jt.getWorkingDirectory()));
        assertFalse(jt.getBlockEmail());
        assertFalse(jt.getJoinFiles());
        assertEquals(jt.ACTIVE_STATE, jt.getJobSubmissionState());
    }

    private static String nullIfEmpty(String s) {
        if (s == null || s.isEmpty()) {
            return null;
        }

        return s;
    }

}
