package com.example.rebeka.admin

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

object AdminUtils {

    /** Длина родительского PIN. Меняется здесь — применяется во всех экранах сразу. */
    const val PIN_LENGTH = 6

    fun adminComponent(context: Context) =
        ComponentName(context, RebekaDeviceAdminReceiver::class.java)

    fun isAdminActive(context: Context): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        return dpm.isAdminActive(adminComponent(context))
    }

    /** Запускает системный экран запроса прав администратора — диалог тут есть, это ок. */
    fun requestAdminIntent(context: Context): Intent =
        Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent(context))
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Нужно, чтобы Rebeka не могли удалить без разрешения родителя."
            )
        }

    // --- PIN: никогда не хранить в открытом виде ---

    fun hashPin(pin: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt.toByteArray())
        val hashed = digest.digest(pin.toByteArray())
        return Base64.getEncoder().encodeToString(hashed)
    }

    fun newSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return Base64.getEncoder().encodeToString(bytes)
    }

    fun verifyPin(pin: String, salt: String, expectedHash: String): Boolean =
        hashPin(pin, salt) == expectedHash
}
