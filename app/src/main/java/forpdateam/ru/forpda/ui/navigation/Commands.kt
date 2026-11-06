package forpdateam.ru.forpda.ui.navigation

import com.github.terrakok.cicerone.Command

class SystemMessage(
    val message: String
) : Command

/** A message that stays until the user taps it away. */
class SystemAlert(
    val title: String,
    val message: String,
    /** Seconds until a retry makes sense: the dialog counts them down. Null: no countdown. */
    val waitSeconds: Long? = null
) : Command