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
import gov.nasa.jpf.util.SparseObjVector;

import static gov.nasa.jpf.util.OATHash.*;

/**
 * an AllocationContext that uses a hash value for comparison. This is
 * lossy - heap implementations using this class have to check/handle
 * collisions.
 * 
 * However, given that we have very good hash data (search global object
 * references), the probability of collisions is low enough that heap
 * implementations might simply report this as a problem requiring a
 * non-lossy AllocationContext.
 * 
 * Ideally, we would like to hash the host VM thread context too (esp.
 * for system allocations), but host VM stack traces are expensive, and it is
 * arguable if would be too strict (e.g. when using a dedicated allocator
 * method called from alternative branches of the caller) 
 * 
 * note - this is a HashMap key type which has to obey the hashCode/equals contract
 */
public class HashedAllocationContext implements AllocationContext {
    
  static final Throwable throwable = new Throwable(); // to avoid frequent allocations
  
  
  static int mixinSUTStack (int h, ThreadInfo ti) {
    h = hashMixin(h, ti.hashCode()); 
    
    for (StackFrame frame = ti.getTopFrame(); frame != null; frame = frame.getPrevious() ) {
      if (!(frame instanceof DirectCallStackFrame)) {
        Instruction insn = frame.getPC();
        h = hashMixin(h, insn.hashCode());        
      }
    }
    
    return h;
  }
  
  // <2do> this method is problematic - we should not assume a fixed stack position
  // but we can't just mixin the whole stack since this would cause different class object
  // allocation contexts (registerClass can happen from lots of locations).
  // At the other end of the spectrum, MJIEnv.newXX() is not differentiating enough since
  // those are convenience methods used from a gazillion of places that might share
  // the same SUT state
  static int mixinJPFStack (int h) {
    throwable.fillInStackTrace();
    StackTraceElement[] ste = throwable.getStackTrace();
    
    // we know that is at least 4 levels deeper:
    //   0: mixinJPFStack
    //   1: getXAllocationContext
    //   2: heap.getXAllocationContext
    //   3: heap.newObject/newArray/newString
    //   4: <allocating methodr>
    StackTraceElement e = ste[4]; // see note below regarding fixed call depth fragility
  
    // <2do> this sucks - MJIEnv.newObject/newArray/newString are used from a gazillion of places that might not differ in SUT state
    if (e.getClassName().equals("gov.nasa.jpf.jvm.MJIEnv") && e.getMethodName().startsWith("new")){
      // there is not much use to loop, since we don't have a good end condition
      e = ste[5];
    }
    
    // NOTE - this is fragile since it is implementation dependent and differs between JPF runs, but the
    // string hash is usually bad
    // the names are interned string from the class object
    // h = hashMixin( h, System.identityHashCode(e.getClassName()));
    // h = hashMixin( h, System.identityHashCode(e.getMethodName()));
    
    h = hashMixin(h, e.getClassName().hashCode());
    h = hashMixin(h, e.getMethodName().hashCode());
    h = hashMixin(h, e.getLineNumber());

    return h;
  }
  
  /*
   * !! NOTE: these always have to be at a fixed call distance of the respective Heap.newX() call:
   * 
   *  ConcreteHeap.newX()
   *    ConcreteHeap.getXAllocationContext()
   *      ConcreteAllocationContext.getXAllocationContext()
   *      
   * that means the allocation site is at stack depth 4. This is not nice, but there is no
   * good heuristic we could use instead, other than assuming there is a newObject/newArray/newString
   * call on the stack
   */
  
  /**
   * this one is for allocations that should depend on the SUT thread context (such as all
   * explicit NEW executions)
   */
  public static AllocationContext getSUTAllocationContext (ClassInfo ci, ThreadInfo ti) {
    int h = 0;
    
    //--- the type that gets allocated
    h = hashMixin(h, ci.hashCode());
    
    //--- the SUT execution context (allocating ThreadInfo and its stack)
    h = mixinSUTStack( h, ti);
    
    //--- the JPF execution context (from where in the JPF code the allocation happens)
    h = mixinJPFStack( h);
    
    h = hashFinalize(h);
    HashedAllocationContext ctx = new HashedAllocationContext(h);

    return ctx;
  }
  
  /**
   * this one is for allocations that should NOT depend on the SUT thread context (such as
   * automatic allocation of java.lang.Class objects by the VM)
   * 
   * @param anchor a value that can be used to provide a context that is heap graph specific (such as
   * a classloader or class object reference)
   */
  public static AllocationContext getSystemAllocationContext (ClassInfo ci, ThreadInfo ti, int anchor) {
    int h = 0;
    
    h = hashMixin(h, ci.hashCode());
    
    // in lieu of the SUT stack, add some magic salt and the anchor
    h = hashMixin(h, 0x14040118);
    h = hashMixin(h, anchor);
    
    //--- the JPF execution context (from where in the JPF code the allocation happens)
    h = mixinJPFStack( h);
    
    h = hashFinalize(h);
    HashedAllocationContext ctx = new HashedAllocationContext(h);
    
    return ctx;
  }

  public static boolean init (Config conf) {
    //pool = new SparseObjVector<HashedAllocationContext>();
    return true;
  }
  
  //--- instance data
  
  // rolled up hash value for all context components
  protected int id;

  
  //--- instance methods
  
  protected HashedAllocationContext (int id) {
    this.id = id;
  }
  
  
  public boolean equals (Object o) {
    if (o instanceof HashedAllocationContext) {
      HashedAllocationContext other = (HashedAllocationContext)o;
      return id == other.id; 
    }
    
    return false;
  }
  
  /**
   * @pre: must be the same for two objects that result in equals() returning true
   */
  public int hashCode() {
    return id;
  }
  
  // for automatic field init allocations
  public AllocationContext extend (ClassInfo ci, int anchor) {
    int h = hash( id, anchor, ci.hashCode());
    return new HashedAllocationContext(h);
  }
}
