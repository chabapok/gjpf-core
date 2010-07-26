//
// Copyright (C) 2010 United States Government as represented by the
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

package gov.nasa.jpf.tool;

import gov.nasa.jpf.Config;
import gov.nasa.jpf.util.FileUtils;
import gov.nasa.jpf.util.JPFSiteUtils;
import gov.nasa.jpf.util.Misc;
import java.lang.reflect.InvocationTargetException;
import java.net.URLClassLoader;

/**
 * tool to run JPF test with configured classpath
 *
 * arguments are supposed to be of type
 *
 *   {<config-option>} <JPF-test-class> {<test-method>}
 *
 * all leading config options are used to create the initial Config, but be
 * aware of that each test (TestJPF.verifyX() invocation) uses its own
 * Config and JPF object, i.e. can have different path settings
 *
 * This automatically adds <project>.test_classpath to the startup classpath
 */
public class RunTest extends Run {

  public static void main(String[] args){
    String testClsName = getTestClassName(args);
    if (testClsName != null) {
      testClsName = checkClassName(testClsName);

      String projectId = JPFSiteUtils.getCurrentProjectId();
      if (projectId != null) {
        try {
          Config conf = new Config(args);
          String testCpKey = projectId + ".test_classpath";
          ClassLoader cl = conf.setClassLoader(RunTest.class.getClassLoader(), "native_classpath", testCpKey);

          Class<?> testJpfCls = cl.loadClass("gov.nasa.jpf.util.test.TestJPF");
          Class<?> testCls = cl.loadClass(testClsName);

          if (testJpfCls.isAssignableFrom(testCls)) {
            String[] testArgs = getTestArgs(args);
            if (!call( testCls, "main", new Object[] { testArgs } )){
              error("can't find public static void main(String[]) in " + testCls.getName());
            }

          } else {
            error("not a gov.nasa.jpf.TestJPF derived class: " + testClsName);
          }


        } catch (NoClassDefFoundError ncfx) {
          ncfx.printStackTrace();
        } catch (ClassNotFoundException cnfx) {
          cnfx.printStackTrace();
          error("cannot find " + testClsName);
        } catch (InvocationTargetException ix) {
          // should already be handled by JPF
          ix.getCause().printStackTrace();
        }
      } else {
        error("RunTest not executed within JPF enabled project (no jpf.properties found)");
      }
    } else {
      error("no test class specified");
    }
  }

  static String getTestClassName(String[] args){
    for (int i=0; i<args.length; i++){
      String a = args[i];
      if (a != null && a.length() > 0 && a.charAt(0) != '+'){
        return a;
      }
    }

    return null;
  }

  // return everything after the first free arg
  static String[] getTestArgs(String[] args){
    int i;

    for (i=0; i<args.length; i++){
      String a = args[i];
      if (a != null && a.length() > 0 && a.charAt(0) != '+'){
        break;
      }
    }

    if (i >= args.length-1){
      return new String[0];
    } else {
      String[] testArgs = new String[args.length-i-1];
      System.arraycopy(args,i+1, testArgs, 0, testArgs.length);
      return testArgs;
    }
  }


}
