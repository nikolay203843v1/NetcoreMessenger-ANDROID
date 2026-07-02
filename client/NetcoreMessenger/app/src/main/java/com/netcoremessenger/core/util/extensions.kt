package com.netcoremessenger.core.util

import android.content.Context
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

fun Context.showToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
    Toast.makeText(this, message, duration).show()
}

fun CoroutineScope.launchIO(block: suspend CoroutineScope.() -> Unit) =
    launch(Dispatchers.IO + SupervisorJob(), block = block)

fun Long.toChatTitle(currentUserId: Long, otherUserName: String): String {
    return otherUserName
}
