package proguard.classfile.util

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import proguard.classfile.AccessConstants
import proguard.classfile.ClassPool
import proguard.exception.ProguardCoreException
import proguard.testutils.classfile.extensions.addMethod
import proguard.testutils.classfile.extensions.buildClass
import proguard.testutils.classfile.extensions.buildInterface

class MemberFinderTest : BehaviorSpec({

    Given("A class overwriting one of two methods") {
        val (classA, bar) = buildClass("A") {
            addMethod("foo")
            addMethod("bar")
        }

        val (classB, fooB) = buildClass("B", classA.name) {
            addMethod("foo")
        }

        val classPool = ClassPool(classA, classB)
        classPool.classesAccept(ClassReferenceInitializer(classPool, ClassPool()))

        When("Looking for a method only defined in the super class, with searchHierarchy set to false") {
            val foundBar = MemberFinder(false).findMethod(classB, "bar", "()V")
            Then("Nothing should be found") {
                foundBar shouldBe null
            }
        }

        When("Looking for a method only defined in the super class, with searchHierarchy set to true") {
            val foundBar = MemberFinder(true).findMethod(classB, "bar", "()V")
            Then("The super definition should be found") {
                foundBar shouldBe bar
            }
        }

        When("Looking for an overwritten method") {
            val foundFooHierarchy = MemberFinder(true).findMethod(classB, "foo", "()V")
            val foundFooNoHierarchy = MemberFinder(false).findMethod(classB, "foo", "()V")

            Then("The result should always be the overwritten method") {
                foundFooHierarchy shouldBe fooB
                foundFooNoHierarchy shouldBe fooB
            }
        }
    }

    Given("A class implementing a abstract interface, and an interface with default implementation") {

        val (classAbstract, abstractFoo) = buildInterface("Abstract") {
            addMethod("foo", accessFlags = AccessConstants.ABSTRACT)
        }

        val (classDefault, defaultFoo) = buildInterface("Default") {
            addInterface(classAbstract)
            addMethod("foo") {
                iconst_0()
                pop()
                return_()
            }
        }

        val (classConcrete, _) = buildClass("Concrete") {
            addInterface(classAbstract)
            addInterface(classDefault)
        }

        When("Looking for the method foo") {
            val firstResult = MemberFinder(true, false).findMethod(classConcrete, "foo", "()V")
            val defaultResult = MemberFinder(true, true).findMethod(classConcrete, "foo", "()V")

            Then("Stopping at the first result gives the abstract top-level definition") {
                firstResult shouldBe abstractFoo
            }
            Then("Continuing until an implementation was found, gives the default implementation") {
                defaultResult shouldBe defaultFoo
            }
        }

        When("Asking to continue on abstract results, but setting searchHierarchy to false") {
            Then("The MemberFinder throws") {
                shouldThrow<ProguardCoreException> { MemberFinder(false, true) }
            }
        }
    }
})
