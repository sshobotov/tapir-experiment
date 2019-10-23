## How to

Start application with

```
sbt run
```

it will run the server with default setting on port 8080 (see logs output).

To play around with implemented API in Swagger UI point your browser to `localhost:8080/docs`,
some endpoints may require authorization so credentials could be found in `Config.scala`,
provide needed credentials in Swagger UI by clicking `Authorize` button. All endpoints has provided 
data example and schema in Swagger UI.

If needed default settings could be overridden by setting environment variables, for example
to start server on non-default port execute

```
env SERVER_PORT=9000 sbt run
```

To run the tests use

```
sbt test
```

## Details of implementation

API layer build with [tapir](https://github.com/softwaremill/tapir) library that allows you to abstract
away from concrete server implementations (`Akka` or `http4s`) and define your protocol first. Based on
the protocol server and always up-to-date documentation could be build, isn't it our dream?

Business logic layer build using `cats-effect` library just because I tired of passing `ExecutionContext`
over and over.

## Known issues

Library itself is great but pretty immature that is why this application suffers from some problems:

- expected exceptions used from application logic (authorization credentials check failure, no content for particular user)
can't be translated into appropriate status code, so by default this exceptions lead to `500 Internal server error`
- validation error messages for JSON body isn't informative and some of them can't tell what field is absent or invalid