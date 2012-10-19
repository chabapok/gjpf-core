package gov.nasa.jpf.jvm;

import gov.nasa.jpf.annotation.MJI;

public class JPF_java_lang_Boolean extends NativePeer {
  // <2do> at this point we deliberately do not override clinit

  @MJI
  public int valueOf__Z__Ljava_lang_Boolean_2 (MJIEnv env, int clsRef, boolean val) {
    return env.valueOfBoolean(val);
  }
}
