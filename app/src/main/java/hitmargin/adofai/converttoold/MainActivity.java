package hitmargin.adofai.converttoold;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.text.InputType;
import android.util.Log;
import android.view.ContextMenu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class MainActivity extends AppCompatActivity {

  private static final int REQUEST_LEGACY_STORAGE_PERMISSION = 100;
  private static final int REQUEST_MANAGE_STORAGE = 101;

  private List<String> itemList; // 存储文件名
  private List<String> timeList; // 存储修改时间
  public static RadioButton modifyVersionOnly;
  public static RadioButton normalmodify;
  private Button explain;
  private TextView currentDirectoryTextView;
  public static RadioButton additionalmodifications;
  private ListView listView;
  private ArrayAdapter<String> adapter;
  private Stack<String> directoryStack;
  private File currentDirectory;
  private File copiedFile;
  private static final int MENU_COPY = 1;
  private static final int MENU_PASTE = 2;
  private static final int MENU_RENAME = 3;
  private static final int MENU_DELETE = 4;
  private static final int MENU_MOVE = 5;
  private static final int MENU_UNZIP = 6;
  private static final int MENU_NEW_FOLDER = 7;
  private static final int MENU_NEW_FILE = 8;
  private boolean lastActionWasCopy = false;
  private Button filterButton;
  private SwipeRefreshLayout swipeRefreshLayout;
  private File file;
  private String line;
  public static int selectedVersion = 0; // 默认为2.4

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    Date currentDate = new Date();
    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss");
    String formattedDate = dateFormat.format(currentDate);
    // Toast.makeText(getApplication(), "构建日期：" + formattedDate + "\n作者：HitMargin |
    // QQ：2228293026", Toast.LENGTH_SHORT).()();
    /*
    Toast.makeText(
                    getApplication(),
                    "构建日期：2025.3.28\n作者：HitMargin | QQ：2228293026",
                    Toast.LENGTH_SHORT)
            .show();
    */
    itemList = new ArrayList<>();
    timeList = new ArrayList<>();
    currentDirectoryTextView = findViewById(R.id.currentDirectoryTextView);
    additionalmodifications = findViewById(R.id.additionalmodifications);
    explain = findViewById(R.id.explain);
    listView = findViewById(R.id.listView);
    normalmodify = findViewById(R.id.normalmodify);
    modifyVersionOnly = findViewById(R.id.modifyVersionOnly);
    swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
    itemList = new ArrayList<>();
    adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, itemList);
    listView.setAdapter(adapter);

    registerForContextMenu(listView);
    checkAllFilesAccessPermission();

    directoryStack = new Stack<>();

    Spinner versionSpinner = findViewById(R.id.versionSpinner);
    ArrayAdapter<CharSequence> adapter =
        ArrayAdapter.createFromResource(
            this, R.array.version_entries, android.R.layout.simple_spinner_item);
    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    versionSpinner.setAdapter(adapter);

    currentDirectoryTextView.setOnClickListener(
        new View.OnClickListener() {
          @Override
          public void onClick(View v) {
            showPathInputDialog();
          }
        });

    // 设置 Spinner 选择监听器
    versionSpinner.setOnItemSelectedListener(
        new AdapterView.OnItemSelectedListener() {
          @Override
          public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            selectedVersion = position;
            updateModificationOptionsVisibility();
            SharedPreferences prefs =
                PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putInt("selectedVersion", selectedVersion);
            editor.apply();
          }

          @Override
          public void onNothingSelected(AdapterView<?> parent) {
            // Do nothing
          }
        });

    // 读取用户选择的版本号

    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
    selectedVersion = prefs.getInt("selectedVersion", 0); // 读取用户的选择
    updateModificationOptionsVisibility();

    filterButton = findViewById(R.id.filterButton);
    filterButton.setOnClickListener(
        new View.OnClickListener() {
          @Override
          public void onClick(View v) {
            showFilterInputDialog();
          }
        });

    listView.setOnItemClickListener(
        new AdapterView.OnItemClickListener() {
          @Override
          public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            String item = itemList.get(position);

            // 提取实际名称（去掉前缀）
            String actualName = extractActualName(item);

            // 处理返回上一级
            if (item.equals("[📁] ... (返回上一级)")) {
              File parentDir = currentDirectory.getParentFile();
              if (parentDir != null && parentDir.exists()) {
                displayFiles(parentDir);
              }
              return;
            }

            if (item.startsWith("[📁]")) {
              // 文件夹点击处理
              File selectedFolder = new File(currentDirectory, actualName);

              // 验证文件夹存在且可访问
              if (!selectedFolder.exists()) {
                Toast.makeText(MainActivity.this, "文件夹不存在: " + actualName, Toast.LENGTH_SHORT)
                    .show();
                return;
              }

              if (!selectedFolder.isDirectory()) {
                Toast.makeText(MainActivity.this, "不是有效的文件夹: " + actualName, Toast.LENGTH_SHORT)
                    .show();
                return;
              }

              if (!selectedFolder.canRead()) {
                Toast.makeText(MainActivity.this, "无权限访问文件夹: " + actualName, Toast.LENGTH_SHORT)
                    .show();
                return;
              }

              // 添加到目录栈并显示内容
              directoryStack.push(currentDirectory.getAbsolutePath());
              displayFiles(selectedFolder);

            } else if (item.startsWith("[📄]")) {
              // 文件点击处理
              File selectedFile = new File(currentDirectory, actualName);

              if (selectedFile.exists() && selectedFile.isFile()) {
                if (actualName.toLowerCase().endsWith(".adofai")) {
                  ConvertToOld(selectedFile);
                } else if (actualName.toLowerCase().endsWith(".zip")) {
                  unZipFile(selectedFile);
                } else {
                  Toast.makeText(MainActivity.this, "不支持的文件类型: " + actualName, Toast.LENGTH_SHORT)
                      .show();
                }
              } else {
                Toast.makeText(MainActivity.this, "文件不存在或无效: " + actualName, Toast.LENGTH_SHORT)
                    .show();
              }
            }
          }
        });

    listView.setOnItemLongClickListener(
        new AdapterView.OnItemLongClickListener() {
          @Override
          public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
            String item = itemList.get(position);

            // 长按返回上一级，显示选项
            if (item.equals("[📁] ... (返回上一级)")) {
              showCreateDialog();
              return true;
            }

            return false;
          }
        });

    explain.setOnClickListener(
        new View.OnClickListener() {

          @Override
          public void onClick(View view) {
            String message =
                "需知：特殊修改：修复移动摄像头错误和背景错误\n只修改版本号是为11\n该工具免费提供！禁止倒卖\n更新内容如下\n修复错误\n(如果出错请在编辑器里重新保存关卡再转换),修复暂停节拍错误,新增版本选择，用的什么版本就选择哪个版本,新增修复自由轨道/n只显示文件夹/.adofai文件，SDK版本调新，所有文件访问权限需要开启\n1.3.0新增文件夹/文件图标显示，添加返回上一目录长按可以选择新增文件/文件夹/粘贴\n1.3.1更新：修复跳过检测，适配手势指示条";
            showMessageDialog(message);
          }
        });

    swipeRefreshLayout.setOnRefreshListener(
        new SwipeRefreshLayout.OnRefreshListener() {
          @Override
          public void onRefresh() {
            // 当用户上滑时触发的操作
            refreshFiles();
          }
        });

    swipeRefreshLayout.setColorSchemeResources(
        android.R.color.holo_purple,
        android.R.color.holo_green_light,
        android.R.color.holo_orange_light,
        android.R.color.holo_blue_light,
        android.R.color.holo_purple,
        android.R.color.holo_red_light);
  }

  private void refreshFiles() {

    displayFiles(currentDirectory);

    // 刷新完成后，调用此方法来关闭刷新指示器
    swipeRefreshLayout.setRefreshing(false);
  }

  // 添加辅助方法提取实际名称
  private String extractActualName(String displayName) {
    // 查找第一个空格后的位置
    int spaceIndex = displayName.indexOf(' ');
    if (spaceIndex != -1 && spaceIndex + 1 < displayName.length()) {
      return displayName.substring(spaceIndex + 1);
    }
    return displayName;
  }

  private void showPathInputDialog() {
    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setTitle("输入路径");

    // 设置输入框
    final EditText input = new EditText(this);
    input.setInputType(InputType.TYPE_CLASS_TEXT);
    input.setText(currentDirectory.getAbsolutePath()); // 设置默认值为当前路径
    input.selectAll(); // 选中全部内容，方便用户直接修改

    builder.setView(input);

    // 设置确认按钮
    builder.setPositiveButton(
        "确定",
        new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            String newPath = input.getText().toString().trim();
            if (!newPath.isEmpty()) {
              File newDirectory = new File(newPath);
              if (newDirectory.exists() && newDirectory.isDirectory()) {
                displayFiles(newDirectory); // 跳转到新路径
              } else {
                Toast.makeText(MainActivity.this, "路径无效或不存在！", Toast.LENGTH_SHORT).show();
              }
            }
          }
        });

    // 设置取消按钮
    builder.setNegativeButton(
        "取消",
        new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            dialog.cancel();
          }
        });

    // 显示对话框
    final AlertDialog dialog = builder.create();
    dialog.show();

    // 请求输入框的焦点并显示输入法
    // 确保输入框获取焦点
    input.requestFocus();

    // 使用 Handler 来延迟执行，确保 UI 线程已经完成布局
    new Handler(Looper.getMainLooper())
        .postDelayed(
            () -> {
              InputMethodManager imm =
                  (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
              if (imm != null) {
                imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
              }
            },
            100); // 增加延迟时间
  }

  // 修改 displayFiles 方法，添加更多日志和验证
  private void displayFiles(File directory) {
    if (directory == null || !directory.exists() || !directory.isDirectory()) {
      Log.e("MainActivity", "无效目录: " + (directory != null ? directory.getAbsolutePath() : "null"));
      Toast.makeText(this, "无法访问目录", Toast.LENGTH_SHORT).show();
      return;
    }

    if (!directory.canRead()) {
      Log.e("MainActivity", "无读取权限: " + directory.getAbsolutePath());
      Toast.makeText(this, "无目录读取权限", Toast.LENGTH_SHORT).show();
      return;
    }

    try {
      currentDirectory = directory;
      String currentPath = directory.getAbsolutePath();
      currentDirectoryTextView.setText("当前目录: " + currentPath);
      itemList.clear();

      // 添加返回上一级选项（如果有父目录且父目录存在）
      File parent = currentDirectory.getParentFile();
      if (parent != null && parent.exists()) {
        itemList.add("[📁] ... (返回上一级)");
      }

      File[] files = directory.listFiles();
      if (files != null) {
        // 分别存储文件夹和文件
        List<File> folders = new ArrayList<>();
        List<File> validFiles = new ArrayList<>();

        for (File file : files) {
          if (file.isHidden()) continue; // 跳过隐藏文件

          if (file.isDirectory()) {
            folders.add(file);
          } else {
            String name = file.getName().toLowerCase();
            if (name.endsWith(".adofai") || name.endsWith(".zip")) {
              validFiles.add(file);
            }
          }
        }

        // 排序
        folders.sort((f1, f2) -> f1.getName().compareToIgnoreCase(f2.getName()));
        validFiles.sort((f1, f2) -> f1.getName().compareToIgnoreCase(f2.getName()));

        // 添加文件夹
        for (File folder : folders) {
          itemList.add("[📁] " + folder.getName());
        }

        // 添加文件
        for (File validFile : validFiles) {
          itemList.add("[📄] " + validFile.getName());
        }
      } else {
        Log.w("MainActivity", "空目录: " + currentPath);
      }

      adapter.notifyDataSetChanged();
      swipeRefreshLayout.setRefreshing(false);

    } catch (SecurityException e) {
      Log.e("MainActivity", "安全异常: " + e.getMessage());
      Toast.makeText(this, "权限不足: " + e.getMessage(), Toast.LENGTH_SHORT).show();
    } catch (Exception e) {
      Log.e("MainActivity", "错误: " + e.getMessage(), e);
      Toast.makeText(this, "错误: " + e.getMessage(), Toast.LENGTH_SHORT).show();
    }
  }

  private void showFilterInputDialog() {
    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setTitle("输入过滤文本");

    // 设置输入框
    final EditText input = new EditText(this);
    input.setInputType(InputType.TYPE_CLASS_TEXT);
    builder.setView(input);

    // 设置确认按钮
    builder.setPositiveButton(
        "确认",
        new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            String filter = input.getText().toString();
            applyFilter(filter);
          }
        });

    // 设置取消按钮
    builder.setNegativeButton(
        "取消",
        new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            dialog.cancel();
          }
        });

    // 显示对话框
    builder.show();
  }

  private void applyFilter(String filter) {
    File directory = currentDirectory;
    itemList.clear();
    File[] files = directory.listFiles();
    if (files != null) {
      for (File file : files) {
        String displayName =
            file.isDirectory() ? "[📁] " + file.getName() : "[📄] " + file.getName();

        if (displayName.toLowerCase().contains(filter.toLowerCase())) {
          itemList.add(displayName);
        }
      }
    }
    adapter.notifyDataSetChanged();
  }

  public void showMessageDialog(String message) {
    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder
        .setMessage(message)
        .setPositiveButton(
            "确定",
            new DialogInterface.OnClickListener() {
              public void onClick(DialogInterface dialog, int id) {
                // 用户点击确定按钮后的操作，如果需要的话
                dialog.dismiss();
              }
            });
    builder.create().show();
  }

  @Override
  public void onRequestPermissionsResult(
      int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);

    // 只处理传统存储权限请求
    if (requestCode == REQUEST_LEGACY_STORAGE_PERMISSION) {
      if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
        displayFiles(Environment.getExternalStorageDirectory());
      } else {
        Toast.makeText(this, "存储权限被拒绝", Toast.LENGTH_SHORT).show();
      }
    }
  }

  private void checkPermissions() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      // Android 11+ 使用 MANAGE_EXTERNAL_STORAGE
      if (!Environment.isExternalStorageManager()) {
        showPermissionPromptDialog();
      } else {
        displayFiles(Environment.getExternalStorageDirectory());
      }
    } else {
      // 旧版本使用 READ/WRITE_EXTERNAL_STORAGE
      if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
          != PackageManager.PERMISSION_GRANTED) {
        ActivityCompat.requestPermissions(
            this,
            new String[] {Manifest.permission.READ_EXTERNAL_STORAGE},
            REQUEST_LEGACY_STORAGE_PERMISSION);
      } else {
        displayFiles(Environment.getExternalStorageDirectory());
      }
    }
  }

  @Override
  public boolean onContextItemSelected(MenuItem item) {
    AdapterView.AdapterContextMenuInfo info =
        (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
    int position = info.position;
    if (position < 0 || position >= itemList.size()) {
      return false;
    }
    String selectedItem = itemList.get(info.position);

    // 跳过返回上一级项
    if (selectedItem.equals("[📁] ... (返回上一级)")) {
      return false;
    }

    // 去除前缀标识（"[📁] " 或 "[📄] "）
    String actualName = extractActualName(selectedItem);
    final File selectedFile = new File(currentDirectory, actualName);

    switch (item.getItemId()) {
      case MENU_COPY:
        copiedFile = selectedFile;
        lastActionWasCopy = true;
        Toast.makeText(this, "已选择：" + actualName, Toast.LENGTH_SHORT).show();
        return true;

      case MENU_MOVE:
        copiedFile = selectedFile;
        lastActionWasCopy = false;
        Toast.makeText(this, "已选择：" + actualName, Toast.LENGTH_SHORT).show();
        return true;

      case MENU_PASTE:
        if (copiedFile == null || !copiedFile.exists()) {
          showMessageDialog("没有可粘贴的文件或文件已不存在！");
          return true;
        }

        try {
          String newName = copiedFile.getName();
          File newFile = new File(currentDirectory, newName);

          // 处理文件名冲突
          int counter = 1;
          while (newFile.exists()) {
            String nameWithoutExt = newName.replaceFirst("[.][^.]+$", "");
            String ext = newName.contains(".") ? newName.substring(newName.lastIndexOf('.')) : "";
            newFile = new File(currentDirectory, nameWithoutExt + " (" + counter + ")" + ext);
            counter++;
          }

          if (lastActionWasCopy) {
            // 使用正确的复制方法
            copyFileOrDirectory(copiedFile, newFile);
            showMessageDialog("复制成功！");
          } else {
            // 优化移动操作
            if (moveFile(copiedFile, newFile)) {
              showMessageDialog("移动成功！");
            } else {
              showMessageDialog("移动失败！");
            }
          }
          displayFiles(currentDirectory);
        } catch (IOException e) {
          showMessageDialog("操作失败: " + e.getMessage());
        }
        return true;

      case MENU_RENAME:
        final String oldName = selectedFile.getName();
        final EditText input = new EditText(this);
        input.setText(oldName);
        input.setSelection(
            0, oldName.lastIndexOf('.') > 0 ? oldName.lastIndexOf('.') : oldName.length());

        new AlertDialog.Builder(this)
            .setTitle("重命名")
            .setView(input)
            .setPositiveButton(
                "确定",
                (dialog, which) -> {
                  String newName = input.getText().toString().trim();
                  if (!newName.isEmpty()) {
                    // 保持文件扩展名
                    if (selectedFile.isFile() && !newName.contains(".")) {
                      int dotIndex = oldName.lastIndexOf('.');
                      if (dotIndex > 0) {
                        newName += oldName.substring(dotIndex);
                      }
                    }

                    File newFile = new File(selectedFile.getParent(), newName);
                    if (selectedFile.renameTo(newFile)) {
                      displayFiles(currentDirectory);
                      showMessageDialog("重命名成功！");
                    } else {
                      showMessageDialog("重命名失败！");
                    }
                  }
                })
            .setNegativeButton("取消", null)
            .show();
        return true;
      case MENU_DELETE:
        new AlertDialog.Builder(this)
            .setTitle("确认删除")
            .setMessage("确定要删除 " + actualName + " 吗？")
            .setPositiveButton(
                "确定",
                new DialogInterface.OnClickListener() {
                  @Override
                  public void onClick(DialogInterface dialog, int which) {
                    if (deleteRecursive(selectedFile)) {
                      displayFiles(currentDirectory);
                      showMessageDialog("删除成功！");
                    } else {
                      showMessageDialog("删除失败！");
                    }
                  }
                })
            .setNegativeButton("取消", null)
            .show();
        return true;

      case MENU_UNZIP:
        unZipFile(selectedFile);
        return true;

      default:
        return super.onContextItemSelected(item);
    }
  }

  // 删除文件或文件夹
  private void copyFileOrDirectory(File source, File dest) throws IOException {
    if (source.isDirectory()) {
      if (!dest.exists() && !dest.mkdirs()) {
        throw new IOException("无法创建目录: " + dest.getAbsolutePath());
      }

      File[] files = source.listFiles();
      if (files != null) {
        for (File file : files) {
          File newFile = new File(dest, file.getName());
          copyFileOrDirectory(file, newFile);
        }
      }
    } else {
      try (FileInputStream in = new FileInputStream(source);
          FileOutputStream out = new FileOutputStream(dest)) {

        FileChannel inChannel = in.getChannel();
        FileChannel outChannel = out.getChannel();
        inChannel.transferTo(0, inChannel.size(), outChannel);
      }
    }
  }

  // 添加移动文件/目录的方法
  private boolean moveFile(File source, File dest) {
    try {
      // 先尝试直接重命名
      if (source.renameTo(dest)) {
        return true;
      }

      // 如果重命名失败，尝试复制后删除
      copyFileOrDirectory(source, dest);
      return deleteRecursive(source);
    } catch (IOException e) {
      Log.e("FileMove", "移动文件失败", e);
      return false;
    }
  }

  // 添加递归删除方法
  private boolean deleteRecursive(File file) {
    if (file.isDirectory()) {
      File[] files = file.listFiles();
      if (files != null) {
        for (File child : files) {
          if (!deleteRecursive(child)) {
            return false;
          }
        }
      }
    }
    return file.delete();
  }

  // 修复解压功能
  private void unZipFile(File zipFile) {
    String zipFileName = zipFile.getName();
    String zipFileNameWithoutExtension = zipFileName.substring(0, zipFileName.lastIndexOf('.'));
    String destinationDir = zipFile.getParent() + File.separator + zipFileNameWithoutExtension;

    File destinationFolder = new File(destinationDir);
    if (!destinationFolder.exists()) {
      destinationFolder.mkdirs();
    }

    try (ZipInputStream zipIn = new ZipInputStream(new FileInputStream(zipFile))) {
      ZipEntry zipEntry;
      while ((zipEntry = zipIn.getNextEntry()) != null) {
        String entryName = zipEntry.getName();
        String filePath = destinationDir + File.separator + entryName;

        if (zipEntry.isDirectory()) {
          File dir = new File(filePath);
          dir.mkdirs();
        } else {
          extractFile(zipIn, filePath);
        }

        zipIn.closeEntry();
      }

      showMessageDialog("解压完成！");
      displayFiles(currentDirectory);
    } catch (IOException e) {
      e.printStackTrace();
      showMessageDialog("解压失败！");
    }
  }

  private static void extractFile(ZipInputStream zipIn, String filePath) throws IOException {
    try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(filePath))) {
      byte[] bytesIn = new byte[4096];
      int read;
      while ((read = zipIn.read(bytesIn)) != -1) {
        bos.write(bytesIn, 0, read);
      }
    }
  }

  // 添加长按返回上一级时的新建文件夹/文件/粘贴功能
  @Override
  public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
    super.onCreateContextMenu(menu, v, menuInfo);
    if (v.getId() == R.id.listView) {
      AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
      String selectedItem = itemList.get(info.position);

      // 跳过返回上一级项
      if (selectedItem.equals("[📁] ... (返回上一级)")) {
        menu.setHeaderTitle("操作");
        menu.add(0, MENU_NEW_FOLDER, 0, "新建文件夹");
        menu.add(0, MENU_NEW_FILE, 0, "新建文件");
        if (copiedFile != null) {
          menu.add(0, MENU_PASTE, 0, "粘贴");
        }
        return;
      }

      // 去除前缀标识
      String actualName = selectedItem.substring(4);

      menu.setHeaderTitle(actualName); // 显示实际文件名
      menu.add(0, MENU_COPY, 0, "复制");
      menu.add(0, MENU_MOVE, 0, "移动");
      if (copiedFile != null) menu.add(0, MENU_PASTE, 0, "粘贴");
      menu.add(0, MENU_RENAME, 0, "重命名");
      menu.add(0, MENU_DELETE, 0, "删除");

      File selectedFile = new File(currentDirectory, actualName);

      // 检查文件是否为ZIP格式
      if (selectedFile.isFile() && actualName.toLowerCase().endsWith(".zip")) {
        menu.add(0, MENU_UNZIP, 0, "解压");
      }
    }
  }

  // 新建文件夹功能
  private void createNewFolder() {
    final EditText input = new EditText(this);
    input.setInputType(InputType.TYPE_CLASS_TEXT);
    new AlertDialog.Builder(this)
        .setTitle("新建文件夹")
        .setMessage("请输入文件夹名称")
        .setView(input)
        .setPositiveButton(
            "确定",
            new DialogInterface.OnClickListener() {
              @Override
              public void onClick(DialogInterface dialog, int which) {
                String folderName = input.getText().toString().trim();
                if (!folderName.isEmpty()) {
                  File newFolder = new File(currentDirectory, folderName);
                  if (newFolder.mkdir()) {
                    displayFiles(currentDirectory);
                    showMessageDialog("文件夹创建成功！");
                  } else {
                    showMessageDialog("文件夹创建失败！");
                  }
                } else {
                  showMessageDialog("文件夹名称不能为空！");
                }
              }
            })
        .setNegativeButton("取消", null)
        .show();
  }

  // 新建文件功能
  private void createNewFile() {
    final EditText input = new EditText(this);
    input.setInputType(InputType.TYPE_CLASS_TEXT);
    new AlertDialog.Builder(this)
        .setTitle("新建文件")
        .setMessage("请输入文件名称")
        .setView(input)
        .setPositiveButton(
            "确定",
            new DialogInterface.OnClickListener() {
              @Override
              public void onClick(DialogInterface dialog, int which) {
                String fileName = input.getText().toString().trim();
                if (!fileName.isEmpty()) {
                  File newFile = new File(currentDirectory, fileName);
                  try {
                    if (newFile.createNewFile()) {
                      displayFiles(currentDirectory);
                      showMessageDialog("文件创建成功！");
                    } else {
                      showMessageDialog("文件创建失败！");
                    }
                  } catch (IOException e) {
                    e.printStackTrace();
                    showMessageDialog("文件创建失败！");
                  }
                } else {
                  showMessageDialog("文件名称不能为空！");
                }
              }
            })
        .setNegativeButton("取消", null)
        .show();
  }

  // 修改 onBackPressed 方法
  @Override
  public void onBackPressed() {
    if (!directoryStack.isEmpty()) {
      String previousPath = directoryStack.pop();
      displayFiles(new File(previousPath));
    } else {
      File parent = currentDirectory.getParentFile();
      if (parent != null && parent.exists()) {
        displayFiles(parent);
      } else {
        super.onBackPressed();
      }
    }
  }

  // 添加长按返回上一级时的新建文件夹/文件/粘贴功能
  private void showCreateDialog() {
    final String[] options = {"新建文件夹", "新建文件", "粘贴"};
    final AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder
        .setTitle("选择操作")
        .setItems(
            options,
            new DialogInterface.OnClickListener() {
              @Override
              public void onClick(DialogInterface dialog, int which) {
                String selectedOption = options[which];
                if ("新建文件夹".equals(selectedOption)) {
                  createNewFolder();
                } else if ("新建文件".equals(selectedOption)) {
                  createNewFile();
                } else if ("粘贴".equals(selectedOption)) {
                  if (copiedFile != null) {
                    String copiedFileName = copiedFile.getName();
                    File newFile = new File(currentDirectory, copiedFileName);

                    if (lastActionWasCopy) { // 复制操作
                      try {
                        copyFileOrDirectory(copiedFile, newFile);
                        displayFiles(currentDirectory);
                        showMessageDialog("已粘贴到：" + currentDirectory.getAbsolutePath() + " ！");
                      } catch (IOException e) {
                        e.printStackTrace();
                        showMessageDialog("无法粘贴文件！");
                      }
                    } else { // 移动操作
                      if (copiedFile.renameTo(newFile)) {
                        displayFiles(currentDirectory);
                        showMessageDialog("已移动到：" + currentDirectory.getAbsolutePath() + " ！");
                      } else {
                        showMessageDialog("移动文件失败！");
                      }
                    }
                  } else {
                    showMessageDialog("没有已复制或已选择的文件！");
                  }
                }
              }
            });
    builder.create().show();
  }

  public void ConvertToOld(File file) {
    this.file = file;
    convertFile();
  }

  // 执行文件转换
  private void convertFile() {
    StringBuilder result = new StringBuilder();
    boolean inActionsSection = false;

    try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
      String line;
      while ((line = reader.readLine()) != null) {
        // 检测是否进入actions部分
        if (line.contains("\"actions\":")) {
          inActionsSection = true;
        }

        if (!inActionsSection) {
          if (enterDeleteMode(line, false)) {
            continue;
          }
          line = modifyLine(line, false);
        } else {
          if (enterDeleteMode(line, true)) {
            continue;
          }
          line = modifyLine(line, true);
        }

        result.append(line).append("\n");
      }
    } catch (IOException e) {
      e.printStackTrace();
    }

    String fileContent = result.toString();
    saveFile(file, fileContent);
  }

  // 检查行是否包含特殊方法或关键词
  private boolean enterDeleteMode(String line, boolean inActionsSection) {
    if (inActionsSection) {
      Set<String> specialMethodsAndKeywords = new HashSet<>();
      if (modifyVersionOnly.isChecked()) {
        return false;
      }
      if (selectedVersion == 1) {
        specialMethodsAndKeywords.add("SetInputEvent");
        return specialMethodsAndKeywords.stream().anyMatch(method -> line.contains(method));
      }

      specialMethodsAndKeywords.add("SetObject");
      specialMethodsAndKeywords.add("AddObject");
      specialMethodsAndKeywords.add("SetFilterAdvanced");
      specialMethodsAndKeywords.add("SetFloorIcon");
      specialMethodsAndKeywords.add("SetDefaultText");
      specialMethodsAndKeywords.add("SetFrameRate");
      specialMethodsAndKeywords.add("\"targetPlanet\": \"GreenPlanet\"");
      specialMethodsAndKeywords.add("EmitParticle");
      specialMethodsAndKeywords.add("SetParticle");
      specialMethodsAndKeywords.add("AddParticle");
      specialMethodsAndKeywords.add("SetInputEvent");

      // 检查行是否包含特殊方法或关键词
      return specialMethodsAndKeywords.stream().anyMatch(method -> line.contains(method));
    } else {
      return false;
    }
  }

  // 修改行内容，如果行包含关键词则返回null
  private String modifyLine(String line, boolean inActionsSection) {
    // 检查关键词并可能跳过行
    /*
            Set<String> keywordsToSkip =
                    Set.of(
                            "		\"song\":",
                            "		\"artist\":",
                            "		\"songFilename\":",
                            "		\"author\":",
                            "		\"legacyFlash\":",
                            "		\"legacyCamRelativeTo\":",
                            "		\"legacySpriteTiles\":",
                            "		\"artistLinks\":",
                            "		\"levelTags\":",
                            "		\"levelDesc\":",
                            "		\"previewImage\":",
                            "		\"previewIcon\":",
                            "		\"previewIconColor\":",
                            "		\"artistPermission\":",
                            "		\"specialArtistType\" ",
                            "		\"trackTexture\":",
                            "		\"bgImage\":",
                            "		\"bgVideo\":");
    */
    if (!inActionsSection) {
      Set<String> keywordsToSkip =
          Set.of(
              "\"song\":",
              "\"artist\":",
              "\"songFilename\":",
              "\"author\":",
              "\"legacyFlash\":",
              "\"legacyCamRelativeTo\":",
              "\"legacySpriteTiles\":",
              "\"artistLinks\":",
              "\"levelTags\":",
              "\"levelDesc\":",
              "\"previewImage\":",
              "\"previewIcon\":",
              "\"previewIconColor\":",
              "\"artistPermission\":",
              "\"specialArtistType\" ",
              "\"trackTexture\":",
              "\"bgImage\":",
              "\"bgVideo\":");

      // 检查文本行中是否包含关键字，如果包含则跳过整行
      for (String keyword : keywordsToSkip) {
        if (line.contains(keyword)) {
          return line; // 直接返回原始行，不进行任何修改
        }
      }
    }

    // 如果没有关键词，进行替换
    return processLine(line);
  }

  public String processLine(String line) {
    // 如果行以"version"开头，则替换版本号
    if (line.trim().startsWith("\"version\":")) {
      return "		\"version\": " + (selectedVersion == 0 ? 11 : 15) + " ,";
    }

    // 如果选择的版本为2.8，则只修改暂停节拍
    if (selectedVersion == 1) {
      if (line.contains("\"eventType\": \"Pause\"")
          || line.contains("\"eventType\": \"FreeRoam\"")) {
        line = replaceAngleCorrectionDir(line);
      }
    }

    // 如果选择的版本为2.4，则应用全部转换逻辑
    if (selectedVersion == 0) {
      if (modifyVersionOnly.isChecked()) {
        if (line.trim().startsWith("\"version\":")) {
          return "		\"version\": 11 ,";
        }
        return line;
      }

      if (additionalmodifications.isChecked()) {
        line = applyModifications(line, true);
      }

      if (normalmodify.isChecked()) {
        line = applyModifications(line, false);
      }

      if (line.contains("\"eventType\": \"Pause\"")
          || line.contains("\"eventType\": \"FreeRoam\"")) {
        line = replaceAngleCorrectionDir(line);
      }
    }

    // 检测特定事件并修改数值
    line = checkAndModifyEventIntegers(line);

    return line;
  }

  private String replaceAngleCorrectionDir(String line) {
    return line.replace("\"angleCorrectionDir\": \"Backward\"", "\"angleCorrectionDir\": -1 ")
        .replace("\"angleCorrectionDir\": \"None\"", "\"angleCorrectionDir\": 0 ")
        .replace("\"angleCorrectionDir\": \"Forward\"", "\"angleCorrectionDir\": 1 ");
  }

  private String applyModifications(String line, boolean isAdditional) {
    StringBuilder result = new StringBuilder();
    int i = 0;
    int len = line.length();

    while (i < len) {
      // 检查是否是独立的 true（不带引号，前后无其他字符）
      if (i + 4 <= len && line.startsWith("true", i)) {
        boolean isStandalone =
            (i == 0 || isSeparator(line.charAt(i - 1)))
                && // 前面是分隔符或行首
                (i + 4 == len || isSeparator(line.charAt(i + 4))); // 后面是分隔符或行尾

        if (isStandalone) {
          result.append("\"Enabled\"");
          i += 4;
        } else {
          result.append(line.charAt(i));
          i++;
        }
      }
      // 检查是否是独立的 false（不带引号，前后无其他字符）
      else if (i + 5 <= len && line.startsWith("false", i)) {
        boolean isStandalone =
            (i == 0 || isSeparator(line.charAt(i - 1)))
                && // 前面是分隔符或行首
                (i + 5 == len || isSeparator(line.charAt(i + 5))); // 后面是分隔符或行尾

        if (isStandalone) {
          result.append("\"Disabled\"");
          i += 5;
        } else {
          result.append(line.charAt(i));
          i++;
        }
      }
      // 其他情况直接追加字符
      else {
        result.append(line.charAt(i));
        i++;
      }
    }

    line = result.toString();

    // 其他替换规则
    if (isAdditional) {
      line =
          line.replace("\"targetPlanet\": \"All\"", "\"targetPlanet\": \"Both\"")
              .replace("null", "0")
              .replace("Unscaled", "FitToScreen")
              .replace("\"lockRot\": \"Disabled\"", "\"lockRot\": \"Enabled\"");
    } else {
      line = line.replace("\"targetPlanet\": \"All\"", "\"targetPlanet\": \"Both\"");
    }

    return line;
  }

  // 判断字符是否是分隔符（允许 true/false 前后出现的字符）
  private boolean isSeparator(char c) {
    return c == '{' || c == '}' || c == '[' || c == ']' || c == ',' || c == ':' || c == ' '
        || c == '\t' || c == '\n' || c == '\r';
  }

  private String[] events = {
    "ScaleMargin",
    "ScaleRadius",
    "ScalePlanets",
    "Multitap",
    "Checkpoint",
    "RepeatEvents",
    "PlaySound"
  };

  public String checkAndModifyEventIntegers(String line) {
    for (String event : events) {
      if (line.contains("\"eventType\": \"" + event + "\"")) {
        line = checkAndModifyNumbers(line);
        break;
      }
    }
    return line;
  }

  private String checkAndModifyNumbers(String line) {
    line = modifyGeneralEvents(line);
    return line;
  }

  private String modifyGeneralEvents(String line) {
    // 先检查是否包含需要处理的事件类型
    boolean needsProcessing = false;
    for (String event : events) {
      if (line.contains("\"eventType\": \"" + event + "\"")) {
        needsProcessing = true;
        break;
      }
    }
    if (!needsProcessing) return line;

    // 使用正则表达式匹配所有数值字段（包括科学计数法）
    Pattern pattern = Pattern.compile("(:\\s*)(-?\\d+\\.?\\d*([eE][+-]?\\d+)?)");
    Matcher matcher = pattern.matcher(line);
    StringBuffer sb = new StringBuffer();

    while (matcher.find()) {
      String fullMatch = matcher.group(2);
      try {
        BigDecimal number = new BigDecimal(fullMatch);

        // 处理int32范围限制
        if (number.compareTo(MAX_INT32) > 0) {
          number = MAX_INT32;
        } else if (number.compareTo(MIN_INT32) < 0) {
          number = MIN_INT32;
        }

        // 构造替换字符串（保留整数部分）
        String replacement = number.toBigInteger().toString();
        matcher.appendReplacement(sb, matcher.group(1) + replacement);
      } catch (NumberFormatException e) {
        // 非数字内容保留原始值
        matcher.appendReplacement(sb, matcher.group(0));
      }
    }
    matcher.appendTail(sb);

    return sb.toString();
  }

  // 类常量定义
  private static final BigDecimal MAX_INT32 = new BigDecimal(Integer.MAX_VALUE);
  private static final BigDecimal MIN_INT32 = new BigDecimal(Integer.MIN_VALUE);

  private void updateModificationOptionsVisibility() {

    if (selectedVersion == 1) { // 如果选择的版本为2.8
      modifyVersionOnly.setVisibility(View.GONE);
      normalmodify.setVisibility(View.GONE);
      additionalmodifications.setVisibility(View.GONE);
    } else { // 如果选择的版本为2.4
      modifyVersionOnly.setVisibility(View.VISIBLE);
      normalmodify.setVisibility(View.VISIBLE);
      additionalmodifications.setVisibility(View.VISIBLE);
    }
  }

  private void saveFile(File originalFile, String fileContent) {

    File defaultDir = originalFile.getParentFile();
    String versionSuffix = selectedVersion == 0 ? "_2.4" : "_2.8";
    final File saveFile =
        new File(
            defaultDir, originalFile.getName().replace(".adofai", versionSuffix + "_old.adofai"));
    try {
      Writer writer = new FileWriter(saveFile);
      writer.write(fileContent);
      writer.close();

      // 文件保存成功后显示弹窗消息
      String message = "文件已保存在：" + saveFile.getAbsolutePath();
      showMessageDialog(message);
      displayFiles(currentDirectory);

    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private static void extractFolderFiles(ZipInputStream zipIn, File folder, String basePath)
      throws IOException {
    File[] files = folder.listFiles();
    if (files == null) {
      return;
    }
    for (File file : files) {
      if (file.isDirectory()) {
        extractFolderFiles(zipIn, file, basePath);
      } else {
        String filePath = basePath + File.separator + file.getName();
        extractFile(zipIn, filePath);
      }
    }
  }

  private boolean hasShownPermissionDialog = false;

  private void showPermissionPromptDialog() {
    new AlertDialog.Builder(this)
        .setTitle("需要权限")
        .setMessage("此应用需要访问所有文件的权限，请前往设置页面开启此权限。")
        .setPositiveButton(
            "前往设置",
            (dialog, which) -> {
              Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
              intent.setData(Uri.fromParts("package", getPackageName(), null));
              startActivity(intent);
            })
        .setNegativeButton(
            "取消",
            (dialog, which) -> {
              dialog.dismiss();
              Toast.makeText(MainActivity.this, "未开启权限，应用将退出！", Toast.LENGTH_SHORT).show();
              finishAffinity();
            })
        .setCancelable(false)
        .show();
  }

  private void checkAllFilesAccessPermission() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      if (!Environment.isExternalStorageManager()) {
        showPermissionPromptDialog();
      } else {
        Toast.makeText(this, "所有文件访问权限已开启！", Toast.LENGTH_SHORT).show();
      }
    } else {
      if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
          != PackageManager.PERMISSION_GRANTED) {
        ActivityCompat.requestPermissions(
            this,
            new String[] {Manifest.permission.READ_EXTERNAL_STORAGE},
            REQUEST_LEGACY_STORAGE_PERMISSION);
      } else {
        Toast.makeText(this, "存储权限已开启！", Toast.LENGTH_SHORT).show();
      }
    }
  }

  @Override
  protected void onResume() {
    super.onResume();
    Toast.makeText(
            getApplication(), "更新日期：2025.6.8\n作者：HitMargin | QQ：2228293026", Toast.LENGTH_SHORT)
        .show();
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      if (Environment.isExternalStorageManager()) {
        displayFiles(Environment.getExternalStorageDirectory());
      }
    } else {
      displayFiles(Environment.getExternalStorageDirectory());
    }
  }
}
