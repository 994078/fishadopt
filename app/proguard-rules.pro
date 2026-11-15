# 一键领养小肥鱼 — R8/ProGuard 规则
# 依赖默认规则即可；保留可序列化日志条目与状态枚举（如需反射）。
-keepclassmembers class * implements android.os.Parcelable { *; }
