# Release собирается с isMinifyEnabled + isShrinkResources (app/build.gradle.kts), а проверяется
# почти всегда debug-ом: то, что ломает R8, в отладке не видно. Правила — только про то,
# что действительно находится рефлексией или по имени класса.

# Сгенерированные kotlinx.serialization-сериализаторы ссылаются на @Serializable-классы,
# их Companion и на дженерик-сигнатуры полей.
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod, RuntimeVisibleAnnotations, AnnotationDefault

# Трек уезжает в inputData задачи WorkManager и в extras очереди MediaItem через Track.serializer();
# композитный сериализатор и компаньон находятся рефлексией, если их не держать.
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1>$Companion Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.vkmusic.**$$serializer { *; }
-keepclassmembers class com.vkmusic.** {
    *** Companion;
}
-keepclasseswithmembers class com.vkmusic.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Room ищет реализацию базы по имени класса ("<Database>_Impl"), WorkManager забирает
# Mp3ExportWorker из сгенерированного Hilt-фактории — имя и конструктор должны остаться.
-keep class com.vkmusic.data.db.AppDatabase_Impl { *; }
-keep class com.vkmusic.data.export.Mp3ExportWorker extends androidx.work.ListenableWorker {
    <init>(...);
}
