package media.whitewhale.iduo

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged

/**
 * Discover's host activity stays above Home as the focused app, and its window cannot take focus,
 * so Android would send the keyboard nowhere: it does not open, or typing is lost and Home stops
 * responding. While this text field is focused, the host is closed so Home can receive input; it is
 * prepared again once the field loses focus.
 */
internal fun Modifier.releasesDiscoverWhileTyping(field: String): Modifier = composed {
    val activity = LocalActivity.current as? MainActivity
    var focused by remember { mutableStateOf(false) }
    DisposableEffect(activity, focused) {
        val typing = activity != null && focused
        if (typing) LiveDiscover.setExternalResultPending(activity!!, "main", "typing-$field", true)
        onDispose { if (typing) LiveDiscover.setExternalResultPending(activity!!, "main", "typing-$field", false) }
    }
    Modifier.onFocusChanged { focused = it.isFocused }
}
