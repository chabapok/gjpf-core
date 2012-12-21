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

import gov.nasa.jpf.JPFException;
import gov.nasa.jpf.jvm.classfile.ClassPath;

import java.io.File;
import java.util.ArrayList;
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

  protected ClassInfo objectClassInfo;
  protected ClassInfo classClassInfo;
  protected ClassInfo stringClassInfo;
  protected ClassInfo weakRefClassInfo;
  protected ClassInfo refClassInfo;
  protected ClassInfo enumClassInfo;
  protected ClassInfo threadClassInfo;
  protected ClassInfo charArrayClassInfo;

  protected String mainClassName;
  protected String[] args;  /** tiMain() arguments */
  protected ThreadInfo tiMain;
  protected VM vm;

  protected SystemClassLoaderInfo (VM vm, String mainClassName, String[] args) {
    super(vm, MJIEnv.NULL, null, null);
    this.mainClassName = mainClassName;
    this.args = args;
    this.tiMain = vm.createMainThreadInfo();
    this.vm = vm;
    setSystemClassPath();
    classInfo = getResolvedClassInfo("java.lang.ClassLoader");
  }

  public String getMainClassName() {
    return mainClassName;
  }

  public String[] getArgs() {
    return args;
  }

  public ThreadInfo getMainThread() {
    return tiMain;
  }

  public ClassInfo getMainClassInfo () {
    return getResolvedClassInfo(mainClassName);
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

  /**
   * be careful - everything that's executed from within here is not allowed
   * to depend on static class init having been done yet
   *
   * we have to do the initialization excplicitly here since we can't execute
   * bytecode yet (which would need a ThreadInfo context)
   */
  protected void initMainThread () {
    
    //--- now create & initialize all the related JPF objects
    Heap heap = vm.getHeap();

    ClassInfo ciThread = getResolvedClassInfo("java.lang.Thread");
    ElementInfo eiThread = heap.newObject( ciThread, tiMain);
    int threadRef = eiThread.getObjectRef();
    int groupRef = createSystemThreadGroup(threadRef);
    ElementInfo eiName = heap.newString("main", tiMain);
    int nameRef = eiName.getObjectRef();
    
    //--- initialize the main Thread object
    eiThread.setReferenceField("group", groupRef);
    eiThread.setReferenceField("name", nameRef);
    eiThread.setIntField("priority", Thread.NORM_PRIORITY);

    ElementInfo eiPermit = heap.newObject(getResolvedClassInfo("java.lang.Thread$Permit"), tiMain);
    eiPermit.setBooleanField("blockPark", true);
    eiThread.setReferenceField("permit", eiPermit.getObjectRef());

    tiMain.id = tiMain.computeId(threadRef);

    //--- initialize the ThreadInfo reference fields
    tiMain.initReferenceFields(threadRef, groupRef, MJIEnv.NULL, nameRef);
  
    //--- set the thread running
    tiMain.setState(ThreadInfo.State.RUNNING);
  }

  protected int createSystemThreadGroup (int mainThreadRef) {
    Heap heap = vm.getHeap();
    
    ElementInfo eiThreadGrp = heap.newObject(getResolvedClassInfo("java.lang.ThreadGroup"), tiMain);

    // since we can't call methods yet, we have to init explicitly (BAD)
    // <2do> - this isn't complete yet

    ElementInfo eiGrpName = heap.newString("main", tiMain);
    eiThreadGrp.setReferenceField("name", eiGrpName.getObjectRef());

    eiThreadGrp.setIntField("maxPriority", java.lang.Thread.MAX_PRIORITY);

    ElementInfo eiThreads = heap.newArray("Ljava/lang/Thread;", 4, tiMain);
    eiThreads.setReferenceElement(0, mainThreadRef);

    eiThreadGrp.setReferenceField("threads", eiThreads.getObjectRef());
    eiThreadGrp.setIntField("nthreads", 1);

    return eiThreadGrp.getObjectRef();
  }

  /**
   * override this method if you want your tiMain class entry to be anything else
   * than "public static void tiMain(String[] args)"
   * 
   * Note that we do a directcall here so that we always have a first frame that
   * can't execute SUT code. That way, we can handle synchronized entry points
   * via normal InvokeInstructions, and thread termination processing via
   * DIRECTCALLRETURN
   */
  protected void pushMainEntry () {
    Heap heap = vm.getHeap();
    
    ClassInfo ciMain = getResolvedClassInfo(mainClassName);
    MethodInfo miMain = ciMain.getMethod("main([Ljava/lang/String;)V", false);

    // do some sanity checks if this is a valid tiMain()
    if (miMain == null || !miMain.isStatic()) {
      throw new JPFException("no main() method in " + ciMain.getName());
    }

    // create the args array object
    ElementInfo eiArgs = heap.newArray("Ljava/lang/String;", args.length, tiMain);
    for (int i = 0; i < args.length; i++) {
      ElementInfo eiElement = heap.newString(args[i], tiMain);
      eiArgs.setReferenceElement(i, eiElement.getObjectRef());
    }
    
    // create the direct call stub
    MethodInfo mainStub = miMain.createDirectCallStub("[main]");
    DirectCallStackFrame frame = new DirectCallStackFrame(mainStub);
    frame.pushRef(eiArgs.getObjectRef());
    // <2do> set RUNSTART pc if we want to catch synchronized tiMain() defects 
    
    tiMain.pushFrame(frame);
  }

  /**
   * Builds the classpath for our system class loaders which resemblances the 
   * location for classes within,
   *        - Java API ($JREHOME/Classes/classes.jar,...) 
   *        - standard extensions packages ($JREHOME/lib/ext/*.jar)
   *        - the local file system ($CLASSPATH)
   */
  protected void setSystemClassPath (){
    cp = new ClassPath();

    for (File f : config.getPathArray("boot_classpath")){
      cp.addPathName(f.getAbsolutePath());
    }

    for (File f : config.getPathArray("classpath")){
      cp.addPathName(f.getAbsolutePath());
    }

    // finally, we load from the standard Java libraries
    String v = System.getProperty("sun.boot.class.path");
    if (v != null) {
      for (String pn : v.split(File.pathSeparator)){
        cp.addPathName(pn);
      }
    }
  }

  @Override
  public ClassInfo loadClass(String cname) {
    return getResolvedClassInfo(cname);
  }

  private ArrayList<String> getStartupClasses(VM vm) {
    ArrayList<String> startupClasses = new ArrayList<String>(128);

    // bare essentials
    startupClasses.add("java.lang.Object");
    startupClasses.add("java.lang.Class");
    startupClasses.add("java.lang.ClassLoader");

    // the builtin types (and their arrays)
    startupClasses.add("boolean");
    startupClasses.add("[Z");
    startupClasses.add("byte");
    startupClasses.add("[B");
    startupClasses.add("char");
    startupClasses.add("[C");
    startupClasses.add("short");
    startupClasses.add("[S");
    startupClasses.add("int");
    startupClasses.add("[I");
    startupClasses.add("long");
    startupClasses.add("[J");
    startupClasses.add("float");
    startupClasses.add("[F");
    startupClasses.add("double");
    startupClasses.add("[D");
    startupClasses.add("void");

    // the box types
    startupClasses.add("java.lang.Boolean");
    startupClasses.add("java.lang.Character");
    startupClasses.add("java.lang.Short");
    startupClasses.add("java.lang.Integer");
    startupClasses.add("java.lang.Long");
    startupClasses.add("java.lang.Float");
    startupClasses.add("java.lang.Double");
    startupClasses.add("java.lang.Byte");

    // the cache for box types
    startupClasses.add("gov.nasa.jpf.BoxObjectCaches");

    // standard system classes
    startupClasses.add("java.lang.String");
    startupClasses.add("java.lang.ThreadGroup");
    startupClasses.add("java.lang.Thread");
    startupClasses.add("java.lang.Thread$State");
    startupClasses.add("java.io.PrintStream");
    startupClasses.add("java.io.InputStream");
    startupClasses.add("java.lang.System");
    startupClasses.add("java.lang.ref.Reference");
    startupClasses.add("java.lang.ref.WeakReference");
    startupClasses.add("java.lang.Enum");

    // we could be more fancy and use wildcard patterns and the current classpath
    // to specify extra classes, but this could be VERY expensive. Projected use
    // is mostly to avoid static init of single classes during the search
    String[] extraStartupClasses = config.getStringArray("vm.extra_startup_classes");
    if (extraStartupClasses != null) {      
      for (String extraCls : extraStartupClasses) {
        startupClasses.add(extraCls);
      }
    }

    // last not least the application main class
    startupClasses.add(mainClassName);

    return startupClasses;
  }

  // it keeps the startup classes
  ArrayList<ClassInfo> startupQueue = new ArrayList<ClassInfo>(32);

  protected void registerStartupClasses (VM vm) {
    ArrayList<String> startupClasses = getStartupClasses(vm);
    startupQueue = new ArrayList<ClassInfo>(32);

    // now resolve all the entries in the list and queue the corresponding ClassInfos
    for (String clsName : startupClasses) {
      ClassInfo ci = getResolvedClassInfo(clsName);
      if (ci != null) {
        ci.registerStartupClass( tiMain, startupQueue);
      } else {
        VM.log.severe("can't find startup class ", clsName);
      }
    }    
  }

  protected ArrayList<ClassInfo> getStartupQueue() {
    return startupQueue;
  }
  
  protected void createStartupClassObjects (){
    for (ClassInfo ci : startupQueue) {
      ci.createAndLinkStartupClassObject(tiMain);
    }
  }

  protected void pushClinits () {
    // we have to traverse backwards, since what gets pushed last is executed first
    for (ListIterator<ClassInfo> it=startupQueue.listIterator(startupQueue.size()); it.hasPrevious(); ) {
      ClassInfo ci = it.previous();

      MethodInfo mi = ci.getMethod("<clinit>()V", false);
      if (mi != null) {
        MethodInfo stub = mi.createDirectCallStub("[clinit]");
        StackFrame frame = new DirectCallStackFrame(stub);
        tiMain.pushFrame(frame);
      } else {
        ci.setInitialized();
      }
    }
  }

  protected void registerThreadListCleanup(){
    ClassInfo ciThread = getResolvedClassInfo("java.lang.Thread");
    assert ciThread != null : "java.lang.Thread not loaded yet";
    
    ciThread.addReleaseAction( new ReleaseAction(){
      public void release(ElementInfo ei) {
        ThreadList tl = vm.getThreadList();
        int objRef = ei.getObjectRef();
        ThreadInfo ti = tl.getThreadInfoForObjRef(objRef);
        if (tl.remove(ti)){        
          vm.getKernelState().changed();    
        }
      }
    });    
  }

  /**
   * This loads the startup classes. Loading includes the following steps:
   *   1. Defines
   *   2. Resolves
   *   3. Initializes
   */
  protected void loadStartUpClasses(VM vm, ThreadInfo ti) {
    registerStartupClasses(vm);
    createStartupClassObjects();
    pushClinits();
  }

  /**
   * Creates a classLoader object in the heap
   */
  protected ElementInfo createSystemClassLoaderObject(ClassInfo ci) {
    Heap heap = vm.getHeap();

    //--- create ClassLoader object of type ci which corresponds to this ClassLoader
    ElementInfo ei = heap.newObject( ci, tiMain);
    int oRef = ei.getObjectRef();

    //--- make sure that the classloader object is not garbage collected 
    heap.registerPinDown(oRef);

    //--- initialize the systemClassLoader object
    this.id = this.computeId(oRef);
    ei.setIntField(ID_FIELD, id);

    int parentRef = MJIEnv.NULL;

    ei.setReferenceField("parent", parentRef);

    this.objRef = oRef;

    return ei;
  }

  //-- ClassInfos cache management --

  protected void updateCachedClassInfos (ClassInfo ci) {
    String name = ci.name;      
    if ((objectClassInfo == null) && name.equals("java.lang.Object")) {
      objectClassInfo = ci;
    } else if ((classClassInfo == null) && name.equals("java.lang.Class")) {
      classClassInfo = ci;
    } else if ((stringClassInfo == null) && name.equals("java.lang.String")) {
      stringClassInfo = ci;
    } else if ((charArrayClassInfo == null) && name.equals("[C")) {
      charArrayClassInfo = ci;
    } else if ((weakRefClassInfo == null) && name.equals("java.lang.ref.WeakReference")) {
      weakRefClassInfo = ci;
    } else if ((refClassInfo == null) && name.equals("java.lang.ref.Reference")) {
      refClassInfo = ci;
    } else if ((enumClassInfo == null) && name.equals("java.lang.Enum")) {
      enumClassInfo = ci;
    } else if ((threadClassInfo == null) && name.equals("java.lang.Thread")) {
      threadClassInfo = ci;
    }
  }

  protected ClassInfo getObjectClassInfo() {
    return objectClassInfo;
  }

  protected ClassInfo getClassClassInfo() {
    return classClassInfo;
  }

  protected ClassInfo getStringClassInfo() {
    return stringClassInfo;
  }
  
  protected ClassInfo getCharArrayClassInfo() {
    return charArrayClassInfo;
  }
}
