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

import gov.nasa.jpf.JPFListener;

/**
 * interface to register for callbacks by the JVM
 * Observer role in equally named pattern
 * 
 * Note that we only have notifications for generic events, NOT for conditions that
 * are property specific, and especially nothing that is just triggered from an extension.
 * If listeners are used to implement high level properties, the notifications should be
 * used to implement properties, not to report some property violation that was detected
 * by JPF 
 */
public interface VMListener extends JPFListener {
  
  /**
   * JVM is about to execute the next instruction
   */
  void executeInstruction (JVM vm);
  
  /**
   * JVM has executed the next instruction
   * (can be used to analyze branches, monitor PUTFIELD / GETFIELD and
   * INVOKExx / RETURN instructions)
   */
  void instructionExecuted (JVM vm);
  
  /**
   * new Thread entered run() method
   */
  void threadStarted (JVM vm);
    
  /**
   * thread waits to acquire a lock
  // NOTE: vm.getLastThreadInfo() does NOT have to be the running thread, as this
  // notification can occur as a result of a lock operation in the current thread
   */
  void threadBlocked (JVM vm);
  
  /**
   * thread is waiting for signal
   */
  void threadWaiting (JVM vm);

  /**
   * thread got notified
   */
  void threadNotified (JVM vm);
    
  /**
   * thread got interrupted
   */
  void threadInterrupted (JVM vm);
  
  /**
   * Thread exited run() method
   */
  void threadTerminated (JVM vm);

  /**
   * new thread was scheduled by JVM
   */
  void threadScheduled (JVM vm); // this might go into the choice generator notifications

  /**
   * new class was loaded
   */
  void classLoaded (JVM vm);
  
  /**
   * new object was created
   */
  void objectCreated (JVM vm);
  
  /**
   * object was garbage collected (after potential finalization)
   */
  void objectReleased (JVM vm);
  
  /**
   * notify if an object lock was taken (this includes automatic
   * surrender during a wait())
   */
  void objectLocked (JVM vm);
  
  /**
   * notify if an object lock was released (this includes automatic
   * reacquisition after a notify())
   */
  void objectUnlocked (JVM vm);
  
  /**
   * notify if a wait() is executed
   */
  void objectWait (JVM vm);
  
  /**
   * notify if an object notifies a single waiter
   */
  void objectNotify (JVM vm);

  /**
   * notify if an object notifies all waiters
   */
  void objectNotifyAll (JVM vm);
  
  void gcBegin (JVM vm);
  
  void gcEnd (JVM vm);
  
  /**
   * exception was thrown
   */
  void exceptionThrown (JVM vm);

  /**
   * exception causes top frame to be purged
   */
  void exceptionBailout (JVM vm);

  /**
   * exception handled by current top frame
   */
  void exceptionHandled (JVM vm);

  /**
   * a new ChoiceGenerator was registered, which means we have a transition boundary
   */
  void choiceGeneratorSet (JVM vm);
  
  /**
   * the next choice was requested from a previously registered ChoiceGenerator
   */
  void choiceGeneratorAdvanced (JVM vm);
  
  /**
   * a ChoiceGnerator has returned all his choices
   */
  void choiceGeneratorProcessed (JVM vm);
  
}

