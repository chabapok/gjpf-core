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

package gov.nasa.jpf.util.json;

import gov.nasa.jpf.JPF;
import gov.nasa.jpf.JPFException;
import gov.nasa.jpf.jvm.ChoiceGenerator;
import gov.nasa.jpf.jvm.ClassInfo;
import gov.nasa.jpf.jvm.ElementInfo;
import gov.nasa.jpf.jvm.FieldInfo;
import gov.nasa.jpf.jvm.Fields;
import gov.nasa.jpf.jvm.MJIEnv;
import gov.nasa.jpf.util.JPFLogger;
import gov.nasa.jpf.util.ObjectConverter;
import java.util.HashMap;
import java.util.Set;

/**
 * Object parsed from JSON document.
 * @author Ivan Mushketik
 */
public class JSONObject{

  private static final JPFLogger logger = JPF.getLogger("gov.nasa.jpf.util.json.JSONObject");

  private HashMap<String, Value> keyValues = new HashMap<String, Value>();
  private HashMap<String, CGCall> cgCalls = new HashMap<String, CGCall>();

  void addValue(String key, Value value) {
    if (keyValues.containsKey(key)) {
      throw new JPFException("Attempt to add two nodes with the same key in JSON object");
    }

    keyValues.put(key, value);
  }

  /**
   * Get value read from JSON document with specified key.
   * @param key - value's key.
   * @return read value.
   */
  public Value getValue(String key) {
    return keyValues.get(key);
  }

  public String[] getValuesKeys() {
    Set<String> valuesKeys = keyValues.keySet();
    String[] result = new String[keyValues.size()];

    valuesKeys.toArray(result);
    return result;
  }

  public void addCGCall(String key, CGCall cgCall) {
    if (cgCalls.containsKey(key)) {
      throw new JPFException("Attempt to add two CG with the same key in JSON object");
    }

    cgCalls.put(key, cgCall);
  }

  public CGCall getCGCall(String key) {
    return cgCalls.get(key);
  }

  public String[] getCGCallsKeys() {
    Set<String> cgKeys = cgCalls.keySet();
    String[] result = new String[cgKeys.size()];

    cgKeys.toArray(result);
    return result;
  }


  //--- the fillers
  public int fillObject(MJIEnv env, String typeName, ChoiceGenerator<?>[] cgs, String prefix) {

    int newObjRef = env.newObject(typeName);
    ElementInfo ei = env.getHeap().get(newObjRef);

    if (ei == null) {
      throw new JPFException("No such class " + typeName);
    }

    ClassInfo ci = ei.getClassInfo();

    // Fill all fields for this class until it has a super class
    while (ci != null) {
      FieldInfo[] fields = ci.getDeclaredInstanceFields();

      for (FieldInfo fi : fields) {
        String fieldName = fi.getName();
        Value val = getValue(fieldName);
        CGCall cgCall = getCGCall(fieldName);

        // If a value was defined in JSON document
        if (val != null) {
          fillFromValue(fi, ei, val, env, cgs, prefix);
        } else if (cgCall != null) {
          // Value of this field should be taken from CG
          String cgId = prefix + fieldName;
          ChoiceGenerator<?> cg = getCGByID(cgs, cgId);
          assert cg != null : "Expected CG with id " + cgId;
          
          Object cgResult = cg.getNextChoice();

          if (!fi.isReference()) {
            convertPrimititve(ei, fi, cgResult);
          } else {
            int newFieldRef = ObjectConverter.JPFObjectFromJavaObject(env, cgResult);
            ei.setReferenceField(fi, newFieldRef);
          }
        } else {
          logger.warning("Value for field ", fi.getFullName(), " isn't specified");
        }
      }

      ci = ci.getSuperClass();
    }

    return newObjRef;
  }

  private void fillFromValue(FieldInfo fi, ElementInfo ei, Value val, MJIEnv env, ChoiceGenerator<?>[] cgs, String prefix) {
    String fieldName = fi.getName();
    // Handle primitive types
    if (!fi.isReference()) {
      fillPrimitive(ei, fi, val);
    } else {
      if (isArrayType(fi.getType())) {
        int newArrRef = createArray(env, fi.getType(), val, cgs, prefix + fieldName);
        ei.setReferenceField(fi, newArrRef);

      } else {
        Creator creator = CreatorsFactory.getCreator(fi.getType());
        if (creator != null) {
          int newSubObjRef = creator.create(env, fi.getType(), val);
          ei.setReferenceField(fi, newSubObjRef);
          
        } else {
          // Not a special case. Fill it recursively
          String subTypeName = fi.getType();
          JSONObject jsonObj = val.getObject();
          int fieldRef = MJIEnv.NULL;
          if (jsonObj != null) {
            fieldRef = jsonObj.fillObject(env, subTypeName, cgs, prefix + fieldName);
          }
          ei.setReferenceField(fi.getName(), fieldRef);
        }
      }
    }
  }


  private static void fillPrimitive(ElementInfo ei, FieldInfo fi, Value val) {
    String primitiveName = fi.getType();

    if (primitiveName.equals("boolean")) {
      ei.setBooleanField(fi, val.getBoolean());

    } else if (primitiveName.equals("byte")) {
      ei.setByteField(fi, val.getDouble().byteValue());

    } else if (primitiveName.equals("short")) {
      ei.setShortField(fi, val.getDouble().shortValue());

    } else if (primitiveName.equals("int")) {
      ei.setIntField(fi, val.getDouble().intValue());

    } else if (primitiveName.equals("long")) {
      ei.setLongField(fi, val.getDouble().longValue());

    } else if (primitiveName.equals("float")) {
      ei.setFloatField(fi, val.getDouble().floatValue());

    } else if (primitiveName.equals("double")) {
      ei.setDoubleField(fi, val.getDouble());
    }
  }

  public int createArray(MJIEnv env, String typeName, Value value, ChoiceGenerator<?>[] cgs, String prefix) {
    Value vals[] = value.getArray();

    // Get name of array's elements types
    String arrayElementType = typeName.substring(0, typeName.lastIndexOf('['));
    int arrayRef;

    // Handle arrays of primitive types
    if (arrayElementType.equals("boolean")) {
       arrayRef = env.newBooleanArray(vals.length);
       ElementInfo arrayEI = env.getHeap().get(arrayRef);
       boolean bools[] = arrayEI.asBooleanArray();

       for (int i = 0; i < vals.length; i++) {
        bools[i] = vals[i].getBoolean();
      }
    } else if (arrayElementType.equals("byte")) {
       arrayRef = env.newByteArray(vals.length);
       ElementInfo arrayEI = env.getHeap().get(arrayRef);
       byte bytes[] = arrayEI.asByteArray();

       for (int i = 0; i < vals.length; i++) {
        bytes[i] = vals[i].getDouble().byteValue();
      }
    } else if (arrayElementType.equals("short")) {
       arrayRef = env.newShortArray(vals.length);
       ElementInfo arrayEI = env.getHeap().get(arrayRef);
       short shorts[] = arrayEI.asShortArray();

       for (int i = 0; i < vals.length; i++) {
        shorts[i] = vals[i].getDouble().shortValue();
      }
    } else if (arrayElementType.equals("int")) {
      arrayRef = env.newIntArray(vals.length);
      ElementInfo arrayEI = env.getHeap().get(arrayRef);
      int[] ints = arrayEI.asIntArray();

      for (int i = 0; i < vals.length; i++) {
        ints[i] = vals[i].getDouble().intValue();
      }
    } else if (arrayElementType.equals("long")) {
      arrayRef = env.newLongArray(vals.length);
      ElementInfo arrayEI = env.getHeap().get(arrayRef);
      long[] longs = arrayEI.asLongArray();

      for (int i = 0; i < vals.length; i++) {
        longs[i] = vals[i].getDouble().longValue();
      }
    } else if (arrayElementType.equals("float")) {
      arrayRef = env.newFloatArray(vals.length);
      ElementInfo arrayEI = env.getHeap().get(arrayRef);
      float[] floats = arrayEI.asFloatArray();

      for (int i = 0; i < vals.length; i++) {
        floats[i] = vals[i].getDouble().floatValue();
      }
    } else if (arrayElementType.equals("double")) {
      arrayRef = env.newDoubleArray(vals.length);
      ElementInfo arrayEI = env.getHeap().get(arrayRef);
      double[] doubles = arrayEI.asDoubleArray();

      for (int i = 0; i < vals.length; i++) {
        doubles[i] = vals[i].getDouble();
      }
    } else {
      // Not an array of primitive types
      arrayRef = env.newObjectArray(arrayElementType, vals.length);
      ElementInfo arrayEI = env.getElementInfo(arrayRef);

      Fields fields = arrayEI.getFields();

      Creator creator = CreatorsFactory.getCreator(arrayElementType);
      for (int i = 0; i < vals.length; i++) {

        int newObjRef;
        if (creator != null) {
          newObjRef = creator.create(env, arrayElementType, vals[i]);
        } else{
          if (isArrayType(arrayElementType)) {
            newObjRef = createArray(env, arrayElementType, vals[i], cgs, prefix + "[" + i);
          } else {
            JSONObject jsonObj = vals[i].getObject();
            if (jsonObj != null) {
              newObjRef = jsonObj.fillObject(env, arrayElementType, cgs, prefix + "[" + i);
            } else {
              newObjRef = MJIEnv.NULL;
            }
          }
        }

        fields.setReferenceValue(i, newObjRef);
      }

    }

    return arrayRef;
  }


  private boolean isArrayType(String typeName) {
    return typeName.lastIndexOf('[') >= 0;
  }

  /**
   * This is method is used to set field of primitive type from CG result object
   * @param ei - ElementInfo to set field in
   * @param fi - FieldInfo of a field we want to set
   * @param cgResult - result of CG call
   */
  private void convertPrimititve(ElementInfo ei, FieldInfo fi, Object cgResult) {
    String primitiveName = fi.getType();

    if (primitiveName.equals("boolean") && cgResult instanceof Boolean) {
      Boolean bool = (Boolean) cgResult;
      ei.setBooleanField(fi, bool.booleanValue());
    } else if (cgResult instanceof Number) {
      Number number = (Number) cgResult;

      if (primitiveName.equals("byte")) {
        ei.setByteField(fi, number.byteValue());

      } else if (primitiveName.equals("short")) {
        ei.setShortField(fi, number.shortValue());

      } else if (primitiveName.equals("int")) {
        ei.setIntField(fi, number.intValue());

      } else if (primitiveName.equals("long")) {
        ei.setLongField(fi, number.longValue());

      } else if (primitiveName.equals("float")) {
        ei.setFloatField(fi, number.floatValue());

      } else if (primitiveName.equals("double")) {
        ei.setDoubleField(fi, number.doubleValue());
      }
    } else if (cgResult instanceof Character) {
      Character c = (Character) cgResult;
      ei.setCharField(fi, c);
      
    } else {
      throw new JPFException("Can't convert " + cgResult.getClass().getCanonicalName() +
                             " to " + primitiveName);
    }
  }

  /**
   * Get CG from current state CG list by it's ID
   * @param cgs - array of CG from current state
   * @param id - id of the CG that we search for
   * @return - CG with a specified id or null if no id with such name found
   */
  private ChoiceGenerator<?> getCGByID(ChoiceGenerator<?>[] cgs, String id) {
    if (cgs == null) {
      return null;
    }
    
    for (int i = 0; i < cgs.length; i++) {
      if (cgs[i].getId().equals(id)) {
        return cgs[i];
      }
    }

    return null;
  }
}
