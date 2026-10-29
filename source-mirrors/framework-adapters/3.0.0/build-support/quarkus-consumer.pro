# CDI discovers these framework entrypoints and their injected configuration.
-keep class coakka.quarkus.CoAkkaRuntime { public protected *; }
-keepclassmembers class coakka.quarkus.CoAkkaRuntime {
    @jakarta.inject.Inject <fields>;
    @org.eclipse.microprofile.config.inject.ConfigProperty <fields>;
    # CDI invokes these package-private lifecycle methods reflectively.
    @jakarta.annotation.PostConstruct <methods>;
    @jakarta.annotation.PreDestroy <methods>;
}
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,MethodParameters
