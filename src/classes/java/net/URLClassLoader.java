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
package java.net;

import java.io.IOException;
import java.security.SecureClassLoader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Vector;

/**
 * @author Nastaran Shafiei <nastaran.shafiei@gmail.com>
 * 
 *  Model class for java.net.URLClassLoader
 */
public class URLClassLoader extends SecureClassLoader {

  private List<URL> urls =  new ArrayList<URL>(0);

  public URLClassLoader(URL[] urls) {
    super();
    addURLs(urls);
  }

  public URLClassLoader(URL[] urls, ClassLoader parent) {
    super(parent);
    addURLs(urls);
  }

  private void addURLs(URL[] urls) {
    for(URL url: urls) {
      addURL(url);
    }
  }

  private native void addToSearchPath(String url);

  private void addToURLs(URL url) {
    urls.add(url);
  }

  protected void addURL(URL url) {
    addToSearchPath(url.toString());    
    addToURLs(url);
  }

  protected native Class<?> findClass(final String name) throws ClassNotFoundException;

  private native String findResource0 (String rname);

  public URL findResource(final String rname) {
    URL url = null;
    String path = findResource0(rname);

    try {
      url = new URL(path);
    } catch (MalformedURLException x){
      url = null;
    }

    return url;
  }

  private native String[] findResources0 (String rname);

  public Enumeration<URL> findResources(String name) throws IOException {
    String[] urls = findResources0(name);
    Vector<URL> list = new Vector<URL>(0);
    for(String url: urls) {
      try {
        list.add(new URL(url));
      } catch (MalformedURLException x){
        // process the rest
      }
    }

    return list.elements();
  }

  public URL[] getURLs() {
    return urls.toArray(new URL[urls.size()]);
  }

  public static URLClassLoader newInstance(URL[] urls) {
    return (new URLClassLoader(urls));
  }

  public static URLClassLoader newInstance(URL[] urls, ClassLoader parent) {
    return (new URLClassLoader(urls, parent));
  }
}
