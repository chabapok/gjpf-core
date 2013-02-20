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
package gov.nasa.jpf.vm;

import gov.nasa.jpf.Config;
import gov.nasa.jpf.JPFException;
import gov.nasa.jpf.jvm.classfile.ClassPath;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

/**
 * @author Nastaran Shafiei <nastaran.shafiei@gmail.com>
 * 
 * Represents the JPF system classloader which models the following hierarchy.
 * 
 *            ----------------
 *            | Bootstrap CL |
 *            ----------------
 *                   |
 *            ----------------
 *            | Extension CL |
 *            ----------------
 *                   |
 *           ------------------
 *           | Application CL |
 *           ------------------
 *           
 * Since in the standard VM user does not have any control over the built-in 
 * classloaders hierarchy, in JPF, we model all three by an instance of 
 * SystemClassLoader which is responsible to load classes from Java API, 
 * standard extensions packages, and the local file system.     
 */
public class SystemClassLoaderInfo extends ClassLoaderInfo {

  // note that initialization requires these to be startup classes
  protected ClassInfo classLoaderClassInfo;
  protected ClassInfo objectClassInfo;
  protected ClassInfo classClassInfo;
  protected ClassInfo stringClassInfo;
  protected ClassInfo weakRefClassInfo;
  protected ClassInfo refClassInfo;
  protected ClassInfo enumClassInfo;
  protected ClassInfo threadClassInfo;
  protected ClassInfo threadGroupClassInfo;
  protected ClassInfo charArrayClassInfo;

  protected int unCachedClasses = 10;

  protected SystemClassLoaderInfo (VM vm, int appIndex){
     super(vm, MJIEnv.NULL, computeSystemClassPath(vm.getConfig()), null);
     
     // this is a hack - for user ClassLoaderInfos, we compute the id from the corresponding
     // objRef of the JPF ClassLoader object. For SystemClassLoaderInfos we can't do that because
     // they are created before we can create JPF objects. However, this is safe if we know
     // the provided id is never going to be the objRef of a future ClassLoader object, which is
     // a safe bet since the first objects created are all system Class objects that are never going to
     // be recycled.
     this.id = computeId(appIndex); // has to be set here so that ClassInfos have proper uniqueIds
  }
  
  /**
   * Builds the classpath for our system class loaders which resemblances the 
   * location for classes within,
   *        - Java API ($JREHOME/Classes/classes.jar,...) 
   *        - standard extensions packages ($JREHOME/lib/ext/*.jar)
   *        - the local file system ($CLASSPATH)
   */
  protected static ClassPath computeSystemClassPath (Config config){
    ClassPath classPath = new ClassPath();

    for (File f : config.getPathArray("boot_classpath")){
      classPath.addPathName(f.getAbsolutePath());
    }

    for (File f : config.getPathArray("classpath")){
      classPath.addPathName(f.getAbsolutePath());
    }

    // finally, we load from the standard Java libraries
    String v = System.getProperty("sun.boot.class.path");
    if (v != null) {
      for (String pn : v.split(File.pathSeparator)){
        classPath.addPathName(pn);
      }
    }
    
    return classPath;
  }
 

  @Override
  public ClassInfo getResolvedClassInfo (String clsName){
    ClassInfo ci = super.getResolvedClassInfo(clsName);
    
    if (unCachedClasses > 0){
      updateCachedClassInfos(ci);
    }
    
    return ci;
  }

  public boolean isSystemClassLoader() {
    return true;
  }

  static boolean checkClassName (String clsName) {
    if ( !clsName.matches("[a-zA-Z_$][a-zA-Z_$0-9.]*")) {
      return false;
    }

    // well, those two could be part of valid class names, but
    // in all likeliness somebody specified a filename instead of
    // a classname
    if (clsName.endsWith(".java")) {
      return false;
    }
    if (clsName.endsWith(".class")) {
      return false;
    }

    return true;
  }

  @Override
  public ClassInfo loadClass(String cname) {
    return getResolvedClassInfo(cname);
  }


  protected void setClassLoaderObject (ElementInfo ei){
    objRef = ei.getObjectRef();
    //id = computeId(objRef);
    
    // cross link
    ei.setIntField(ID_FIELD, id);
  }
  

  //-- ClassInfos cache management --

  protected void updateCachedClassInfos (ClassInfo ci) {
    String name = ci.name;

    if ((objectClassInfo == null) && name.equals("java.lang.Object")) {
      objectClassInfo = ci; unCachedClasses--;
    } else if ((classClassInfo == null) && name.equals("java.lang.Class")) {
      classClassInfo = ci; unCachedClasses--;
    } else if ((classLoaderClassInfo == null) && name.equals("java.lang.ClassLoader")) {
      classInfo = ci;
      classLoaderClassInfo = ci;  unCachedClasses--;
    } else if ((stringClassInfo == null) && name.equals("java.lang.String")) {
      stringClassInfo = ci; unCachedClasses--;
    } else if ((charArrayClassInfo == null) && name.equals("[C")) {
      charArrayClassInfo = ci; unCachedClasses--;
    } else if ((weakRefClassInfo == null) && name.equals("java.lang.ref.WeakReference")) {
      weakRefClassInfo = ci; unCachedClasses--;
    } else if ((refClassInfo == null) && name.equals("java.lang.ref.Reference")) {
      refClassInfo = ci; unCachedClasses--;
    } else if ((enumClassInfo == null) && name.equals("java.lang.Enum")) {
      enumClassInfo = ci; unCachedClasses--;
    } else if ((threadClassInfo == null) && name.equals("java.lang.Thread")) {
      threadClassInfo = ci; unCachedClasses--;
    } else if ((threadGroupClassInfo == null) && name.equals("java.lang.ThreadGroup")) {
      threadGroupClassInfo = ci; unCachedClasses--;
    }
  }
  
  protected ClassInfo getObjectClassInfo() {
    return objectClassInfo;
  }

  protected ClassInfo getClassClassInfo() {
    return classClassInfo;
  }

  protected ClassInfo getClassLoaderClassInfo() {
    return classLoaderClassInfo;
  }

  protected ClassInfo getStringClassInfo() {
    return stringClassInfo;
  }
  
  protected ClassInfo getCharArrayClassInfo() {
    return charArrayClassInfo;
  }

  protected ClassInfo getEnumClassInfo() {
    return enumClassInfo;
  }

  protected ClassInfo getThreadClassInfo() {
    return threadClassInfo;
  }

  protected ClassInfo getThreadGroupClassInfo() {
    return threadGroupClassInfo;
  }

  protected ClassInfo getReferenceClassInfo() {
    return refClassInfo;
  }

  protected ClassInfo getWeakReferenceClassInfo() {
    return weakRefClassInfo;
  }

}