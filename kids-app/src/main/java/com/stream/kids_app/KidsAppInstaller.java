package com.stream.kids_app;

import com.stream.kids_app.util.VersionUtils;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.mail.MailClient;
import io.vertx.ext.mail.MailConfig;
import io.vertx.ext.mail.MailMessage;
import io.vertx.ext.sql.SQLConnection;

import java.time.LocalDateTime;

public class KidsAppInstaller extends AbstractVerticle {

/*  @Override
  public void start() throws Exception {//Promise<Void> startPromise
    vertx.createHttpServer().requestHandler(req -> {
      req.response()
        .putHeader("content-type", "text/json")
        .end("Hello from Vert.x!");
    }).listen(8800);

  }*/

  private JDBCClient jdbcClient;
  private MailClient mailClient;
  private static final int MAX_RETRIES = 3;

  @Override
  public void start(Promise<Void> startPromise) {


    // Initialize the JDBC Client for PostgreSQL
    JsonObject dbConfig = new JsonObject()
      .put("url", "jdbc:postgresql://localhost:5432/Kids")
      .put("driver_class", "org.postgresql.Driver")
      .put("user", "postgres")
      .put("password", "root");



    // Create a Vertx instance
    Vertx vertx = Vertx.vertx();

    jdbcClient = JDBCClient.create(vertx, dbConfig);



    // Initialize the Mail Client for email notifications
    MailConfig mailConfig = new MailConfig()
      .setHostname("smtp.gmail.com")
      .setPort(587)
      .setUsername("koel.kunj@gmail.com")
      .setPassword("sqjy msdk szzu");




    mailClient = MailClient.createShared(vertx, mailConfig);

    processApps(startPromise);
  }

  private void processApps(Promise<Void> startPromise) {
    fetchNextApp()
      .compose(this::installApp)
      .onComplete(result -> {
        if (result.failed()) {
          System.err.println("Installation process failed: " + result.cause().getMessage());
          startPromise.fail(result.cause());
        } else {
          System.out.println("Installation process completed successfully.");
          startPromise.complete();
        }
      });
  }

  private Future<JsonObject> fetchNextApp() {
    Promise<JsonObject> promise = Promise.promise();
    jdbcClient.getConnection(conn -> {
      if (conn.succeeded()) {
        SQLConnection connection = conn.result();
        String query = "SELECT * FROM apps WHERE state = 'SCHEDULED' ORDER BY updated_at ASC LIMIT 1";
        connection.query(query, res -> {
          connection.close();
          if (res.succeeded() && !res.result().getRows().isEmpty()) {
            promise.complete(res.result().getRows().get(0));
          } else {
            promise.fail("No apps to process.");
          }
        });
      } else {
        promise.fail(conn.cause());
      }
    });
    return promise.future();
  }

  private Future<Void> installApp(JsonObject app) {
    Promise<Void> promise = Promise.promise();

    String appName = app.getString("name");
    int retries = app.getInteger("retries");


    // check for current_version and latest_version
    // if latest_version is > than current_version then call update()
    // save state of application that it is installed with this version



    updateState(app, "PICKEDUP")
      .compose(v -> performInstallation(app))
      .onSuccess(v -> updateState(app, "COMPLETED"))
      .onFailure(err -> {
        if (retries + 1 < MAX_RETRIES) {
          retryInstallation(app, retries + 1, err.getMessage());
        } else {
          markAsFailed(app, err.getMessage());
          sendFailureNotification(app, err.getMessage());
        }
      })
      .onComplete(promise);

    return promise.future();
  }

  private Future<Void> updateState(JsonObject app, String newState) { //installed_version     latest_version
    Promise<Void> promise = Promise.promise();
    jdbcClient.getConnection(conn -> {
      if (conn.succeeded()) {
        SQLConnection connection = conn.result();
        String query = "UPDATE apps SET state = ?, installed_version = ?, updated_at = ? WHERE id = ?";
        connection.updateWithParams(query,
          new JsonArray().add(newState).add(app.getString("latest_version")).add(LocalDateTime.now().toString()).add(app.getInteger("id")),
          res -> {
            connection.close();
            if (res.succeeded()) {
              System.out.println("App " + app.getString("name") + " transitioned to state: " + newState);
              promise.complete();
            } else {
              promise.fail(res.cause());
            }
          });
      } else {
        promise.fail(conn.cause());
      }
    });
    return promise.future();
  }

  private Future<Void> performInstallation(JsonObject app) {
    // Simulate installation process (e.g., deploying, configuring, etc.)
    Promise<Void> promise = Promise.promise();
    vertx.setTimer(2000, timerId -> {
      // Simulate success or failure
      if (Math.random() > 0.3) { // 70% success rate
        promise.complete();
      } else {
        promise.fail("Installation failed for app: " + app.getString("name"));
      }
    });
    return promise.future();
  }

  private void retryInstallation(JsonObject app, int newRetries, String error) {
    jdbcClient.getConnection(conn -> {
      if (conn.succeeded()) {
        SQLConnection connection = conn.result();
        String query = "UPDATE apps SET state = 'SCHEDULED', retries = ?, last_error = ?, updated_at = ? WHERE id = ?";
        connection.updateWithParams(query,
          new JsonArray().add(newRetries).add(error).add(LocalDateTime.now().toString()).add(app.getInteger("id")),
          res -> connection.close());
      }
    });
  }

  private void markAsFailed(JsonObject app, String error) {
    jdbcClient.getConnection(conn -> {
      if (conn.succeeded()) {
        SQLConnection connection = conn.result();
        String query = "UPDATE apps SET state = 'ERROR', last_error = ?, updated_at = ? WHERE id = ?";
        connection.updateWithParams(query,
          new JsonArray().add(error).add(LocalDateTime.now().toString()).add(app.getInteger("id")),
          res -> connection.close());
      }
    });
  }

  private void sendFailureNotification(JsonObject app, String error) {
    String appName = app.getString("name");
    MailMessage message = new MailMessage()
      .setFrom("koel.kunj@gmail.com")
      .setTo("stream.abhimanyu@gmail.com")
      .setSubject("App Installation Failure: " + appName)
      .setText("The app " + appName + " failed to install after " + MAX_RETRIES + " retries.\nError: " + error);

    mailClient.sendMail(message, result -> {
      if (result.succeeded()) {
        System.out.println("Failure notification sent for app: " + appName);
      } else {
        System.err.println("Failed to send notification for app: " + appName);
      }
    });
  }
}
