package hitmargin.adofai.converttoold;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import androidx.appcompat.app.AlertDialog;
import android.webkit.URLUtil;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONObject;

public class VersionChecker {
    private static final String GITHUB_VERSION_URL =
            "https://2228293026.github.io/ConvertToOld/version.json";

    public static void checkForUpdate(Context context) {
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder().url(GITHUB_VERSION_URL).build();

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {
                String json = response.body().string();
                JSONObject versionInfo = new JSONObject(json);
                String latestVersion = versionInfo.getString("latest_version");
                String minVersion = versionInfo.getString("min_version");
                String releaseNotes = versionInfo.getString("release_notes");
                String downloadUrl = versionInfo.getString("download_url");

                int currentVersionCode = BuildConfig.VERSION_CODE;
                String currentVersionName = BuildConfig.VERSION_NAME;

                // 比较版本号
                if (isUpdateNeeded(currentVersionName, latestVersion, minVersion)) {
                    showUpdateDialog(context, latestVersion, releaseNotes, downloadUrl);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static boolean isUpdateNeeded(
            String currentVersion, String latestVersion, String minVersion) {
        // 比较当前版本与最低版本
        if (compareVersions(currentVersion, minVersion) < 0) {
            return true; // 当前版本低于最低版本，必须更新
        }

        // 比较当前版本与最新版本
        return compareVersions(currentVersion, latestVersion) < 0;
    }

    private static int compareVersions(String version1, String version2) {
        String[] parts1 = version1.split("\\.");
        String[] parts2 = version2.split("\\.");

        for (int i = 0; i < Math.min(parts1.length, parts2.length); i++) {
            int num1 = Integer.parseInt(parts1[i]);
            int num2 = Integer.parseInt(parts2[i]);

            if (num1 != num2) {
                return num1 - num2;
            }
        }

        return parts1.length - parts2.length;
    }

    private static void showUpdateDialog(
            Context context, String latestVersion, String releaseNotes, String downloadUrl) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("版本更新");
        builder.setMessage("发现新版本：" + latestVersion + "\n更新内容：\n" + releaseNotes);
        builder.setPositiveButton(
                "更新",
                (dialog, which) -> {
                    // 打开下载链接
                    if (URLUtil.isValidUrl(downloadUrl)) {
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl));
                        context.startActivity(intent);
                    }
                });
        builder.setNegativeButton("取消", null);
        builder.show();
    }
}
