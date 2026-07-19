package com.duitrip.app.data.model

import com.google.firebase.firestore.Exclude
import org.junit.Assert.fail
import org.junit.Test

/**
 * Guards against the class of bug that crashed trip creation: a model exposing a
 * getter Firestore would try to serialize but can't — a function/lambda getter (causes
 * an object cycle) or a computed property that isn't annotated @Exclude.
 *
 * Pure JVM reflection — fast, no emulator. Runs in testDebugUnitTest.
 */
class ModelSerializationTest {

    // Every data class written to Firestore (top-level docs + nested).
    private val firestoreClasses = listOf(
        User::class.java,
        Trip::class.java,
        TripMember::class.java,
        TripInvite::class.java,
        CustomCategory::class.java,
        Expense::class.java,
        SplitEntry::class.java,
        Settlement::class.java,
    )

    @Test
    fun models_haveNoFirestoreUnsafeGetters() {
        for (clazz in firestoreClasses) {
            val backingFields = clazz.declaredFields.map { it.name }.toSet()
            for (method in clazz.declaredMethods) {
                if (method.parameterCount != 0) continue
                val name = method.name
                val isGetter = name.startsWith("get") || name.startsWith("is")
                if (!isGetter) continue
                // Skip data-class/Kotlin synthetics that aren't property getters.
                if (name == "getClass" || name.contains("$")) continue

                val excluded = method.isAnnotationPresent(Exclude::class.java)

                // 1) A getter returning a function type breaks serialization (object cycle).
                if (method.returnType.name.startsWith("kotlin.jvm.functions.Function") && !excluded) {
                    fail("${clazz.simpleName}.$name returns a function type without @Exclude — breaks Firestore serialization")
                }

                // 2) A computed property (no backing field) must be @Exclude, or Firestore
                //    will try to serialize the getter's result.
                val fieldName = if (name.startsWith("get")) name.removePrefix("get").replaceFirstChar { it.lowercase() } else name
                val hasBackingField = fieldName in backingFields
                if (!hasBackingField && !excluded) {
                    fail("${clazz.simpleName}.$name is a computed getter that isn't @Exclude — Firestore will try to serialize it")
                }
            }
        }
    }
}
