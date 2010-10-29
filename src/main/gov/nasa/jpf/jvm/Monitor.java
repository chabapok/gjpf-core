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

import gov.nasa.jpf.util.HashData;

import java.io.PrintWriter;
import java.util.Arrays;

/**
 * Represents the variable, hash-collapsed pooled data associated with an object
 * that is not related to the object values (->Fields), but to the use of the
 * object for synchronization purposes (locks and signals).
 * 
 */
public class Monitor {
  
  static ThreadInfo[] emptySet = new ThreadInfo[0];
  
  /** the thread owning the lock */
  private ThreadInfo lockingThread;

  /** the nesting level for recursive lock acquisition */
  private int lockCount;
  
  /** the stack depth at the time the lock is first acquired */
  private int lockedStackDepth = -1;
  
  /** the list of waiting or blocked threads */
  ThreadInfo[] lockedThreads;

  /**
   * Creates a new empty monitor.
   */
  public Monitor () {
    lockedThreads = emptySet;
  }

  private Monitor (ThreadInfo locking, int depth, int count, ThreadInfo[] locked) {
    lockingThread = locking;
    lockedStackDepth = depth;
    lockCount = count;
    lockedThreads = locked.clone();
    Arrays.sort(lockedThreads);
  }
  
  void printFields (PrintWriter pw) {
    int i;
    
    pw.print("[");
    if (lockingThread != null) {
      pw.print( "locked by: ");
      pw.print( lockingThread.getName());
    } else {
      pw.print( "unlocked");
    }
    
    pw.print(", lockCount: ");
    pw.print( lockCount);
    
    pw.print(", lockedStackDepth: ");
    pw.print(lockedStackDepth);
    
    pw.print(", locked: {");
    for (i=0; i<lockedThreads.length; i++) {
      if (i > 0) pw.print(',');
      pw.print(lockedThreads[i].getName());
      pw.print(':');
      pw.print(lockedThreads[i].getStateName());
    }
    pw.println("}]");
  }
  
  
  Monitor cloneWithLocked (ThreadInfo ti) {
    return new Monitor(lockingThread, lockedStackDepth, lockCount, add(lockedThreads, ti));
  }

  Monitor cloneWithoutLocked (ThreadInfo ti) {
    return new Monitor(lockingThread, lockedStackDepth, lockCount, remove(lockedThreads, ti));
  }

  public Monitor clone () {
    return new Monitor(lockingThread, lockedStackDepth, lockCount, lockedThreads.clone());
  }
  
  
  /**
   * Compares to another object.
   */
  public boolean equals (Object o) {
    if (o == null) {
      return false;
    }

    if (!(o instanceof Monitor)) {
      return false;
    }

    Monitor m = (Monitor) o;

    if (lockingThread != m.getLockingThread()) {
      return false;
    }

    if (lockCount != m.getLockCount()) {
      return false;
    }
    
    if (lockedStackDepth != m.getLockedStackDepth()) {
      return false;
    }
    
    ThreadInfo[] list = m.lockedThreads;
    if (lockedThreads.length != list.length) {
      return false;
    }

    for (int i = 0; i < lockedThreads.length; i++) {
      if (lockedThreads[i] != list[i]) {
        return false;
      }
    }

    return true;
  }
  

  public void hash (HashData hd) {
    if (lockingThread != null) {
      hd.add(lockingThread.getIndex());
    }
    
    hd.add(lockCount);
    hd.add(lockedStackDepth);
    
    for (int i = 0, l = lockedThreads.length; i < l; i++) {
      hd.add(lockedThreads[i].getIndex());
    }    
  }

  
  public int hashCode () {
    HashData hd = new HashData();
    hash(hd);
    return hd.getValue();
  }
  

  /**
   * Returns the number of nested locks acquired.
   */
  public int getLockCount () {
    return lockCount;
  }


  /**
   * Returns the identifier of the thread holding the lock.
   */
  public ThreadInfo getLockingThread () {
    return lockingThread;
  }


  /**
   * Returns the list of locked threads
   */ 
  public ThreadInfo[] getLockedThreads() {
    return lockedThreads;
  }
  

  public boolean hasLockedThreads () {
    return (lockedThreads.length > 0);
  }
  
  public boolean hasWaitingThreads () {
    for (int i=0; i<lockedThreads.length; i++) {
      if (lockedThreads[i].isWaiting()) {
        return true;
      }
    }

    return false;
  }
  
  /**
   * Returns true if it is possible to lock the monitor.
   */
  public boolean canLock (ThreadInfo th) {
    if (lockingThread == null) {
      return true;
    }

    return (lockingThread == th);
  }


  void setLockingThread (ThreadInfo ti) {
    lockingThread = ti;
  }
  
  public int getLockedStackDepth() {
    return lockedStackDepth;
  }
  
  void setLockedStackDepth(int depth) {
    lockedStackDepth = depth;
  }
  
  void incLockCount () {
    if (lockCount == 0) {
      lockedStackDepth = lockingThread.getStackDepth(); 
    }
    
    lockCount++;
  }
  
  void decLockCount () {
    assert lockCount > 0 : "negative lockCount";
    lockCount--;

    if (lockCount <= 0) {
      lockedStackDepth = -1;
    }
  }
  
  void setLockCount (int lc) {
    assert lc >= 0 : "attempt to set negative lockCount";
    lockCount = lc;
  }
  
  public int objectHashCode () {
    return super.hashCode();
  }

  void resetLockedThreads () {
    lockedThreads = emptySet;
  }

  static ThreadInfo[] add (ThreadInfo[] list, ThreadInfo ti) {
    int len = list.length;
    ThreadInfo[] newList = new ThreadInfo[len+1];
    
    int pos = 0;
    for (; pos < len && ti.compareTo(list[pos]) > 0; pos++) {
      newList[pos] = list[pos];
    }
    newList[pos] = ti;
    for (; pos < len; pos++) {
      newList[pos+1] = list[pos];
    }

    return newList;
  }
  
  void addLocked (ThreadInfo ti) {
    lockedThreads = add(lockedThreads, ti);
  }
  
  static ThreadInfo[] remove (ThreadInfo[] list, ThreadInfo ti) {
    int len = list.length;

    if (len == 0) { // nothing to remove from
      return list;
      
    } else if (len == 1) {  // one element list optimization
      if (list[0] == ti) {
        return emptySet;
      } else {
        return list;
      }
    } else {
      for (int i=0; i<len; i++) {
        if (list[i] == ti) {
          int newLen = len-1;
          ThreadInfo[] newList = new ThreadInfo[newLen];
          if (i > 0) {
            System.arraycopy(list, 0, newList, 0, i);
          }
          if (i < newLen) {
            System.arraycopy(list, i+1, newList, i, newLen-i);
          }
          return newList;
        }
      }
      // else, not in list:
      return list;
    }
  }
  
  void removeLocked (ThreadInfo ti) {
    lockedThreads = remove(lockedThreads, ti);
  }
}
