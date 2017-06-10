package hudson.plugins.accurev

open class AccurevUtils {
    companion object {
        @JvmStatic fun cleanAccurevPath(str: String) = str.replace("\\", "/").removePrefix("/./")
    }
}
