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

import gov.nasa.jpf.jvm.ClassInfo;
import gov.nasa.jpf.jvm.DynamicArea;
import gov.nasa.jpf.jvm.JVM;
import gov.nasa.jpf.jvm.KernelState;
import gov.nasa.jpf.jvm.NoClassInfoException;
import gov.nasa.jpf.jvm.StackFrame;
import gov.nasa.jpf.jvm.SystemState;
import gov.nasa.jpf.jvm.ThreadInfo;

import org.apache.bcel.classfile.ConstantPool;


/**
 * Create new object
 * ... => ..., objectref
 */
public class NEW extends Instruction {
  protected String cname;

  public void setPeer (org.apache.bcel.generic.Instruction i, ConstantPool cp) {
    cname = cp.constantToString(cp.getConstant(
                                      ((org.apache.bcel.generic.NEW) i).getIndex()));
  }
  
  public String getClassName()    // Needed for Java Race Finder
  {
     return(cname);
  }

  public Instruction execute (SystemState ss, KernelState ks, ThreadInfo ti) {
    JVM vm = ti.getVM();
    DynamicArea da = vm.getDynamicArea();
    ClassInfo ci;
    StackFrame frame = ti.getTopFrame();

    try {
      ci = ClassInfo.getResolvedClassInfo(cname);

    } catch (NoClassInfoException cx){
      // can be any inherited class or required interface
      return ti.createAndThrowException("java.lang.NoClassDefFoundError", cx.getMessage());
    }

    if (!ci.isRegistered()){
      ci.registerClass(ti);
    }

    // since this is a NEW, we also have to pushClinit
    if (!ci.isInitialized()) {
      if (ci.initializeClass(ti)) {
        return ti.getPC();  // reexecute this instruction once we return from the clinits
      }
    }

    if (da.getOutOfMemory()) { // simulate OutOfMemoryError
      return ti.createAndThrowException("java.lang.OutOfMemoryError",
                                        "trying to allocate new " + cname);
    }

    int objRef = da.newObject(ci, ti);

    // pushes the return value onto the stack
    ti.push(objRef, true);

    ss.checkGC(); // has to happen after we push the new object ref

    return getNext(ti);
  }

  public int getLength() {
    return 3; // opcode, index1, index2
  }

  public int getByteCode () {
    return 0xBB;
  }
  
  public void accept(InstructionVisitor insVisitor) {
	  insVisitor.visit(this);
  }

  public String toString() {
    return "new " + cname;
  }
}
