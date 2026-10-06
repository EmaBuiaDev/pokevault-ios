package com.emabuia.pokevault.data

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

actual fun platformSecureStore(data: FileCache): SecureStore = KeychainStore(SERVICE)

private const val SERVICE = "com.emabuia.pokevault.auth"

/**
 * Password generiche nel Portachiavi, una per chiave.
 *
 * "Dopo il primo sblocco, solo questo dispositivo": leggibile anche col
 * telefono bloccato (un refresh in background), mai copiata su un altro
 * iPhone da un backup. Dopo un ripristino si rifa' il login, come con l'SDK.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private class KeychainStore(private val service: String) : SecureStore {

    override fun read(key: String): String? = memScoped {
        val query = query(key, kSecReturnData to kCFBooleanTrue, kSecMatchLimit to kSecMatchLimitOne)
        val result = alloc<CFTypeRefVar>()
        val status = SecItemCopyMatching(query, result.ptr)
        CFRelease(query)
        if (status != errSecSuccess) return null
        val data = CFBridgingRelease(result.value) as? NSData ?: return null
        NSString.create(data = data, encoding = NSUTF8StringEncoding)?.toString()
    }

    override fun write(key: String, value: String) {
        // Cancella e riscrivi: piu' semplice di SecItemUpdate, e la voce e' una sola.
        remove(key)
        @Suppress("CAST_NEVER_SUCCEEDS")
        val bytes = (value as NSString).dataUsingEncoding(NSUTF8StringEncoding) ?: return
        val data = CFBridgingRetain(bytes)
        val item = query(key, kSecValueData to data, kSecAttrAccessible to kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly)
        SecItemAdd(item, null)
        CFRelease(item)
        CFRelease(data)
    }

    override fun remove(key: String) {
        val query = query(key)
        SecItemDelete(query)
        CFRelease(query)
    }

    /** La voce [key] di questo servizio, piu' [extra]. Da rilasciare con CFRelease. */
    private fun query(key: String, vararg extra: Pair<CFTypeRef?, CFTypeRef?>): CFMutableDictionaryRef? {
        val dictionary = CFDictionaryCreateMutable(
            null, 0, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr,
        )
        // Il dizionario trattiene chiavi e valori: le stringhe si rilasciano subito.
        val serviceRef = CFBridgingRetain(service)
        val accountRef = CFBridgingRetain(key)
        CFDictionaryAddValue(dictionary, kSecClass, kSecClassGenericPassword)
        CFDictionaryAddValue(dictionary, kSecAttrService, serviceRef)
        CFDictionaryAddValue(dictionary, kSecAttrAccount, accountRef)
        for ((k, v) in extra) CFDictionaryAddValue(dictionary, k, v)
        CFRelease(serviceRef)
        CFRelease(accountRef)
        return dictionary
    }
}
