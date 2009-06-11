//
// Copyright (C) 2006 United States Government as represented by the
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
/**
 * this is a raw test class for detection of thread-shared fields, i.e.
 * it executes the garbage collection based reachability analysis
 */

package gov.nasa.jpf.test.mc.threads;

import gov.nasa.jpf.Property;
import gov.nasa.jpf.util.test.TestJPF;
import gov.nasa.jpf.tools.PreciseRaceDetector;
import org.junit.Test;

class SharedObject {
  int instanceField;
  int whatEver;
}


public class RaceTest extends TestJPF {

  static final Class<? extends Property>  PROPERTY = PreciseRaceDetector.class;
  static final String LISTENER = "+jpf.listener=" + PROPERTY.getName();

  public static void main (String[] args) {
    runTestsOfThisClass(args);
  }

  static int staticField;

  @Test public void testStaticRace () {
    if (verifyUnhandledException("java.lang.RuntimeException")) {

      Runnable r1 = new Runnable() {

        public void run() {
          staticField = 1;
          if (staticField != 1) {
            throw new RuntimeException("r1 detected race!");
          }
        }
      };

      Runnable r2 = new Runnable() {

        public void run() {
          staticField = 0;
          if (staticField != 0) {
            throw new RuntimeException("r2 detected race!");
          }
        }
      };

      Thread t1 = new Thread(r1);
      Thread t2 = new Thread(r2);

      t1.start();
      t2.start();
    }
  }

  @Test public void testStaticRaceNoThrow () {
    if (verifyPropertyViolation(PROPERTY, LISTENER)) {
      Runnable r1 = new Runnable() {

        public void run() {
          staticField = 1;
        }
      };

      Runnable r2 = new Runnable() {

        public void run() {
          staticField = 0;
        }
      };

      Thread t1 = new Thread(r1);
      Thread t2 = new Thread(r2);

      t1.start();
      t2.start();
    }
  }
  
  
  @Test public void testInstanceRace () {
    if (verifyUnhandledException("java.lang.RuntimeException")) {
      final SharedObject o = new SharedObject();

      Runnable r1 = new Runnable() {

        SharedObject d = o;

        public void run() {
          d.instanceField = 1;
          if (d.instanceField != 1) {
            throw new RuntimeException("r1 detected race!");
          }
        }
      };

      Runnable r2 = new Runnable() {

        SharedObject d = o;

        public void run() {
          d.instanceField = 0;
          if (d.instanceField != 0) {
            throw new RuntimeException("r2 detected race!");
          }
        }
      };

      Thread t1 = new Thread(r1);
      Thread t2 = new Thread(r2);

      t1.start();
      t2.start();
    }
  }

  @Test public void testInstanceRaceNoThrow () {
    if (verifyPropertyViolation(PROPERTY, LISTENER)) {
      final SharedObject o = new SharedObject();

      Runnable r1 = new Runnable() {

        SharedObject d = o;

        public void run() {
          d.instanceField = 1;
        }
      };

      Runnable r2 = new Runnable() {

        SharedObject d = o;

        public void run() {
          d.instanceField = 0;
        }
      };

      Thread t1 = new Thread(r1);
      Thread t2 = new Thread(r2);

      t1.start();
      t2.start();
    }
  }

  @Test public void testInstanceRaceListenerExclude () {
    if (verifyNoPropertyViolation(LISTENER, "+race.exclude="+ SharedObject.class.getName() + ".*")){
      testInstanceRaceNoThrow();
    }
  }

  @Test public void testInstanceRaceListenerInclude () {
    if (verifyPropertyViolation(PROPERTY, LISTENER,
                                 "+race.include=" + SharedObject.class.getName() + ".instanceField")){
      testInstanceRaceNoThrow();
    }
  }

  @Test public void testStaticRaceListenerIncludeOther () {
    if (verifyNoPropertyViolation(LISTENER, "+race.include=" + SharedObject.class.getName() + ".instanceField")){
      testStaticRaceNoThrow();
    }
  }


  //--- these are tests to check false positives

  static class SameInsnRunnable implements Runnable {
    SharedObject o = new SharedObject();

    public void run () {
      o.instanceField = 42;  // same insn, different 'o', no race
    }
  }

  @Test public void testSameInsnOtherObject () {
    if (verifyNoPropertyViolation(LISTENER)) {
      SameInsnRunnable r1 = new SameInsnRunnable();
      SameInsnRunnable r2 = new SameInsnRunnable();

      Thread t = new Thread(r1);
      t.start();

      r2.run();
    }
  }

  @Test public void testSameObjectOtherField() {
    if (verifyNoPropertyViolation(LISTENER)) {
      final SharedObject o = new SharedObject();

      Runnable r = new Runnable() {

        public void run() {
          o.instanceField = 42;
        }
      };

      Thread t = new Thread(r);

      o.whatEver = -42;  // different field, no race
    }
  }
  
  
  //--- try variations of locks
  
  class AnotherSharedObject {
    Object lock1 = new Object();
    Object lock2 = new Object();
    
    int x = 0;
  }
  
  @Test public void testNoSync() {
    if (verifyUnhandledException("java.lang.RuntimeException")) {

      final AnotherSharedObject o = new AnotherSharedObject();
      Runnable r = new Runnable() {

        public void run() {
          o.x++;
          if (o.x == 0) {
            throw new RuntimeException("testNoSync race");
          }
        }
      };
      Thread t = new Thread(r);
      t.start();

      o.x--;
    }
  }
  
  
  @Test public void testTSync() {
    if (verifyUnhandledException("java.lang.RuntimeException")) {

      final AnotherSharedObject o = new AnotherSharedObject();
      Runnable r = new Runnable() {

        public void run() {
          synchronized (o.lock1) {
            o.x++;
            if (o.x == 0) {
              throw new RuntimeException("testT1Sync race");
            }
          }
        }
      };
      Thread t = new Thread(r);
      t.start();

      // no sync
      o.x--;
    }
  }
  
  @Test public void testMainSync () {
    if (verifyUnhandledException("java.lang.RuntimeException")) {

      final AnotherSharedObject o = new AnotherSharedObject();
      Runnable r = new Runnable() {

        public void run() {
          // not synchronized
          o.x++;
          if (o.x == 0) {
            throw new RuntimeException("testMainSync race");
          }
        }
      };
      Thread t = new Thread(r);
      t.start();

      synchronized (o.lock1) {
        o.x--;
      }
    }
  }
  
  @Test public void testBothSync () {
    if (verifyNoPropertyViolation()) {
      final AnotherSharedObject o = new AnotherSharedObject();
      Runnable r = new Runnable() {

        public void run() {
          synchronized (o.lock1) {
            o.x++;
            if (o.x == 0) {
              throw new RuntimeException("testBothSync race??");
            }
          }
        }
      };
      Thread t = new Thread(r);
      t.start();

      synchronized (o.lock1) {
        o.x = 0;
      }
    }
  }

  @Test public void testWrongSync () {
    if (verifyUnhandledException("java.lang.RuntimeException")) {

      final AnotherSharedObject o = new AnotherSharedObject();

      Runnable r = new Runnable() {

        public void run() {
          synchronized (o.lock1) {
            o.x++;
            if (o.x == 0) {
              throw new RuntimeException("testWrongSync race");
            }
          }
        }
      };
      Thread t = new Thread(r);
      t.start();

      synchronized (o.lock2) {
        o.x--;
      }
    }
  }
   
}


