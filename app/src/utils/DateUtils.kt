package utils

import java.util.*

class DateUtils {
    object GMT {
        fun time(): Date {
            val calendar = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("GMT"))
            return calendar.time
        }
    }
}