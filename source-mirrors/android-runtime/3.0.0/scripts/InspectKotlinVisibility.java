import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import kotlin.Metadata;
import kotlin.metadata.Attributes;
import kotlin.metadata.KmClass;
import kotlin.metadata.KmConstructor;
import kotlin.metadata.KmFunction;
import kotlin.metadata.KmProperty;
import kotlin.metadata.KmClassifier;
import kotlin.metadata.KmType;
import kotlin.metadata.KmValueParameter;
import kotlin.metadata.Visibility;
import kotlin.metadata.jvm.JvmExtensionsKt;
import kotlin.metadata.jvm.JvmMethodSignature;
import kotlin.metadata.jvm.KotlinClassMetadata;

/** Build-only visibility projection; class initializers never execute. */
final class InspectKotlinVisibility {
    private static void emit(String owner, Visibility visibility, JvmMethodSignature signature, String contract) {
        if (signature != null) {
            System.out.println(owner + "\t" + visibility + "\t" + signature.getName() + "\t" + signature.getDescriptor() + "\t" + contract);
        }
    }

    private static String type(KmType value) {
        if (value == null) return "-";
        KmClassifier classifier = value.getClassifier();
        String name = classifier instanceof KmClassifier.Class ? ((KmClassifier.Class) classifier).getName()
            : classifier instanceof KmClassifier.TypeAlias ? ((KmClassifier.TypeAlias) classifier).getName()
            : "#" + ((KmClassifier.TypeParameter) classifier).getId();
        return name + value.getArguments().stream().map(argument ->
            ":" + argument.getVariance() + ":" + type(argument.getType())).collect(Collectors.joining(",", "<", ">"))
            + (Attributes.isNullable(value) ? "?" : "!")
            + (Attributes.isDefinitelyNonNull(value) ? "&Any" : "");
    }

    private static String parameters(List<KmValueParameter> values) {
        return values.stream().map(value -> value.getName() + ":" + type(value.getType())
            + ":vararg=" + type(value.getVarargElementType())
            + ":default=" + Attributes.getDeclaresDefaultValue(value))
            .collect(Collectors.joining(",", "(", ")"));
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("classes JAR and public type manifest required");
        try (URLClassLoader loader = new URLClassLoader(new URL[]{Path.of(args[0]).toUri().toURL()},
                InspectKotlinVisibility.class.getClassLoader())) {
            for (String owner : Files.readAllLines(Path.of(args[1]))) {
                if (owner.isBlank() || owner.startsWith("#")) continue;
                Class<?> type = Class.forName(owner, false, loader);
                Metadata annotation = type.getAnnotation(Metadata.class);
                if (annotation == null) throw new IllegalStateException("public Kotlin metadata missing: " + owner);
                KotlinClassMetadata parsed = KotlinClassMetadata.readStrict(annotation);
                if (!(parsed instanceof KotlinClassMetadata.Class)) {
                    throw new IllegalStateException("unexpected public Kotlin declaration: " + owner);
                }
                KmClass klass = ((KotlinClassMetadata.Class) parsed).getKmClass();
                for (KmFunction function : klass.getFunctions()) {
                    emit(owner, Attributes.getVisibility(function), JvmExtensionsKt.getSignature(function),
                        function.getName() + parameters(function.getValueParameters())
                        + ":receiver=" + type(function.getReceiverParameterType())
                        + ":return=" + type(function.getReturnType())
                        + ":suspend=" + Attributes.isSuspend(function));
                }
                for (KmConstructor constructor : klass.getConstructors()) {
                    emit(owner, Attributes.getVisibility(constructor), JvmExtensionsKt.getSignature(constructor),
                        parameters(constructor.getValueParameters()));
                }
                for (KmProperty property : klass.getProperties()) {
                    String contract = property.getName() + ":" + type(property.getReturnType())
                        + ":var=" + Attributes.isVar(property) + ":const=" + Attributes.isConst(property);
                    emit(owner, Attributes.getVisibility(property.getGetter()), JvmExtensionsKt.getGetterSignature(property), contract);
                    if (property.getSetter() != null) {
                        emit(owner, Attributes.getVisibility(property.getSetter()), JvmExtensionsKt.getSetterSignature(property), contract);
                    }
                }
            }
        }
    }
}
