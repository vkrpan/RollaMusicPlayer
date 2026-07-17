# R8/ProGuard rules for the app. Referenced from the release build type (and inherited by the
# baselineprofile plugin's auto-generated minified benchmarkRelease variant).

# jaudiotagger (the offline tag-editing fork) contains desktop-Java code paths that reference
# java.awt / javax.imageio; they are never executed on Android (see :feature:tageditor's
# `isAndroid` handling), but R8's strict missing-class check fails the build without these.
-dontwarn java.awt.image.BufferedImage
-dontwarn javax.imageio.ImageIO
-dontwarn javax.imageio.stream.ImageInputStream
