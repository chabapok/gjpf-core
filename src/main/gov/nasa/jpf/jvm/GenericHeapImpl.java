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

import java.util.ArrayList;
import java.util.Iterator;

import gov.nasa.jpf.Config;
import gov.nasa.jpf.JPFException;
import gov.nasa.jpf.util.ArrayObjectQueue;
import gov.nasa.jpf.util.HashData;
import gov.nasa.jpf.util.IntTable;
import gov.nasa.jpf.util.IntVector;
import gov.nasa.jpf.util.ObjVector;
import gov.nasa.jpf.util.ObjectQueue;
import gov.nasa.jpf.util.Processor;

/**
 * this is an abstract root for Heap implementations, providing a standard
 * mark&sweep collector, change attribute management, and generic pinDownList,
 * weakReference and internString handling
 * 
 * The concrete Heap implementors have to provide the ElementInfo collection
 * and associated getters, allocators and iterators
 */
public abstract class GenericHeapImpl implements Heap, Iterable<ElementInfo> {

  protected class ElementInfoMarker implements Processor<ElementInfo>{
    public void process (ElementInfo ei) {
      ei.markRecursive( GenericHeapImpl.this); // this might in turn call queueMark
    }
  }
  
  protected JVM vm;

  // list of pinned down references (this is only efficient for a small number of objects)
  // this is copy-on-first-write
  protected IntVector pinDownList;

  // interned Strings
  // this is copy-on-first-write
  protected IntTable<String> internStrings;


  // the usual drill - the lower 2 bytes are sticky, the upper two ones 
  // hold change status and transient (transition local) flags
  int attributes;

  static final int ATTR_GC            = 0x0001;
  static final int ATTR_OUT_OF_MEMORY = 0x0002;
  static final int ATTR_RUN_FINALIZER = 0x0004;

  static final int ATTR_ELEMENTS_CHANGED  = 0x10000;
  static final int ATTR_PINDOWN_CHANGED   = 0x20000;
  static final int ATTR_INTERN_CHANGED    = 0x40000;
  static final int ATTR_ATTRIBUTE_CHANGED = 0x80000;

  // masks and sets
  static final int ATTR_STORE_MASK = 0x0000ffff;
  static final int ATTR_ANY_CHANGED = (ATTR_ELEMENTS_CHANGED | ATTR_PINDOWN_CHANGED | ATTR_INTERN_CHANGED | ATTR_ATTRIBUTE_CHANGED);


  //--- these objects are only used during gc

  // used to keep track of marked WeakRefs that might have to be updated (no need to restore, only transient use during gc)
  protected ArrayList<ElementInfo> weakRefs;

  protected ObjectQueue<ElementInfo> markQueue = new ArrayObjectQueue<ElementInfo>();

  // this is set to false upon backtrack/restore
  protected boolean liveBitValue;
  
  protected ElementInfoMarker elementInfoMarker = new ElementInfoMarker();
  
  //--- constructors

  public GenericHeapImpl (Config config, KernelState ks){
    vm = JVM.getVM();

    pinDownList = new IntVector(256);
    attributes |= ATTR_PINDOWN_CHANGED; // no need to clone on next add

    internStrings = new IntTable<String>(8);
    attributes |= ATTR_INTERN_CHANGED; // no need to clone on next add

    if (config.getBoolean("vm.finalize", true)){
      attributes |= ATTR_RUN_FINALIZER;
    }

    if (config.getBoolean("vm.sweep",true)){
      attributes |= ATTR_GC;
    }
  }


  protected DynamicElementInfo createElementInfo (ClassInfo ci, Fields f, Monitor m, ThreadInfo ti){
    return new DynamicElementInfo(ci,f,m,ti);
  }
  
  //--- pinDown handling
  protected void addToPinDownList (int objref){
    if ((attributes & ATTR_PINDOWN_CHANGED) == 0) {
      pinDownList = pinDownList.clone();
      attributes |= ATTR_PINDOWN_CHANGED;
    }
    pinDownList.add(objref);
  }
  
  protected void removeFromPinDownList (int objref){
    if ((attributes & ATTR_PINDOWN_CHANGED) == 0) {
      pinDownList = pinDownList.clone();
      attributes |= ATTR_PINDOWN_CHANGED;
    }
    pinDownList.removeFirst(objref);    
  }

  @Override
  public void registerPinDown(int objref){
    ElementInfo ei = get(objref);
    if (ei != null) {
      if (ei.incPinDown()){
        addToPinDownList(objref);
      }
    } else {
      throw new JPFException("pinDown reference not a live object: " + objref);
    }
  }

  @Override
  public void releasePinDown(int objref){
    ElementInfo ei = get(objref);
    if (ei != null) {
      if (ei.decPinDown()){
        removeFromPinDownList(objref);
      }
    } else {
      throw new JPFException("pinDown reference not a live object: " + objref);
    }
  }  
  void markPinDownList (){
    if (pinDownList != null){
      int len = pinDownList.size();
      for (int i=0; i<len; i++){
        int objref = pinDownList.get(i);
        queueMark(objref);
      }
    }
  }
  
  //--- weak reference handling
  
  public void registerWeakReference (ElementInfo ei) {
    if (weakRefs == null) {
      weakRefs = new ArrayList<ElementInfo>();
    }

    weakRefs.add(ei);
  }
  
  /**
   * reset all weak references that now point to collected objects to 'null'
   * NOTE: this implementation requires our own Reference/WeakReference implementation, to
   * make sure the 'ref' field is the first one
   */
  protected void cleanupWeakRefs () {
    if (weakRefs != null) {
      for (ElementInfo ei : weakRefs) {
        Fields f = ei.getFields();
        int    ref = f.getIntValue(0); // watch out, the 0 only works with our own WeakReference impl
        if (ref != -1) {
          ElementInfo refEi = get(ref);
          if ((refEi == null) || (refEi.isNull())) {
            // we need to make sure the Fields are properly state managed
            ei.setReferenceField(ei.getFieldInfo(0), -1);
          }
        }
      }

      weakRefs = null;
    }
  }
  
  protected abstract int getNewElementInfoIndex (ClassInfo ci, ThreadInfo ti, String allocLocation);
  protected abstract void set (int index, ElementInfo ei);
  
  //--- allocators
    
  @Override
  public int newObject(ClassInfo ci, ThreadInfo ti, String allocLocation) {
    // create the thing itself
    Fields f = ci.createInstanceFields();
    Monitor m = new Monitor();
    ElementInfo ei = createElementInfo(ci, f, m, ti);

    int index = getNewElementInfoIndex( ci, ti, allocLocation);
    ei.setObjectRef(index);
    set(index, ei);

    attributes |= ATTR_ELEMENTS_CHANGED;

    // and do the default (const) field initialization
    ci.initializeInstanceData(ei, ti);

    vm.notifyObjectCreated(ti, ei);
    
    // note that we don't return -1 if 'outOfMemory' (which is handled in
    // the NEWxx bytecode) because our allocs are used from within the
    // exception handling of the resulting OutOfMemoryError (and we would
    // have to override it, since the VM should guarantee proper exceptions)

    return index;
  }

  @Override
  public int newArray(String elementType, int nElements, ThreadInfo ti, String allocLocation) {
    String type = "[" + elementType;
    ClassInfo ci = ClassInfo.getResolvedClassInfo(type);

    if (!ci.isInitialized()){
      // we do this explicitly here since there are no clinits for array classes
      ci.registerClass(ti);
      ci.setInitialized();
    }

    Fields  f = ci.createArrayFields(type, nElements,
                                     Types.getTypeSize(elementType),
                                     Types.isReference(elementType));
    Monitor  m = new Monitor();
    DynamicElementInfo ei = createElementInfo(ci, f, m, ti);

    int index = getNewElementInfoIndex(ci, ti, allocLocation);
    ei.setObjectRef(index);
    set(index, ei);
    
    attributes |= ATTR_ELEMENTS_CHANGED;

    vm.notifyObjectCreated(ti, ei);

    // see newObject for 'outOfMemory' handling

    return index;
  }

  
  // <2do> these should probably also have locations, but the problem is that they represent bundle allocators
  // (String, char[]) and location should not be shared between different object allocations
  
  @Override
  public abstract int newString(String str, ThreadInfo ti);

  @Override
  public abstract int newInternString(String str, ThreadInfo ti);

  
  //--- abstract accessors
  
  /**
   * public reference lookup
   */
  @Override
  public abstract ElementInfo get (int ref);
  
  /**
   * internal setter (used by generic sweep)
   */
  protected abstract void remove (int ref);
  
  /**
   * return Iterator for all non-null ElementInfo entries
   */
  public abstract Iterator<ElementInfo> iterator();
  
  @Override
  public abstract Iterable<ElementInfo> liveObjects();
  
  
  //--- garbage collection
  
  public boolean isGcEnabled (){
    return (attributes & ATTR_GC) != 0;
  }

  public void setGcEnabled (boolean doGC) {
    if (doGC != isGcEnabled()) {
      if (doGC) {
        attributes |= ATTR_GC;
      } else {
        attributes &= ~ATTR_GC;
      }
      attributes |= ATTR_ATTRIBUTE_CHANGED;
    }
  }
  
  public void unmarkAll(){
    for (ElementInfo ei : liveObjects()){
      ei.setUnmarked();
    }
  }
  
  /**
   * add a non-null, not yet marked reference to the markQueue
   *  
   * called from ElementInfo.markRecursive(). We don't want to expose the
   * markQueue since a copying collector might not have it
   */
  public void queueMark (int objref){
    if (objref == -1) {
      return;
    }

    ElementInfo ei = get(objref);
    if (!ei.isMarked()){ // only add objects once
      ei.setMarked();
      markQueue.add(ei);
    }
  }
  
  /**
   * called during non-recursive phase1 marking of all objects reachable
   * from static fields
   * @aspects: gc
   */
  public void markStaticRoot (int objref) {
    if (objref != -1) {
      queueMark(objref);
    }
  }

  /**
   * called during non-recursive phase1 marking of all objects reachable
   * from Thread roots
   * @aspects: gc
   */
  public void markThreadRoot (int objref, int tid) {
    if (objref != -1) {
      queueMark(objref);
    }
  }
  
  /**
   * this implementation uses a generic ElementInfo iterator, it can be replaced
   * with a more efficient container specific version
   */
  protected void sweep () {
    ThreadInfo ti = vm.getCurrentThread();
    int tid = ti.getId();
    boolean isThreadTermination = ti.isTerminated();
    
    // now go over all objects, purge the ones that are not live and reset attrs for rest
    for (ElementInfo ei : this){
      
      if (ei.isMarked()){ // live object, prepare for next transition & gc cycle
        ei.setUnmarked();
        ei.setAlive(liveBitValue);
        
        ei.cleanUp(this, isThreadTermination, tid);
        
      } else { // object is no longer reachable  
        /** no finalizer support yet
        MethodInfo mi = ei.getClassInfo().getFinalizer();
        if (mi != null){
          // add to finalizer queue, but keep alive until processed
        } else {
        }
        **/
        ei.processReleaseActions();
        
        // <2do> still have to process finalizers here, which might make the object live again
        vm.notifyObjectReleased(ei);
        remove(ei.getObjectRef());
      }
    }    
  }
  
  protected void mark () {
    markQueue.clear();
    
    //--- mark everything in our root set
    markPinDownList();
    vm.getThreadList().markRoots(this); // mark thread stacks
    vm.getStaticArea().markRoots(this); // mark objects referenced from StaticArea ElementInfos

    //--- trace all entries - this gets recursive
    markQueue.processQueue(elementInfoMarker);    
  }
  
  @Override
  public void gc() {
    vm.notifyGCBegin();

    weakRefs = null;
    liveBitValue = !liveBitValue;

    mark();
    
    // at this point all live objects are marked
    sweep();

    cleanupWeakRefs(); // for potential nullification

    vm.processPostGcActions();
    vm.notifyGCEnd();
  }

  /**
   * clean up reference values that are stored outside of reference fields 
   * called from KernelState to process live ElementInfos after GC has finished
   * and only live objects remain in the heap.
   * 
   * <2do> full heap enumeration is BAD - check if this can be moved into the sweep loop
   */
  public void cleanUpDanglingReferences() {
    ThreadInfo ti = ThreadInfo.getCurrentThread();
    int tid = ti.getId();
    boolean isThreadTermination = ti.isTerminated();
    
    for (ElementInfo e : this) {
      if (e != null) {
        e.cleanUp(this, isThreadTermination, tid);
      }
    }
  }
  
  /**
   * check if object is alive. This is here and not in ElementInfo
   * because we might own the liveness bit. In fact, the generic
   * implementation uses bit-toggle to avoid iteration over all live
   * objects at the end of GC
   */
  public boolean isAlive (ElementInfo ei){
    return (ei == null || ei.isMarkedOrAlive(liveBitValue));
  }
  
  //--- state management
  
  // since we can't provide generic implementations, we force concrete subclasses to
  // handle volatile information
  
  @Override
  public abstract void resetVolatiles();

  @Override
  public abstract void restoreVolatiles();
  
  public boolean hasChanged() {
    return (attributes & ATTR_ANY_CHANGED) != 0;
  }
  
  public void markChanged(int objref) {
    attributes |= ATTR_ELEMENTS_CHANGED;
  }

  public void markUnchanged() {
    attributes &= ~ATTR_ANY_CHANGED;
  }
  
  @Override
  public abstract Memento<Heap> getMemento(MementoFactory factory);

  @Override
  public abstract Memento<Heap> getMemento();

  
  //--- out of memory simulation
  
  public boolean isOutOfMemory() {
    return (attributes & ATTR_OUT_OF_MEMORY) != 0;
  }

  public void setOutOfMemory(boolean isOutOfMemory) {
    if (isOutOfMemory != isOutOfMemory()) {
      if (isOutOfMemory) {
        attributes |= ATTR_OUT_OF_MEMORY;
      } else {
        attributes &= ~ATTR_OUT_OF_MEMORY;
      }
      attributes |= ATTR_ATTRIBUTE_CHANGED;
    }
  }


  
  //--- debugging

  @Override
  public void checkConsistency(boolean isStateStore) {
    for (ElementInfo ei : this){
      ei.checkConsistency();
    }
  }
}
