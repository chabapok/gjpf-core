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
package gov.nasa.jpf.jvm.bytecode;

import gov.nasa.jpf.vm.ChoiceGenerator;
import gov.nasa.jpf.vm.ElementInfo;
import gov.nasa.jpf.vm.Instruction;
import gov.nasa.jpf.vm.VM;
import gov.nasa.jpf.vm.SystemState;
import gov.nasa.jpf.vm.ThreadInfo;


/**
 * abstraction for all array instructions
 */
public abstract class ArrayInstruction extends JVMInstruction {

  int arrayRef;
  int index;

  public ArrayInstruction () {
  }


  /**
   * return size of array elements in stack words (long,double: 2, all other: 1)
   * e.g. used to determine where the object reference is on the stack
   */
  protected int getElementSize () {
    return 1;
  }
  
  protected boolean createAndSetArrayCG ( ElementInfo ei, ThreadInfo ti,
                                int aref, int idx, boolean isRead) {
    // unfortunately we can't do the field filtering here because
    // there is no field info for array instructions - the reference might
    // have been on the operand-stack for a while, and the preceeding
    // GET_FIELD already was a scheduling point (i.e. we can't cache it)
    
    VM vm = ti.getVM();
    ChoiceGenerator<?> cg = vm.getSchedulerFactory().createSharedArrayAccessCG(ei, ti);
    if (vm.setNextChoiceGenerator(cg)){
      // we need to set the array access info (ref, index) before it is
      // lost from the insn cache (insn might get reexecuted later-on
      // on non-shared object
      //ArrayAccess aac = new ArrayAccess(aref,idx,isRead);
      //cg.setAttr(aac);

      ti.skipInstructionLogging();
      return true;
    }
        
    return false;
  }
  
  /**
   * this depends on the SchedulerFactory in use being smart about which array
   * element ops really can cause races, otherwise there is a high chance this
   * is a major state exploder
   */
  boolean isNewPorBoundary (ElementInfo ei, ThreadInfo ti) {
    //return false;

    // ei is the array object
    ei = ei.getInstanceWithUpdatedSharedness(ti);
    return (!ti.checkPorFieldBoundary() && ei.isShared());
  }

  /**
   * only makes sense from an executeInstruction or instructionExecuted listener
   */
  public int getArrayRef (ThreadInfo ti){
    if (ti.isPreExec()){
      return peekArrayRef(ti);
    } else {
      return arrayRef;
    }
  }

  public int getIndex (ThreadInfo ti){
    if (ti.isPreExec()){
      return peekIndex(ti);
    } else {
      return index;
    }
  }

  abstract protected int peekArrayRef (ThreadInfo ti);

  // we need this to be abstract because of the LongArrayStore insns
  abstract protected int peekIndex (ThreadInfo ti);

  public abstract boolean isRead();

  public void accept(InstructionVisitor insVisitor) {
	  insVisitor.visit(this);
  }
}
