/*
 * ProGuardCORE -- library to process Java bytecode.
 *
 * Copyright (c) 2002-2020 Guardsquare NV
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package proguard.classfile.visitor;

import proguard.classfile.*;

/**
 * This {@link ClassVisitor} delegates its visits to another given {@link ClassVisitor}, but only
 * when the visited class has the proper access flags.
 *
 * @see ClassConstants
 * @author Eric Lafortune
 */
public class ClassAccessFilter implements ClassVisitor {
  private final int requiredSetAccessFlags;
  private final int requiredUnsetAccessFlags;
  private final ClassVisitor acceptedClassVisitor;
  private final ClassVisitor rejectedClassVisitor;

  /**
   * Creates a new ClassAccessFilter.
   *
   * @param requiredSetAccessFlags the class access flags that should be set.
   * @param requiredUnsetAccessFlags the class access flags that should be unset.
   * @param acceptedClassVisitor the <code>ClassVisitor</code> to which visits will be delegated.
   * @param rejectedClassVisitor the <code>ClassVisitor</code> to which visits of classes that do
   *     not have the proper flags will be delegated.
   */
  public ClassAccessFilter(
      int requiredSetAccessFlags,
      int requiredUnsetAccessFlags,
      ClassVisitor acceptedClassVisitor,
      ClassVisitor rejectedClassVisitor) {
    this.requiredSetAccessFlags = requiredSetAccessFlags;
    this.requiredUnsetAccessFlags = requiredUnsetAccessFlags;
    this.acceptedClassVisitor = acceptedClassVisitor;
    this.rejectedClassVisitor = rejectedClassVisitor;
  }

  /**
   * Creates a new ClassAccessFilter.
   *
   * @param requiredSetAccessFlags the class access flags that should be set.
   * @param requiredUnsetAccessFlags the class access flags that should be unset.
   * @param acceptedClassVisitor the <code>ClassVisitor</code> to which visits will be delegated.
   */
  public ClassAccessFilter(
      int requiredSetAccessFlags, int requiredUnsetAccessFlags, ClassVisitor acceptedClassVisitor) {
    this(requiredSetAccessFlags, requiredUnsetAccessFlags, acceptedClassVisitor, null);
  }

  // Implementations for ClassVisitor.

  @Override
  public void visitAnyClass(Clazz clazz) {
    ClassVisitor delegateVisitor = getDelegateVisitor(clazz.getAccessFlags());
    if (delegateVisitor != null) {
      clazz.accept(delegateVisitor);
    }
  }

  // Small utility methods.

  private ClassVisitor getDelegateVisitor(int accessFlags) {
    return accepted(accessFlags) ? acceptedClassVisitor : rejectedClassVisitor;
  }

  private boolean accepted(int accessFlags) {
    return (requiredSetAccessFlags & ~accessFlags) == 0
        && (requiredUnsetAccessFlags & accessFlags) == 0;
  }
}
