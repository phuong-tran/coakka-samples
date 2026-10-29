package coakka.quarkus;

final class CoAkkaTargets {
    private CoAkkaTargets() {
    }

    static String fromAnnotation(Class<?> beanClass) {
        Class<?> current = beanClass;
        while (current != null && current != Object.class) {
            CoAkkaHandler annotation = current.getAnnotation(CoAkkaHandler.class);
            if (annotation != null && !annotation.value().isBlank()) {
                return annotation.value();
            }
            current = current.getSuperclass();
        }
        throw new IllegalStateException(
            "CoAkkaLocalHandler " + beanClass.getName() + " must override target() or declare @CoAkkaHandler"
        );
    }
}
