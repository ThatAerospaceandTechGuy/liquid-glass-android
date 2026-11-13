// Root build script: the only two plugins in this project.
// No libraries, no version catalog, no annotation processors.
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
}
