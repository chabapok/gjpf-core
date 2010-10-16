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
import gov.nasa.jpf.jvm.KernelState;
import gov.nasa.jpf.jvm.MethodInfo;
import gov.nasa.jpf.jvm.SystemState;
import gov.nasa.jpf.jvm.ThreadInfo;
import gov.nasa.jpf.util.Source;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.apache.bcel.classfile.ConstantPool;
import org.apache.bcel.generic.InstructionHandle;

/**
 * common root of all JPF bytecode instruction classes 
 */
public abstract class Instruction implements InstructionVisit {

  protected static final List<String> unimplemented = new ArrayList<String>();
  protected int position; // accumulated position (prev pos + prev bc-length)
  protected int offset;   // consecutive index of instruction
  protected MethodInfo mi;        // the method this insn belongs to
  protected String asString;  // on demand string representation

  abstract public int getByteCode();

  // to allow a classname and methodname context for each instruction
  public void setContext(String className, String methodName, int lineNumber,
          int offset) {
  }

  /**
   * is this the first instruction in a method
   */
  public boolean isFirstInstruction() {
    return (offset == 0);
  }


  /**
   * answer if this is a potential loop closing jump
   */
  public boolean isBackJump() {
    return false;
  }

  public boolean isDeterministic(SystemState ss, KernelState ks, ThreadInfo ti) {
    return true;
  }

  /**
   * is this one of our own, artificial insns?
   */
  public boolean isExtendedInstruction() {
    return false;
  }

  public boolean isExecutable(SystemState ss, KernelState ks, ThreadInfo th) {
    return true;
  }

  public MethodInfo getMethodInfo() {
    return mi;
  }


  /**
   * that's used for explicit construction of MethodInfos (synthetic methods)
   */
  public void setMethodInfo(MethodInfo mi) {
    this.mi = mi;
  }

  /**
   * this returns the next instruction within the same method, which might
   * or might not be the next one to execute (branches, overlay calls etc.)
   */
  public Instruction getFollowingInstruction() {
    return mi.getInstruction(offset + 1);
  }

  public int getOffset() {
    return offset;
  }

  public int getPosition() {
    return position;
  }

  public void setLocation(int off, int pos) {
    offset = off;
    position = pos;
  }

  /**
   * return the length in bytes of this instruction.
   * override if this is not 1
   */
  public int getLength() {
    return 1;
  }

  public Instruction getPrev() {
    if (offset > 0) {
      return mi.getInstruction(offset - 1);
    } else {
      return null;
    }
  }

  /**
   * this is for listeners that process instructionExecuted(), but need to
   * determine if there was a CG registration, an overlayed direct call
   * (like clinit) etc.
   * The easy case is the instruction not having been executed yet, in
   * which case ti.getNextPC() == null
   * There are two cases for re-execution: either nextPC was set to the
   * same insn (which is what CG creators usually use), or somebody just
   * pushed another stackframe that executes something which will return to the
   * same insn (that is what automatic <clinit> calls and the like do - we call
   * it overlays)
   */
  public boolean isCompleted(ThreadInfo ti) {
    Instruction nextPc = ti.getNextPC();

    if (nextPc == null) {
      return ti.isTerminated();

    } else {

      return (nextPc != this) && (ti.getStackFrameExecuting(this, 1) == null);
    }

    // <2do> how do we account for exceptions? 
  }

  public boolean isSchedulingRelevant(SystemState ss, KernelState ks, ThreadInfo ti) {
    return false;
  }

  /**
   * this is the real workhorse
   * returns next instruction to execute in this thread
   * 
   * <2do> it's unfortunate we roll every side effect into this method, because
   * it diminishes the value of the 'executeInstruction' notification: all
   * insns that require some sort of late binding (InvokeVirtual, GetField, ..)
   * are not yet fully analyzable (e.g. the callee of InvokeVirtuals is not
   * known yet), putting the burden of duplicating the related code of
   * execute() in the listener. It would be better if we factor this
   * 'prepareExecution' out of execute()
   */
  public abstract Instruction execute(SystemState ss, KernelState ks, ThreadInfo ti);

  public boolean examine(SystemState ss, KernelState ks, ThreadInfo th) {
    return false;
  }

  public boolean examineAbstraction(SystemState ss, KernelState ks,
          ThreadInfo th) {
    return false;
  }

  public String toString() {
    if (asString == null) {
      asString = getMnemonic();
    }
    return asString;
  }

  public String getMnemonic() {
    String s = getClass().getSimpleName();
    return s.toLowerCase();
  }

  public int getLineNumber() {
    return mi.getLineNumber(this);
  }

  public String getSourceLine() {
    ClassInfo ci = mi.getClassInfo();
    if (ci != null) {
      int line = mi.getLineNumber(this);
      String file = ci.getSourceFileName();

      Source src = Source.getSource(file);
      if (src != null) {
        String srcLine = src.getLine(line);
        if (srcLine != null) {
          return srcLine;
        }
      }

      return "(" + file + ":" + line + ")"; // fallback

    } else {
      return "[synthetic] " + mi.getName();
    }
  }

  /**
   * this returns a "pathname:line" string
   */
  public String getFileLocation() {
    ClassInfo ci = mi.getClassInfo();
    if (ci != null) {
      int line = mi.getLineNumber(this);
      String fname = ci.getSourceFileName();
      return (fname + ":" + line);
    } else {
      return "[synthetic] " + mi.getName();
    }
  }

  /**
   * this returns a "filename:line" string
   */
  public String getFilePos() {
    ClassInfo ci = mi.getClassInfo();
    int line = mi.getLineNumber(this);
    String file = ci.getSourceFileName();
    int i = file.lastIndexOf(File.separatorChar);
    if (i >= 0) {
      file = file.substring(i + 1);
    }

    if (file != null) {
      return (file + ':' + line);
    } else {
      return ("pc " + position);
    }
  }

  /**
   * this returns a "class.method(line)" string
   */
  public String getSourceLocation() {
    ClassInfo ci = mi.getClassInfo();

    if (ci != null) {
      String s = ci.getName() + '.' + mi.getName() +
              '(' + getFilePos() + ')';
      return s;

    } else {
      return null;
    }
  }

  protected void setPeer(org.apache.bcel.generic.Instruction i, ConstantPool cp) {
    // nothing here
  }

  public void init(InstructionHandle h, int off, MethodInfo m,
          ConstantPool cp) {
    position = h.getPosition();
    offset = off;
    mi = m;
    //asString = h.getInstruction().toString(cp);
    setPeer(h.getInstruction(), cp);
  }

  public void init(MethodInfo mi, int offset, int position) {
    this.mi = mi;
    this.offset = offset;
    this.position = position;
  }

  public boolean causedClinitCalls(ThreadInfo ti, ClassInfo ci) {

    //if (!ti.isResumedInstruction(this)) {

    // <2do> why would a resumed insn not require class init? it might be resumed
    // for other reasons than having previously forced a <clinit>

    if (!ci.isRegistered()){
      ci.registerClass(ti);
    }

    if (!ci.isInitialized()) {
      if (ci.initializeClass(ti)) {
        //ti.skipInstructionLogging();
        return true; // there are new <clinit> frames on the stack, execute them
      }
    }
    //}

    return false;
  }

  /**
   * this is returning the next Instruction to execute, to be called to obtain
   * the return value of execute() if this is not a branch insn
   *
   * Be aware of that we might have had exceptions caused by our execution
   * (-> lower frame), or we might have had overlaid calls (-> higher frame),
   * i.e. we can't simply assume it's the following insn. We have to
   * acquire this through the top frame of the ThreadInfo.
   *
   * note: the System.exit() problem should be gone, now that it is implemented
   * as ThreadInfo state (TERMINATED), rather than purged stacks
   */
  public Instruction getNext (ThreadInfo ti) {
    return ti.getPC().getFollowingInstruction();
  }
  
  public void accept(InstructionVisitor insVisitor) {
	  insVisitor.visit(this);
  }
}
