package com.stream.kids_app;

import io.vertx.core.Vertx;

public class KidsApplication {

  public static void main(String[] args) throws Exception {

    System.setProperty("vertx.logger-delegate-factory-class-name", "io.vertx.core.logging.SLF4JLogDelegateFactory");

    // Create a Vertx instance
    Vertx vertx = Vertx.vertx();


    // Create an instance of the MainApp
    //KidsAppInstaller app = new KidsAppInstaller();
    KidsAppInstallerWithUpdateCheckAndMarkScheduledForInstallation app = new KidsAppInstallerWithUpdateCheckAndMarkScheduledForInstallation();
    vertx.deployVerticle(app, res -> {
      if (res.succeeded()) {
        System.out.println("Verticle deployed successfully");
      } else {
        System.err.println("Verticle deployment failed: " + res.cause().getMessage());
      }
    });

  }
}

