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

/**
 * @author Nastaran Shafiei <nastaran.shafiei@gmail.com>
 * 
 *         Cache management implementation for the types Boolean, Byte,
 *         Character, Short, Integer, Long. The references to the caches are in
 *         the class classes/gov/nasa/jpf/BoxObjectCaches.
 * 
 *         All the caches, except Boolean, are initialized on the first
 *         invocation of valueOf(), and they all exempt from garbage collection.
 * 
 *         NOTE: All classes obtained from getResolvedClassInfo in
 *         BoxObjectCacheManager are safe, and there is no need to check if they
 *         are initialized. The wrappers and BoxObjectCaches are initialized in
 *         JVM.intialize(), and there are no clinit for array classes.
 */
public class BoxObjectCacheManager {
  private static String boxObjectCaches = "gov.nasa.jpf.BoxObjectCaches";

  // cache default bounds
  private static int defLow = -128;

  private static int defHigh = 127;

  public static int valueOfBoolean (ThreadInfo ti, boolean b) {
    ClassInfo cls = ClassInfo.getResolvedClassInfo("java.lang.Boolean");

    int boolObj;
    if (b) {
      boolObj = cls.getStaticElementInfo().getReferenceField("TRUE");
    } else {
      boolObj = cls.getStaticElementInfo().getReferenceField("FALSE");
    }

    return boolObj;
  }

  // Byte cache bounds
  private static byte byteLow;

  private static byte byteHigh;

  public static int initByteCache (ThreadInfo ti) {
    byteLow = (byte) ti.getVM().getConfig().getInt("vm.cache.low_byte", defLow);
    byteHigh = (byte) ti.getVM().getConfig().getInt("vm.cache.high_byte", defHigh);

    int n = (byteHigh - byteLow) + 1;
    int aRef = ti.getHeap().newArray("Ljava/lang/Byte", n, ti);
    ElementInfo ei = ti.getModifiableElementInfo(aRef);

    ClassInfo ci = ClassInfo.getResolvedClassInfo("java.lang.Byte");
    byte val = byteLow;
    for (int i = 0; i < n; i++) {
      int byteObj = ti.getHeap().newObject(ci, ti);
      ti.getModifiableElementInfo(byteObj).setByteField("value", val++);
      ei.setReferenceElement(i, byteObj);
    }

    ClassInfo cacheClass = ClassInfo.getResolvedClassInfo(boxObjectCaches);
    cacheClass.getModifiableStaticElementInfo().setReferenceField("byteCache", aRef);
    return aRef;
  }

  public static int valueOfByte (ThreadInfo ti, byte b) {
    ClassInfo cacheClass = ClassInfo.getResolvedClassInfo(boxObjectCaches);
    int byteCache = cacheClass.getStaticElementInfo().getReferenceField("byteCache");

    if (byteCache == MJIEnv.NULL) { // initializing the cache on demand
      byteCache = initByteCache(ti);
    }

    if (b >= byteLow && b <= byteHigh) { return ti.getElementInfo(byteCache).getReferenceElement(b - byteLow); }

    ClassInfo ci = ClassInfo.getResolvedClassInfo("java.lang.Byte");
    int byteObj = ti.getHeap().newObject(ci, ti);
    ti.getModifiableElementInfo(byteObj).setByteField("value", b);
    return byteObj;
  }

  // Character cache bound
  private static int charHigh;

  public static int initCharCache (ThreadInfo ti) {
    charHigh = ti.getVM().getConfig().getInt("vm.cache.high_char", defHigh);

    int n = charHigh + 1;
    int aRef = ti.getHeap().newArray("Ljava/lang/Character", n, ti);
    ElementInfo ei = ti.getModifiableElementInfo(aRef);

    ClassInfo ci = ClassInfo.getResolvedClassInfo("java.lang.Character");
    for (int i = 0; i < n; i++) {
      int charObj = ti.getHeap().newObject(ci, ti);
      ti.getModifiableElementInfo(charObj).setCharField("value", (char) i);
      ei.setReferenceElement(i, charObj);
    }

    ClassInfo cacheClass = ClassInfo.getResolvedClassInfo(boxObjectCaches);
    cacheClass.getModifiableStaticElementInfo().setReferenceField("charCache", aRef);
    return aRef;
  }

  public static int valueOfCharacter (ThreadInfo ti, char c) {
    ClassInfo cacheClass = ClassInfo.getResolvedClassInfo(boxObjectCaches);
    int charCache = cacheClass.getStaticElementInfo().getReferenceField("charCache");

    if (charCache == MJIEnv.NULL) { // initializing the cache on demand
      charCache = initCharCache(ti);
    }

    if (c >= 0 && c <= charHigh) { return ti.getElementInfo(charCache).getReferenceElement(c); }

    ClassInfo ci = ClassInfo.getResolvedClassInfo("java.lang.Character");
    int charObj = ti.getHeap().newObject(ci, ti);
    ti.getModifiableElementInfo(charObj).setCharField("value", c);
    return charObj;
  }

  // Short cache bounds
  private static short shortLow;

  private static short shortHigh;

  public static int initShortCache (ThreadInfo ti) {
    shortLow = (short) ti.getVM().getConfig().getInt("vm.cache.low_short", defLow);
    shortHigh = (short) ti.getVM().getConfig().getInt("vm.cache.high_short", defHigh);

    int n = (shortHigh - shortLow) + 1;
    int aRef = ti.getHeap().newArray("Ljava/lang/Short", n, ti);
    ElementInfo ei = ti.getModifiableElementInfo(aRef);

    ClassInfo ci = ClassInfo.getResolvedClassInfo("java.lang.Short");
    short val = shortLow;
    for (int i = 0; i < n; i++) {
      int shortObj = ti.getHeap().newObject(ci, ti);
      ti.getModifiableElementInfo(shortObj).setShortField("value", val++);
      ei.setReferenceElement(i, shortObj);
    }

    ClassInfo cacheClass = ClassInfo.getResolvedClassInfo(boxObjectCaches);
    cacheClass.getModifiableStaticElementInfo().setReferenceField("shortCache", aRef);
    return aRef;
  }

  public static int valueOfShort (ThreadInfo ti, short s) {
    ClassInfo cacheClass = ClassInfo.getResolvedClassInfo(boxObjectCaches);
    int shortCache = cacheClass.getStaticElementInfo().getReferenceField("shortCache");

    if (shortCache == MJIEnv.NULL) { // initializing the cache on demand
      shortCache = initShortCache(ti);
    }

    if (s >= shortLow && s <= shortHigh) { return ti.getElementInfo(shortCache).getReferenceElement(s - shortLow); }

    ClassInfo ci = ClassInfo.getResolvedClassInfo("java.lang.Short");
    int shortObj = ti.getHeap().newObject(ci, ti);
    ti.getModifiableElementInfo(shortObj).setShortField("value", s);
    return shortObj;
  }

  // Integer cache bounds
  private static int intLow;

  private static int intHigh;

  public static int initIntCache (ThreadInfo ti) {
    intLow = ti.getVM().getConfig().getInt("vm.cache.low_int", defLow);
    intHigh = ti.getVM().getConfig().getInt("vm.cache.high_int", defHigh);

    int n = (intHigh - intLow) + 1;
    int aRef = ti.getHeap().newArray("Ljava/lang/Integer", n, ti);
    ElementInfo ei = ti.getModifiableElementInfo(aRef);

    ClassInfo ci = ClassInfo.getResolvedClassInfo("java.lang.Integer");
    for (int i = 0; i < n; i++) {
      int intObj = ti.getHeap().newObject(ci, ti);
      ti.getModifiableElementInfo(intObj).setIntField("value", i + intLow);
      ei.setReferenceElement(i, intObj);
    }

    ClassInfo cacheClass = ClassInfo.getResolvedClassInfo(boxObjectCaches);
    cacheClass.getModifiableStaticElementInfo().setReferenceField("intCache", aRef);
    return aRef;
  }

  public static int valueOfInteger (ThreadInfo ti, int i) {
    ClassInfo cacheClass = ClassInfo.getResolvedClassInfo(boxObjectCaches);
    int intCache = cacheClass.getStaticElementInfo().getReferenceField("intCache");

    if (intCache == MJIEnv.NULL) { // initializing the cache on demand
      intCache = initIntCache(ti);
    }

    if (i >= intLow && i <= intHigh) { return ti.getElementInfo(intCache).getReferenceElement(i - intLow); }

    ClassInfo ci = ClassInfo.getResolvedClassInfo("java.lang.Integer");
    int intObj = ti.getHeap().newObject(ci, ti);
    ti.getModifiableElementInfo(intObj).setIntField("value", i);
    return intObj;
  }

  // Long cache bounds
  private static int longLow;

  private static int longHigh;

  public static int initLongCache (ThreadInfo ti) {
    longLow = ti.getVM().getConfig().getInt("vm.cache.low_long", defLow);
    longHigh = ti.getVM().getConfig().getInt("vm.cache.high_long", defHigh);

    int n = (longHigh - longLow) + 1;
    int aRef = ti.getHeap().newArray("Ljava/lang/Long", n, ti);
    ElementInfo ei = ti.getModifiableElementInfo(aRef);

    ClassInfo ci = ClassInfo.getResolvedClassInfo("java.lang.Long");
    for (int i = 0; i < n; i++) {
      int longObj = ti.getHeap().newObject(ci, ti);
      ti.getModifiableElementInfo(longObj).setLongField("value", i + longLow);
      ei.setReferenceElement(i, longObj);
    }

    ClassInfo cacheClass = ClassInfo.getResolvedClassInfo(boxObjectCaches);
    cacheClass.getModifiableStaticElementInfo().setReferenceField("longCache", aRef);
    return aRef;
  }

  public static int valueOfLong (ThreadInfo ti, long l) {
    ClassInfo cacheClass = ClassInfo.getResolvedClassInfo(boxObjectCaches);
    int longCache = cacheClass.getStaticElementInfo().getReferenceField("longCache");

    if (longCache == MJIEnv.NULL) { // initializing the cache on demand
      longCache = initLongCache(ti);
    }

    if (l >= longLow && l <= longHigh) { return ti.getElementInfo(longCache).getReferenceElement((int) l - longLow); }

    ClassInfo ci = ClassInfo.getResolvedClassInfo("java.lang.Long");
    int longObj = ti.getHeap().newObject(ci, ti);
    ti.getModifiableElementInfo(longObj).setLongField("value", l);
    return longObj;
  }
}
