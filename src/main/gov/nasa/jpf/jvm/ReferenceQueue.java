 //
// Copyright (C) 2010 United States Government as represented by the
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

/**
 * add class used to recursively mark live heap objects.
 * We use an explicit add to avoid recursive calls that can run out of
 * stack space when traversing long reference chains (e.g. linked lists)
 */
public class ReferenceQueue {

  static final int MAX_FREE = 1024;

  static class Entry {
    Entry next; // single linked list

    int objref;  // reference value
  }

  Entry markEnd;
  Entry markHead;

  // since Entry objects are used/processed during the mark phase
  // in rapid succession, we cache up to MAX_FREE of them
  int nFree;
  Entry free;

  public void add(int objref) {
    Entry e;

    if (nFree > 0){ // reuse a cached Entry object
      e = free;
      free = e.next;
      nFree--;

    } else {
      e = new Entry();
    }

    e.objref = objref;
    e.next = null;

    if (markEnd != null) {
      markEnd.next = e;
    } else {
      markHead = e;
    }

    markEnd = e;
  }

  public void process( ReferenceProcessor proc) {
    for (Entry e = markHead; e != null; ) {
      proc.processReference( e.objref);

      if (nFree < MAX_FREE){
        // recycle to save some allocation and a lot of shortliving garbage
        Entry next = e.next;
        e.next = (nFree++ > 0) ? free : null;
        free = e;
        e = next;

      } else {
        e = e.next;
      }
    }
    clear();
  }

  public void clear () {
    markHead = null;
    markEnd = null;

    // don't reset nFree and free since we limit the memory size of our cache
    // and the Entry object do not reference anything
  }
}
