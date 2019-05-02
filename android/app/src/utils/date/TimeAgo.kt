package utils.date

import java.util.*

class TimeAgo {
    companion object {
        private val SECOND_MILLIS = 1000
        private val MINUTE_MILLIS = 60 * SECOND_MILLIS
        private val HOUR_MILLIS = 60 * MINUTE_MILLIS
        private val DAY_MILLIS = 24 * HOUR_MILLIS

        fun getTimeAgo(date: Date): String? {
            return getTimeAgo(date.time)
        }

        fun getTimeAgo(timestamp: Long): String? {
            var time = timestamp
            if (time < 1000000000000L) {
                time *= 1000

            }
            val now = DateUtils.GMT.time().time
            if (time > now || time <= 0) {
                return null
            }

            val diff = now - time
            return if (diff < MINUTE_MILLIS) {
                "just now"
            } else if (diff < 2 * MINUTE_MILLIS) {
                "a minute ago"
            } else if (diff < 50 * MINUTE_MILLIS) {
                val minutes = diff / MINUTE_MILLIS
                "$minutes minutes ago"
            } else if (diff < 90 * MINUTE_MILLIS) {
                "an hour ago"
            } else if (diff < 24 * HOUR_MILLIS) {
                val hours = diff / HOUR_MILLIS
                "$hours hours ago"
            } else if (diff < 48 * HOUR_MILLIS) {
                "yesterday"
            } else {
                val days = diff / DAY_MILLIS
                "$days days ago"
            }
        }
    }
}