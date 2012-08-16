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
package gov.nasa.jpf.util;


import java.io.PrintStream;

/**
 * an immutable Vector that is implemented as a bitwise trie with 32-element nodes. Trie levels are
 * ordered in most significant bit order on top, so that consecutive key values are stored in the
 * same (terminal) nodes.
 */
public class PersistentMsbIntMap<V> extends PersistentIntMap<V> {
  
  //--- static ImmutableObjectTable methods
  
  static <V> Node<V> createNode (int shift, int finalShift, int key, V value, V nodeValue, Result<V> result){
    int idx = ((key>>>shift) & 0x01f);
    Node<V> node;
    
    if (shift == finalShift) {
      if (nodeValue != null) {
        Object[] nodesOrValues = new Object[2];
        nodesOrValues[0] = nodeValue;
        nodesOrValues[1] = value;
        int bitmap = (1 << idx) | 1;
        node = new BitmapNode<V>(bitmap, nodesOrValues);        
      } else {
        node = new OneNode<V>( idx, value);
      }
      result.addedValue(node, value);
      
    } else {
      node = createNode( shift-5, finalShift, key, value, nodeValue, result);
      node = new OneNode<V>(idx, node);
    }
    
    return node;
  }

  static <V> Node<V> createAndMergeNode (int shift, int finalShift, int key, V value, Node<V> mergeNode, Result<V> result){
    int k = (key >>> shift);
    int idx = (k & 0x01f);
    
    int bitmap = (1 << idx) | 1;
    Object[] nodesOrValues = new Object[2];
    nodesOrValues[0] = mergeNode;

    Node<V> node;
    if (shift == finalShift) {
      nodesOrValues[1] = value;
      node = new BitmapNode<V>(bitmap, nodesOrValues);
      result.addedValue( node, value);  
    } else {
      nodesOrValues[1] = createNode( shift-5, finalShift, key, value, null, result);
      node = new BitmapNode<V>(bitmap, nodesOrValues);
    }
    
    return node;
  }
  
  static <V> Node<V> propagateMergeNode (int shift, int nodeShift, Node<V> node){
    shift -= 5;
    while (shift > nodeShift){
      node = new OneNode<V>(0, node);
      shift -= 5;
    }
    
    return node;
  }
  
  /**
  static int getInitialShift (int key) {
    if ((key & 0xc0000000) != 0) return 30;
    if ((key & 0x3e000000) != 0) return 25;
    if ((key & 0x1f00000) != 0)  return 20;
    if ((key & 0xf8000) != 0)    return 15;
    if ((key & 0x7c00) != 0)     return 10;
    if ((key & 0x3e0) != 0)      return 5;
    return 0;
  }
  **/
  
  static final int LeadingMultiplyDeBruijnBitPosition[] = {
    0, 5, 0, 10, 10, 20, 0, 25, 10, 10, 15, 15, 20, 25, 0, 30,
    5, 10, 20, 25, 15, 15, 20, 5, 15, 25, 20, 5, 25, 5, 0, 30
  };
  static int getInitialShift (int v){
    v |= v >>> 1;
    v |= v >>> 2;
    v |= v >>> 4;
    v |= v >>> 8;
    v |= v >>> 16;

    return LeadingMultiplyDeBruijnBitPosition[(v * 0x07C4ACDD) >>> 27];
  }

  
  
  /**
  static int getFinalShift (int key) {
    if (key == 0 || (key & 0x1f) != 0)       return 0;
    if ((key & 0x3e0) != 0)      return 5;
    if ((key & 0x7c00) != 0)     return 10;
    if ((key & 0xf8000) != 0)    return 15;
    if ((key & 0x1f00000) != 0)  return 20;
    if ((key & 0x3e000000) != 0) return 25;
    return 30;
  }
  **/
  
  /**
  static final int Mod37Shifts[] = {
    0, 0, 0, 25, 0, 20, 25, 0, 0, 15, 20, 30, 25, 10, 0, 10, 0,
    5, 15, 0,  25,  20, 30, 15, 25, 10,  10, 5, 0, 20, 10, 5, 5,
    20, 5, 15, 15
  };
  static int getFinalShift (int v){
    return Mod37Shifts[ (-v & v) % 37];
  }
  **/

  static final int TrailingMultiplyDeBruijnBitPosition[] = {
      0, 0, 25, 0, 25, 10, 20, 0, 30, 20, 20, 15, 25, 15, 0, 5, 
      30, 25, 10, 20, 20, 15, 15, 5, 25, 10, 15, 5, 10, 5, 10, 5
  };
  static int getFinalShift (int v) {
    return TrailingMultiplyDeBruijnBitPosition[(((v & -v) * 0x077CB531)) >>> 27];
  }
  
  //--- instance fields
  
  // for msb first mode, we keep track of the initial shift to save cloning empty top nodes
  // (minimize trie height)
  final protected int rootShift;
  
  
  public PersistentMsbIntMap(){
    rootShift = 0;
  }

  protected PersistentMsbIntMap (int size, Node<V> root, int initialShift){
    super(size,root);
    this.rootShift = initialShift;
  }
  
  //--- set() related methods
  protected Node<V> assocNodeValue (Node<V> node, V value, Result<V> result){
    Object o = node.getElement(0);

    if (o != null){                        // we got something for index 0
      if (o instanceof Node){           // we've got a node
        Node<V> newNodeElement = assocNodeValue( (Node<V>)o, value, result);
        if (newNodeElement == o){
          return node;
        } else {
          return node.cloneWithReplacedElement( 0, newNodeElement);
        }

      } else {                             // we've got a value
        if (o == value){
          return node;
        } else {
          node = node.cloneWithReplacedElement( 0, value);
          result.replacedValue( node, (V)o);
          return node;
        }
      }

    } else {                               // we didn't have anything for this index
      node = node.cloneWithAddedElement( 0, value);
      result.addedValue( node, value);
      return node;
    }
  }
  
  protected Node<V> assoc (int shift, int finalShift, int key, V value, Node<V> node, Result<V> result){
    int k = key >>> shift;
    int levelIdx = k & 0x1f;
    Object o = node.getElement(levelIdx);

    if (o != null){                       // we already have a node or value for this index

      if (o instanceof Node){             // we had a node for this index
        Node<V> replacedNode;
        if (shift == finalShift){         // at value level
          replacedNode = assocNodeValue( node, value, result);
        } else {                          // not at value level yet, recurse
          replacedNode = assoc( shift-5, finalShift, key, value, (Node<V>)o, result);
        }
        if (replacedNode == o){           // nothing changed
          return node;
        } else {                          // element got replaced, clone the node
          return node.cloneWithReplacedElement( levelIdx, replacedNode);
        }        

      } else { // we had a value for this index
        if (shift == finalShift){         // at value level
          if (value == o){                // nothing changed
            return node;
          } else {                        // clone with updated value
            node = node.cloneWithReplacedElement( levelIdx, value);
            result.replacedValue( node, (V)o);
            return node;
          }

        } else {                          // not at value level yet
          Node<V> addedNode = createNode( shift-5, finalShift, key, value, (V)o, result);
          return node.cloneWithReplacedElement( levelIdx, addedNode);
        }
      }

    } else { // nothing for this index yet
      if (shift == finalShift){           // at value level
        node = node.cloneWithAddedElement( levelIdx, value);
        result.addedValue( node, value);

      } else {                            // not at value level yet
        Node<V> addedNode = createNode( shift-5, finalShift, key, value, null, result);
        node = node.cloneWithAddedElement( levelIdx, addedNode);
      }
      return node;
    }
  }
  
  public PersistentMsbIntMap<V> set (int key, V value, Result<V> result){
    int ish = getInitialShift(key);
    int fsh = getFinalShift(key);
    result.clear();
    
    if (root == null){
      Node<V> newRoot = createNode( ish, fsh, key, value, null, result);
      return new PersistentMsbIntMap<V>( 1, newRoot, ish);

    } else {            
      Node<V> newRoot;
      int newRootShift;
      
      if (ish <= rootShift) { // new key can be added to current root (key smaller than previous ones)
        newRoot = assoc( rootShift, fsh, key, value, root, result);
        newRootShift = rootShift;

      } else { // current root has to be merged into new one
        Node<V> mergeNode = propagateMergeNode( ish, rootShift, root);
        newRoot = createAndMergeNode( ish, fsh, key, value, mergeNode, result);
        newRootShift = ish;
      }
      
      if (root == newRoot){ // key and value were already there
        return this;
      } else { // could have been a replaced value that didn't change the size
        int newSize = size + result.getSizeChange();
        return new PersistentMsbIntMap<V>( newSize, newRoot, newRootShift);
      }
    }
  }

  public PersistentMsbIntMap<V> set (int key, V value){
    Result<V> result = Result.getNewResult();
    return set( key, value, result);
  }  

  /**
   * retrieve value for specified key
   * @return null if this key is not in the map
   */
  public V get (int key){
    if (root != null){
      int shift = rootShift;
      int finalShift = getFinalShift(key);      
      Node<V> node = root;

      for (;;){
        int levelIdx = (key>>>shift) & 0x1f;
        Object o = node.getElement(levelIdx);
        if (o != null){                      // do we have something for this index
          if (o instanceof Node){            // we have a node
            node = (Node<V>)o;
            if (shift == finalShift){        // at value level
              return node.getNodeValue();
            } else {                         // shift to next level (tail recursion)
              shift -= 5;
              continue;
            }
            
          } else {                           // we have a value
            if (shift == finalShift){        // at value level
              return (V)o;
            } else {
              return null;                   // can't go down, so it's not there
            }
          }
          
        } else {                             // nothing for this index
          return null;
        }
      }
      
    } else {                                 // no root
      return null;
    }
  }

  //--- remove() related methods
  
  protected Node<V> removeNodeValue (Node<V> node, Result<V> result){
    Object o = node.getElement(0);

    if (o != null){                        // we got something for index 0
      if (o instanceof Node){              // we've got a node
        Node<V> newNodeElement = removeNodeValue((Node<V>)o, result);
        if (newNodeElement == null){
          return node.cloneWithoutElement( 0);
        } else if (newNodeElement == o){
          return node;
        } else {
          return node.cloneWithReplacedElement( 0, newNodeElement);
        }

      } else {                             // we've got a value
        node = node.cloneWithoutElement( 0);
        result.removedValue( node, (V)o);
        return node;
      }

    } else {                               // we didn't have anything for this index
      return node;
    }
  }
  
  protected Node<V> remove (int shift, int finalShift, int key, Node<V> node, Result<V> result){
    int k = (key >>> shift);
    int levelIdx = k & 0x1f;
    Object o = node.getElement(levelIdx);

    if (o != null){                        // we got something for this index
      if (o instanceof Node){              // we've got a node
        Node<V> newNodeElement;
        if (shift == finalShift){          // at value level
          newNodeElement = removeNodeValue( (Node<V>)o, result);
        } else {                           // not yet at value level, recurse downwards
          newNodeElement = remove(shift-5, finalShift, key, (Node<V>)o, result);
        }
        if (newNodeElement == null){       // nothing left
          return node.cloneWithoutElement( levelIdx);
        } else if (newNodeElement == o){   // nothing changed
          return node;
        } else {
          return node.cloneWithReplacedElement( levelIdx, newNodeElement);
        }

      } else {                             // we've got a value
        if (shift == finalShift){          // at value level
          node = node.cloneWithoutElement( levelIdx);
          result.removedValue( node, (V)o);
          return node;
        } else {                           // not at value level, key isn't in the map
          return node;
        }
      }
    } else {                               // we didn't have anything for this index
      return node;
    }
  }
   
  /**
   * the public remove() method
   */
  public PersistentMsbIntMap<V> remove(int key, Result<V> result){
    if (root == null){
      return this;
      
    } else {
      result.clear();
      
      int fsh = getFinalShift(key);
      //Node<V> newRoot = root.remove( rootShift, fsh, key, result);
      Node<V> newRoot = remove( rootShift, fsh, key, root, result);

      if (root == newRoot){ // nothing removed
        return this;
      } else {
        // <2do> we should check if we can increase the initialShift
        return new PersistentMsbIntMap<V>( size-1, newRoot, rootShift);
      }
    }
  }

  public PersistentMsbIntMap<V> remove(int key){
    Result<V> result = Result.getNewResult();
    return remove( key, result);
  } 
  
  //--- bulk remove
  
  protected Node<V> removeAllSatisfying (Node<V> node, Predicate<V> predicate, Result<V> result) {
    return node.removeAllSatisfying( this, predicate, result);
  }
  
  public PersistentMsbIntMap<V> removeAllSatisfying (Predicate<V> predicate){
    Result<V> result = Result.getNewResult();
    return removeAllSatisfying(predicate, result);
  }
    
  public PersistentMsbIntMap<V> removeAllSatisfying (Predicate<V> predicate, Result<V> result){
    if (root != null){
      result.clear();
      Node<V> newRoot = (Node<V>) root.removeAllSatisfying( this, predicate, result);
      
      // <2do> we should check if we can increase the initialShift

      return new PersistentMsbIntMap<V>( size+result.getSizeChange(), newRoot, rootShift);
      
    } else {
      return this;
    }
  }
  
  
  //--- iterator-less generic value processing
  
  protected void processNode (Node<V> node, Processor<V> processor) {
    node.process(this, processor);
  }
  
  public void process (Processor<V> processor){
    if (root != null){
      root.process(this, processor);
    }
  }
  
  public void processInKeyOrder (Processor<V> processor){
    process( processor);
  }
  
  
  //--- debugging
  
  public void printOn (PrintStream ps) {
    if (root != null) {
      root.printNodeInfoOn(ps);
      ps.println();
      root.printOn(ps, 1);
    }
  }
}