package ephyra.app.widget

import android.content.Context
import android.util.AttributeSet
import android.widget.EditText
import androidx.core.view.inputmethod.EditorInfoCompat
import com.google.android.material.textfield.TextInputEditText
import ephyra.domain.base.BasePreferences
import ephyra.app.R
import ephyra.app.widget.TachiyomiTextInputEditText.Companion.setIncognito
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koin.core.context.GlobalContext

/**
 * A custom [TextInputEditText] that sets [EditorInfoCompat.IME_FLAG_NO_PERSONALIZED_LEARNING] to imeOptions
 * if [BasePreferences.incognitoMode] is true. Some IMEs may not respect this flag.
 *
 * @see setIncognito
 */
class TachiyomiTextInputEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = R.attr.editTextStyle,
) : TextInputEditText(context, attrs, defStyleAttr) {

    private var scope: CoroutineScope? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        setIncognito(scope!!, GlobalContext.get().get())
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        scope?.cancel()
        scope = null
    }

    companion object {
        /**
         * Sets Flow to this [EditText] that sets [EditorInfoCompat.IME_FLAG_NO_PERSONALIZED_LEARNING] to imeOptions
         * if [BasePreferences.incognitoMode] is true. Some IMEs may not respect this flag.
         */
        fun EditText.setIncognito(viewScope: CoroutineScope, preferences: BasePreferences) {
            preferences.incognitoMode().changes()
                .onEach {
                    imeOptions = if (it) {
                        imeOptions or EditorInfoCompat.IME_FLAG_NO_PERSONALIZED_LEARNING
                    } else {
                        imeOptions and EditorInfoCompat.IME_FLAG_NO_PERSONALIZED_LEARNING.inv()
                    }
                }
                .launchIn(viewScope)
        }
    }
}
