package proguard.normalize;

import proguard.classfile.*;
import proguard.classfile.constant.*;
import proguard.classfile.constant.visitor.*;
import proguard.classfile.editor.ConstantPoolEditor;
import proguard.classfile.visitor.ClassVisitor;

/**
 * This class transforms method references (MethodrefConstant) to interface methods into interface
 * method references (InterfaceMethodrefConstant).
 */
public class InterfaceMethodReferenceFixer implements ClassVisitor, ConstantVisitor {
  private ConstantPoolEditor constantPoolEditor;

  // Implementations for ClassVisitor.

  @Override
  public void visitAnyClass(Clazz clazz) {}

  @Override
  public void visitProgramClass(ProgramClass programClass) {
    constantPoolEditor = new ConstantPoolEditor(programClass);
    programClass.accept(new AllConstantVisitor(this));
  }

  // Implementations for ConstantVisitor.

  @Override
  public void visitAnyConstant(Clazz clazz, Constant constant) {}

  @Override
  public void visitMethodrefConstant(Clazz clazz, MethodrefConstant methodrefConstant) {
    if (referencesInterfaceMethod(methodrefConstant)) {
      ProgramClass programClass = (ProgramClass) clazz;

      int i = constantPoolEditor.findOrAddConstant(methodrefConstant);
      programClass.constantPool[i] =
          new InterfaceMethodrefConstant(
              methodrefConstant.u2classIndex,
              methodrefConstant.u2nameAndTypeIndex,
              methodrefConstant.referencedClass,
              methodrefConstant.referencedMethod);
    }
  }

  private static boolean referencesInterfaceMethod(MethodrefConstant constant) {
    if (constant.referencedClass == null) {
      return false;
    }

    return (constant.referencedClass.getAccessFlags() & AccessConstants.INTERFACE) != 0;
  }
}
