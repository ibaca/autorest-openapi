package com.intendia.openapi;

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.base.Strings.emptyToNull;
import static com.google.common.base.Strings.nullToEmpty;
import static java.lang.System.out;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toMap;

import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import javax.lang.model.element.Modifier;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import rx.Observable;

public class Main {
    private static final Logger log = Logger.getLogger(Main.class.getName());

    public static final ApisGuru APIS_GURU = new ApisGuru_RestServiceModel(
            () -> new JreResourceBuilder().path("https://api.apis.guru/"));

    public static void main(String[] args) throws Exception {
        String api = "thetvdb.com";
        APIS_GURU.spec(api, "2.1.1").doOnNext(doc -> {
            try {
                ClassName jaxRsTypeName = ClassName.get(api.replace(".", "_"), "Api");
                TypeSpec jaxRsTypeSpec = openApi2JaxRs(jaxRsTypeName, doc);
                JavaFile jaxRsFile = JavaFile.builder(jaxRsTypeName.packageName(), jaxRsTypeSpec).build();
                jaxRsFile.writeTo(Paths.get("target"));
            } catch (IOException e) {
                throw Throwables.propagate(e);
            }
        }).subscribe();
    }

    static boolean isObject(OpenApi.Schema schema) { return schema != null && "object".equals(schema.type); }

    static class TypeResolver {
        final Map<String, Def> types = new TreeMap<>();

        void put(String ref, ClassName className, OpenApi.Schema schema) {
            types.put(ref, new Def(className, schema));
        }

        TypeName getTypeName(OpenApi.Parameter p) {
            return getTypeName(p.type, p.$ref);
        }

        TypeName getTypeName(String type, @Nullable String $ref) {
            TypeName pType = TypeName.OBJECT;
            if (types.containsKey(nullToEmpty($ref))) {
                pType = types.get($ref).name;
            } else switch (nullToEmpty(type)) {
                case "string": pType = TypeName.get(String.class); break;
                case "integer": pType = TypeName.get(Double.class); break;
            }
            return pType;
        }

        class Def {
            final ClassName name;
            final OpenApi.Schema schema;
            final TypeSpec type;
            Def(ClassName name, OpenApi.Schema schema) {
                this.name = name;
                this.schema = schema;
                TypeSpec.Builder builder = TypeSpec.classBuilder(name).addModifiers(Modifier.PUBLIC, Modifier.STATIC);
                builder.addJavadoc("$L\n\n<pre>$L</pre>\n", firstNonNull(emptyToNull(schema.description), name),
                        schema);
                schema.properties.entrySet().forEach(e -> {
                    String paramName = e.getKey();
                    OpenApi.Schema paramSchema = e.getValue();
                    String description = firstNonNull(emptyToNull(paramSchema.description), paramName);
                    TypeName paramType = getTypeName(paramSchema.type, paramSchema.$ref);
                    builder.addField(FieldSpec.builder(paramType, paramName, Modifier.PUBLIC)
                            .addJavadoc("$L\n\n<pre>$L</pre>\n", description, paramSchema)
                            .build());
                });
                this.type = builder.build();
            }
        }
    }

    private static TypeSpec openApi2JaxRs(ClassName api, OpenApi.Doc doc) throws IOException {
        log.info(doc.info.title);

        Map<String, OpenApi.Tag> tags = Stream.of(doc.tags).collect(toMap(t -> t.name, identity()));
        Map<String, OpenApi.Parameter> parameters = doc.parameters;
        TypeResolver resolver = new TypeResolver();
        doc.definitions.entrySet()
                .forEach(e -> resolver.put("#/definitions/" + e.getKey(), api.nestedClass(e.getKey()), e.getValue()));
        checkUnsupportedSchemaUsage(doc);

        doc.paths.entrySet().forEach(path -> path.getValue().operations().entrySet().forEach(oe -> {
            OpenApi.Operation o = oe.getValue();
            System.out.println(oe.getKey() + " " + path.getKey() + " " + o);
        }));
        return TypeSpec.interfaceBuilder(api)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(annotation(SuppressWarnings.class, "unused"))
                .addAnnotation(annotation(Path.class, doc.basePath))
                .addTypes(() -> resolver.types.values().stream().map(i -> i.type).iterator())
                .addMethods(() -> doc.paths.entrySet().stream()
                        .flatMap(pathEntry -> pathEntry.getValue().operations().entrySet().stream().map(operation -> {
                            String path = pathEntry.getKey();
                            String method = operation.getKey();
                            String oName = Stream.of((method.toLowerCase() + "/" + path).split("/"))
                                    .filter(s -> !(Strings.isNullOrEmpty(s) || s.startsWith("{")))
                                    .collect(joining("_"));
                            return MethodSpec.methodBuilder(oName)
                                    .addJavadoc("$L\n\n<pre>$L</pre>\n", operation.getValue().description,
                                            operation.getValue().toString())
                                    .addAnnotation(annotation(Path.class, path))
                                    .addAnnotation(ClassName.get("javax.ws.rs", method))
                                    .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                                    .addParameters(() -> operation.getValue().parameters(parameters::get).map(p -> {
                                        TypeName pType = resolver.getTypeName(p);
                                        String pName = p.name.replace("-", "").replace(" ", "_");
                                        ParameterSpec.Builder out = ParameterSpec.builder(pType, pName);
                                        AnnotationSpec annotation = null;
                                        switch (nullToEmpty(p.in)) {
                                            case "query": annotation = annotation(QueryParam.class, p.name); break;
                                            case "path": annotation = annotation(PathParam.class, p.name); break;
                                            case "header": annotation = annotation(HeaderParam.class, p.name); break;
                                            case "body": break;
                                            default: log.warning("unsupported 'in' value for " + p);
                                        }
                                        if (annotation != null) out.addAnnotation(annotation);
                                        if (!p.required) out.addAnnotation(Nullable.class);
                                        return out.build();
                                    }).iterator())
                                    .returns(operation.getValue().responses
                                            .entrySet().stream().filter(e -> e.getKey().equals("200")).findAny()
                                            .map(e -> {
                                                OpenApi.Response response = e.getValue();
                                                TypeName rType = TypeName.OBJECT;
                                                if (response.schema != null && resolver.types
                                                        .containsKey(nullToEmpty(response.schema.$ref))) {
                                                    rType = resolver.types.get(response.schema.$ref).name;
                                                }
                                                return ParameterizedTypeName
                                                        .get(ClassName.get(Observable.class), rType);
                                            })
                                            .orElseGet(() -> ParameterizedTypeName.get(Observable.class, Void.class)))
                                    .build();
                        })).iterator())
                .build();
    }

    private static void checkUnsupportedSchemaUsage(OpenApi.Doc doc) {
        doc.paths.entrySet().stream().flatMap(path -> {
            String PATH = "#/paths/" + trimSlash(path.getKey());
            return Stream.concat(
                    Stream.of(firstNonNull(path.getValue().parameters, new OpenApi.Parameter[0]))
                            .filter(p -> isObject(p.schema)).map(p -> PATH + "/parameters/" + p.name),
                    path.getValue().operations().entrySet().stream().flatMap(operation -> {
                        String OPERATION = PATH + "/operations/" + operation.getKey();
                        return Stream.concat(
                                Stream.of(firstNonNull(operation.getValue().parameters, new OpenApi.Parameter[0]))
                                        .filter(i -> isObject(i.schema))
                                        .map(i -> OPERATION + "/parameters/" + i.name),
                                operation.getValue().responses.entrySet().stream()
                                        .filter(i -> isObject(i.getValue().schema))
                                        .map(i -> OPERATION + "/responses/" + i.getKey()));
                    }));
        }).forEach(ref -> log.warning("Unsupported type at " + ref + " (Types should be declared in "
                + "#/definitions/{ref}, so the 'ref' is used as type name. Creating anonymous, random named and "
                + "duplicated types look like a waste of time, so please normalize your schema using definitions!)"));
    }

    private static AnnotationSpec annotation(Class<?> type, String name) {
        return AnnotationSpec.builder(type).addMember("value", "$S", name).build();
    }

    private static String trimSlash(String path) {
        if (path.startsWith("/")) path = path.substring(1, path.length());
        if (path.endsWith("/")) path = path.substring(0, path.length() - 1);
        return path;
    }

    private static void fetchAllSpecs() {
        APIS_GURU.list().flatMap(api -> Observable.from(api.entrySet()))
                .doOnNext(e -> {
                    out.println(e.getKey() + ": " + e.getValue().versions.get(e.getValue().preferred).swaggerUrl);
                    e.getValue().versions.entrySet().forEach(version -> out.println(" - " + version.getKey() + ": "
                            + version.getValue().swaggerUrl + " (" + version.getValue().added + ")"));
                })
                .flatMap(e -> APIS_GURU.spec(e.getKey().replace(":", "/"), e.getValue().preferred))
                .doOnNext(doc -> log.info(doc.info.title))
                .subscribe();
    }
}
