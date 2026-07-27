package com.wishlist.app.util

import java.util.concurrent.TimeUnit

/**
 * Formats a "고민한 기간" duration as whole days. 시작일/완료일 are date-only, so anything finer
 * than a day would just be noise.
 */
fun formatPonderedDuration(millis: Long): String =
    "${TimeUnit.MILLISECONDS.toDays(millis)}일"
