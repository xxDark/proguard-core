package proguard.evaluation

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.datatest.withGivens
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import proguard.classfile.ProgramClass
import proguard.evaluation.value.BasicValueFactory
import proguard.evaluation.value.DetailedArrayValueFactory
import proguard.evaluation.value.IdentifiedValueFactory
import proguard.evaluation.value.InstructionOffsetValue
import proguard.evaluation.value.MultiTypedReferenceValueFactory
import proguard.evaluation.value.ParticularReferenceValue
import proguard.evaluation.value.ParticularValueFactory
import proguard.evaluation.value.RangeIntegerValue
import proguard.evaluation.value.TopValue
import proguard.evaluation.value.TracedReferenceValue
import proguard.evaluation.value.TypedReferenceValueFactory
import proguard.evaluation.value.Value
import proguard.evaluation.value.`object`.AnalyzedObjectFactory

private val basicValueFactory = BasicValueFactory()
private val typedValueFactory = TypedReferenceValueFactory()
private val particularValueFactory = ParticularValueFactory(ParticularReferenceValueFactory())
private val detailedArrayValueFactory = ParticularValueFactory(
    DetailedArrayValueFactory(ParticularReferenceValueFactory()),
    ParticularReferenceValueFactory(),
)
private val identifiedValueFactory = IdentifiedValueFactory()
private val multiTypedValueFactory = MultiTypedReferenceValueFactory()

private const val STRING_TYPE = "Ljava/lang/String;"

/** Creates a stack with the given values, which may be empty slots. */
private fun stackOf(vararg stackValues: Value?): Stack {
    val stack = Stack(stackValues.size.coerceAtLeast(1))
    stackValues.forEachIndexed { index, value ->
        stack.push(basicValueFactory.createIntegerValue())
        stack.setBottom(index, value)
    }
    return stack
}

/** Creates a variable frame with the given values, which may be empty variables. */
private fun variablesOf(vararg variableValues: Value?): Variables {
    val variables = Variables(variableValues.size)
    // Set the values directly, since `store` does not accept empty variables.
    variableValues.forEachIndexed { index, value -> variables.values[index] = value }
    return variables
}

/**
 * [Stack.generalize] and [Variables.generalize] skip slots that hold the identical [Value] instance
 * in both frames, and only report a change when the generalization differs from the value that was
 * already there.
 *
 * These tests pin down the invariants that fast path relies on:
 *  - `value.generalize(value)` is equal to `value` for every kind of [Value].
 *  - the `changed` flag returned by both methods is still exact, since the partial evaluator uses
 *    it to decide whether a code block needs to be re-evaluated.
 */
class GeneralizeIdentityTest : BehaviorSpec({

    val values: List<Pair<String, Value>> = listOf(
        "unknown value" to BasicValueFactory.UNKNOWN_VALUE,
        "top value" to TopValue(),

        "unknown integer" to basicValueFactory.createIntegerValue(),
        "unknown long" to basicValueFactory.createLongValue(),
        "unknown float" to basicValueFactory.createFloatValue(),
        "unknown double" to basicValueFactory.createDoubleValue(),

        "particular integer" to particularValueFactory.createIntegerValue(42),
        "particular long" to particularValueFactory.createLongValue(42L),
        "particular float" to particularValueFactory.createFloatValue(4.2f),
        "particular double" to particularValueFactory.createDoubleValue(4.2),
        // NaN is not equal to itself under `==`; the value classes compare raw bits instead, which
        // is exactly what the identity fast path and the `changed` flag depend on.
        "particular float NaN" to particularValueFactory.createFloatValue(Float.NaN),
        "particular double NaN" to particularValueFactory.createDoubleValue(Double.NaN),
        "particular float -0.0" to particularValueFactory.createFloatValue(-0.0f),
        "particular double -0.0" to particularValueFactory.createDoubleValue(-0.0),

        "range integer" to RangeIntegerValue(1, 10),

        "identified integer" to identifiedValueFactory.createIntegerValue(),
        "identified long" to identifiedValueFactory.createLongValue(),
        "identified float" to identifiedValueFactory.createFloatValue(),
        "identified double" to identifiedValueFactory.createDoubleValue(),

        "unknown reference" to basicValueFactory.createReferenceValue(),
        "null reference" to typedValueFactory.createReferenceValueNull(),
        "typed reference" to typedValueFactory.createReferenceValue(STRING_TYPE, null, false, true),
        "identified reference" to identifiedValueFactory.createReferenceValue(
            STRING_TYPE,
            null,
            false,
            true,
        ),
        "particular reference" to ParticularReferenceValue(
            ProgramClass(),
            ParticularReferenceValueFactory(),
            1,
            AnalyzedObjectFactory.createPrecise("value"),
        ),
        "multi typed reference" to multiTypedValueFactory.createReferenceValue(
            STRING_TYPE,
            null,
            false,
            true,
        ),
        "array reference" to typedValueFactory.createArrayReferenceValue(
            "I",
            null,
            particularValueFactory.createIntegerValue(2),
        ),
        "identified array reference" to identifiedValueFactory.createArrayReferenceValue(
            "I",
            null,
            particularValueFactory.createIntegerValue(2),
        ),
        "detailed array reference" to detailedArrayValueFactory.createArrayReferenceValue(
            "I",
            null,
            particularValueFactory.createIntegerValue(2),
        ),
        "traced reference" to TracedReferenceValue(
            typedValueFactory.createReferenceValue(STRING_TYPE, null, false, true),
            InstructionOffsetValue(3),
        ),

        "empty instruction offset" to InstructionOffsetValue(intArrayOf()),
        "instruction offset" to InstructionOffsetValue(3),
        "multiple instruction offsets" to InstructionOffsetValue(intArrayOf(3, 7, 11)),
    )

    withGivens(nameFn = { (name, _) -> "A $name" }, values) { (_, value) ->
        When("It is generalized with itself") {
            val generalizedValue = value.generalize(value)

            Then("The result is equal to the original value") {
                // Skipping the slot is only sound if generalizing a value
                // with itself does not widen it.
                generalizedValue shouldBe value
            }

            Then("The result is the original value") {
                // Every implementation short-circuits on identity or equality.
                generalizedValue shouldBeSameInstanceAs value
            }
        }

        When("It is compared with itself") {
            Then("It is equal to itself") {
                // The `changed` flag falls back to `equals` when the generalization is a new
                // instance, so `equals` has to be reflexive, NaN included.
                value.equals(value) shouldBe true
            }
        }
    }

    Given("Two stacks holding the identical value instance") {
        val shared = particularValueFactory.createIntegerValue(42)
        val stack = stackOf(shared, shared)
        val other = stackOf(shared, shared)

        When("They are generalized") {
            val changed = stack.generalize(other)

            Then("No change is reported") {
                changed shouldBe false
            }

            Then("The slots are left untouched") {
                stack.getBottom(0) shouldBeSameInstanceAs shared
                stack.getBottom(1) shouldBeSameInstanceAs shared
            }
        }
    }

    Given("Two stacks holding equal but distinct value instances") {
        val stack = stackOf(particularValueFactory.createIntegerValue(42))
        val other = stackOf(ParticularValueFactory().createIntegerValue(42))

        When("They are generalized") {
            val changed = stack.generalize(other)

            Then("No change is reported") {
                changed shouldBe false
            }

            Then("The slot still holds an equal value") {
                stack.getBottom(0) shouldBe particularValueFactory.createIntegerValue(42)
            }
        }
    }

    Given("Two stacks holding different values") {
        val stack = stackOf(particularValueFactory.createIntegerValue(42))
        val other = stackOf(particularValueFactory.createIntegerValue(43))

        When("They are generalized") {
            val changed = stack.generalize(other)

            Then("A change is reported") {
                changed shouldBe true
            }

            Then("The slot holds the generalized value") {
                stack.getBottom(0) shouldBe BasicValueFactory.INTEGER_VALUE
            }
        }
    }

    Given("Two stacks with an empty slot") {
        val stack = stackOf(null)
        val other = stackOf(null)

        When("They are generalized") {
            val changed = stack.generalize(other)

            Then("No change is reported") {
                changed shouldBe false
            }

            Then("The slot stays empty") {
                stack.getBottom(0) shouldBe null
            }
        }
    }

    Given("A stack with a value and a stack with an empty slot") {
        val stack = stackOf(particularValueFactory.createIntegerValue(42))
        val other = stackOf(null)

        When("They are generalized") {
            val changed = stack.generalize(other)

            Then("A change is reported") {
                changed shouldBe true
            }

            Then("The slot is cleared") {
                stack.getBottom(0) shouldBe null
            }
        }
    }

    Given("A stack with an empty slot and a stack with a value") {
        val stack = stackOf(null)
        val other = stackOf(particularValueFactory.createIntegerValue(42))

        When("They are generalized") {
            val changed = stack.generalize(other)

            Then("No change is reported") {
                changed shouldBe false
            }

            Then("The slot stays empty") {
                stack.getBottom(0) shouldBe null
            }
        }
    }

    Given("Two stacks with identical slots, of which the other one grew larger") {
        val shared = particularValueFactory.createIntegerValue(42)

        val stack = Stack(4)
        stack.push(shared)

        val other = Stack(4)
        other.push(shared)
        other.push(shared)
        other.push(shared)
        other.pop()
        other.pop()

        When("They are generalized") {
            val changed = stack.generalize(other)

            Then("No change is reported") {
                changed shouldBe false
            }

            Then("The maximum size of the other stack is taken over") {
                stack.actualMaxSize shouldBe 3
            }
        }
    }

    Given("Two variable frames holding the identical value instance") {
        val shared = particularValueFactory.createIntegerValue(42)
        val variables = variablesOf(shared, shared)
        val other = variablesOf(shared, shared)

        When("They are generalized") {
            val changed = variables.generalize(other, false)

            Then("No change is reported") {
                changed shouldBe false
            }

            Then("The variables are left untouched") {
                variables.getValue(0) shouldBeSameInstanceAs shared
                variables.getValue(1) shouldBeSameInstanceAs shared
            }
        }
    }

    Given("Two variable frames holding equal but distinct value instances") {
        val variables = variablesOf(particularValueFactory.createIntegerValue(42))
        val other = variablesOf(ParticularValueFactory().createIntegerValue(42))

        When("They are generalized") {
            val changed = variables.generalize(other, false)

            Then("No change is reported") {
                changed shouldBe false
            }

            Then("The variable still holds an equal value") {
                variables.getValue(0) shouldBe particularValueFactory.createIntegerValue(42)
            }
        }
    }

    Given("Two variable frames holding different values") {
        val variables = variablesOf(particularValueFactory.createIntegerValue(42))
        val other = variablesOf(particularValueFactory.createIntegerValue(43))

        When("They are generalized") {
            val changed = variables.generalize(other, false)

            Then("A change is reported") {
                changed shouldBe true
            }

            Then("The variable holds the generalized value") {
                variables.getValue(0) shouldBe BasicValueFactory.INTEGER_VALUE
            }
        }
    }

    Given("Two variable frames with an empty variable") {
        val variables = variablesOf(null)
        val other = variablesOf(null)

        When("They are generalized, clearing conflicting other variables") {
            val changed = variables.generalize(other, true)

            Then("No change is reported") {
                changed shouldBe false
            }

            Then("Both variables stay empty") {
                variables.getValue(0) shouldBe null
                other.getValue(0) shouldBe null
            }
        }
    }

    Given("A variable frame with a value and a variable frame with an empty variable") {
        val variables = variablesOf(particularValueFactory.createIntegerValue(42))
        val other = variablesOf(null)

        When("They are generalized") {
            val changed = variables.generalize(other, false)

            Then("A change is reported") {
                changed shouldBe true
            }

            Then("The variable is cleared") {
                variables.getValue(0) shouldBe null
            }
        }
    }

    Given("A variable frame with an empty variable and a variable frame with a value") {
        val variables = variablesOf(null)
        val other = variablesOf(particularValueFactory.createIntegerValue(42))

        When("They are generalized") {
            val changed = variables.generalize(other, false)

            Then("No change is reported") {
                changed shouldBe false
            }

            Then("The variable stays empty") {
                variables.getValue(0) shouldBe null
            }
        }
    }

    Given("Two variable frames holding values of a conflicting computational type") {
        val variables = variablesOf(particularValueFactory.createIntegerValue(42))
        val other = variablesOf(
            typedValueFactory.createReferenceValue(STRING_TYPE, null, false, true),
        )

        When("They are generalized, keeping conflicting other variables") {
            val changed = variables.generalize(other, false)

            Then("A change is reported") {
                changed shouldBe true
            }

            Then("The variable is cleared, but the other one is kept") {
                variables.getValue(0) shouldBe null
                other.getValue(0) shouldNotBe null
            }
        }
    }

    Given("Two other variable frames holding values of a conflicting computational type") {
        val variables = variablesOf(particularValueFactory.createIntegerValue(42))
        val other = variablesOf(
            typedValueFactory.createReferenceValue(STRING_TYPE, null, false, true),
        )

        When("They are generalized, clearing conflicting other variables") {
            val changed = variables.generalize(other, true)

            Then("A change is reported") {
                changed shouldBe true
            }

            Then("Both variables are cleared") {
                variables.getValue(0) shouldBe null
                other.getValue(0) shouldBe null
            }
        }
    }
})
