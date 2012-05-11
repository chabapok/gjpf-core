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

import java.io.File;
import java.util.List;

import gov.nasa.jpf.Config;
import gov.nasa.jpf.classfile.ClassPath;
import gov.nasa.jpf.util.ObjVector;

/**
 * Represents the classloader construct in JVM which is responsible for loading
 * classes.
 */
public class ClassLoaderInfo {

  // List of classes defined (directly loaded) by this classloader
  protected final ObjVector<ClassInfo> definedClasses;

  // Represents the locations where this classloader can load classes form
  protected ClassPath cp;

  protected ClassLoaderInfo(ClassPath cp) {
    definedClasses = new ObjVector<ClassInfo>(100);
    this.cp = cp;
  }

  /**
   * Builds the classpath for our system classes loader which resemblances the 
   * location for classes within,
   *        - Java API ($JREHOME/Classes/classes.jar,...) 
   *        - standard extensions packages ($JREHOME/lib/ext/*.jar)
   *        - the local file system ($CLASSPATH)
   */
  protected static void buildSystemClassPath (Config config){
    ClassPath sysClassPath = new ClassPath();

    for (File f : config.getPathArray("boot_classpath")){
      sysClassPath.addPathName(f.getAbsolutePath());
    }

    for (File f : config.getPathArray("classpath")){
      sysClassPath.addPathName(f.getAbsolutePath());
    }

    // finally, we load from the standard Java libraries
    String v = System.getProperty("sun.boot.class.path");
    if (v != null) {
      for (String pn : v.split(File.pathSeparator)){
        sysClassPath.addPathName(pn);
      }
    }
  }

  public static ClassLoaderInfo getCurrentClassLoader() {
    try {
      ThreadInfo ti = ThreadInfo.getCurrentThread();

      MethodInfo mi = ti.getTopFrame().getMethodInfo();

      ClassInfo ci = mi.getClassInfo();

      if(ci != null) {
        return ci.classLoader;
      } else {
        List<StackFrame> stack = ti.getStack();

        for(StackFrame sf: stack) {
          ci = sf.getMethodInfo().getClassInfo();
          if(ci != null) {
            return ci.classLoader;
          }
        }
      }
    } catch(NullPointerException e) {
      return JVM.getSysClassLoader();
    }

    return null;
  }
}
