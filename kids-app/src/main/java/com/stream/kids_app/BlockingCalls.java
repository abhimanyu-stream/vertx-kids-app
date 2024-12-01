package com.stream.kids_app;

import io.vertx.core.Vertx;

public class BlockingCalls {

  public static void main(String[] args) {

    Vertx vertx = Vertx.vertx();

    vertx.executeBlocking(promise -> {
      // Blocking code here
      try {
        //throw new Exception("some ex");
        // Blocking code here
        Thread.sleep(2000);
        //String result = "Result from blocking operation";
        promise.complete("Result from blocking operation");

      } catch (Exception e) {
        throw new RuntimeException(e);
      }


    }, res -> {
      if (res.succeeded()) {
        System.out.println("Result: " + res.result());
      } else {
        System.out.println("Error: " + res.cause());
      }
    });
  }
}

