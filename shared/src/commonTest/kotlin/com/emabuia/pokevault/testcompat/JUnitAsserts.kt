package com.emabuia.pokevault.testcompat

/**
 * Le asserzioni di org.junit.Assert, con lo stesso ordine degli argomenti
 * (il messaggio prima), sopra kotlin.test.
 *
 * Servono ai test portati da Android cosi' come sono: con queste girano anche
 * su iOS (iosSimulatorArm64Test in CI), dove JUnit non c'e'. Ed e' li' che
 * conta farli girare, perche' le espressioni regolari dei parser su iOS usano
 * un altro motore.
 */
fun assertEquals(expected: Any?, actual: Any?) = kotlin.test.assertEquals(expected, actual)

fun assertEquals(message: String?, expected: Any?, actual: Any?) = kotlin.test.assertEquals(expected, actual, message)

fun assertEquals(expected: Double, actual: Double, delta: Double) = kotlin.test.assertEquals(expected, actual, delta)

fun assertEquals(message: String?, expected: Double, actual: Double, delta: Double) =
    kotlin.test.assertEquals(expected, actual, delta, message)

fun assertEquals(expected: Float, actual: Float, delta: Float) = kotlin.test.assertEquals(expected, actual, delta)

fun assertEquals(message: String?, expected: Float, actual: Float, delta: Float) =
    kotlin.test.assertEquals(expected, actual, delta, message)

fun assertNotEquals(unexpected: Any?, actual: Any?) = kotlin.test.assertNotEquals(unexpected, actual)

fun assertNotEquals(message: String?, unexpected: Any?, actual: Any?) = kotlin.test.assertNotEquals(unexpected, actual, message)

fun assertTrue(condition: Boolean) = kotlin.test.assertTrue(condition)

fun assertTrue(message: String?, condition: Boolean) = kotlin.test.assertTrue(condition, message)

fun assertFalse(condition: Boolean) = kotlin.test.assertFalse(condition)

fun assertFalse(message: String?, condition: Boolean) = kotlin.test.assertFalse(condition, message)

fun assertNull(actual: Any?) = kotlin.test.assertNull(actual)

fun assertNull(message: String?, actual: Any?) = kotlin.test.assertNull(actual, message)

fun assertNotNull(actual: Any?) {
    kotlin.test.assertNotNull(actual)
}

fun assertNotNull(message: String?, actual: Any?) {
    kotlin.test.assertNotNull(actual, message)
}

fun fail(message: String?): Nothing = kotlin.test.fail(message)
