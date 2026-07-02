package com.netcoremessenger.core.util

object PhoneNumberUtils {
    private val phoneRegex = Regex("^\\+?[1-9]\\d{6,14}$")

    fun isValidPhone(phone: String): Boolean {
        return phoneRegex.matches(phone.replace(Regex("[\\s\\-()]"), ""))
    }

    fun formatPhoneDisplay(phone: String): String {
        val digits = phone.replace(Regex("[^\\d+]"), "")
        if (digits.length < 4) return digits

        val code = when {
            digits.startsWith("+7") -> "+7"
            digits.startsWith("7") -> "+7"
            digits.startsWith("+1") -> "+1"
            else -> ""
        }

        val number = if (code.isNotEmpty()) digits.removePrefix(code.removePrefix("+")) else digits

        return when (code) {
            "+7" -> {
                val parts = number.chunked(3)
                "+7 ${parts.getOrElse(1) { "" }} ${parts.getOrElse(2) { "" }} ${parts.getOrElse(3) { "" }}".trim()
            }
            "+1" -> {
                val parts = number.chunked(3)
                "+1 ${parts.getOrElse(0) { "" }} ${parts.getOrElse(1) { "" }} ${parts.getOrElse(2) { "" }}".trim()
            }
            else -> digits
        }
    }

    fun formatPhoneForApi(phone: String): String {
        return phone.replace(Regex("[^\\d+]"), "")
    }
}
