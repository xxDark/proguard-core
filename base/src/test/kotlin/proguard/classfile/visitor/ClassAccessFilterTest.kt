package proguard.classfile.visitor

import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import proguard.classfile.AccessConstants
import proguard.classfile.Clazz

class ClassAccessFilterTest : BehaviorSpec({

    Given("a ClassAccessFilter requiring PUBLIC set and FINAL unset") {
        val acceptedVisitor = mockk<ClassVisitor>(relaxUnitFun = true)
        val rejectedVisitor = mockk<ClassVisitor>(relaxUnitFun = true)

        val filter = ClassAccessFilter(
            AccessConstants.PUBLIC,
            AccessConstants.FINAL,
            acceptedVisitor,
            rejectedVisitor,
        )

        When("visiting a class that matches the criteria (PUBLIC, not FINAL)") {
            // relaxUnitFun = true automatically handles the void accept() method
            val clazz = mockk<Clazz>(relaxUnitFun = true)
            every { clazz.accessFlags } returns AccessConstants.PUBLIC

            filter.visitAnyClass(clazz)

            Then("the class accepts the acceptedVisitor") {
                verify(exactly = 1) { clazz.accept(acceptedVisitor) }
                verify(exactly = 0) { clazz.accept(rejectedVisitor) }
            }
        }

        When("visiting a class that has a forbidden flag (PUBLIC and FINAL)") {
            val clazz = mockk<Clazz>(relaxUnitFun = true)
            every { clazz.accessFlags } returns (AccessConstants.PUBLIC or AccessConstants.FINAL)

            filter.visitAnyClass(clazz)

            Then("the class accepts the rejectedVisitor") {
                verify(exactly = 0) { clazz.accept(acceptedVisitor) }
                verify(exactly = 1) { clazz.accept(rejectedVisitor) }
            }
        }

        When("visiting a class missing the required flag (Neither PUBLIC nor FINAL)") {
            val clazz = mockk<Clazz>(relaxUnitFun = true)
            every { clazz.accessFlags } returns 0

            filter.visitAnyClass(clazz)

            Then("the class accepts the rejectedVisitor") {
                verify(exactly = 0) { clazz.accept(acceptedVisitor) }
                verify(exactly = 1) { clazz.accept(rejectedVisitor) }
            }
        }
    }

    Given("a ClassAccessFilter without a rejected visitor") {
        val acceptedVisitor = mockk<ClassVisitor>(relaxUnitFun = true)

        val filter = ClassAccessFilter(
            AccessConstants.PUBLIC,
            0,
            acceptedVisitor,
        )

        When("visiting a class that does not match the criteria (PRIVATE)") {
            val clazz = mockk<Clazz>(relaxUnitFun = true)
            every { clazz.accessFlags } returns AccessConstants.PRIVATE

            filter.visitAnyClass(clazz)

            Then("no visitor is accepted and no exceptions are thrown") {
                verify(exactly = 0) { clazz.accept(any()) }
            }
        }
    }

    Given("a ClassAccessFilter requiring multiple set flags") {
        val acceptedVisitor = mockk<ClassVisitor>(relaxUnitFun = true)
        val rejectedVisitor = mockk<ClassVisitor>(relaxUnitFun = true)

        val filter = ClassAccessFilter(
            AccessConstants.PUBLIC or AccessConstants.INTERFACE,
            0,
            acceptedVisitor,
            rejectedVisitor,
        )

        When("visiting a class having both flags") {
            val clazz = mockk<Clazz>(relaxUnitFun = true)
            every { clazz.accessFlags } returns (AccessConstants.PUBLIC or AccessConstants.INTERFACE)

            filter.visitAnyClass(clazz)

            Then("the class accepts the acceptedVisitor") {
                verify(exactly = 1) { clazz.accept(acceptedVisitor) }
            }
        }

        When("visiting a class missing one of the required flags (Only PUBLIC)") {
            val clazz = mockk<Clazz>(relaxUnitFun = true)
            every { clazz.accessFlags } returns AccessConstants.PUBLIC

            filter.visitAnyClass(clazz)

            Then("the class accepts the rejectedVisitor") {
                verify(exactly = 1) { clazz.accept(rejectedVisitor) }
            }
        }
    }
})
