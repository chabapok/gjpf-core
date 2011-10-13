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

package gov.nasa.jpf.jvm;

import gov.nasa.jpf.jvm.choice.IntChoiceFromList;
import gov.nasa.jpf.util.test.TestJPF;
import org.junit.Test;

/**
 * unit test for IntChoiceFromList
 */
public class IntChoiceFromListTest extends TestJPF {

  private void testListContents(IntChoiceGenerator cg) {
    cg.advance();
    assertTrue (cg.hasMoreChoices());
    assertEquals ((int)cg.getNextChoice(), 1);
    cg.advance();
    assertTrue (cg.hasMoreChoices());
    assertEquals ((int)cg.getNextChoice(), 2);
    cg.advance();
    assertTrue (cg.hasMoreChoices());
    assertEquals ((int)cg.getNextChoice(), 3);
    cg.advance();
    assertEquals ((int)cg.getNextChoice(), 4);
  }

  @Test
  public void testListWithOutDuplicates() {
    IntChoiceFromList cg = new IntChoiceFromList("test", 1, 2, 3, 4);
    testListContents(cg);
    assertFalse (cg.hasMoreChoices());
  }

  @Test
  public void testListWithDuplicates() {
    IntChoiceFromList cg = new IntChoiceFromList("test1", 1, 2, 3, 4, 4);
    testListContents(cg);
    cg.advance();
    assertFalse (cg.hasMoreChoices());
    assertEquals ((int)cg.getNextChoice(), 4);
  }

  @Test
  public void testListWithDuplicates2() {
    IntChoiceFromList cg = new IntChoiceFromList("test2", 1, 2, 1, 2, 1, 2);
    cg.advance();
    assertTrue (cg.hasMoreChoices());
    assertEquals ((int)cg.getNextChoice(), 1);
    cg.advance();
    assertTrue (cg.hasMoreChoices());
    assertEquals ((int)cg.getNextChoice(), 2);
    cg.advance();
    assertTrue (cg.hasMoreChoices());
    assertEquals ((int)cg.getNextChoice(), 1);
    cg.advance();
    assertTrue (cg.hasMoreChoices());
    assertEquals ((int)cg.getNextChoice(), 2);
    cg.advance();
    assertTrue (cg.hasMoreChoices());
    assertEquals ((int)cg.getNextChoice(), 1);
    cg.advance();
    assertFalse (cg.hasMoreChoices());
    assertEquals ((int)cg.getNextChoice(), 2);
  }
}
