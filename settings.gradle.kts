pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { setUrl("https://jitpack.io") }
    }
}

rootProject.name = "AdsXml"
include(":app")

include(":admobAds:adsMain")
include(":admobAds:appOpen")
include(":admobAds:appUpdate")
include(":admobAds:bannerAds")
include(":admobAds:composeViews")
include(":admobAds:consent")
include(":admobAds:debug")
include(":admobAds:firebaseConfig")
include(":admobAds:interstitials")
include(":admobAds:nativeAds")
include(":admobAds:rewadedAd")
include(":admobAds:rewardedInterAds")
include(":admobAds:core")
include(":admobAds:supaBase")
