package app.kvity.data

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Текущая дата: выдаётся сразу и заново после каждой полуночи, чтобы экраны не показывали вчерашний день. */
fun ticker(today: () -> LocalDate = LocalDate::now): Flow<LocalDate> = flow {
    while (true) {
        emit(today())
        val now = LocalDateTime.now()
        delay(Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay()).toMillis() + 500)
    }
}
