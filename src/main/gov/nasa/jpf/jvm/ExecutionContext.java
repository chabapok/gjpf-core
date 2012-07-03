//
// Copyright (C) 2012 United States Government as represented by the
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

import gov.nasa.jpf.Config;
import gov.nasa.jpf.util.OATHash;

import java.util.Arrays;
import java.util.HashMap;

/**
 * class that captures execution context consisting of executing thread and 
 * pc's of ti's current StackFrames
 * 
 * note that we pool (i.e. use static factory methods) in order to avoid
 * creating a myriad of redundant objects
 */
public class ExecutionContext {

  // this is search global
  static private HashMap<ExecutionContext,ExecutionContext> ccCache = new HashMap<ExecutionContext,ExecutionContext>();
  
  protected ThreadInfo ti;
  protected Instruction[] cc;
  protected int hashCode; // computed once during construction (from LookupContext)
  
  // a mutable ExecutionContext that is only used internally to avoid creating superfluous new instances to
  // find out if we already have seen a similar one
  private static class LookupContext extends ExecutionContext {
    int stackDepth;
    
    LookupContext (){
      cc = new Instruction[64];
    }
    
    public int getStackDepth(){
      return stackDepth;
    }    
  }
  
  private static LookupContext lookupContext = new LookupContext();
  
  static boolean init (Config config) {
    ccCache = new HashMap<ExecutionContext,ExecutionContext>();
    return true;
  }
  
  public static synchronized ExecutionContext getExecutionContext (ThreadInfo ti){
    int stackDepth = ti.getStackDepth();
    int h = 0;
    
    lookupContext.ti = ti;
    lookupContext.stackDepth = stackDepth;
    
    h = OATHash.hashMixin(h, ti.getId());
    
    Instruction[] cc = lookupContext.cc;
    if (cc.length < stackDepth){
      cc = new Instruction[stackDepth];
      lookupContext.cc = cc;
    }

    int i=0;
    for (StackFrame f = ti.getTopFrame(); f != null; f = f.getPrevious()){
      Instruction insn = f.getPC();
      cc[i++] = insn;
      h = OATHash.hashMixin(h, insn.hashCode());
    }
    h = OATHash.hashFinalize(h);
    lookupContext.hashCode = h;
    
    ExecutionContext ec = ccCache.get(lookupContext);
    if (ec == null){
      ec = new ExecutionContext(ti, Arrays.copyOf(cc, stackDepth), h);
      ccCache.put(ec, ec);
    }
    
    return ec;
  }
  
  protected ExecutionContext(){
    // for subclassing
  }
  
  // we only construct this from a LookupContext, which already has all the data
  private ExecutionContext (ThreadInfo ti, Instruction[] cc, int hashCode){
    this.ti = ti;
    this.cc = cc;
    this.hashCode = hashCode;
  }
  
  public int hashCode(){
    return hashCode;
  }
  
  public int getStackDepth(){
    return cc.length;
  }
    
  public boolean equals (Object o){
    if (o == this){ // identity shortcut
      return true;
      
    } else {
      if (o instanceof ExecutionContext){
        ExecutionContext other = (ExecutionContext)o;
        if (hashCode == other.hashCode){ // we might get here because of bin masking
          if (ti.getId() == other.ti.getId()) {
            Instruction[] ccOther = other.cc;
            if (cc.length == other.getStackDepth()) {
              for (int i = 0; i < cc.length; i++) {
                if (cc[i] != ccOther[i]) {
                  return false;
                }
              }
              return true;
            }
          }
        }
      }
      
      return false;
    }
  }
  
  
  /** mostly for debugging purposes */
  public String toString(){
    StringBuffer sb = new StringBuffer();
    sb.append("(tid=");
    sb.append(ti.getId());
    sb.append(",stack=[");
    for (int i=0; i<cc.length; i++){
      if (i>0){
        sb.append(',');
      }
      sb.append(cc[i]);
    }
    sb.append("])");
    return sb.toString();
  }
}
