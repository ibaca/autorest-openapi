package com.intendia.openapi;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_ABSENT;
import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY;
import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.base.Strings.isNullOrEmpty;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;
import javax.annotation.Nullable;

public interface OpenApi {

    @JsonInclude(NON_ABSENT) class Doc {
        /**
         * <b>Required.</b> Specifies the Swagger Specification version being used. It can be used by the Swagger UI
         * and other clients to interpret the API listing. The value MUST be "2.0".
         */
        public String swagger;

        /** <b>Required.</b> Provides metadata about the API. The metadata can be used by the clients if needed. */
        public Info info;

        /**
         * The host (name or ip) serving the API. This MUST be the host only and does not include the scheme nor
         * sub-paths. It MAY include a port. If the host is not included, the host serving the documentation is to be
         * used (including the port). The host does not support path templating.
         */
        public String host;

        /**
         * The base path on which the API is served, which is relative to the host. If it is not included, the API is
         * served directly under the host. The value MUST start with a leading slash (/). The basePath does not support
         * path templating.
         */
        public String basePath;

        /**
         * The transfer protocol of the API. Values MUST be from the list: "http", "https", "ws", "wss". If the schemes
         * is not included, the default scheme to be used is the one used to access the Swagger definition itself.
         */
        public String[] schemes;

        /**
         * A list of MIME types the APIs can consume. This is global to all APIs but can be overridden on specific API
         * calls. Value MUST be as described under Mime Types.
         */
        public String[] consumes;

        /**
         * A list of MIME types the APIs can produce. This is global to all APIs but can be overridden on specific API
         * calls. Value MUST be as described under Mime Types.
         */
        public String[] produces;

        /** <b>Required.</b> The available paths and operations for the API. */
        public Map<String, PathItem> paths;

        /** An object to hold data types produced and consumed by operations. */
        public @Nullable Map<String, Schema> definitions;

        /**
         * An object to hold parameters that can be used across operations. This property does not define global
         * parameters for all operations.
         */
        public @Nullable Map<String, Parameter> parameters;

        /**
         * An object to hold responses that can be used across operations. This property does not define global
         * responses for all operations.
         */
        public @Nullable Map<String, Response> responses;

        /** Security scheme definitions that can be used across the specification. */
        public @Nullable Map<String, SecurityDefinition> securityDefinitions;

        /**
         * A declaration of which security schemes are applied for the API as a whole. The list of values describes
         * security schemes that can be used
         * This will be an array of maps. The array members will be OR'd and the map entries will be AND'ed
         * requirements). Individual operations can override this definition.
         */
        public @Nullable Map<String, String[]>[] security;
        /**
         * A list of tags used by the specification with additional metadata. The order of the tags can be used to
         * reflect on their order by the parsing tools. Not all tags that are used by the Operation Object must be
         * declared. The tags that are not declared may be organized randomly or based on the tools' logic. Each tag
         * name in the list MUST be unique.
         */
        public Tag[] tags;

        /** Additional external documentation. */
        public ExternalDocumentation externalDocs;
    }

    @JsonInclude(NON_ABSENT) class Info {
        /** Required. The title of the application. */
        public String title;

        /** A short description of the application. GFM syntax can be used for rich text representation. */
        public String description;

        /** The Terms of Service for the API. */
        public String termsOfService;

        /** The contact information for the exposed API. */
        public Contact contact;

        /** The license information for the exposed API. */
        public License license;

        /** Required Provides the version of the application API (not to be confused with the specification version). */
        public String version;
    }

    @JsonInclude(NON_ABSENT) class Contact {
        /** The identifying name of the contact person/organization. */
        public String name;

        /** The URL pointing to the contact information. MUST be in the format of a URL. */
        public String url;

        /** The email address of the contact person/organization. MUST be in the format of an email address. */
        public String email;
    }

    @JsonInclude(NON_ABSENT) class License {
        /** Required. The license name used for the API. */
        public String name;

        /** A URL to the license used for the API. MUST be in the format of a URL. */
        public String url;
    }

    @JsonInclude(NON_ABSENT) class PathItem {
        /**
         * Allows for an external definition of this path item. The referenced structure MUST be in the format of a
         * Path
         * Item Object. If there are conflicts between the referenced definition and this Path Item's definition, the
         * behavior is undefined.
         */
        public String $ref;

        /** A definition of a GET operation on this path. */
        public Operation get;

        /** A definition of a PUT operation on this path. */
        public Operation put;

        /** A definition of a POST operation on this path. */
        public Operation post;

        /** A definition of a DELETE operation on this path. */
        public Operation delete;

        /** A definition of a OPTIONS operation on this path. */
        public Operation options;

        /** A definition of a HEAD operation on this path. */
        public Operation head;

        /** A definition of a PATCH operation on this path. */
        public Operation patch;

        public @JsonIgnore Map<String, Operation> operations() {
            Map<String, OpenApi.Operation> operations = new HashMap<>();
            if (get != null) operations.put("GET", get);
            if (put != null) operations.put("PUT", put);
            if (post != null) operations.put("POST", post);
            if (delete != null) operations.put("DELETE", delete);
            if (options != null) operations.put("OPTIONS", options);
            if (head != null) operations.put("HEAD", head);
            if (patch != null) operations.put("PATCH", patch);
            return operations;
        }

        /**
         * A list of parameters that are applicable for all the operations described under this path. These parameters
         * can be overridden at the operation level, but cannot be removed there. The list MUST NOT include duplicated
         * parameters. A unique parameter is defined by a combination of a name and location. The list can use the
         * Reference Object to link to parameters that are defined at the Swagger Object's parameters. There can be one
         * "body" parameter at most.
         */
        public Parameter[] parameters;
    }

    @JsonInclude(NON_ABSENT) class Operation {
        /**
         * A list of tags for API documentation control. Tags can be used for logical grouping of operations by
         * resources or any other qualifier.
         */
        public String[] tags;

        /**
         * A short summary of what the operation does. For maximum readability in the swagger-ui, this field SHOULD be
         * less than 120 characters.
         */
        public String summary;

        /** A verbose explanation of the operation behavior. GFM syntax can be used for rich text representation. */
        public String description;

        /** Additional external documentation for this operation. */
        public ExternalDocumentation externalDocs;

        /**
         * Unique string used to identify the operation. The id MUST be unique among all operations described in the
         * API. Tools and libraries MAY use the operationId to uniquely identify an operation, therefore, it is
         * recommended to follow common programming naming conventions.
         */
        public String operationId;

        /**
         * A list of MIME types the operation can consume. This overrides the consumes definition at the Swagger
         * Object.
         * An empty value MAY be used to clear the global definition. Value MUST be as described under Mime Types.
         */
        public String[] consumes;

        /**
         * A list of MIME types the operation can produce. This overrides the produces definition at the Swagger
         * Object.
         * An empty value MAY be used to clear the global definition. Value MUST be as described under Mime Types.
         */
        public String[] produces;

        /**
         * A list of parameters that are applicable for this operation. If a parameter is already defined at the Path
         * Item, the new definition will override it, but can never remove it. The list MUST NOT include duplicated
         * parameters. A unique parameter is defined by a combination of a name and location. The list can use the
         * Reference Object to link to parameters that are defined at the Swagger Object's parameters. There can be one
         * "body" parameter at most.
         */
        public Parameter[] parameters;

        public @JsonIgnore Stream<Parameter> parameters(Function<String, Parameter> resolver) {
            return Stream.of(firstNonNull(parameters, new Parameter[0]))
                    .map(p -> !isNullOrEmpty(p.$ref) ? resolver.apply(p.$ref.replace("#/parameters/", "")) : p);
        }

        /**
         * Required. The list of possible responses as they are returned from executing this operation.
         */
        public Map<String, Response> responses;

        /**
         * The transfer protocol for the operation. Values MUST be from the list: "http", "https", "ws", "wss". The
         * value overrides the Swagger Object schemes definition.
         */
        public String[] schemes;

        /**
         * Declares this operation to be deprecated. Usage of the declared operation should be refrained. Default value
         * is false.
         */
        public boolean deprecated;

        /**
         * A declaration of which security schemes are applied for the API as a whole. The list of values describes
         * security schemes that can be used
         * This will be an array of maps. The array members will be OR'd and the map entries will be AND'ed
         * requirements). Individual operations can override this definition.
         */
        public @Nullable Map<String, String[]>[] security;

        @Override public String toString() {
            return MoreObjects.toStringHelper(this)
                    .omitNullValues()
                    .add("tags", tags)
                    .add("summary", summary)
                    .add("externalDocs", externalDocs)
                    .add("operationId", operationId)
                    .add("consumes", consumes)
                    .add("produces", produces)
                    .add("parameters", parameters)
                    .add("responses", responses)
                    .add("schemes", schemes)
                    .add("deprecated", !deprecated ? null : deprecated)
                    .add("security", security)
                    .toString();
        }
    }

    /** Parameter or reference. */
    @JsonInclude(NON_ABSENT) class Parameter {
        public String $ref;
        public String name;
        /** 'query', 'path', 'header', 'body' */
        public String in;
        public boolean required;

        /** Required if in = 'body', null otherwise. */
        public Schema schema;

        /**
         * <li>'boolean' (do not require format)
         * <li>'integer' (format 'int32')
         * <li>'string' (do not require format, 'date-time')
         * <li>'array' (define type in items)
         */
        public String type;

        /** 'int32', 'date-time' */
        public String format;
        @JsonProperty("enum") public String[] enumValues;

        public String description;

        @JsonProperty("default") public String defaultValue;

        public Schema items;

        /**
         * Determines the format of the array if type array is used. Possible values are:
         * <ul>
         * <li>csv - comma separated values foo,bar.
         * <li>ssv - space separated values foo bar.
         * <li>tsv - tab separated values foo\tbar.
         * <li>pipes - pipe separated values foo|bar.
         * <li>multi - corresponds to multiple parameter instances instead of multiple values for a single instance
         * foo=bar&foo=baz. This is valid only for parameters in "query" or "formData".
         * </ul>
         * Default value is csv.
         */
        public String collectionFormat;

        @Override public String toString() {
            return MoreObjects.toStringHelper(this)
                    .omitNullValues()
                    .add("$ref", $ref)
                    .add("name", name)
                    .add("in", in)
                    .add("required", required)
                    .add("schema", schema)
                    .add("type", type)
                    .add("format", format)
                    .add("enumValues", enumValues)
                    .add("defaultValue", defaultValue)
                    .add("items", items)
                    .add("collectionFormat", collectionFormat)
                    .toString();
        }
    }

    /**
     * Any HTTP status code can be used as the property name (one property per HTTP status code). Describes the
     * expected
     * response for that HTTP status code. Reference Object can be used to link to a response that is defined at the
     * Swagger Object's responses section.
     */
    @JsonInclude(NON_ABSENT) class Response {
        /** Required. A short description of the response. GFM syntax can be used for rich text representation. */
        public String description;

        /**
         * A definition of the response structure. It can be a primitive, an array or an object. If this field does not
         * exist, it means no content is returned as part of the response. As an extension to the Schema Object, its
         * root type value may also be "file". This SHOULD be accompanied by a relevant produces mime-type.
         */
        public Schema schema;

        /** A list of headers that are sent with the response. */
        public Map<String, Header> headers;

        /** An example of the response message. */
        public Map<String, Object> examples;

        @Override public String toString() {
            return MoreObjects.toStringHelper(this)
                    .omitNullValues()
                    .add("schema", schema)
                    .add("headers", headers)
                    .add("examples", examples)
                    .toString();
        }
    }

    @JsonInclude(NON_ABSENT) class Schema {
        public String $ref;
        public String type;
        public String format;
        @JsonInclude(NON_EMPTY) public String description;
        @JsonProperty("enum") public String[] enumValues;
        public String[] required;
        /** Required if type = 'array', null otherwise. */
        public @Nullable Schema items;
        public @Nullable Map<String, Schema> properties;
        @Override public String toString() {
            return MoreObjects.toStringHelper(this)
                    .omitNullValues()
                    .add("$ref", $ref)
                    .add("type", type)
                    .add("format", format)
                    .add("enumValues", enumValues)
                    .add("required", required)
                    .add("items", items)
                    .add("properties", properties)
                    .toString();
        }
    }

    @JsonInclude(NON_ABSENT) class Header {}

    //TODO: Add OAuth2 fields
    @JsonInclude(NON_ABSENT) class SecurityDefinition {
    	@JsonInclude(NON_EMPTY) public String type;
    	public String in;
    	public String name;
        @Override public String toString() {
            return MoreObjects.toStringHelper(this)
                    .omitNullValues()
                    .add("type", type)
                    .add("in", in)
                    .add("name", name	)
                    .toString();
        }
    }

    @JsonInclude(NON_ABSENT) class Tag {
        /** Required. The name of the tag. */
        public String name;

        /** A short description for the tag. GFM syntax can be used for rich text representation. */
        public String description;

        /** Additional external documentation for this tag. */
        public ExternalDocumentation externalDocs;
    }

    @JsonInclude(NON_ABSENT) class ExternalDocumentation {}
}
