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

import gov.nasa.jpf.util.IntTable;
import gov.nasa.jpf.util.ObjVector;

/**
 * Statics implementation that uses a simple ObjVector as the underlying container.
 * 
 * The ids used to retrieve ElementInfos are dense and search global, computation is based 
 * on the assumption that each ClassLoader can only define one class per binary class name
 */
public class ObjVectorStatics implements Statics {

  static class OVMemento implements Memento<Statics> {
    ObjVector.Snapshot<ElementInfo> eiSnap;
    
    OVMemento (ObjVectorStatics statics){
      eiSnap = statics.elementInfos.getSnapshot();
    }
    
    @Override
    public Statics restore(Statics inSitu) {
      ObjVectorStatics statics = (ObjVectorStatics) inSitu;
      statics.elementInfos.restore(eiSnap);
      return statics;
    }
  }
  
  protected ObjVector<ElementInfo> elementInfos;
  
  // search global class ids (for this ClassLoader only)
  // NOTE this is per instance so that each one is as dense as possible, but since
  // it is search global it does NOT have to be restored and we can copy the reference when cloning
  protected int nextId;
  protected IntTable<String> ids;
  
  
  //--- construction
  
  protected int computeId (ClassInfo ci) {
    String clsName = ci.getName();
    IntTable.Entry<String> e = ids.get(clsName);
    if (e == null) {
      int id = nextId++;
      ids.put( clsName, id);
      return id;
      
    } else {
      return e.val;
    }
  }
  
  protected StaticElementInfo createStaticElementInfo (ClassInfo ci, ThreadInfo ti) {
    Fields   f = ci.createStaticFields();
    Monitor  m = new Monitor();

    StaticElementInfo ei = new StaticElementInfo( ci, f, m, ti, ci.getClassObjectRef());

    ci.initializeStaticData(ei, ti);

    return ei;
  }
  
  @Override
  public int newClass(ClassInfo ci, ThreadInfo ti) {
    int id = computeId( ci);
    
    StaticElementInfo ei = createStaticElementInfo( ci, ti);
    elementInfos.set(id, ei);
    
    return id;
  }

  
  //--- accessors
  
  @Override
  public ElementInfo get(int id) {
    return elementInfos.get(id);
  }

  @Override
  public ElementInfo getModifiable(int id) {
    ElementInfo ei = elementInfos.get(id);
    if (ei.isFrozen()) {
      ei = ei.deepClone();
      ei.defreeze();
      elementInfos.set(id, ei);
    }
    
    return ei;
  }

  
  //--- state restoration
  
  @Override
  public Memento<Statics> getMemento(MementoFactory factory) {
    return null;
  }

  @Override
  public Memento<Statics> getMemento() {
    return null;
  }
}
