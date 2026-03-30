package ephyra.domain.release.interactor

import ephyra.core.common.preference.Preference
import ephyra.core.common.preference.PreferenceStore
import ephyra.domain.release.model.Release
import ephyra.domain.release.service.ReleaseService
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class GetApplicationReleaseTest {

    private lateinit var getApplicationRelease: GetApplicationRelease
    private lateinit var releaseService: ReleaseService
    private lateinit var preference: Preference<Long>

    @BeforeEach
    fun beforeEach() {
        val preferenceStore = mockk<PreferenceStore>()
        preference = mockk()
        every { preferenceStore.getLong(any(), any()) } returns preference
        releaseService = mockk()

        getApplicationRelease = GetApplicationRelease(releaseService, preferenceStore)
    }

    @Test
    fun `When has update but is preview expect new update`() = runTest {
        coEvery { preference.get() } returns 0
        every { preference.set(any()) }.answers { }

        val release = Release(
            "r2000",
            "info",
            "http://example.com/release_link",
            "http://example.com/release_link.apk",
        )

        coEvery { releaseService.latest(any()) } returns release

        val result = getApplicationRelease.await(
            GetApplicationRelease.Arguments(
                isFoss = false,
                isPreview = true,
                commitCount = 1000,
                versionName = "",
                repository = "test",
            ),
        )

        (result as GetApplicationRelease.Result.NewUpdate).release shouldBe GetApplicationRelease.Result.NewUpdate(
            release,
        ).release
    }

    @Test
    fun `When has update expect new update`() = runTest {
        coEvery { preference.get() } returns 0
        every { preference.set(any()) }.answers { }

        val release = Release(
            "v2.0.0",
            "info",
            "http://example.com/release_link",
            "http://example.com/release_link.apk",
        )

        coEvery { releaseService.latest(any()) } returns release

        val result = getApplicationRelease.await(
            GetApplicationRelease.Arguments(
                isFoss = false,
                isPreview = false,
                commitCount = 0,
                versionName = "v1.0.0",
                repository = "test",
            ),
        )

        (result as GetApplicationRelease.Result.NewUpdate).release shouldBe GetApplicationRelease.Result.NewUpdate(
            release,
        ).release
    }

    @Test
    fun `When has no update expect no new update`() = runTest {
        coEvery { preference.get() } returns 0
        every { preference.set(any()) }.answers { }

        val release = Release(
            "v1.0.0",
            "info",
            "http://example.com/release_link",
            "http://example.com/release_link.apk",
        )

        coEvery { releaseService.latest(any()) } returns release

        val result = getApplicationRelease.await(
            GetApplicationRelease.Arguments(
                isFoss = false,
                isPreview = false,
                commitCount = 0,
                versionName = "v2.0.0",
                repository = "test",
            ),
        )

        result shouldBe GetApplicationRelease.Result.NoNewUpdate
    }

    @Test
    fun `When nightly has update with different SHA expect new update`() = runTest {
        coEvery { preference.get() } returns 0
        every { preference.set(any()) }.answers { }

        val release = Release(
            "abc1234",
            "info",
            "http://example.com/release_link",
            "http://example.com/release_link.apk",
        )

        coEvery { releaseService.latest(any()) } returns release

        val result = getApplicationRelease.await(
            GetApplicationRelease.Arguments(
                isFoss = false,
                isPreview = false,
                isNightly = true,
                commitCount = 0,
                commitSha = "def5678",
                versionName = "0.19.4-nightly-def5678",
                repository = "test",
            ),
        )

        (result as GetApplicationRelease.Result.NewUpdate).release shouldBe GetApplicationRelease.Result.NewUpdate(
            release,
        ).release
    }

    @Test
    fun `When nightly has same SHA expect no new update`() = runTest {
        coEvery { preference.get() } returns 0
        every { preference.set(any()) }.answers { }

        val release = Release(
            "abc1234",
            "info",
            "http://example.com/release_link",
            "http://example.com/release_link.apk",
        )

        coEvery { releaseService.latest(any()) } returns release

        val result = getApplicationRelease.await(
            GetApplicationRelease.Arguments(
                isFoss = false,
                isPreview = false,
                isNightly = true,
                commitCount = 0,
                commitSha = "abc1234",
                versionName = "0.19.4-nightly-abc1234",
                repository = "test",
            ),
        )

        result shouldBe GetApplicationRelease.Result.NoNewUpdate
    }

    @Test
    fun `When now is before three days expect no new update`() = runTest {
        coEvery { preference.get() } returns Instant.now().toEpochMilli()
        every { preference.set(any()) }.answers { }

        val release = Release(
            "v1.0.0",
            "info",
            "http://example.com/release_link",
            "http://example.com/release_link.apk",
        )

        coEvery { releaseService.latest(any()) } returns release

        val result = getApplicationRelease.await(
            GetApplicationRelease.Arguments(
                isFoss = false,
                isPreview = false,
                commitCount = 0,
                versionName = "v2.0.0",
                repository = "test",
            ),
        )

        coVerify(exactly = 0) { releaseService.latest(any()) }
        result shouldBe GetApplicationRelease.Result.NoNewUpdate
    }
}
