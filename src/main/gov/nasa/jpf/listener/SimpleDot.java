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

package gov.nasa.jpf.listener;

import gov.nasa.jpf.Config;
import gov.nasa.jpf.JPFConfigException;
import gov.nasa.jpf.Error;
import gov.nasa.jpf.ListenerAdapter;
import gov.nasa.jpf.Property;
import gov.nasa.jpf.jvm.ChoiceGenerator;
import gov.nasa.jpf.jvm.ExceptionInfo;
import gov.nasa.jpf.jvm.JVM;
import gov.nasa.jpf.jvm.MethodInfo;
import gov.nasa.jpf.jvm.NoUncaughtExceptionsProperty;
import gov.nasa.jpf.jvm.NotDeadlockedProperty;
import gov.nasa.jpf.search.Search;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import gov.nasa.jpf.jvm.ThreadInfo;
import gov.nasa.jpf.jvm.bytecode.EXECUTENATIVE;
import gov.nasa.jpf.jvm.bytecode.FieldInstruction;
import gov.nasa.jpf.jvm.bytecode.GETFIELD;
import gov.nasa.jpf.jvm.bytecode.GETSTATIC;
import gov.nasa.jpf.jvm.bytecode.Instruction;
import gov.nasa.jpf.jvm.bytecode.InvokeInstruction;
import gov.nasa.jpf.jvm.bytecode.LockInstruction;
import gov.nasa.jpf.jvm.bytecode.PUTFIELD;
import gov.nasa.jpf.jvm.bytecode.PUTSTATIC;
import java.util.HashSet;

/**
 * an alternative  Graphviz dot-file generator for simple,educational state graphs
 * except of creating funny wallpapers, it doesn't help much in real life if the
 * state count gets > 50, but for the small ones it's actually quite readable.
 * Good for papers.
 *
 * normal states are labeled with their numeric ids, end states are double circled.
 * start, end and error states are color filled
 *
 * edges have two labels: the choice value at the beginning and the CG cause
 * at the end. Only the first incoming edge into a state shows the CG cause
 *
 * we only render one backtrack edge per from-state
 *
 * <2do> the GraphViz attributes should be initialized through config
 * <2do> GraphViz doesn't seem to handle color or fontname for head/tail labels correctly
 */
public class SimpleDot extends ListenerAdapter {

  JVM vm;
  String app;
  PrintWriter pw;

  int lastId; // where we come from

  // helper because GraphViz cannot eliminate duplicate edges
  HashSet<Integer> seenBacktracks;

  public SimpleDot( Config config){
    app = config.getTarget();

    String fname = config.getString("dot.file");
    if (fname == null){
      fname = stripToLastDot(app);
      fname += ".dot";
    }

    try {
      FileWriter fw = new FileWriter(fname);
      pw = new PrintWriter(fw);
    } catch (IOException iox){
      throw new JPFConfigException("unable to open SimpleDot output file: " + fname);
    }
  }

  //--- the listener interface

  @Override
  public void searchStarted(Search search){
    vm = search.getVM();
    seenBacktracks = new HashSet<Integer>();

    printHeader();
    printStartState("0");
  }

  @Override
  public void stateAdvanced(Search search){
    int id = search.getStateId();
    if (id == 0){
      return; // skip the root state and property violations (reported separately)
    }

    if (search.isErrorState()) {
      String eid = "e" + search.getNumberOfErrors();
      printTransition(getStateId(lastId), eid, getLastChoice(), getError(search));
      printErrorState(eid);

    } else if (search.isNewState()) {
      printTransition(getStateId(lastId), getStateId(id), getLastChoice(), getNextCG());

      if (search.isEndState()) {
        printEndState(getStateId(id));
      }

    } else { // already visited state
      printTransition(getStateId(lastId), getStateId(id), getLastChoice(), null);
    }

    lastId = id;
  }

  @Override
  public void stateBacktracked(Search search){
    int id = search.getStateId();
    ChoiceGenerator<?> cg = vm.getChoiceGenerator();
    if (cg.hasMoreChoices() && !cg.isDone()){
      if (!seenBacktracks.contains(lastId)){
        printBacktrack(getStateId(lastId),getStateId(id));
        seenBacktracks.add(lastId);
      }
      lastId = id;
    }
  }

  @Override
  public void searchFinished (Search search){
    pw.println("}");
    pw.close();
  }

  //--- data collection

  protected String getStateId (int id){
    return Integer.toString(id);
  }

  protected String getLastChoice() {
    ChoiceGenerator<?> cg = vm.getChoiceGenerator();
    Object choice = cg.getNextChoice();

    if (choice instanceof ThreadInfo){
      int idx = ((ThreadInfo)choice).getIndex();
      return "T"+idx;
    } else {
      return choice.toString(); // we probably want more here
    }
  }

  protected String getNextCG(){
    ChoiceGenerator<?> cg = vm.getChoiceGenerator(); // that's the next one
    Instruction insn = cg.getInsn();

    if (insn instanceof EXECUTENATIVE) {
      MethodInfo mi = ((EXECUTENATIVE) insn).getExecutedMethod();
      return mi.getName();
    } else if (insn instanceof FieldInstruction) {
      String varId = stripToLastDot(((FieldInstruction)insn).getVariableId());

      if (insn instanceof PUTFIELD) {
        return "put " + varId; // maybe add field name
      } else if (insn instanceof GETFIELD) {
        return "get " + varId; // maybe add field name
      } else if (insn instanceof PUTSTATIC) {
        return "sput " + varId;
      } else if (insn instanceof GETSTATIC) {
        return "sget " + varId;
      }
    } else if (insn instanceof LockInstruction){
      return "sync"; // maybe object
    } else if (insn instanceof InvokeInstruction){
      MethodInfo mi = ((InvokeInstruction) insn).getInvokedMethod();
      return mi.getName() + "()";
    }

    return insn.getMnemonic(); // our fallback
  }

  protected String getError (Search search){
    String e;
    Error error = search.getLastError();
    Property prop = error.getProperty();

    if (prop instanceof NoUncaughtExceptionsProperty){
      ExceptionInfo xi = ((NoUncaughtExceptionsProperty)prop).getUncaughtExceptionInfo();
      return stripToLastDot(xi.getExceptionClassname());

    } else if (prop instanceof NotDeadlockedProperty){
      return "deadlock";
    }

    // fallback
    return stripToLastDot(prop.getClass().getName());
  }

  protected static String stripToLastDot (String s){
    int i = s.lastIndexOf('.');
    if (i>=0){
      return s.substring(i+1);
    } else {
      return s;
    }
  }

  //--- dot file stuff

  protected void printHeader(){
    pw.println("digraph {");
    pw.println("node [shape=circle,style=filled,fillcolor=white]");
    pw.println("edge [fontsize=10,fontname=Helvetica,fontcolor=blue,color=cadetblue,style=\"setlinewidth(0.5)\",arrowhead=empty,arrowsize=0.5]");
    pw.println();
    pw.print("label=");
    pw.println(app);
    pw.println();
  }

  protected void printTransition(String fromState, String toState, String choiceVal, String cgCause){
    pw.println();
    pw.print(fromState);
    pw.print(" -> ");
    pw.print( toState);
    pw.print(" [taillabel=\"");
    pw.print(choiceVal);
    pw.print('"');
    if (cgCause != null){
      pw.print(",headlabel=\"");
      pw.print(cgCause);
      pw.print('"');
    }
    pw.println("]");
  }

  protected void printBacktrack (String fromState, String toState){
    pw.println();
    pw.print(fromState);
    pw.print(" -> ");
    pw.print( toState);
    pw.println(" [color=gray]");
  }

  protected void printStartState(String stateId){
    pw.print(stateId);
    pw.println(" [fillcolor=green]");
  }

  protected void printEndState(String stateId){
    pw.print(stateId);
    pw.println(" [shape=doublecircle,fillcolor=cyan]");
  }

  protected void printErrorState(String error){
    pw.print(error);
    pw.println(" [color=red,fillcolor=yellow]");
  }
}
