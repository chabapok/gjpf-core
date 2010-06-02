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

import gov.nasa.jpf.jvm.BooleanChoiceGenerator;
import gov.nasa.jpf.jvm.ChoiceGenerator;
import gov.nasa.jpf.jvm.KernelState;
import gov.nasa.jpf.jvm.SystemState;
import gov.nasa.jpf.jvm.ThreadInfo;

/**
 * abstraction for all comparison instructions
 */
public abstract class IfInstruction extends Instruction {
  protected int targetPosition;  // insn position at jump offset
  protected Instruction target;  // jump target
  
  protected boolean conditionValue;  /** value of last evaluation of branch condition */
  
  /**
   * return which branch was taken. Only useful after instruction got executed
   * WATCH OUT - 'true' means the jump condition is met, which logically is
   * the 'false' branch
   */
  public boolean getConditionValue() {
    return conditionValue;
  }
    
  /**
   *  Added so that SimpleIdleFilter can detect do-while loops when 
   * the while statement evaluates to true.
   */
  public boolean isBackJump () { 
    return (conditionValue) && (targetPosition <= position);
  }
  
  public void setPeer (org.apache.bcel.generic.Instruction insn,
                       org.apache.bcel.classfile.ConstantPool cp) {
    targetPosition = ((org.apache.bcel.generic.BranchInstruction) insn).getTarget().getPosition();
  }
  
  /** 
   * retrieve value of jump condition from operand stack
   * (not ideal to have this public, but some listeners might need it for
   * skipping the insn, plus we require it for subclass factorization)
   */
  public abstract boolean popConditionValue(ThreadInfo ti);
  
  public Instruction getTarget() {
    if (target == null) {
      target = mi.getInstructionAt(targetPosition);
    }
    return target;
  }
  
  public Instruction execute (SystemState ss, KernelState ks, ThreadInfo ti) {
    conditionValue = popConditionValue(ti);
    if (conditionValue) {
      return getTarget();
    } else {
      return getNext(ti);
    }
  }

  /**
   * use this as a delegatee in overridden executes of derived IfInstructions
   * (e.g. for symbolic execution)
   */
  protected Instruction executeBothBranches (SystemState ss, KernelState ks, ThreadInfo ti){
    if (!ti.isFirstStepInsn()) {
      ChoiceGenerator cg = new BooleanChoiceGenerator(ti.getVM().getConfig(),
                                                      mi.getName() +
                                                      getClass().getSimpleName());
      ss.setNextChoiceGenerator(cg);
      return this;
      
    } else {
      ChoiceGenerator cg = ss.getChoiceGenerator();
      assert (cg != null) && (cg instanceof BooleanChoiceGenerator) :
        "expected BooleanChoiceGenerator, got: " + cg;
      
      popConditionValue(ti); // we are not interested in concrete values
      
      conditionValue = ((BooleanChoiceGenerator)cg).getNextChoice();
      
      if (conditionValue) {
        return getTarget();
      } else {
        return getNext(ti);
      }

    }
  }
  
  public String toString () {
    if (asString == null) {
      asString = getMnemonic() + " " + getTarget().getOffset();
    }
    return asString;
  }
  
  public int getLength() {
    return 3; // usually opcode, bb1, bb2
  }
  
  public void accept(InstructionVisitor insVisitor) {
	  insVisitor.visit(this);
  }
}
