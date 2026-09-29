package com.komanda.business.features.pos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class CheckoutAttemptStoreTest {

    @Test
    fun computeCartHash_isDeterministic() {
        val items1 = listOf("item-1" to 2, "item-2" to 1)
        val hash1 = CheckoutAttemptStore.computeCartHash(items1, "Juan Perez", "Sin cebolla")

        val items2 = listOf("item-2" to 1, "item-1" to 2) // different input order
        val hash2 = CheckoutAttemptStore.computeCartHash(items2, "Juan Perez", "Sin cebolla")

        assertEquals(hash1, hash2)
    }

    @Test
    fun computeCartHash_differsOnDifferentData() {
        val items1 = listOf("item-1" to 2)
        val hash1 = CheckoutAttemptStore.computeCartHash(items1, "Juan Perez", null)

        val items2 = listOf("item-1" to 3)
        val hash2 = CheckoutAttemptStore.computeCartHash(items2, "Juan Perez", null)

        assertNotEquals(hash1, hash2)
    }
}
