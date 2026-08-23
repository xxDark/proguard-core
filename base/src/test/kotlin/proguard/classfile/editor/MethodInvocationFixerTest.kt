package proguard.classfile.editor

import io.kotest.core.spec.style.BehaviorSpec
import proguard.classfile.AccessConstants
import proguard.classfile.ClassConstants
import proguard.classfile.ClassPool
import proguard.classfile.attribute.visitor.AllAttributeVisitor
import proguard.classfile.util.ClassReferenceInitializer
import proguard.classfile.util.InstructionSequenceMatcher
import proguard.classfile.visitor.AllMethodVisitor
import proguard.normalize.InterfaceMethodReferenceFixer
import proguard.testutils.ClassPoolBuilder.Companion.fromSource
import proguard.testutils.KotlinSource
import proguard.testutils.classfile.extensions.addMethod
import proguard.testutils.classfile.extensions.addStaticMethod
import proguard.testutils.classfile.extensions.buildClass
import proguard.testutils.classfile.extensions.buildInterface
import proguard.testutils.classfile.extensions.get
import proguard.testutils.classfile.extensions.shouldMatch
import proguard.testutils.findMethod
import proguard.testutils.shouldMatch

class MethodInvocationFixerTest : BehaviorSpec({

    Given("A class hierarchy with a diamond") {

        val (programClassPool, libraryClassPool) = fromSource(
            KotlinSource(
                "Test.kt",
                """
            interface Api {
                val value: Int
            }

            interface DefaultApi : Api {
                override val value: Int get() = 42
            }

            interface Diamond : Api, DefaultApi

            class SampleA : Diamond
                """.trimIndent(),
            ),
        )

        When("Running the MethodInvocationFixer") {
            programClassPool.classesAccept(ClassReferenceInitializer(programClassPool, libraryClassPool))
            programClassPool.classesAccept(AllMethodVisitor(AllAttributeVisitor(MethodInvocationFixer())))

            Then("invokespecial is still there") {
                val sampleA = programClassPool.getClass("SampleA")
                val getValue = sampleA.findMethod("getValue")

                sampleA[getValue] shouldMatch {
                    aload_0()
                    invokespecial(InstructionSequenceMatcher.X)
                    ireturn()
                }
            }
        }
    }

    Given("A linear interface hierarchy with default method invocation") {

        val (apiClass, foo) = buildInterface("Api") {
            addMethod("foo", "()I", AccessConstants.ABSTRACT)
        }

        val (defaultClass, defaultFoo) = buildInterface("Default") {
            addInterface(apiClass)
            addMethod(foo.getName(apiClass), foo.getDescriptor(apiClass)) {
                iconst_3()
                ireturn()
            }
        }

        val (implClass, other) = buildClass("Impl") {
            addInterface(defaultClass)
            addMethod("other") {
                aload_0()
                invokespecial(defaultClass.name, defaultFoo.getName(defaultClass), defaultFoo.getDescriptor(defaultClass))
                pop()
                return_()
            }
        }

        val programClassPool = ClassPool(apiClass, implClass)

        When("Running the MethodInvocationFixer") {
            programClassPool.classesAccept(ClassReferenceInitializer(programClassPool, ClassPool()))
            programClassPool.classesAccept(InterfaceMethodReferenceFixer())
            programClassPool.classesAccept(AllMethodVisitor(AllAttributeVisitor(MethodInvocationFixer())))

            Then("invokespecial is still there") {
                implClass[other] shouldMatch {
                    aload_0()
                    invokespecial(defaultClass.name, defaultFoo.getName(defaultClass), defaultFoo.getDescriptor(defaultClass))
                    pop()
                    return_()
                }
            }
        }
    }
    Given("A simple interface hierarchy with unexpected invokespecial") {

        val (apiClass, foo) = buildInterface("Api") {
            addMethod("foo", "()I", AccessConstants.ABSTRACT)
        }

        val (implClass, other) = buildClass("Impl") {
            addInterface(apiClass)
            addMethod("other") {
                aload_0()
                invokespecial(apiClass.name, foo.getName(apiClass), foo.getDescriptor(apiClass))
                pop()
                return_()
            }
        }

        val programClassPool = ClassPool(apiClass, implClass)

        When("Running the MethodInvocationFixer") {
            programClassPool.classesAccept(ClassReferenceInitializer(programClassPool, ClassPool()))
            programClassPool.classesAccept(InterfaceMethodReferenceFixer())
            programClassPool.classesAccept(AllMethodVisitor(AllAttributeVisitor(MethodInvocationFixer())))

            Then("invokespecial is converted into an invokeinterface") {
                implClass[other] shouldMatch {
                    aload_0()
                    invokeinterface(apiClass, foo)
                    pop()
                    return_()
                }
            }
        }
    }

    Given("An unexpected non-invokespecial referring to an initializer") {

        val (referencedClass, _) = buildClass("Ref") {
            addMethod("<init>")
        }

        val (fooClass, foo) = buildClass("Foo") {
            addStaticMethod("foo") {
                new_(referencedClass)
                invokevirtual(referencedClass.name, ClassConstants.METHOD_NAME_INIT, ClassConstants.METHOD_TYPE_INIT)
                return_()
            }
        }

        When("Running the MethodInvocationFixer") {
            fooClass.accept(ClassReferenceInitializer(ClassPool(fooClass, referencedClass), ClassPool()))
            fooClass.accept(AllMethodVisitor(AllAttributeVisitor(MethodInvocationFixer())))

            Then("The instruction is converted into an invokespecial") {
                fooClass[foo] shouldMatch {
                    new_(referencedClass)
                    invokespecial(referencedClass.name, ClassConstants.METHOD_NAME_INIT, ClassConstants.METHOD_TYPE_INIT)
                    return_()
                }
            }
        }
    }

    Given("An invokespecial referring to an instance method in the same class") {

        val (fooClass, methods) = buildClass("Foo") {
            val bar = addMethod("bar") {
                return_()
            }

            val foo = addMethod("foo") {
                aload_0()
                invokespecial(targetClass.name, bar.getName(targetClass), bar.getDescriptor(targetClass))
                return_()
            }
            Pair(foo, bar)
        }
        val (foo, bar) = methods

        When("Running the MethodInvocationFixer") {
            fooClass.accept(ClassReferenceInitializer(ClassPool(fooClass), ClassPool()))
            fooClass.accept(AllMethodVisitor(AllAttributeVisitor(MethodInvocationFixer())))

            Then("The instruction is converted into an invokevirtual") {
                fooClass[foo] shouldMatch {
                    invokevirtual(fooClass.name, bar.getName(fooClass), bar.getDescriptor(fooClass))
                    return_()
                }
            }
        }
    }

    Given("An invokestatic referring to an instance method") {

        val (fooClass, methods) = buildClass("Foo") {
            val bar = addMethod("bar") {
                return_()
            }

            val foo = addMethod("foo") {
                aload_0()
                invokestatic(targetClass.name, bar.getName(targetClass), bar.getDescriptor(targetClass))
                return_()
            }
            Pair(foo, bar)
        }
        val (foo, bar) = methods

        When("Running the MethodInvocationFixer") {
            fooClass.accept(ClassReferenceInitializer(ClassPool(fooClass), ClassPool()))
            fooClass.accept(AllMethodVisitor(AllAttributeVisitor(MethodInvocationFixer())))

            Then("The instruction is converted into an invokevirtual") {
                fooClass[foo] shouldMatch {
                    invokevirtual(fooClass.name, bar.getName(fooClass), bar.getDescriptor(fooClass))
                    return_()
                }
            }
        }
    }
})
