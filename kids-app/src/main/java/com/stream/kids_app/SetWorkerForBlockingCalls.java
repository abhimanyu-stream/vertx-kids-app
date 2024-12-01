package com.stream.kids_app;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.ext.mail.MailConfig;

import static io.vertx.core.VertxOptions.DEFAULT_WORKER_POOL_SIZE;

public class SetWorkerForBlockingCalls {

  public static void main(String[] args) {
    Vertx vertx = Vertx.vertx();
    // Using Worker Verticles:
    //Worker verticles are designed for executing blocking code. You can offload blocking tasks to these verticles without impacting the performance of the event loop.
    //Important Considerations:
    //Avoid blocking the event loop. Blocking the event loop can severely impact the performance and responsiveness of your Vert.x application.
    //Use executeBlocking for short-lived blocking operations. If you have long-running blocking tasks, consider using worker verticles.
    //Configure worker pool size. You can customize the size of the worker pool to match your application's requirements.
    DeploymentOptions options = new DeploymentOptions().setWorker(true).setWorkerPoolSize(DEFAULT_WORKER_POOL_SIZE);;
    vertx.deployVerticle(new WorkerVerticle(), options);
  }


}
class WorkerVerticle extends AbstractVerticle {

  @Override
  public void start() {
    vertx.executeBlocking(promise -> {
      // Blocking code here
      try {
        Thread.sleep(2000);
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
      String result = "Result from worker verticle";
      promise.complete(result);
    }, res -> {
      if (res.succeeded()) {
        System.out.println("Result: " + res.result());
      } else {
        System.out.println("Error: " + res.cause());
      }
    });
  }
}
