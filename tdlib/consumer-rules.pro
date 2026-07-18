# TDLib: классы TdApi создаются нативным кодом через JNI, R8 не видит ссылок на них
-keep class org.drinkless.tdlib.** { *; }
-keepclassmembers class org.drinkless.tdlib.** {
    <init>(...);
    <fields>;
}
-dontwarn org.drinkless.tdlib.**
