package forpdateam.ru.forpda.entity.app.other

import forpdateam.ru.forpda.presentation.Screen


class AppMenuItem(
    val id: Int,
    val screen: Screen? = null
) {
    var count: Int = 0
    /** Small red dot without a number, e.g. "an update is available". */
    var dot: Boolean = false
}
