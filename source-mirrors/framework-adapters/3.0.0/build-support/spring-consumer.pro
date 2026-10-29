# Spring loads this class by the name in its packaged auto-configuration list.
-keep class coakka.spring.CoAkkaRuntimeAutoConfiguration { public protected *; }
# Property names are read reflectively by Spring's configuration binding.
-keepclassmembers class coakka.spring.CoAkkaRuntimeProperties { public protected *; }
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,MethodParameters
