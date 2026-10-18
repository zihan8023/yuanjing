# IQ Code release shrinker rules.
#
# :app is the only minified module. Every reflection / hidden-API / JNI surface in it is pinned by
# name because the runtime resolves those members dynamically: the vendored BlackBox runtime, the
# black-reflection generated bridges, FreeReflection, the Termux PTY JNI entry points and the
# binary-only Termux compatibility jar.

# Keep crash reports actionable (the sandbox debug log and stall reports quote stack traces).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# black-reflection bridges are looked up by annotation and by member name at runtime.
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,Exceptions
-keep class top.niunaijun.blackreflection.** { *; }
-keep @top.niunaijun.blackreflection.annotation.BClass class * {*;}
-keep @top.niunaijun.blackreflection.annotation.BClassName class * {*;}
-keep @top.niunaijun.blackreflection.annotation.BClassNameNotProcess class * {*;}
-keepclasseswithmembernames class * {
    @top.niunaijun.blackreflection.annotation.BField.* <methods>;
    @top.niunaijun.blackreflection.annotation.BFieldNotProcess.* <methods>;
    @top.niunaijun.blackreflection.annotation.BFieldSetNotProcess.* <methods>;
    @top.niunaijun.blackreflection.annotation.BFieldCheckNotProcess.* <methods>;
    @top.niunaijun.blackreflection.annotation.BMethod.* <methods>;
    @top.niunaijun.blackreflection.annotation.BStaticField.* <methods>;
    @top.niunaijun.blackreflection.annotation.BStaticMethod.* <methods>;
    @top.niunaijun.blackreflection.annotation.BMethodCheckNotProcess.* <methods>;
    @top.niunaijun.blackreflection.annotation.BConstructor.* <methods>;
    @top.niunaijun.blackreflection.annotation.BConstructorNotProcess.* <methods>;
}
# top.niunaijun.blackbox.** / top.niunaijun.jnihook.** / mirror.** keeps arrive via :Bcore
# consumer-rules.pro (Bcore/proguard-rules.pro only applies to Bcore's own variant build).

# The hidden-API bypass rewrites framework access before the first reflective call.
-keep class me.weishu.reflection.** { *; }

# libtermux.so exports Java_com_termux_terminal_JNI_<name>, so the class and its native methods
# must keep their original names.
-keepclasseswithmembernames class * { native <methods>; }

# Binary-only Termux compatibility jar: kept whole because its bytecode is not under review here.
-keep class com.iqge.RuntimeInstaller { *; }
-keep class com.iqge.RuntimeInstaller$Progress { *; }

# 极致压缩：放宽访问修饰符（便于内联/合并），并把未 keep 的类重打包进一个短包名。
# 只作用于没被上面规则 keep 的类（com.iqge.* / com.termux.* 等，代码里没有对这些名字的反射）。
-allowaccessmodification
-repackageclasses 'o'
