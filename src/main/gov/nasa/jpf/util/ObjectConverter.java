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

package gov.nasa.jpf.util;

import gov.nasa.jpf.JPFException;
import gov.nasa.jpf.jvm.ClassInfo;
import gov.nasa.jpf.jvm.ElementInfo;
import gov.nasa.jpf.jvm.FieldInfo;
import gov.nasa.jpf.jvm.Fields;
import gov.nasa.jpf.jvm.MJIEnv;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Object transformer from Java objects to JPF objects
 * @author Ivan Mushketik
 */
public class ObjectConverter {
  // <2do> add support for arrays, and fields of reference types
  public static int JPFObjectFromJavaObject(MJIEnv env, Object javaObject) {
    try {
      Class<?> javaClass = javaObject.getClass();
      String typeName = javaClass.getCanonicalName();
      int newObjRef = env.newObject(typeName);
      ElementInfo newObjEI = env.getElementInfo(newObjRef);

      ClassInfo ci = env.getClassInfo(newObjRef);
      while (ci != null) {
        for (FieldInfo fi : ci.getDeclaredInstanceFields()) {
          if (!fi.isReference()) {
            setJPFPrimitive(newObjEI, fi, javaObject);
          }
          else if (isArrayField(fi)) {
            int arrRef = getJPFArrayRef(env, fi, javaObject);
            newObjEI.setReferenceField(fi, arrRef);
          }
        }

        ci = ci.getSuperClass();
      }

      return newObjRef;
    }
    catch (Exception ex) {
      throw new JPFException(ex);
    }
  }

  private static void setJPFPrimitive(ElementInfo newObjEI, FieldInfo fi, Object javaObject) {
    try {

      String jpfTypeName = fi.getType();
      Class javaClass = javaObject.getClass();
      Field javaField = getField(fi.getName(), javaClass);
      javaField.setAccessible(true);

      if (jpfTypeName.equals("char")) {
        newObjEI.setCharField(fi, javaField.getChar(javaObject));
      }
      else if (jpfTypeName.equals("byte")) {
        newObjEI.setByteField(fi, javaField.getByte(javaObject));
      }
      else if (jpfTypeName.equals("short")) {
        newObjEI.setShortField(fi, javaField.getShort(javaObject));
      }
      else if (jpfTypeName.equals("int")) {
        newObjEI.setIntField(fi, javaField.getInt(javaObject));
      }
      else if (jpfTypeName.equals("long")) {
        newObjEI.setLongField(fi, javaField.getLong(javaObject));
      }
      else if (jpfTypeName.equals("float")) {
        newObjEI.setFloatField(fi, javaField.getFloat(javaObject));
      }
      else if (jpfTypeName.equals("double")) {
        newObjEI.setDoubleField(fi, javaField.getDouble(javaObject));
      }
    }
    catch (Exception ex) {
      throw new JPFException(ex);
    }
  }

  private static Field getField(String fieldName, Class javaClass) throws NoSuchFieldException {
    while (true) {
      try {
        Field field = javaClass.getDeclaredField(fieldName);
        return field;
      } catch (NoSuchFieldException ex) {
        javaClass = javaClass.getSuperclass();

        if (javaClass == null) {
          throw ex;
        }
      }
    }

  }

  private static int getJPFArrayRef(MJIEnv env, FieldInfo fi, Object javaObject) throws NoSuchFieldException, IllegalAccessException {
    String fieldName = fi.getName();

    Class javaClass = javaObject.getClass();
    Field javaFiled = javaClass.getDeclaredField(fieldName);
    Class arrayElementClass = javaFiled.getType().getComponentType();
    javaFiled.setAccessible(true);

    Object javaArr = javaFiled.get(javaObject);
    int javaArrLength = Array.getLength(javaArr);
    int arrRef;

    if (arrayElementClass == Character.TYPE) {
      arrRef = env.newCharArray(javaArrLength);
      ElementInfo charArrRef = env.getElementInfo(arrRef);
      char[] charArr = charArrRef.asCharArray();

      for (int i = 0; i < javaArrLength; i++) {
        charArr[i] = Array.getChar(javaArr, i);
      }
    }
    else if (arrayElementClass == Byte.TYPE) {
      arrRef = env.newByteArray(javaArrLength);
      ElementInfo byteArrRef = env.getElementInfo(arrRef);
      byte[] byteArr = byteArrRef.asByteArray();

      for (int i = 0; i < javaArrLength; i++) {
        byteArr[i] = Array.getByte(javaArr, i);
      }
    }
    else if (arrayElementClass == Short.TYPE) {
      arrRef = env.newShortArray(javaArrLength);
      ElementInfo shortArrRef = env.getElementInfo(arrRef);
      short[] shortArr = shortArrRef.asShortArray();

      for (int i = 0; i < javaArrLength; i++) {
        shortArr[i] = Array.getShort(javaArr, i);
      }
    }
    else if (arrayElementClass == Integer.TYPE) {
      arrRef = env.newIntArray(javaArrLength);
      ElementInfo intArrRef = env.getElementInfo(arrRef);
      int[] intArr = intArrRef.asIntArray();

      for (int i = 0; i < javaArrLength; i++) {
        intArr[i] = Array.getInt(javaArr, i);
      }
    }
    else if (arrayElementClass == Long.TYPE) {
      arrRef = env.newLongArray(javaArrLength);
      ElementInfo longArrRef = env.getElementInfo(arrRef);
      long[] longArr = longArrRef.asLongArray();

      for (int i = 0; i < javaArrLength; i++) {
        longArr[i] = Array.getLong(javaArr, i);
      }
    }
    else if (arrayElementClass == Float.TYPE) {
      arrRef = env.newFloatArray(javaArrLength);
      ElementInfo floatArrRef = env.getElementInfo(arrRef);
      float[] floatArr = floatArrRef.asFloatArray();

      for (int i = 0; i < javaArrLength; i++) {
        floatArr[i] = Array.getFloat(javaArr, i);
      }
    }
    else if (arrayElementClass == Double.TYPE) {
      arrRef = env.newDoubleArray(javaArrLength);
      ElementInfo floatArrRef = env.getElementInfo(arrRef);
      double[] doubleArr = floatArrRef.asDoubleArray();

      for (int i = 0; i < javaArrLength; i++) {
        doubleArr[i] = Array.getDouble(javaArr, i);
      }
    }
    else {
      arrRef = env.newObjectArray(arrayElementClass.getCanonicalName(), javaArrLength);
      ElementInfo arrayEI = env.getElementInfo(arrRef);

      Fields fields = arrayEI.getFields();

      for (int i = 0; i < javaArrLength; i++) {
        int newArrElRef;
        Object javaArrEl = Array.get(javaArr, i);        
        
        if (javaArrEl != null) {
          newArrElRef = JPFObjectFromJavaObject(env, javaArrEl);
        }
        else {
          newArrElRef = MJIEnv.NULL;
        }

        fields.setReferenceValue(i, newArrElRef);
      }
    }

    return arrRef;
  }

  // Do we need this??
  public static Object javaObjectFromJPFObject(ElementInfo ei) {
    try {
      String typeName = ei.getType();
      Class clazz = ClassLoader.getSystemClassLoader().loadClass(typeName);

      Object javaObject = clazz.newInstance();
      ClassInfo ci = ei.getClassInfo();
      while (ci != null) {

        for (FieldInfo fi : ci.getDeclaredInstanceFields()) {
          if (!fi.isReference()) {
            setJavaPrimitive(javaObject, ei, fi);
          }
        }

        ci = ci.getSuperClass();
      }

      return javaObject;
    } catch (Exception ex) {
      throw new JPFException(ex);
    }
  }

  private static void setJavaPrimitive(Object javaObject, ElementInfo ei, FieldInfo fi) throws NoSuchFieldException, IllegalAccessException {
    String primitiveType = fi.getName();
    String fieldName = fi.getName();

    Class javaClass = javaObject.getClass();
    Field javaField = javaClass.getDeclaredField(fieldName);
    javaField.setAccessible(true);

    if (primitiveType.equals("char")) {
      javaField.setChar(javaObject, ei.getCharField(fi));
    }
    else if (primitiveType.equals("byte")) {
      javaField.setByte(javaObject, ei.getByteField(fi));
    }
    else if (primitiveType.equals("short")) {
      javaField.setShort(javaObject, ei.getShortField(fi));
    }
    else if (primitiveType.equals("int")) {
      javaField.setInt(javaObject, ei.getIntField(fi));
    }
    else if (primitiveType.equals("long")) {
      javaField.setLong(javaObject, ei.getLongField(fi));
    }
    else if (primitiveType.equals("float")) {
      javaField.setFloat(javaObject, ei.getFloatField(fi));
    }
    else if (primitiveType.equals("double")) {
      javaField.setDouble(javaObject, ei.getDoubleField(fi));
    }
    else {
      throw new JPFException("Can't convert " + primitiveType + " to " +
              javaField.getClass().getCanonicalName());
    }
  }

  private static boolean isArrayField(FieldInfo fi) {
    return fi.getType().lastIndexOf('[') >= 0;
  }


}
