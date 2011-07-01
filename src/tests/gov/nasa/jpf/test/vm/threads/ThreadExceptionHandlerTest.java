//
// Copyright (C) 2011 United States Government as represented by the
// Administrator of the National Aeronautics and Space Administration
// (NASA).  All Rights Reserved.
//
// This software is distributed under the NASA Open Source Agreement
// (NOSA), version 1.3.  The NOSA has been approved by the Open Source
// Initiative.  See the file NOSA-1.3-JPF at the top of the distribution
// directory tree for the complete NOSA document.
//
// THE SUBJECT SOFTWARE IS PROVIDED "AS IS" WITHOUT ANY WARRANTY OF ANY
// KIND, EITHER EXPRESSED, IMPLIED, OR STATUTORY, INCLUDING, BUT NOT
// LIMITED TO, ANY WARRANTY THAT THE SUBJECT SOFTWARE WILL CONFORM TO
// SPECIFICATIONS, ANY IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR
// A PARTICULAR PURPOSE, OR FREEDOM FROM INFRINGEMENT, ANY WARRANTY THAT
// THE SUBJECT SOFTWARE WILL BE ERROR FREE, OR ANY WARRANTY THAT
// DOCUMENTATION, IF PROVIDED, WILL CONFORM TO THE SUBJECT SOFTWARE.
//

package gov.nasa.jpf.test.vm.threads;

import gov.nasa.jpf.util.test.TestJPF;
import org.junit.Test;

import gov.nasa.jpf.util.test.TestJPF;
import static gov.nasa.jpf.util.test.TestJPF.*;

/** Unit tests for the three levels of exception handlers that threads have. */
/* TODO: These tests must be run inside JPF to test JPF itself.
 * However, that would currently fail because JPF does not yet support all
 * the methods needed. */

public class ThreadExceptionHandlerTest extends TestJPF {
    static int n = 0;

    public final static void main(String[] testMethods) {
        runTestsOfThisClass(testMethods);
    }

    class NPEHandler implements Thread.UncaughtExceptionHandler {
	public void uncaughtException(Thread t, Throwable e) {
	    assertTrue(e instanceof NullPointerException);
	    n = 1;
	}
    }

    class NPEHandler2 extends ThreadGroup {
	public NPEHandler2(String name) {
	    super(name);
	}

	public NPEHandler2(ThreadGroup parent, String name) {
	    super(parent, name);
	}

	public void uncaughtException(Thread t, Throwable e) {
	    assertTrue(e instanceof NullPointerException);
	    n = 2;
	}
    }

    class NPEHandler3 extends ThreadGroup {
	public NPEHandler3(String name) {
	    super(name);
	}

	public void uncaughtException(Thread t, Throwable e) {
	    assertTrue(e instanceof NullPointerException);
	    n = 3;
	}
    }

    class TestRunnable implements Runnable {
	public void run() {
	    throw new NullPointerException();
	}
    }

    class TestRunnable2 implements Runnable {
	public void run() {
	}
    }

    /* DefaultHandler is null, the other two are equal */
    @Test public void checkDefaults() {
	Thread ct = Thread.currentThread();
	assertEquals(ct.getUncaughtExceptionHandler(),
		     ct.getThreadGroup());
	assertNull(ct.getDefaultUncaughtExceptionHandler());
    }

    /* Test if handler gets executed */
    @Test public void testChildHandler() {
	n = 0;
	Thread w = new Thread(new TestRunnable());
	w.setUncaughtExceptionHandler(new NPEHandler());
	w.start();
	try {
	    w.join();
	} catch (InterruptedException e) {
	}
	assertEquals(n, 1);
	n = 0;
    }

    /* Test if default handler gets executed */
    /* The existing handlers should forward the exception to the newly
     * set default handler. */
    @Test public void testChildDefaultHandler() {
	n = 0;
	Thread w = new Thread(new TestRunnable());
	w.setDefaultUncaughtExceptionHandler(new NPEHandler());
	w.start();
	try {
	    w.join();
	} catch (InterruptedException e) {
	}
	assertEquals(n, 1);
	n = 0;
    }

    /* Handler information should be set to null after thread termination,
     * except for custom default handler which does not get reset (this is
     * poorly documented in official Java 1.6. */
    @Test public void testChildHandlerAfterTermination() {
	Thread w = new Thread(new TestRunnable2());
	w.setUncaughtExceptionHandler(new NPEHandler());
	w.setDefaultUncaughtExceptionHandler(new NPEHandler());
	assertNotNull(w.getUncaughtExceptionHandler());
	assertNotNull(w.getThreadGroup());
	assertNotNull(w.getDefaultUncaughtExceptionHandler());
	w.start();
	try {
	    w.join();
	} catch (InterruptedException e) {
	}
	assertNull(w.getUncaughtExceptionHandler());
	assertNull(w.getThreadGroup());
	assertNotNull(w.getDefaultUncaughtExceptionHandler());
    }

    /* The uncaughtHandler has precedence over any other handlers,
     * including the thread group a thread belongs to. */
    @Test public void testPrecedence1() {
	n = 0;
	Thread w = new Thread(new NPEHandler2("test"), new TestRunnable());
	w.setUncaughtExceptionHandler(new NPEHandler());
	w.start();
	try {
	    w.join();
	} catch (InterruptedException e) {
	}
	assertEquals(n, 1);
	n = 0;
    }

    /* The handler of a thread's ThreadGroup has precedence over
     * the default handler. */
    @Test public void testPrecedence2() {
	n = 0;
	Thread w = new Thread(new NPEHandler2("test"), new TestRunnable());
	w.setDefaultUncaughtExceptionHandler(new NPEHandler());
	w.start();
	try {
	    w.join();
	} catch (InterruptedException e) {
	}
	assertEquals(n, 2);
	n = 0;
    }

    /* The handler of a ThreadGroup's parent has precedence over
     * the child ThreadGroup and the default handler. */
    @Test public void testPrecedence3() {
	n = 0;
	Thread w =
	    new Thread(new NPEHandler2(new NPEHandler3("parent"), "child"),
		       new TestRunnable());
	w.setDefaultUncaughtExceptionHandler(new NPEHandler());
	w.start();
	try {
	    w.join();
	} catch (InterruptedException e) {
	}
	// FIXME: should be 3 but is 2, contradicting Sun's documentation
	// assertEquals(n, 3);
	n = 0;
    }
}
