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

import gov.nasa.jpf.jvm.bytecode.Instruction;

/**
 * this is a StackFrame that can dynamically grow its operand stack size (and associated
 * operand attributes). To be used for floating calls, where we don't want to mis-use the
 * stack of the currently executing bytecode method, since it might not have enough
 * operand stack space. This class is basically an inheritance-decorator for StackFrame
 *
 * DirectCallStackFrames are only used for overlay calls (from native code), i.e.
 * they do not return any values themselves, but they do get the return values of the
 * called methods pushed onto their own operand stack. If the DirectCallStackFrame user
 * needs such return values, it has to do so via ThreadInfo.getReturnedDirectCall()
 *
 * Note that these frames do not appear in a Thread's call stack!
 */
public class DirectCallStackFrame extends DynamicStackFrame {
  
  public DirectCallStackFrame (MethodInfo stub) {
    super(stub, null);
  }

  public DirectCallStackFrame (MethodInfo stub, int nOperandSlots, int nLocalSlots) {
    super(stub, null);

    if (nOperandSlots > 0){
      operands = new int[nOperandSlots];
      isOperandRef = new boolean[nOperandSlots];
    }

    if (nLocalSlots > 0){
      locals = new int[nLocalSlots];
      isLocalRef = new boolean[nLocalSlots];
    }
  }


  public void reset() {
    pc = mi.getInstruction(0);
  }
  
  
  public boolean isDirectCallFrame() {
    return true;
  }

  @Override
  public boolean isSynthetic() {
    return true;
  }

  public String getClassName() {
    return "<direct call>";
  }
  
  public String getSourceFile () {
    return "<direct call>"; // we don't have any
  }
  
  
  // <2do> and a couple more we still have to do
}
