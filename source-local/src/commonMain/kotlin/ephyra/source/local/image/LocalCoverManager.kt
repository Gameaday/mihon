package ephyra.source.local.image

import com.hippo.unifile.UniFile
import eu.kanade.ephyra.source.model.SManga
import java.io.InputStream

expect class LocalCoverManager {

    fun find(mangaUrl: String): UniFile?

    fun update(manga: SManga, inputStream: InputStream): UniFile?
}
