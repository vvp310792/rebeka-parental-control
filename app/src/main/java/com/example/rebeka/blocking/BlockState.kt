package com.example.rebeka.blocking

/**
 * Общее состояние между BlockService и BlockAccessibilityService.
 *
 * Accessibility-служба живёт в своём процессе-компоненте и не может спрашивать
 * сервис напрямую, а лезть в БД на каждое событие окна слишком дорого —
 * событий десятки в секунду. Поэтому простой volatile-флаг.
 */
object BlockState {
    @Volatile
    var blocked: Boolean = false
}
