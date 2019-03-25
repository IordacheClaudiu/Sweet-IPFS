package utils.date

import java.util.*

class DateUtils {
    object GMT {
        fun time(): Date {
            val calendar = Calendar.getInstance(java.util.TimeZone.getTimeZone("GMT"))
            return calendar.time
        }
    }
}