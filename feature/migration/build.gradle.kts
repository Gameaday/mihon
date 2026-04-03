plugins {
    id("ephyra.library")
    id("ephyra.library.compose")
}

android {
    namespace = "ephyra.feature.migration"
}

dependencies {
    api(projects.presentationCore)
    api(projects.domain)
    api(projects.i18n)
}
