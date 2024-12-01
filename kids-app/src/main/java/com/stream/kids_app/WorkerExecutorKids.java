package com.stream.kids_app;

import io.vertx.core.Vertx;
import io.vertx.core.WorkerExecutor;

public class WorkerExecutorKids {

  public static void main(String[] args) {
    // Create Vert.x instance
    Vertx vertx = Vertx.vertx();

    // Create a shared worker executor with a custom pool
    WorkerExecutor workerExecutor = vertx.createSharedWorkerExecutor("kids-worker-pool", 2, 2000);


      // Use the worker executor to run a blocking task
      workerExecutor.executeBlocking(promise -> {
        // Simulate a blocking operation
        try {
          System.out.println("Blocking task running on thread: " + Thread.currentThread().getName());
          Thread.sleep(2000); // Simulate a long-running operation
          promise.complete("Task completed successfully");
        } catch (InterruptedException e) {
          promise.fail(e);
        }
      }, result -> {
        // Handle the result of the blocking task
        if (result.succeeded()) {
          System.out.println(result.result());
        } else {
          System.err.println("Task failed: " + result.cause());
        }

        // Optionally close the worker executor when no longer needed
        workerExecutor.close();
      });

      // Add a small delay to ensure the worker executor completes before the main thread exits
      vertx.setTimer(3000, id -> vertx.close());
    }
}
