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
package gov.nasa.jpf.util.test;

import gov.nasa.jpf.jvm.*;

import gov.nasa.jpf.Property;
import gov.nasa.jpf.util.Misc;
import gov.nasa.jpf.util.Reflection;
import java.io.PrintStream;


import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import org.junit.*;


/**
 * base class for JPF unit tests. TestJPF mostly includes JPF invocations
 * that check for occurrence or absence of certain execution results
 * 
 * This class can be used in two modes:
 *
 * <ol>
 * <li> wrapping a number of related tests for different SuTs into one class
 * (suite) that calls the various JPF runners with complete argument lists
 * (as in JPF.main(String[]args)) </li>
 *
 * <li> derive a class from TestJPF that uses the "..This" methods, which in
 * turn use reflection to automatically append the test class and method to the
 * JPF.main argument list (based on the calling class / method names). Note that
 * you have to obey naming conventions for this to work:
 *
 * <ul>
 * <li> the SuT class has to be the same as the test class without "Test", e.g.
 * "CastTest" -> "Cast" </li>
 * 
 * <li> the SuT method has to have the same name as the @Test method that
 * invokes JPF, e.g. "CastTest {.. @Test void testArrayCast() ..}" ->
 * "Cast {.. void testArrayCast()..} </li>
 *
 * </li>
 * </ol>
 */
public abstract class TestJPF extends Assert  {
  static PrintStream out = System.out;

  public static final String UNNAMED_PACKAGE = "";
  public static final String SAME_PACKAGE = null;

  static  boolean showConfig; // for debugging purposes

  //--- those are only used outside of JPF execution
  private boolean runDirectly; // don't run test methods through JPF, invoke it directly
  private String sutClassName;

  //------ internal methods


  public void fail (String msg, String[] args, String cause){
    StringBuilder sb = new StringBuilder();

    sb.append(msg);
    if (args != null){
      for (String s : args){
        sb.append(s);
        sb.append(' ');
      }
    }

    if (cause != null){
      sb.append(':');
      sb.append(cause);
    }

    if (isJUnitRun()){
      super.fail(sb.toString());
    } else {
      System.err.println(sb.toString());
    }
  }

  public void report (String[] args) {
    out.print("  running jpf with args:");

    for (int i = 0; i < args.length; i++) {
      out.print(' ');
      out.print(args[i]);
    }

    out.println();
  }

  private String[] getArgsForCallerMethod (String[] jpfArgs){
    StackTraceElement callerEntry = Reflection.getCallerElement(2);

    String testMethod = callerEntry.getMethodName();
    String[] args = Misc.appendArray(jpfArgs, sutClassName, testMethod);

    return args;
  }

  /**
   * compute the SuT class name for a given JUnit test class: remove
   * optionally ending "..Test", and replace package (if specified)
   * 
   * @param testClass the JUnit test class
   * @param sutPackage optional SuT package name (without ending '.', null
   * os SAME_PACKAGE means same package, "" or UNNAMED_PACKAGE means unnamed package)
   * @return main class name of system under test
   */
  protected static String getSutClassName (String testClassName, String sutPackage){

    String sutClassName = testClassName;

    int i = sutClassName.lastIndexOf('.');
    if (i >= 0){  // testclass has a package

      if (sutPackage == null){   // use same package
        // nothing to do
      } else if (sutPackage.length() > 0) { // explicit sut package
        sutClassName = sutPackage + sutClassName.substring(i);

      } else { // unnamed sut package
        sutClassName = sutClassName.substring(i+1);
      }

    } else { // test class has no package
      if (sutPackage == null || sutPackage.length() == 0){   // use same package
        // nothing to do
      } else { // explicit sut package
        sutClassName = sutPackage + '.' + sutClassName;
      }
    }

    if (sutClassName.endsWith("JPF")) {
      sutClassName = sutClassName.substring(0, sutClassName.length() - 3);
    }

    return sutClassName;
  }

  // we can't set the sutClassName only from main() called methods (like
  // runTestsOfThisClass()) since main() doesn't get called if this is executed
  // by Ant (via <junit> task)
  // the default ctor is always executed
  public TestJPF () {
    sutClassName = getSutClassName(getClass().getName(), SAME_PACKAGE);
  }

  protected void runDirectly(boolean runDirectly) {
    this.runDirectly = runDirectly;
  }

  //------ the API to be used by subclasses

  /**
   * to be used from default ctor of derived class if the SuT is in a different
   * package
   * @param sutClassName the qualified SuT class name to be checked by JPF
   */
  protected TestJPF (String sutClassName){
    this.sutClassName = sutClassName;
  }

  public static boolean isJPFRun () {
    return false;
  }

  public static boolean isJUnitRun() {
    // intercepted by native peer if this runs under JPF
    Throwable t = new Throwable();
    t.fillInStackTrace();

    for (StackTraceElement se : t.getStackTrace()){
      if (se.getClassName().startsWith("org.junit.")){
        return true;
      }
    }

    return false;
  }

  public static boolean showConfig() {
    return showConfig;
  }

  private static void runTests (Class<? extends TestJPF> testCls, String... args){
    Method testMethod = null;
    TestJPF testObject = null;

    boolean runDirectly = false;
    int nArgs = 0;

    if (args != null){
      for (int i=0; i<args.length; i++){
        if (args[i] != null && args[i].startsWith("-")){
          if (args[i].equals("-d")){
            runDirectly = true;
          } else if (args[i].equals("-s")){
            showConfig = true;
          }
          args[i] = null;
        } else {
          nArgs++;
        }
      }
    }

    try {
      if (nArgs > 0) {
          for (String test : args) {
            if (test != null) {

              try {
                Method m = testCls.getDeclaredMethod(test);

                if (!m.isAnnotationPresent(org.junit.Test.class)){
                  throw new TestException("test method does not have @Test annotation: " + test);
                }

                if (!Modifier.isPublic(m.getModifiers())){
                  throw new TestException("test method not public: " + test);                
                }
                if (Modifier.isStatic(m.getModifiers())){
                  throw new TestException("test method is static: " + test);                
                }
                testMethod = m;

              } catch (NoSuchMethodException x) {
                throw new TestException("method: " + test +
                        "() not in test class: " + testCls.getName(), x);
              }

              testObject = testCls.newInstance();
              testObject.runDirectly(runDirectly);

              System.out.println("-- running test: " + test);
              testMethod.invoke(testObject);
            }
          }

      } else {
        int nTests = 0;
        for (Method m : testCls.getDeclaredMethods()) {
          testMethod = m;
          int mod = m.getModifiers();
          if (m.getParameterTypes().length == 0 &&
                  m.isAnnotationPresent(org.junit.Test.class) &&
                  Modifier.isPublic(mod) && !Modifier.isStatic(mod)) {
            testObject = testCls.newInstance();
            testObject.runDirectly(runDirectly);

            nTests++;
            System.out.println("-- running test: " + m.getName());
            testMethod.invoke(testObject);
          }
        }

        if (nTests == 0){
          System.out.println("WARNING: no \"@Test public void test..()\" methods found");
        }
      }

    } catch (InstantiationException x) {
      throw new TestException("error instantiating test class: " + testCls.getName(), x);
    } catch (IllegalAccessException x) {
      throw new TestException("no public method: " +
              ((testMethod != null) ? testMethod.getName() : "<init>") +
              " of test class: " + testCls.getName(), x);
    } catch (IllegalArgumentException x) {
      throw new TestException("illegal argument for test method: " + testMethod.getName(), x);

    } catch (InvocationTargetException x) {
      throw new TestException("failed test method: " + testMethod.getName(), x);
    }
  }


  protected static void runTestsOfThisClass (String[] testMethods){
    // needs to be at the same stack level, so we can't delegate
    Class<? extends TestJPF> testClass = Reflection.getCallerClass(TestJPF.class);
    runTests(testClass, testMethods);
  }


  /**
   * run JPF expecting a AssertionError in the SuT
   * @param args JPF main() arguments
   */
  public void assertionError (String details, String... args) {
    unhandledException("java.lang.AssertionError", details, args );
  }
  protected boolean verifyAssertionErrorDetails (String details, String... args){
    if (runDirectly) {
      return true;
    } else {
      StackTraceElement caller = Reflection.getCallerElement();
      args = Misc.appendArray(args, caller.getClassName(), caller.getMethodName());
      unhandledException("java.lang.AssertionError", details, args);
      return false;
    }
  }
  protected boolean verifyAssertionError (String... args){
    if (runDirectly) {
      return true;
    } else {
      StackTraceElement caller = Reflection.getCallerElement();
      args = Misc.appendArray(args, caller.getClassName(), caller.getMethodName());
      unhandledException("java.lang.AssertionError", null, args);
      return false;
    }
  }

  /**
   * run JPF expecting no SuT property violations or JPF exceptions
   * @param args JPF main() arguments
   */
  public void noPropertyViolation (String... args) {
    JPFTestRun.noPropertyViolation(this, args);
  }
  protected boolean verifyNoPropertyViolation (String...jpfArgs){
    if (runDirectly) {
      return true;
    } else {
      StackTraceElement caller = Reflection.getCallerElement();
      String[] args = Misc.appendArray(jpfArgs, caller.getClassName(), caller.getMethodName());
      noPropertyViolation(args);
      return false;
    }
  }

  /**
   * NOTE: this uses the exception class name because it might be an
   * exception type that is only known to JPF (i.e. not in the native classpath)
   *
   * @param xClassName name of the exception base type that is expected
   * @param details detail message of the expected exception
   * @param args JPF arguments
   */
  public void unhandledException ( String xClassName, String details, String... args) {
    JPFTestRun.unhandledException(this, xClassName, details, args);
  }
  protected boolean verifyUnhandledExceptionDetails (String xClassName, String details, String... args){
    if (runDirectly) {
      return true;
    } else {
      StackTraceElement caller = Reflection.getCallerElement();
      args = Misc.appendArray(args, caller.getClassName(), caller.getMethodName());
      unhandledException(xClassName, details, args);
      return false;
    }
  }
  protected boolean verifyUnhandledException (String xClassName, String... args){
    if (runDirectly) {
      return true;
    } else {
      StackTraceElement caller = Reflection.getCallerElement();
      args = Misc.appendArray(args, caller.getClassName(), caller.getMethodName());
      unhandledException(xClassName, null, args);
      return false;
    }
  }


  /**
   * run JPF expecting it to throw an exception
   * NOTE - xClassName needs to be the concrete exception, not a super class
   * @param args JPF main() arguments
   */
  public void jpfException (Class<? extends Throwable> xCls, String... args) {
    JPFTestRun.jpfException(this, xCls, args);
  }
  protected boolean verifyJPFException (Class<? extends Throwable> xCls, String... args){
    if (runDirectly) {
      return true;
    } else {
      StackTraceElement caller = Reflection.getCallerElement();
      args = Misc.appendArray(args, caller.getClassName(), caller.getMethodName());
      jpfException(xCls, args);
      return false;
    }
  }

  
  
  /**
   * run JPF expecting a property violation of the SuT
   * @param args JPF main() arguments
   */
  public void propertyViolation (Class<? extends Property> propertyCls, String... args ){
    JPFTestRun.propertyViolation(this, propertyCls, args);
  }
  protected boolean verifyPropertyViolation (Class<? extends Property> propertyCls, String... args){
    if (runDirectly) {
      return true;
    } else {
      StackTraceElement caller = Reflection.getCallerElement();
      args = Misc.appendArray(args, caller.getClassName(), caller.getMethodName());
      propertyViolation(propertyCls, args);
      return false;
    }
  }


  /**
   * run JPF expecting a deadlock in the SuT
   * @param args JPF main() arguments
   */
  public void deadlock (String... args) {
    propertyViolation(NotDeadlockedProperty.class, args );
  }
  protected boolean verifyDeadlock (String... args){
    if (runDirectly) {
      return true;
    } else {
      StackTraceElement caller = Reflection.getCallerElement();
      args = Misc.appendArray(args, caller.getClassName(), caller.getMethodName());
      propertyViolation(NotDeadlockedProperty.class, args);
      return false;
    }
  }

}
