package com.intendia.openapi;

import com.intendia.gwt.autorest.client.AutoRestGwt;
import io.reactivex.Observable;
import java.util.HashMap;
import java.util.Map;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import jsinterop.annotations.JsPackage;
import jsinterop.annotations.JsType;

@AutoRestGwt @Path("v2")
@Produces(MediaType.APPLICATION_JSON) @Consumes(MediaType.APPLICATION_JSON)
public interface ApisGuru {

    @Path("list.json")
    @GET Observable<ApiMap> list();

    @Path("specs/{api}/{version}/swagger.json")
    @GET Observable<OpenApi.Doc> spec(@PathParam("api") String api, @PathParam("version") String version);

    class ApiMap extends HashMap<String, Api> {}

    @JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "Object") //
    class Api {
        public String added; //ex: "2015-02-22T20:00:45.000Z",
        public String preferred; //ex: "v3",
        public Map<String, ApiVersion> versions;
    }

    @JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "Object") //
    class ApiVersion {
        public String added; // string <date-time> Required: Timestamp when the version was added
        public Info info; // object Required Copy of info section from Swagger spec
        public String swaggerUrl; // string <url> Required URL to Swagger spec in JSON format
        public String swaggerYamlUrl; // string <url> Required URL to Swagger spec in YAML format
        public String updated; // string <date-time> Required Timestamp when the version was updated
    }

    @JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "Object") //
    class Info {
    }
}

