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
package gov.nasa.jpf.jvm;

import gov.nasa.jpf.Config;
import gov.nasa.jpf.jvm.choice.ThreadChoiceFromSet;


/**
 * the general policy is that we only create Thread CGs here (based on their
 * status), but we don't change any thread or lock status. This has to happen
 * in the instruction before calling the factory
 */

public class DefaultSchedulerFactory implements SchedulerFactory {

  protected JVM vm;
  protected SystemState ss;

  boolean breakAll;

  public DefaultSchedulerFactory (Config config, JVM vm, SystemState ss) {
    this.vm = vm;
    this.ss = ss;

    breakAll = config.getBoolean("cg.threads.break_all", false);
  }

  /*************************************** internal helpers *****************/

  /**
   * post process a list of choices. This is our primary interface towards
   * subclasses (together with overriding the relevant insn APIs
   */
  protected ThreadInfo[] filter ( ThreadInfo[] list) {
    // we have nothing to do, but subclasses can use it to
    // shuffle the order (e.g. to avoid the IdleFilter probblem),
    // or to filter out the top priorities
    return list;
  }

  protected ChoiceGenerator<ThreadInfo> getRunnableCG () {
    ThreadInfo[] choices = getRunnablesIfChoices();
    if (choices != null) {
      return new ThreadChoiceFromSet( choices, true);
    } else {
      return null;
    }
  }

  protected ChoiceGenerator<ThreadInfo> getSyncCG (ElementInfo ei, ThreadInfo ti) {
    return getRunnableCG();
  }

  /**************************************** our choice acquisition methods ***/

  /**
   * get list of all runnable threads
   */
  protected ThreadInfo[] getRunnables() {
    ThreadList tl = vm.getThreadList();
    return filter(tl.getRunnableThreads());
  }

  /**
   * return a list of runnable choices, or null if there is only one
   */
  protected ThreadInfo[] getRunnablesIfChoices() {
    ThreadList tl = vm.getThreadList();

    if (tl.getRunnableThreadCount() > 1) {
      return filter(tl.getRunnableThreads());
    } else {
      return null;
    }
  }

  protected ThreadInfo[] getRunnablesWith (ThreadInfo ti) {
    ThreadList tl = vm.getThreadList();
    return filter( tl.getRunnableThreadsWith(ti));
  }

  protected ThreadInfo[] getRunnablesWithout (ThreadInfo ti) {
    ThreadList tl = vm.getThreadList();
    return filter( tl.getRunnableThreadsWithout(ti));
  }


  /************************************ the public interface towards the insns ***/

  public ChoiceGenerator<ThreadInfo> createSyncMethodEnterCG (ElementInfo ei, ThreadInfo ti) {
    return createMonitorEnterCG(ei, ti);
  }

  public ChoiceGenerator<ThreadInfo> createSyncMethodExitCG (ElementInfo ei, ThreadInfo ti) {
    return null; // nothing, left mover
  }

  public ChoiceGenerator<ThreadInfo> createMonitorEnterCG (ElementInfo ei, ThreadInfo ti) {
    if (ti.isBlocked()) { // we have to return something
      if (ss.isAtomic()) {
        ss.setBlockedInAtomicSection();
      }

      return new ThreadChoiceFromSet(getRunnables(), true);

    } else {
      if (ss.isAtomic()) {
        return null;
      }

      return getSyncCG(ei, ti);
    }
  }

  public ChoiceGenerator<ThreadInfo> createMonitorExitCG (ElementInfo ei, ThreadInfo ti) {
    return null; // nothing, left mover
  }


  public ChoiceGenerator<ThreadInfo> createWaitCG (ElementInfo ei, ThreadInfo ti, long timeOut) {
    if (ss.isAtomic()) {
      ss.setBlockedInAtomicSection();
    }

    return new ThreadChoiceFromSet(getRunnables(), true);
  }

  public ChoiceGenerator<ThreadInfo> createNotifyCG (ElementInfo ei, ThreadInfo ti) {
    if (ss.isAtomic()) {
      return null;
    }

    ThreadInfo[] waiters = ei.getWaitingThreads();
    if (waiters.length < 2) {
      // if there are less than 2 threads waiting, there is no nondeterminism
      return null;
    } else {
      return new ThreadChoiceFromSet(waiters, false);
    }
  }

  public ChoiceGenerator<ThreadInfo> createNotifyAllCG (ElementInfo ei, ThreadInfo ti) {
    return null; // no nondeterminism here, left mover
  }

  public ChoiceGenerator<ThreadInfo> createSharedFieldAccessCG (ElementInfo ei, ThreadInfo ti) {
    if (ss.isAtomic()) {
      return null;
    }

    return getSyncCG(ei, ti);
  }

  public ChoiceGenerator<ThreadInfo> createSharedArrayAccessCG (ElementInfo ei, ThreadInfo ti) {
    if (ss.isAtomic()) {
      return null;
    }

    // not sure if we really want to do this, since there should always be a
    // preceding field access - see ArrayInstruction.isNewPorBoundary()
    return getSyncCG(ei, ti);
  }

  public ChoiceGenerator<ThreadInfo> createThreadStartCG (ThreadInfo newThread) {
    // NOTE if newThread is sync and blocked, it already will be blocked
    // before this gets called

    // we've been going forth & back a number of times with this. The idea was
    // that it would be more intuitive to see a transition break every time we
    // start a new thread, but this causes significant state bloat in case of
    // pure starter threads, i.e. something that simply does
    //   ...
    //   t.start();
    //   return
    //
    // because we get a state branch at the "t.start()" and the "return".
    // It should be safe to go on, since the important thing is to set the new thread
    // runnable.

    if (breakAll) {
      if (ss.isAtomic()) {
        return null;
      }
      return getRunnableCG();

    } else {
      return null;
    }

  }

  public ChoiceGenerator<ThreadInfo> createThreadYieldCG (ThreadInfo yieldThread) {
    if (breakAll) {
      if (ss.isAtomic()) {
        return null;
      }
      return getRunnableCG();

    } else {
      return null;
    }
  }
  
  public ChoiceGenerator<ThreadInfo> createInterruptCG (ThreadInfo interruptedThread) {
    if (ss.isAtomic()) {
      return null;
    }

    return getRunnableCG();
  }

  public ChoiceGenerator<ThreadInfo> createThreadSleepCG (ThreadInfo sleepThread, long millis, int nanos) {
    if (breakAll) {
      if (ss.isAtomic()) {
        return null;
      }
      // we treat this as a simple reschedule
      return createThreadYieldCG(sleepThread);

    } else {
      return null;
    }
  }

  public ChoiceGenerator<ThreadInfo> createThreadTerminateCG (ThreadInfo terminateThread) {
    // terminateThread is already TERMINATED at this point
    ThreadList tl = vm.getThreadList();
    
    // NOTE returning null does not directly define an end state - that's up to
    // a subsequent call to vm.isEndState()
    // <2do> FIXME this is redundant and error prone
    if (tl.hasAnyAliveThread()) {
      return new ThreadChoiceFromSet(getRunnablesWithout(terminateThread), true);
    } else {
      return null;
    }
  }
}
