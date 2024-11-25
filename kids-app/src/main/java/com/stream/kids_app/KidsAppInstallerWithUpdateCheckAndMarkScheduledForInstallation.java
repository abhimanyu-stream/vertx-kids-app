package com.stream.kids_app;

import com.stream.kids_app.util.VersionUtils;
import io.vertx.core.*;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.mail.MailClient;
import io.vertx.ext.mail.MailConfig;
import io.vertx.ext.mail.MailMessage;
import io.vertx.ext.sql.SQLConnection;

import java.time.LocalDateTime;

public class KidsAppInstallerWithUpdateCheckAndMarkScheduledForInstallation extends AbstractVerticle {


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
    // Start periodic version check
    //vertx.setPeriodic(60000, timerId -> fetchAndUpdateLatestVersionsForAllApp());
    fetchAndUpdateLatestVersionsForAllApp();
    //processApps(startPromise);
  }


  /**
   * Periodically fetch and update the latest version of apps.
   */
  private void fetchAndUpdateLatestVersionsForAllApp() {
    jdbcClient.getConnection(conn -> {
      if (conn.succeeded()) {
        SQLConnection connection = conn.result();
        String query = "SELECT * FROM apps";
        connection.query(query, res -> {
          connection.close();
          if (res.succeeded()) {
            res.result().getRows().forEach(this::checkAndUpdateVersion);// check if any update available for an app installed in a device
          } else {
            System.err.println("Failed to fetch apps for version update: " + res.cause().getMessage());
          }
        });
      } else {
        System.err.println("Failed to connect to database for fetching apps: " + conn.cause().getMessage());
      }
    });
  }

  /**
   * Check and update the app's latest version if available.
   */
  private void checkAndUpdateVersion(JsonObject app) {
    String appName = app.getString("name");
    String currentVersion = app.getString("current_version");
    System.out.println("App In Process "+appName);
    String latestVersion = fetchLatestVersionFromPlayStore(appName);

    if (VersionUtils.isNewVersion(currentVersion, latestVersion)) {
      updateLatestVersionInDatabase(app, latestVersion);
    }
  }

  /**
   * Simulate fetching the latest version from an external source.
   */
  private String fetchLatestVersionFromPlayStore(String appName) {
    //logic: actual API or logic to fetch the latest version
    return "1.2.0"; //  fetched latest version for all app
  }

  /**
   * Update the app's latest version in the database.
   */
  private void updateLatestVersionInDatabase(JsonObject app, String latestVersion) {
    jdbcClient.getConnection(conn -> {
      if (conn.succeeded()) {
        SQLConnection connection = conn.result();
        String query = "UPDATE apps SET latest_version = ?, state = ?, retries = ? , updated_at = ? WHERE id = ?";
        JsonArray params = new JsonArray()
          .add(latestVersion)
          .add("SCHEDULED")
          .add(0)
          .add(LocalDateTime.now().toString())
          .add(app.getInteger("id"));
//
        connection.updateWithParams(query, params, res -> {
          connection.close();
          if (res.succeeded()) {
            //System.out.println("Updated latest version for app: " + app.getString("name") + " to " + latestVersion);


          } else {
            System.err.println("Failed to update latest version for app: " + app.getString("name"));
          }
        });
      } else {
        System.err.println("Failed to connect to database for updating version.");
      }
    });
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
        //String query = "SELECT * FROM apps WHERE state = 'SCHEDULED' ORDER BY updated_at ASC LIMIT 1";
        String query = "SELECT * FROM apps WHERE state IN ('SCHEDULED', 'ERROR') ORDER BY updated_at ASC LIMIT 1";
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

    updateState(app, "PICKEDUP")
      .compose(v -> performInstallation(app))
      .onSuccess(v -> updateStateOnCOMPLETED(app, "COMPLETED"))
      .onFailure(err -> {
        //updateStateOnERROR(app, "ERROR");
        int retries = app.getInteger("retries");
        if (retries + 1 <= MAX_RETRIES) {
          retryInstallation(app, retries + 1, err.getMessage());
        } else {
          markAsFailed(app, err.getMessage());
          sendFailureNotification(app, err.getMessage());
        }
      })
      .onComplete(promise);

    return promise.future();
  }

  private void updateStateOnCOMPLETED(JsonObject app, String completed) {

    Promise<Void> promise = Promise.promise();
    jdbcClient.getConnection(conn -> {
      if (conn.succeeded()) {
        try (SQLConnection connection = conn.result()) {
          String query = "UPDATE apps SET state = ?,  installed_version = ?, updated_at = ? WHERE id = ?";
          JsonArray params = new JsonArray()
            .add("COMPLETED")
            .add(app.getString("latest_version"))
            .add(LocalDateTime.now().toString())
            .add(app.getInteger("id"));

          connection.updateWithParams(query, params, res -> {
            connection.close();
            if (res.succeeded()) {
              System.out.println("App " + app.getString("name") + " transitioned to state: " + completed + " with installed_version " + app.getString("latest_version"));
              promise.complete();
            } else {
              promise.fail(res.cause());
            }
          });
        }
      } else {
        promise.fail(conn.cause());
      }
    });
   // return promise.future();
  }


  private Future<Void> updateState(JsonObject app, String newState) {
    Promise<Void> promise = Promise.promise();
    jdbcClient.getConnection(conn -> {
      if (conn.succeeded()) {
        SQLConnection connection = conn.result();
        String query = "UPDATE apps SET state = ?,  updated_at = ? WHERE id = ?";
        JsonArray params = new JsonArray()
          .add(newState)//PICKEDUP
          .add(LocalDateTime.now().toString())
          .add(app.getInteger("id"));

        connection.updateWithParams(query, params, res -> {
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
    Promise<Void> promise = Promise.promise();
    vertx.setTimer(2000, timerId -> {
      if (Math.random() > 0.3) { // Simulate 70% success rate, [random success for installation]
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


