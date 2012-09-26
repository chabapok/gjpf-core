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
import gov.nasa.jpf.jvm.ClassLoaderInfo;
import gov.nasa.jpf.jvm.Heap;
import gov.nasa.jpf.jvm.KernelState;
import gov.nasa.jpf.jvm.LoadOnJPFRequired;
import gov.nasa.jpf.jvm.SystemState;
import gov.nasa.jpf.jvm.ThreadInfo;
import gov.nasa.jpf.jvm.Types;


/**
 * Create new array of reference
 * ..., count => ..., arrayref
 */
public class ANEWARRAY extends NewArrayInstruction {

  public ANEWARRAY (String typeDescriptor){
    type = Types.getTypeSignature(typeDescriptor, true);
  }

  public Instruction execute (SystemState ss, KernelState ks, ThreadInfo ti) {
    ClassInfo cls = ti.getMethod().getClassInfo();

    // resolve the component class first
    String compType = Types.getTypeName(type);
    if(Types.isReferenceSignature(type)) {
      try {
        cls.resolveReferencedClass(compType);
      } catch(LoadOnJPFRequired lre) {
        return ti.getPC();
      }
    }

    // there is no clinit for array classes, but we still have  to create a class object
    // since its a builtin class, we also don't have to bother with NoClassDefFoundErrors
    String clsName = "[" + type;
    ClassInfo ci = ClassInfo.getResolvedClassInfo(clsName);

    if (!ci.isRegistered()) {
      ci.registerClass(ti);
      ci.setInitialized();
    }

    arrayLength = ti.pop();
    if (arrayLength < 0){
      return ti.createAndThrowException("java.lang.NegativeArraySizeException");
    }

    Heap heap = ti.getHeap();
    if (heap.isOutOfMemory()) { // simulate OutOfMemoryError
      return ti.createAndThrowException("java.lang.OutOfMemoryError",
                                        "trying to allocate new " +
                                          Types.getTypeName(type) +
                                        "[" + arrayLength + "]");
    }
    
    // pushes the object reference on the top stack frame
    ti.push(heap.newArray(type, arrayLength, ti), true);

    ss.checkGC(); // has to happen after we push the new object ref
    
    return getNext(ti);
  }

  public int getLength () {
    return 3; // opcode, index1, index2
  }
  
  public int getByteCode () {
    return 0xBD;
  }
  
  public void accept(InstructionVisitor insVisitor) {
	  insVisitor.visit(this);
  }
}
