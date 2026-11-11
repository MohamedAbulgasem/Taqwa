# The Wear app's own R8 rules (wearApp/build.gradle.kts). There are none yet: AGP keeps the
# manifest's components, and the libraries (Compose for Wear OS, the Tiles and complications
# libraries, Play services' Data Layer, kotlinx.serialization) ship their own consumer rules.
# Every rule added here names the failure on a minified build (staging or release) that asked for it.
