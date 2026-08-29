package com.example.rebeka.blocking

/**
 * Общее состояние между BlockService, экраном настроек и BlockAccessibilityService.
 *
 * Accessibility-служба получает десятки событий окон в секунду, поэтому лезть
 * в БД на каждое событие нельзя — только volatile-поля в памяти.
 */
object BlockState {

    @Volatile
    var blocked: Boolean = false

    /**
     * До какого момента разрешено удаление приложения. Родитель открывает это окно
     * из настроек под PIN, когда действительно хочет удалить приложение.
     *
     * Специально хранится в памяти, а не в БД: после перезагрузки телефона или
     * убийства процесса значение сбрасывается, то есть защита сама возвращается
     * во включённое состояние. Забыть выключить обратно невозможно.
     */
    @Volatile
    var uninstallAllowedUntilEpochMillis: Long = 0

    val uninstallProtectionActive: Boolean
        get() = System.currentTimeMillis() > uninstallAllowedUntilEpochMillis

    fun allowUninstall(minutes: Int) {
        uninstallAllowedUntilEpochMillis = System.currentTimeMillis() + minutes * 60_000L
    }
}
