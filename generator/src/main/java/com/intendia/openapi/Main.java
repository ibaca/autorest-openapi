package com.intendia.openapi;

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.base.Strings.emptyToNull;
import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.base.Strings.nullToEmpty;
import static java.util.Collections.emptyMap;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.joining;

import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import com.google.gson.Gson;
import com.intendia.gwt.autorest.client.AutoRestGwt;
import com.intendia.gwt.autorest.client.IgnoreRest;
import com.intendia.gwt.autorest.client.Security;
import com.intendia.gwt.autorest.client.SecurityDefinition;
import com.intendia.gwt.autorest.client.SecurityDefinition.Location;
import com.intendia.gwt.autorest.client.SecurityDefinition.SecurityType;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ArrayTypeName;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import javax.lang.model.element.Modifier;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import jsinterop.annotations.JsPackage;
import jsinterop.annotations.JsType;
import io.reactivex.Observable;
import io.reactivex.Single;

public class Main {
    private static final Logger log = Logger.getLogger(Main.class.getName());

    public static final ApisGuru APIS_GURU = new ApisGuru_RestServiceModel(
            () -> new JreResourceBuilder().path("https://api.apis.guru/"));

    public static void main(String[] args) throws Exception {
        Observable<SpecData> spec$ = null;
        if (args.length != 1) { help(); return; }
        if (args[0].equalsIgnoreCase("all")) spec$ = fetchAllSpecs(APIS_GURU);
        if (args[0].contains("@")) spec$ = fetchSpec(APIS_GURU, SpecData.valueOf(args[0]));
        if (args[0].contains(":")) spec$ = loadSpec(args[0]);
        if (spec$ == null) { help(); return; }

        spec$.subscribe(Main::generate);
    }

    private static void help() {
        // eg 'thetvdb.com@2.1.1', or '~/Code/petstore.json'
        System.out.println("gen [all|<api>@<version>|<uri>]");
        System.out.println("all - fetch and generates all available APIs in https://api.apis.guru/");
        System.out.println("<api>@<version> - fetch and generate the specified api/version");
        System.out.println("    All available APIs here: https://api.apis.guru/v2/list.json");
        System.out.println("<uri> - generate code for the specified openapi json, uri should start with '<scheme>:'");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("gen file:///Users/ibaca/Code/petstore.json");
        System.out.println("gen http://petstore.swagger.io/v2/swagger.json");
    }

    private static void generate(SpecData spec) {
        try {
            ClassName jaxRsTypeName = ClassName.get(spec.name.replace(".", "_"), "Api");
            TypeSpec jaxRsTypeSpec = openApi2JaxRs(jaxRsTypeName, spec.doc);
            JavaFile jaxRsFile = JavaFile.builder(jaxRsTypeName.packageName(), jaxRsTypeSpec).build();
            jaxRsFile.writeTo(Paths.get("target"));
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

    public static class SpecData {
        public String name;
        public String version;
        public OpenApi.Doc doc;
        public SpecData(String name, String version) { this.name = name; this.version = version; }
        public SpecData doc(OpenApi.Doc doc) { this.doc = doc; return this; }
        public static SpecData valueOf(String apiVersion) {
            String[] split = apiVersion.split("@");
            return new SpecData(split[0], split[1]);
        }
    }

    public static Observable<SpecData> fetchAllSpecs(ApisGuru api) {
        return api.list().flatMapIterable(HashMap::entrySet)
                .map(entry -> new SpecData(entry.getKey(), entry.getValue().preferred))
                .flatMap(spec -> fetchSpec(api, spec));
    }

    private static Observable<SpecData> fetchSpec(ApisGuru api, SpecData spec) {
        return api.spec(spec.name.replace(":", "/"), spec.version).map(spec::doc);
    }

    private static Observable<SpecData> loadSpec(String uri) {
        try (JsonConnection ctx = new JsonConnection((HttpURLConnection) new URI(uri).toURL().openConnection());
                InputStream inputStream = ctx.connection.getInputStream()) {
            SpecData spec = new SpecData("api", "0");
            spec.doc = new Gson().fromJson(new InputStreamReader(inputStream), OpenApi.Doc.class);
            return Observable.just(spec);
        } catch (Exception e) {
            return Observable.error(e);
        }
    }

    static class JsonConnection implements AutoCloseable {
        final HttpURLConnection connection;
        JsonConnection(HttpURLConnection connection) {
            this.connection = connection;
            connection.setRequestProperty("Accept", "application/json");
        }
        @Override public void close() { connection.disconnect(); }
    }

    static boolean isObject(OpenApi.Schema schema) { return schema != null && "object".equals(schema.type); }

    static class TypeResolver {
        enum Collection {
            ARRAY {
                @Override TypeName wrap(TypeName t) { return ArrayTypeName.of(t); }
            },
            LIST {
                @Override TypeName wrap(TypeName t) { return ParameterizedTypeName.get(ClassName.get(List.class), t.box()); }
            };
            abstract TypeName wrap(TypeName t);
        }

        final Map<String, Def> types = new TreeMap<>();

        void put(String ref, ClassName className, OpenApi.Schema schema) {
            types.put(ref, new Def(className, schema));
        }

        TypeName type(OpenApi.Parameter p) {
            if (p.schema != null) return type(p.schema, Collection.LIST);
            else {
                final OpenApi.Schema schema = new OpenApi.Schema();
                schema.$ref = p.$ref;
                schema.type = p.type;
                schema.format = p.format;
                schema.description = p.description;
                schema.enumValues = p.enumValues;
                schema.items = p.items;
                return type(schema, Collection.LIST);
            }
        }

        TypeName type(@Nullable OpenApi.Schema schema) {return type(schema, Collection.ARRAY);}
        TypeName type(@Nullable OpenApi.Schema schema, final Collection arrayType) {
            TypeName pType = TypeName.OBJECT;
            if (schema == null) return pType;
            if (!isNullOrEmpty(schema.$ref)) {
                pType = ofNullable(types.get(schema.$ref)).map(d -> d.name).orElse(ClassName.OBJECT);
            } else switch (nullToEmpty(schema.type)) {
                case "": pType = TypeName.VOID.box(); break;
                case "string": pType = TypeName.get(String.class); break;
                case "integer": pType = TypeName.get(Number.class); break;
                case "number": pType = TypeName.get(Number.class); break;
                case "array": pType = arrayType.wrap(type(schema.items)); break;
            }
            return pType;
        }

        class Def {
            final ClassName name;
            final OpenApi.Schema schema;
            Def(ClassName name, OpenApi.Schema schema) {
                this.name = name;
                this.schema = schema;
            }
            TypeSpec type() {
                TypeSpec.Builder out = TypeSpec.classBuilder(name).addModifiers(Modifier.PUBLIC, Modifier.STATIC);
                out.addAnnotation(AnnotationSpec.builder(JsType.class)
                        .addMember("isNative","$L", "true")
                        .addMember("namespace","$T.$L", JsPackage.class, "GLOBAL")
                        .addMember("name","$S", "Object")
                        .build());
                out.addJavadoc("$L\n\n<pre>$L</pre>\n", firstNonNull(emptyToNull(schema.description), name), schema);
                schema.properties.entrySet().forEach(e -> {
                    String paramName = e.getKey();
                    OpenApi.Schema paramSchema = e.getValue();
                    String description = firstNonNull(emptyToNull(paramSchema.description), paramName);
                    TypeName paramType = TypeResolver.this.type(paramSchema);
                    out.addField(FieldSpec.builder(paramType, paramName, Modifier.PUBLIC)
                            .addJavadoc("$L\n\n<pre>$L</pre>\n", description, paramSchema)
                            .build());
                });
                return out.build();
            }
        }
    }

    private static TypeSpec openApi2JaxRs(ClassName api, OpenApi.Doc doc) throws IOException {
        log.info(doc.info.title);

        //Map<String, OpenApi.Tag> tags = Stream.of(doc.tags).collect(toMap(t -> t.name, identity()));
        Map<String, OpenApi.Parameter> parameters = firstNonNull(doc.parameters, emptyMap());
        TypeResolver resolver = new TypeResolver();
        if (doc.definitions != null) doc.definitions.entrySet()
                .forEach(e -> resolver.put("#/definitions/" + e.getKey(), api.nestedClass(e.getKey()), e.getValue()));
        checkUnsupportedSchemaUsage(doc);

        doc.paths.entrySet().forEach(path -> path.getValue().operations().entrySet().forEach(oe -> {
            OpenApi.Operation o = oe.getValue();
            System.out.println(oe.getKey() + " " + path.getKey() + " " + o);
        }));

        Map<String, String[]> globalSecurity = (doc.security != null && doc.security.length > 0) ? doc.security[0] : null;
        TypeSpec.Builder retSpec = TypeSpec.interfaceBuilder(api)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(annotation(SuppressWarnings.class, "unused"))
                .addAnnotation(annotation(Path.class, (doc.basePath != null) ? doc.basePath : ""));
        
        if(globalSecurity != null)
        {
        	AnnotationSpec.Builder aSpec = AnnotationSpec.builder(Security.class);
        	
        	globalSecurity.entrySet().stream()
    			.forEach(entry -> {
	    				OpenApi.SecurityDefinition sd = doc.securityDefinitions.get(entry.getKey());
	    				if(sd == null)
	    					return;
						SecurityType type = SecurityType.fromString(sd.type);
						Location loc = Location.fromString(sd.in);
						aSpec.addMember("value",  "$L", AnnotationSpec.builder(SecurityDefinition.class)
	    						.addMember("type", "$T.$L", SecurityType.class, type.name())
	    						.addMember("location", "$T.$L", Location.class, loc.name())
	    						.addMember("name", "\"$L\"", sd.name)
    						.build());
        			});
        	retSpec.addAnnotation(aSpec.build());
        }    
        	
        retSpec.addTypes(() -> resolver.types.values().stream().map(TypeResolver.Def::type).iterator())
                .addMethod(MethodSpec.methodBuilder("setSecurityToken")
                		.addAnnotation(IgnoreRest.class)
                		.addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                		.addParameter(String.class, "tokenName")
        				.addParameter(String.class, "tokenVal")
        				.returns(TypeName.VOID)
        				.build())
                .addMethods(() -> doc.paths.entrySet().stream()
                        .flatMap(pathEntry -> pathEntry.getValue().operations().entrySet().stream().map(operation -> {
                            String path = pathEntry.getKey();
                            String method = operation.getKey();
                            String oName = Stream.of((method.toLowerCase() + "/" + path).split("/"))
                                    .filter(s -> !(Strings.isNullOrEmpty(s) || s.startsWith("{")))
                                    .collect(joining("_"));
                            MethodSpec.Builder methodSpec = MethodSpec.methodBuilder(oName)
                                    .addJavadoc("$L\n\n<pre>$L</pre>\n", operation.getValue().description,
                                            operation.getValue().toString())
                                    .addAnnotation(annotation(Path.class, path))
                                    .addAnnotation(ClassName.get("javax.ws.rs", method))
                                    .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                                    .addParameters(() -> operation.getValue().parameters(parameters::get).map(p -> {
                                        String pName = p.name.replace("-", "").replace(" ", "_");
                                        ParameterSpec.Builder out = ParameterSpec.builder(resolver.type(p), pName);
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
                                                OpenApi.Schema s = response.schema;
                                                if (s == null) return observable(TypeName.VOID.box());
                                                if ("array".equals(s.type)) return observable(resolver.type(s.items));
                                                else return single(resolver.type(s));
                                            })
                                            .orElseGet(() -> observable(TypeName.VOID.box())));
                            if(operation.getValue().security != null) {
                            	if(operation.getValue().security.length == 0) {
                            		methodSpec.addAnnotation(AnnotationSpec.builder(Security.class).build());
                            	}
                            	else {
                                	AnnotationSpec.Builder aSpec = AnnotationSpec.builder(Security.class);
                            		operation.getValue().security[0].entrySet().stream()
                                			.forEach(entry -> {
                        	    				OpenApi.SecurityDefinition sd = doc.securityDefinitions.get(entry.getKey());
                        	    				if(sd == null)
                        	    					return;
                        						SecurityType type = SecurityType.fromString(sd.type);
                        						Location loc = Location.fromString(sd.in);
                        						aSpec.addMember("value",  "$L", AnnotationSpec.builder(SecurityDefinition.class)
                        	    						.addMember("type", "$T.$L", SecurityType.class, type.name())
                        	    						.addMember("location", "$T.$L", Location.class, loc.name())
                        	    						.addMember("name", "\"$L\"", sd.name)
                            						.build());
                                			});
                                	methodSpec.addAnnotation(aSpec.build());
                            	}
                            }
                            return methodSpec.build(); 
                        })).iterator());

        return retSpec.build();
    }

    private static ParameterizedTypeName observable(TypeName type) {
        return ParameterizedTypeName.get(ClassName.get(Observable.class), type);
    }

    private static ParameterizedTypeName single(TypeName type) {
        return ParameterizedTypeName.get(ClassName.get(Single.class), type);
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
}
