package com.emabuia.pokevault.data

actual fun platformSecureStore(data: FileCache): SecureStore = FileSecureStore(data)
