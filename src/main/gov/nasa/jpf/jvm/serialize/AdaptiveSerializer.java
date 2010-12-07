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

package gov.nasa.jpf.jvm.serialize;

import gov.nasa.jpf.jvm.ChoiceGenerator;
import gov.nasa.jpf.jvm.ElementInfo;
import gov.nasa.jpf.jvm.StackFrame;
import gov.nasa.jpf.jvm.ThreadInfo;

/**
 * a CG type adaptive, canonicalizing & filtering serializer.
 *
 * This came to bear by accidentally discovering that JPF seems to finds
 * concurrency defects by just serializing the thread states, their topmost stack
 * frames and the objects directly referenced from there.
 *
 * for non-scheduling points, we just fall back to serializing statics, all thread
 * stacks and all the data reachable from there
 *
 * <2do> this seems too conservative, we can probably at least reduce the set
 * of statics to serialize to what is used from within this thread, which can
 * be determined at class load time (e.g. by a listener looking for GET/PUTSTATIC
 * targets)
 */
public class AdaptiveSerializer extends CFSerializer {

  boolean traverseObjects;
  boolean isSchedulingPoint;

  @Override
  protected void initReferenceQueue() {
    super.initReferenceQueue();
    traverseObjects = true;

    ChoiceGenerator<?> nextCg = vm.getNextChoiceGenerator();
    isSchedulingPoint = (nextCg != null) && nextCg.isSchedulingPoint();
  }

  //@Override
  protected void serializeThread(ThreadInfo ti){
    addObjRef(ti.getThreadObjectRef());

    buf.add(ti.getState().ordinal()); // maybe that's enough for locking ?
    buf.add(ti.getStackDepth());

    // locking state
    addObjRef(ti.getLockRef());
    for (ElementInfo ei: ti.getLockedObjects()){
      addObjRef(ei.getIndex());
    }

    if (isSchedulingPoint){
      // only serialize the top frame, assuming that we have CGs on all relevant insns
      serializeFrame(ti.getTopFrame());
    } else {
      // serialize all frames
      for (StackFrame frame : ti) {
        serializeFrame(frame);
      }
    }
  }


  @Override
  protected void queueReference(ElementInfo ei){
    if (traverseObjects){
      refQueue.add(ei);
    }
  }

  @Override
  protected void processReferenceQueue() {
    if (isSchedulingPoint){
      traverseObjects = false;
    }
    refQueue.process(this);
  }

  //@Override
  protected void serializeStatics(){
    // for thread CGs we skip this - assuming that this is only relevant if there is
    // a class object lock, which is covered by the thread lock info
    if (!isSchedulingPoint){
      // <2do> this seems too conservative - we should only serialize what is
      // used from this thread, which can be collected at class load time
      // by looking at GET/PUTSTATIC targets (and their superclasses)
      super.serializeStatics();
    }
  }
}
