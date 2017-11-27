package org.typeunsafe;

import io.vertx.rxjava.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.rxjava.core.http.HttpServer;
import io.vertx.core.json.JsonObject;

import io.vertx.rxjava.ext.web.Router;
import io.vertx.rxjava.ext.web.handler.StaticHandler;
import io.vertx.rxjava.ext.web.handler.BodyHandler;

import io.vertx.rxjava.servicediscovery.types.HttpEndpoint;
import io.vertx.rxjava.servicediscovery.ServiceDiscovery;

import io.vertx.servicediscovery.ServiceDiscoveryOptions;
import io.vertx.servicediscovery.Record;

import java.util.Optional;
//import java.util.function.*;
//import java.util.List;

public class Hey extends AbstractVerticle {
  
  private ServiceDiscovery discovery;
  private Record record;

  private void setDiscovery() {
    ServiceDiscoveryOptions serviceDiscoveryOptions = new ServiceDiscoveryOptions();

    // how to access to the backend
    Integer httpBackendPort = Integer.parseInt(Optional.ofNullable(System.getenv("HTTPBACKEND_PORT")).orElse("8080"));
    String httpBackendHost = Optional.ofNullable(System.getenv("HTTPBACKEND_HOST")).orElse("127.0.0.1");
    
    // Mount the service discovery backend (my http backend)
    discovery = ServiceDiscovery.create(
      vertx,
      serviceDiscoveryOptions.setBackendConfiguration(
        new JsonObject()
          .put("host", httpBackendHost)
          .put("port", httpBackendPort)
          .put("registerUri", "/register")
          .put("removeUri", "/remove")
          .put("updateUri", "/update")
          .put("recordsUri", "/records")
      ));
  }

  private void setRecord() {

    // Settings to record the service
    String serviceName = Optional.ofNullable(System.getenv("SERVICE_NAME")).orElse("hey");
    String serviceHost = Optional.ofNullable(System.getenv("SERVICE_HOST")).orElse("localhost"); // domain name
    Integer servicePort = Integer.parseInt(Optional.ofNullable(System.getenv("SERVICE_PORT")).orElse("9091")); 
    String serviceRoot = Optional.ofNullable(System.getenv("SERVICE_ROOT")).orElse("/api");

    // create the microservice record
    record = HttpEndpoint.createRecord(
      serviceName,
      serviceHost,
      servicePort,
      serviceRoot
    );
    // add some meta data
    record.setMetadata(new JsonObject()
      .put("kind", "http")
      .put("message", "Hello 🌍")
      .put("uri", "/ping")
    );

  }

  public void stop(Future<Void> stopFuture) {
    System.out.println("Unregistration process is started ("+record.getRegistration()+")...");

    discovery
      .rxUnpublish(record.getRegistration())
      .subscribe(
        successfulResult -> {
          System.out.println("👋 bye bye " + record.getRegistration());
          stopFuture.complete();
        },
        failure -> {
          failure.getCause().printStackTrace();
          System.out.println("😡 Unable to unpublish the microservice: " + failure.getMessage());
        }
      );
  }

  private Router defineRoutes(Router router) {
    
    router.route().handler(BodyHandler.create());

    router.get("/api/ping").handler(context -> {
      context.response()
        .putHeader("content-type", "application/json;charset=UTF-8")
        .end(
          new JsonObject().put("message", "🏓 pong!").toString()
        );
    });

    // serve static assets, see /resources/webroot directory
    router.route("/*").handler(StaticHandler.create());

    return router;
  }

  public void start() {

    setDiscovery();
    setRecord();

    /* === Define routes and start the server === */
    Router router = Router.router(vertx);
    defineRoutes(router);
    Integer httpPort = record.getLocation().getInteger("port");
    // if you use container or VM the httpPort and the servicePort could be different
    HttpServer server = vertx.createHttpServer();

    server
      .requestHandler(router::accept)
      .rxListen(httpPort)
      .subscribe(
        successfulHttpServer -> {
          System.out.println("🌍 Listening on " + successfulHttpServer.actualPort());
          /* === publication ===
              publish the microservice to the discovery backend
          */
          discovery
            .rxPublish(record)
            .subscribe(
              succesfulRecord -> {
                System.out.println("😃 Microservice is published! " + succesfulRecord.getRegistration());
              },
              failure -> {
                System.out.println("😡 Not able to publish the microservice: " + failure.getMessage());
              }
            );
        },
        failure -> {
          System.out.println("😡 Houston, we have a problem: " + failure.getMessage());
        }
      );
  }
}
