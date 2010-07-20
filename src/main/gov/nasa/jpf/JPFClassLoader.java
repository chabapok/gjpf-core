//
// Copyright (C) 2009 United States Government as represented by the
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
package gov.nasa.jpf;

import java.io.File;
import java.io.InputStream;
import java.io.FileInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.regex.Pattern;
import java.util.List;
import java.util.LinkedList;
import java.util.jar.JarFile;
import java.util.jar.JarEntry;
import java.net.URL;
import java.net.MalformedURLException;

/**
 * this is the class loader we use if JPF is started via  gov.nasa.jpf.Main
 * the purpose is to add whatever is configured in the site.properties or
 * an explicit "+native_classpath" setting to the class path, in addition to
 * JPF required libraries that were not specified when invoking Main
 *
 * the twist is that jpf core classes will be accessible through the
 * classpath of whatever loaded Main *and* this JPFClassLoader, hence we can't
 * go with the standard parent-first delegation model. If we do, all core
 * classes will be loaded by the current CL, which then is not able to resolve
 * references that require components we picked up during our project dir
 * configuration (by processing the jpf.properties)
 *
 * The solution is to reverse the lookup order, but filter out all JPF classes we
 * know have been already loaded (including the JPFClassLoader itself).
 *
 * We have to implement our own class loading here (i.e. can't use URLClassLoader)
 * because we need to start with a path entry that covers at least gov.nasa.jpf.JPF
 * and gov.nasa.jpf.Config (most likely from jpf.jar), but later on want to be
 * able to override the jpf-core entries with extension entries. URLClassLoader
 * supports adding URLs, but not re-ordering them.
 *
 * NOTE: this means we *might* dynamically re-arrange path entries! This can
 * be confusing if you try to overload JPF or Config (which DOES NOT WORK), or
 * if you explicitly specify a startup class for Main ("-a <app-class>") that
 * refers to overridden jpf-core classes. The reason we accept this otherwise
 * bad behavior is that we consider it more important to stay with the
 * principle of uniform project priorities in order of jpf.properties processing.
 * While it is arguable that jpf-core main classes should not be overridden
 * (a good practice!), we need to support overriding of core model classes
 * (e.g. java.util.concurrent) by extensions, which usually come with their
 * own peer classes (i.e. we have to keep the classpath consistent with the
 * native_classpath)
 *
 * NOTE: none of the JPF classes except of Main and JPFClassLoader should be
 * loaded at this time, since the JPFClassLoader might reload such classes
 * from different locations.
 */
public class JPFClassLoader extends ClassLoader {

  abstract static class CpEntry {
    abstract byte[] getClassData (String clsName);
    abstract URL getResourceURL (String name);
    abstract String getPath();
  }

  static class JarCpEntry extends CpEntry {
    JarFile jar;
    String urlBase;

    JarCpEntry (JarFile f){
      jar = f;
      urlBase =  "jar:file:" + f.getName() + "!/";
    }

    byte[] getClassData (String clsName) {
      String pn = clsName.replace('.', '/') + ".class";
      JarEntry je = jar.getJarEntry(pn);
      if (je != null) {
        try {
          int len = (int)je.getSize();
          byte data[] = new byte[len];
          InputStream is = jar.getInputStream(je);
          DataInputStream dis = new DataInputStream(is);
          dis.readFully(data);
          dis.close();
          return data;

        } catch (IOException iox) {
          return null;
        }
      }

      return null;
    }

    URL getResourceURL (String name) {
      JarEntry je = jar.getJarEntry(name);
      if (je != null) {
        try {
          return new URL(urlBase + name);
        } catch (MalformedURLException mfux) {
          return null;
        }
      }

      return null;
    }

    String getPath() {
      return jar.getName();
    }

    public boolean equals(Object o){
      return (o instanceof JarCpEntry) && ((JarCpEntry)o).urlBase.equals(urlBase);
    }
    public int hashCode(){
      return urlBase.hashCode();
    }
  }

  static class DirCpEntry extends CpEntry {
    File dir;

    DirCpEntry (File f) {
      dir = f;
    }

    byte[] getClassData (String clsName) {
      String pn = clsName.replace('.', File.separatorChar) + ".class";

      File classFile = new File(dir, pn);
      if (classFile.isFile()){
        try {
          int len = (int)classFile.length();
          byte data[] = new byte[len];
          FileInputStream fis = new FileInputStream(classFile);
          DataInputStream dis = new DataInputStream(fis);
          dis.readFully(data);
          dis.close();
          return data;
        } catch (IOException iox) {
          return null;
        }
      }

      return null;
    }

    URL getResourceURL (String name) {
      if (File.separatorChar == '/'){
        name = name.replace('\\', '/');
      } else {
        name = name.replace('/', '\\');
      }

      File f = new File(dir, name);
      if (f.isFile()){
        try {
          return f.toURI().toURL();
        } catch (MalformedURLException mfux) {
          return null;
        }
      }

      return null;
    }

    String getPath() {
      return dir.getPath();
    }

    public boolean equals(Object o){
      return (o instanceof DirCpEntry) && ((DirCpEntry)o).dir.equals(dir);
    }
    public int hashCode(){
      return dir.hashCode();
    }
  }



  static Pattern libClassPattern = Pattern.compile("(javax?|sun)\\.");

  HashMap<String,Class<?>> preloads = new HashMap<String,Class<?>>();
  List<CpEntry> cpEntries = new LinkedList<CpEntry>();

  public JPFClassLoader (String[] args) {
    // JPF URLs will be added later on, mostly by JPF after we have a Config
    super(JPFClassLoader.class.getClassLoader());
  }


  public void addPreloadedClass (Class<?> cls){
    preloads.put(cls.getName(), cls);
  }

  public void setPathElements (String[] pathElements){
    cpEntries.clear(); // this is dangerous if there already have been classes loaded from cpEntries
    addPathElements(pathElements);
  }

  public void addPathElements (String[] pathElements){
    for (String e : pathElements){
      CpEntry ce = getCpEntry(e);
      if (ce != null) {
        addUniqueCpEntry(ce);
      }
    }
  }

  public void addPathElement (String cpElement){
    CpEntry ce = getCpEntry(cpElement);
    if (ce != null) {
      addUniqueCpEntry(ce);
    }
  }

  private void addUniqueCpEntry (CpEntry e){
    if (!cpEntries.contains(e)){
      cpEntries.add(e);
    }
  }

  //--- ClassLoader basics


  protected Class<?> loadClass (String name, boolean resolve) throws ClassNotFoundException {

 //   synchronized (getClassLoadingLock(name)) {
    Class<?> cls = findLoadedClass(name);
    if (cls == null) {

      cls = preloads.get(name);
      if (cls == null) {
        if (libClassPattern.matcher(name).matches()) {
          cls = findSystemClass(name);

        } else {

          try {
            cls = findClass(name);

          } catch (ClassNotFoundException e) {
            ClassLoader parent = getParent();
            if (parent != null) {
              cls = parent.loadClass(name);
            } else {
              cls = findSystemClass(name);
            }
          }
        }

        if (resolve) {
          resolveClass(cls);
        }
      }
    }

    return cls;
 //   }
  }

  protected Class<?> findClass (String name) throws ClassNotFoundException {
    for (CpEntry e : cpEntries) {
      byte[] data = e.getClassData(name);
      if (data != null) {
        Class<?> cls = defineClass(name, data, 0, data.length, null);
        return cls;
      }
    }

    throw new ClassNotFoundException(name);
  }

  public URL getResource (String name) {
    URL url = findResource(name);

    if (url == null) {
      ClassLoader parent = getParent();
      if (parent != null) {
        return parent.getResource(name);
      } else {
        return getSystemResource(name);
      }
    }

    return url;
  }

  protected URL findResource (String name) {
    for (CpEntry e : cpEntries) {
      URL url = e.getResourceURL(name);
      if (url != null){
        return url;
      }
    }

    return null;
  }

  public String[] getClasspathElements() {
    String[] list = new String[cpEntries.size()];

    int i=0;
    for (CpEntry e : cpEntries){
      list[i++] = e.getPath();
    }

    return list;
  }


  //--- internals

  private CpEntry getCpEntry(String e) {
    File f = new File(e);

    if (f.isFile()){
      if (e.endsWith(".jar")){
        try {
          return new JarCpEntry( new JarFile(f));
        } catch (IOException iox) {
          return null;
        }
      }
    } else if (f.isDirectory()){
      return new DirCpEntry(f);
    }

    return null;
  }


  // for debugging purposes
  public void printEntries() {
    System.out.println("JPFClassLoader.getClasspathElements() :");
    for (String pe : getClasspathElements()){
      System.out.print("  ");
      System.out.println(pe);
    }
  }
}
