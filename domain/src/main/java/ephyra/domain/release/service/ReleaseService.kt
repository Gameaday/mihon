package ephyra.domain.release.service

import ephyra.domain.release.interactor.GetApplicationRelease
import ephyra.domain.release.model.Release

interface ReleaseService {

    suspend fun latest(arguments: GetApplicationRelease.Arguments): Release?
}
