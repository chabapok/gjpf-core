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

import java.util.ArrayList;
import java.util.Set;

import gov.nasa.jpf.Config;
import gov.nasa.jpf.jvm.bytecode.Instruction;


/**
 * MJI NativePeer class for java.lang.Class library abstraction
 */
public class JPF_java_lang_Class {
  
  public static void init (Config conf){
    // we create Method and Constructor objects, so we better make sure these
    // classes are initialized (they already might be so)
    JPF_java_lang_reflect_Method.init(conf);
    JPF_java_lang_reflect_Constructor.init(conf);    
  }
  
  public static boolean isArray____Z (MJIEnv env, int robj) {
    return getReferredClassInfo(env, robj).isArray();
  }

  public static int getComponentType____Ljava_lang_Class_2 (MJIEnv env, int robj) {
    if (isArray____Z(env, robj)) {
      ThreadInfo ti = env.getThreadInfo();
      Instruction insn = ti.getPC();
      ClassInfo ci = getReferredClassInfo(env, robj).getComponentClassInfo();

      if (insn.requiresClinitCalls(ti, ci)) {
        env.repeatInvocation();
        return MJIEnv.NULL;
      }

      return ci.getClassObjectRef();
    }

    return MJIEnv.NULL;
  }

  public static boolean isInstance__Ljava_lang_Object_2__Z (MJIEnv env, int robj,
                                                         int r1) {
    ElementInfo sei = env.getClassElementInfo(robj);
    ClassInfo   ci = sei.getClassInfo();
    ClassInfo   ciOther = env.getClassInfo(r1);

    return (ciOther.isInstanceOf(ci.getName()));
  }

  public static boolean isInterface____Z (MJIEnv env, int robj){
    ClassInfo ci = getReferredClassInfo(env, robj);
    return ci.isInterface();
  }
  
  public static boolean isAssignableFrom__Ljava_lang_Class_2__Z (MJIEnv env, int rcls,
                                                              int r1) {
    ElementInfo sei1 = env.getClassElementInfo(rcls);
    ClassInfo   ci1 = sei1.getClassInfo();

    ElementInfo sei2 = env.getClassElementInfo(r1);
    ClassInfo   ci2 = sei2.getClassInfo();
    
    return ci2.isInstanceOf( ci1.getName());
  }
  
  public static int getAnnotations_____3Ljava_lang_annotation_Annotation_2 (MJIEnv env, int robj){    
    ClassInfo ci = getReferredClassInfo(env, robj);
    AnnotationInfo[] ai = ci.getAnnotations();

    try {
      return env.newAnnotationProxies(ai);
    } catch (ClinitRequired x){
      env.handleClinitRequest(x.getRequiredClassInfo());
      return MJIEnv.NULL;
    }
  }
  
  public static int getAnnotation__Ljava_lang_Class_2__Ljava_lang_annotation_Annotation_2 (MJIEnv env, int robj,
                                                                                int annoClsRef){
    ClassInfo ci = getReferredClassInfo(env, robj);
    ClassInfo aci = JPF_java_lang_Class.getReferredClassInfo(env,annoClsRef);
    
    AnnotationInfo ai = ci.getAnnotation(aci.getName());
    if (ai != null){
      ClassInfo aciProxy = ClassInfo.getAnnotationProxy(aci);
      aciProxy.loadAndInitialize(env.getThreadInfo());
      
      try {
        return env.newAnnotationProxy(aciProxy, ai);
      } catch (ClinitRequired x){
        env.handleClinitRequest(x.getRequiredClassInfo());
        return MJIEnv.NULL;
      }
    } else {
      return MJIEnv.NULL;
    }
  }
  
  public static int getPrimitiveClass__Ljava_lang_String_2__Ljava_lang_Class_2 (MJIEnv env,
                                                            int rcls, int stringRef) {
    String clsName = env.getStringObject(stringRef);

    // we don't really have to check for a valid class name here, since
    // this is a package default method that just gets called from
    // the clinit of box classes
    // note this does NOT return the box class (e.g. java.lang.Integer), which
    // is a normal, functional class, but a primitive class (e.g. 'int') that
    // is rather a strange beast (not even Object derived)
    StaticArea        sa = env.getStaticArea();
    StaticElementInfo ei = sa.get(clsName);
    int               cref = ei.getClassObjectRef();
    env.setBooleanField(cref, "isPrimitive", true);

    return cref;
  }

  public static boolean desiredAssertionStatus____Z (MJIEnv env, int robj) {
    ClassInfo ci = getReferredClassInfo(env,robj);
    return ci.areAssertionsEnabled();
  }
  
  public static int forName__Ljava_lang_String_2__Ljava_lang_Class_2 (MJIEnv env,
                                                                       int rcls,
                                                                       int stringRef) {
    ThreadInfo ti = env.getThreadInfo();
    Instruction insn = ti.getPC();
    String            clsName = env.getStringObject(stringRef);
    
    ClassInfo         ci = ClassInfo.getClassInfo(clsName);    
    if (ci == null){
      env.throwException("java.lang.ClassNotFoundException", clsName);
      return MJIEnv.NULL;
    }
    
    if (insn.requiresClinitCalls(ti, ci)) {
      env.repeatInvocation();
      return MJIEnv.NULL;
    }

    StaticElementInfo ei = env.getStaticArea().get(clsName);
    int               ref = ei.getClassObjectRef();

    return ref;
  }

  /**
   * this is an example of a native method issuing direct calls - otherwise known
   * as a round trip.
   * We don't have to deal with class init here anymore, since this is called
   * via the class object of the class to instantiate
   */
  public static int newInstance____Ljava_lang_Object_2 (MJIEnv env, int robj) {
    ClassInfo ci = getReferredClassInfo(env,robj);   // what are we
    ThreadInfo ti = env.getThreadInfo();
    Instruction insn = ti.getPC();
    int objRef = MJIEnv.NULL;
    
    if(ci.isAbstract()){
      env.throwException("java.lang.InstantiationException");
      return MJIEnv.NULL;
    }
    
    // this is a java.lang.Class instance method, so the class we are instantiating
    // must already be initialized (either by Class.forName() or accessing the
    // .class field
    
    if (!ti.isResumedInstruction(insn)) {
      objRef = env.getDynamicArea().newObject(ci, ti);  // create the thing
    
      MethodInfo mi = ci.getMethod("<init>()V", true);
      if (mi != null) { // Oops - direct call required
        
        // <2do> - still need to handle protected
        if (mi.isPrivate()){
          env.throwException("java.lang.IllegalAccessException", "cannot access non-public member of class " + ci.getName());
          return MJIEnv.NULL;          
        }
        
        MethodInfo stub = mi.createDirectCallStub("[init]");
        DirectCallStackFrame frame = new DirectCallStackFrame(stub, insn);
        frame.push( objRef, true);
        // Hmm, we borrow the DirectCallStackFrame to cache the object ref
        // (don't try that with a normal StackFrame)
        frame.dup();
        ti.pushFrame(frame);
        env.repeatInvocation();
        return MJIEnv.NULL;
      }
        
    } else { // it was resumed after we had to direct call the default ctor
      objRef = ti.getReturnedDirectCall().pop();
    }
    
    return objRef;
  }
  
  public static int getSuperclass____Ljava_lang_Class_2 (MJIEnv env, int robj) {
    ClassInfo ci = getReferredClassInfo(env, robj);
    ClassInfo sci = ci.getSuperClass();
    if (sci != null) {
      return sci.getClassObjectRef();
    } else {
      return MJIEnv.NULL;
    }
  }

  public static int getClassLoader____Ljava_lang_ClassLoader_2 (MJIEnv env, int objref){
    // <2do> - that's a shortcut hack for now, since we don't support user defined
    // ClassLoaders yet
    int clRef = env.getStaticReferenceField("java.lang.ClassLoader", "systemClassLoader");
    return clRef;
  }

  static int getMethod (MJIEnv env, int clsRef, String mname, int argTypesRef,
                        boolean isRecursiveLookup) {

    ClassInfo ci = getReferredClassInfo(env, clsRef);
    
    StringBuffer sb = new StringBuffer(mname);
    sb.append('(');
    int nParams = argTypesRef != MJIEnv.NULL ? env.getArrayLength(argTypesRef) : 0;
    for (int i=0; i<nParams; i++) {
      int cRef = env.getReferenceArrayElement(argTypesRef, i);
      ClassInfo cit = getReferredClassInfo(env, cRef);
      String tname = cit.getName();
      String tcode = tname;
      tcode = Types.getTypeCode(tcode, false);
      sb.append(tcode);
    }
    sb.append(')');
    String fullMthName = sb.toString();

    MethodInfo mi = ci.getReflectionMethod(fullMthName, isRecursiveLookup);
    if (mi == null) {
      env.throwException("java.lang.NoSuchMethodException", ci.getName() + '.' + fullMthName);
      return MJIEnv.NULL;
      
    } else {
      return createMethodObject(env,mi);      
    }
  }

  static int createMethodObject (MJIEnv env, MethodInfo mi) {
    // NOTE - we rely on Constructor and Method peers being initialized
    if (mi.isCtor()){
      return JPF_java_lang_reflect_Constructor.createConstructorObject(env,mi);
    } else {
      return JPF_java_lang_reflect_Method.createMethodObject(env,mi);      
    }
  }
  
  public static int getDeclaredMethod__Ljava_lang_String_2_3Ljava_lang_Class_2__Ljava_lang_reflect_Method_2 (MJIEnv env, int clsRef,
                                                                                                     int nameRef, int argTypesRef) {
    String mname = env.getStringObject(nameRef);
    return getMethod(env, clsRef, mname, argTypesRef, false);
  }

  
  public static int getDeclaredConstructor___3Ljava_lang_Class_2__Ljava_lang_reflect_Constructor_2 (MJIEnv env,
                                                                                               int clsRef,
                                                                                               int argTypesRef){
    int ctorRef =  getMethod(env,clsRef,"<init>",argTypesRef,false);
    return ctorRef;
  }
  
  public static int getMethod__Ljava_lang_String_2_3Ljava_lang_Class_2__Ljava_lang_reflect_Method_2 (MJIEnv env, int clsRef,
                                                                                                     int nameRef, int argTypesRef) {
    try {
    String mname = env.getStringObject(nameRef);
    return getMethod(env, clsRef, mname, argTypesRef, true);
    } catch (Throwable t){
      t.printStackTrace();
      System.exit(0);
      return -1;
    }
  }

  public static int getMethods_____3Ljava_lang_reflect_Method_2 (MJIEnv env, int objref) {
    ClassInfo ciMdc = getReferredClassInfo(env,objref);

    // collect all the public, non-ctor instance methods
    if (!ciMdc.isPrimitive()) {
      ArrayList<MethodInfo> methodInfos = new ArrayList<MethodInfo>();
      
      for (ClassInfo ci = ciMdc; ci != null; ci = ci.getSuperClass()) {
        for (MethodInfo mi : ci.getDeclaredMethodInfos()) {
          // filter out non-public, <clinit> and <init>
          if (mi.isPublic() && !(mi.getName().charAt(0) == '<')) {
            methodInfos.add(mi);
          }
        }
      }

      // and now for the tricky part - if this is an abstract class, we might
      // have to add un-implemented methods from all our interfaces, which is recursive
      if (ciMdc.isAbstract()) {
        for (String ifcName : ciMdc.getAllInterfaces()) {
          ClassInfo ifc = ClassInfo.getClassInfo(ifcName);
          for (MethodInfo mi : ifc.getDeclaredMethodInfos()) {
            MethodInfo match = null;
            String mname = mi.getUniqueName();
            
            for (int i=0; i<methodInfos.size(); i++) {
              MethodInfo m = methodInfos.get(i);
              if (m.getUniqueName().equals(mname)) {
                match = m;
                break;
              }
            }
            
            if (match == null) {
              methodInfos.add(mi);
            }
          }
        }
      }
      
      int n = methodInfos.size();
      int aref = env.newObjectArray("Ljava/lang/reflect/Method;", n);
      for (int i=0; i<n; i++) {
        int mref = createMethodObject(env, methodInfos.get(i));
        env.setReferenceArrayElement(aref,i,mref);
      }

      return aref;

    } else {
      return env.newObjectArray("Ljava/lang/reflect/Method;", 0);
    }
  }
  
  public static int getDeclaredMethods_____3Ljava_lang_reflect_Method_2 (MJIEnv env, int objref) {
    ClassInfo ci = getReferredClassInfo(env,objref);
    MethodInfo[] methodInfos = ci.getDeclaredMethodInfos();
    
    // we have to filter out the ctors and the static init
    int nMth = methodInfos.length;
    for (int i=0; i<methodInfos.length; i++){
      if (methodInfos[i].getName().charAt(0) == '<'){
        methodInfos[i] = null;
        nMth--;
      }
    }
    
    int aref = env.newObjectArray("Ljava/lang/reflect/Method;", nMth);
    
    for (int i=0, j=0; i<methodInfos.length; i++) {
      if (methodInfos[i] != null){
        int mref = createMethodObject(env, methodInfos[i]);
        env.setReferenceArrayElement(aref,j++,mref);
      }
    }
    
    return aref;
  }
  
  static int getConstructors (MJIEnv env, int objref, boolean publicOnly){
    ClassInfo ci = getReferredClassInfo(env,objref);
    ArrayList<MethodInfo> ctors = new ArrayList<MethodInfo>();
    
    // we have to filter out the ctors and the static init
    for (MethodInfo mi : ci.getDeclaredMethodInfos()){
      if (mi.getName().equals("<init>")){
        if (!publicOnly || mi.isPublic()) {
          ctors.add(mi);
        }
      }
    }
    
    int nCtors = ctors.size();
    int aref = env.newObjectArray("Ljava/lang/reflect/Constructor;", nCtors);
    
    for (int i=0; i<nCtors; i++){
      env.setReferenceArrayElement(aref, i, createMethodObject(env, ctors.get(i)));
    }
    
    return aref;
  }
  
  public static int getConstructors_____3Ljava_lang_reflect_Constructor_2 (MJIEnv env, int objref){
    return getConstructors(env, objref, true);
  }  
  
  public static int getDeclaredConstructors_____3Ljava_lang_reflect_Constructor_2 (MJIEnv env, int objref){
    return getConstructors(env, objref, false);
  }
  
  public static int getConstructor___3Ljava_lang_Class_2__Ljava_lang_reflect_Constructor_2 (MJIEnv env, int clsRef,
                                                                                       int argTypesRef){
    // <2do> should only return a public ctor 
    return getMethod(env,clsRef, "<init>",argTypesRef,false);
  }
  
  public static int getDeclaredFields_____3Ljava_lang_reflect_Field_2 (MJIEnv env, int objRef) {
    
    ThreadInfo ti = env.getThreadInfo();
    Instruction insn = ti.getPC();
    ClassInfo fci = ClassInfo.getClassInfo("java.lang.reflect.Field");
    
    if (insn.requiresClinitCalls(ti, fci)) {
      env.repeatInvocation();
      return MJIEnv.NULL;
    }

    
    ClassInfo ci = getReferredClassInfo(env,objRef);
    int nInstance = ci.getNumberOfDeclaredInstanceFields();
    int nStatic = ci.getNumberOfStaticFields();
    int aref = env.newObjectArray("Ljava/lang/reflect/Field;", nInstance + nStatic);
    int i, j=0;
    
    for (i=0; i<nStatic; i++) {
      FieldInfo fi = ci.getStaticField(i);
      int regIdx = JPF_java_lang_reflect_Field.registerFieldInfo(fi);
      int eidx = env.newObject(fci);
      ElementInfo ei = env.getElementInfo(eidx);
      
      ei.setIntField("regIdx", regIdx);
      env.setReferenceArrayElement(aref,j++,eidx);
    }    
    
    for (i=0; i<nInstance; i++) {
      FieldInfo fi = ci.getDeclaredInstanceField(i);
      
      int regIdx = JPF_java_lang_reflect_Field.registerFieldInfo(fi);
      int eidx = env.newObject(fci);
      ElementInfo ei = env.getElementInfo(eidx);
      
      ei.setIntField("regIdx", regIdx);
      env.setReferenceArrayElement(aref,j++,eidx);
    }
    
    return aref;
  }
  
  static int getField (MJIEnv env, int clsRef, int nameRef, boolean isRecursiveLookup) {
    ClassInfo ci = getReferredClassInfo(env, clsRef);
    String fname = env.getStringObject(nameRef);
    FieldInfo fi = null;
    
    if (isRecursiveLookup) {
      fi = ci.getInstanceField(fname);
      if (fi == null) {
        fi = ci.getStaticField(fname);
      }      
    } else {
        fi = ci.getDeclaredInstanceField(fname);
        if (fi == null) {
          fi = ci.getDeclaredStaticField(fname);
        }
    }
    
    if (fi == null) {      
      env.throwException("java.lang.NoSuchFieldException", ci.getName() + '.' + fname);
      return MJIEnv.NULL;
      
    } else {
      ThreadInfo ti = env.getThreadInfo();
      Instruction insn = ti.getPC();
      ClassInfo fci = ClassInfo.getClassInfo("java.lang.reflect.Field");
      
      if (insn.requiresClinitCalls(ti, fci)) {
        env.repeatInvocation();
        return MJIEnv.NULL;
      }
      
      int regIdx = JPF_java_lang_reflect_Field.registerFieldInfo(fi);
      int eidx = env.newObject(fci);
      ElementInfo ei = env.getElementInfo(eidx);
      
      ei.setIntField("regIdx", regIdx);
      return eidx;
    }
  }
  
  public static int getDeclaredField__Ljava_lang_String_2__Ljava_lang_reflect_Field_2 (MJIEnv env, int clsRef, int nameRef) {
    return getField(env,clsRef,nameRef, false);
  }  
 
  public static int getField__Ljava_lang_String_2__Ljava_lang_reflect_Field_2 (MJIEnv env, int clsRef, int nameRef) {
    return getField(env,clsRef,nameRef, true);    
  }

  public static int getModifiers____I (MJIEnv env, int clsRef){
    ClassInfo ci = getReferredClassInfo(env,clsRef);
    return ci.getModifiers();
  }
  
  public static int getEnumConstants (MJIEnv env, int clsRef){
    ClassInfo ci = getReferredClassInfo(env,clsRef);
    if (ci.getSuperClass().getName().equals("java.lang.Enum")) {
      ArrayList<FieldInfo> list = new ArrayList<FieldInfo>();
      String cName = ci.getName();
      
      for (FieldInfo fi : ci.getDeclaredStaticFields()) {
        if (fi.isFinal() && cName.equals(fi.getType())){
          list.add(fi);
        }
      }
      
      int aRef = env.newObjectArray(cName, list.size());      
      StaticElementInfo sei = ci.getStaticElementInfo();
      int i=0;
      for (FieldInfo fi : list){
        env.setReferenceArrayElement( aRef, i++, sei.getReferenceField(fi));
      }
      return aRef;
    }
    
    return MJIEnv.NULL;
  }
  
  static ClassInfo getReferredClassInfo (MJIEnv env, int robj) {
    return env.getReferredClassInfo(robj);
  }
  
  static public int getInterfaces_____3Ljava_lang_Class_2 (MJIEnv env, int clsRef){
    ClassInfo ci = getReferredClassInfo(env,clsRef);
    int aref = MJIEnv.NULL;
    ThreadInfo ti = env.getThreadInfo();
    
    // contrary to the API doc, this only returns the interfaces directly
    // implemented by this class, not it's bases
    // <2do> this is not exactly correct, since the interfaces should be ordered
    Set<String> ifcNames = ci.getInterfaces();
    aref = env.newObjectArray("Ljava/lang/Class;", ifcNames.size());
    
    int i=0;
    for (String ifc : ifcNames){
      ClassInfo ici = ClassInfo.getClassInfo(ifc);
      
      if (!ici.isInitialized()) {
        if (ici.loadAndInitialize(ti, ti.getPC()) > 0) {
          env.repeatInvocation();
          return MJIEnv.NULL; // doesn't matter
        }
      }
      
      env.setReferenceArrayElement(aref, i++, ici.getClassObjectRef());
    }
    
    return aref;
  }
  
}
