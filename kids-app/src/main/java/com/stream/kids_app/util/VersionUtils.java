package com.stream.kids_app.util;

public class VersionUtils {
  public static boolean isNewVersion(String currentVersion, String latestVersion) {
    if (currentVersion == null || latestVersion == null) return false;
    return latestVersion.compareTo(currentVersion) > 0;
  }
}
