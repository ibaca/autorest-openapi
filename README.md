# AutoREST OpenAPI Generator [![jitpack artifacts](https://jitpack.io/v/ibaca/autorest-openapi.svg)](https://jitpack.io/#ibaca/autorest-openapi)

[JAX-RS][jaxrs] REST service interface generator for [OpenAPI specification][openapi] (a.k.a. Swagger). This services
can be used with [AutoREST][autorest] to create JRE, Android or GWT clients.

**Modules**
* [APIs Guru][apisguru-web] public [API][apisguru-api], this gives access to 
  an bunch of OpenAPI specs
* OpenAPI API, to parse and process specs
* OpenAPI to JAX-RS generator tool

The project is under development, currently can be tested executing the [Main][main] class directly. 


[autorest]: https://github.com/intendia-oss/autorest
[jaxrs]: https://jax-rs-spec.java.net/
[openapi]: https://openapis.org/
[apisguru-web]: https://apis.guru/
[apisguru-api]: https://github.com/ibaca/autorest-openapi/tree/master/api/src/main/java/com/intendia/openapi/ApisGuru.java
[main]: https://github.com/ibaca/autorest-openapi/tree/master/generator/src/main/java/com/intendia/openapi/Main.java
