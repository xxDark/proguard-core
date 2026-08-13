package proguard.classfile.util

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import proguard.classfile.AccessConstants
import proguard.classfile.ClassConstants
import proguard.classfile.ProgramMethod
import proguard.classfile.VersionConstants
import proguard.classfile.attribute.CodeAttribute
import proguard.classfile.editor.ClassBuilder
import proguard.classfile.editor.CompactCodeAttributeComposer
import proguard.classfile.instruction.InstructionFactory

class BranchTargetFinderTest : FunSpec({

    fun verifyLeaders(
        testName: String,
        code: CompactCodeAttributeComposer.() -> Unit,
        expectedLeaders: List<Boolean>,
    ) {
        val programClass =
            ClassBuilder(
                VersionConstants.CLASS_VERSION_1_8,
                AccessConstants.PUBLIC,
                "Test",
                ClassConstants.NAME_JAVA_LANG_OBJECT,
            )
                .addMethod(
                    AccessConstants.PUBLIC or AccessConstants.STATIC,
                    testName,
                    "()V",
                    100,
                ) { composer -> composer.apply(code) }
                .programClass

        val method = programClass.findMethod(testName, "()V") as ProgramMethod
        val codeAttribute = method.attributes.find { it is CodeAttribute } as CodeAttribute

        val finder = BranchTargetFinder()
        finder.visitCodeAttribute(programClass, method, codeAttribute)

        val actualLeaders = buildList {
            var offset = 0
            while (offset < codeAttribute.u4codeLength) {
                add(finder.isLeader(offset))
                offset += InstructionFactory.create(codeAttribute.code, offset).length(offset)
            }
        }

        actualLeaders shouldBe expectedLeaders
    }

    test("first instruction is always a leader") {
        verifyLeaders(
            "first",
            { return_() },
            expectedLeaders = listOf(true),
        )
    }

    test("sequential instructions are not leaders") {
        verifyLeaders(
            "sequential",
            {
                iconst_0()
                iconst_1()
                return_()
            },
            expectedLeaders = listOf(true, false, false),
        )
    }

    test("unconditional goto target and after-goto instruction are leaders") {
        verifyLeaders(
            "goto",
            {
                val target = createLabel()
                iconst_0()
                goto_(target)
                iconst_1()
                label(target)
                iconst_2()
                return_()
            },
            expectedLeaders = listOf(true, false, true, true, false),
        )
    }

    test("conditional branch target and fall-through instruction are leaders") {
        verifyLeaders(
            "conditional",
            {
                val target = createLabel()
                iconst_0()
                ifeq(target)
                iconst_1()
                label(target)
                iconst_2()
                return_()
            },
            expectedLeaders = listOf(true, false, true, true, false),
        )
    }

    test("switch case targets are leaders") {
        verifyLeaders(
            "switch",
            {
                val case1 = createLabel()
                val case2 = createLabel()
                val defaultCase = createLabel()

                iconst_0()
                tableswitch(defaultCase, 0, 1, arrayOf(case1, case2))
                label(case1)
                iconst_1()
                goto_(defaultCase)
                label(case2)
                iconst_2()
                goto_(defaultCase)
                label(defaultCase)
                return_()
            },
            expectedLeaders = listOf(true, false, true, false, true, false, true),
        )
    }

    test("exception handler entry is a leader") {
        verifyLeaders(
            "exception",
            {
                val tryStart = createLabel()
                val tryEnd = createLabel()

                label(tryStart)
                iconst_0()
                label(tryEnd)
                catch_(tryStart, tryEnd, "java/lang/Exception", null)
                iconst_m1()
                return_()
            },
            expectedLeaders = listOf(true, true, false),
        )
    }

    test("overlapping branch target and after-branch instruction") {
        verifyLeaders(
            "overlap",
            {
                val shared = createLabel()
                iconst_0()
                goto_(shared)
                label(shared)
                iconst_1()
                return_()
            },
            expectedLeaders = listOf(true, false, true, false),
        )
    }
})
