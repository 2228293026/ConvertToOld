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
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
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
    private boolean lastActionWasCopy = false;
    private Button filterButton;
    private SwipeRefreshLayout swipeRefreshLayout;
    private File file;
    private boolean actionsFound = false; // 标记是否已找到"actions":
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
                    public void onItemSelected(
                            AdapterView<?> parent, View view, int position, long id) {
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
                    public void onItemClick(
                            AdapterView<?> parent, View view, int position, long id) {
                        String item = itemList.get(position); // 获取完整的字符串
                        String fileName = item.split("\n")[0]; // 文件名是第一行
                        File selectedFile = new File(currentDirectory, fileName);

                        if (selectedFile.isDirectory()) {
                            try {
                                directoryStack.push(currentDirectory.getAbsolutePath());
                                displayFiles(selectedFile); // 跳转到新的文件夹
                            } catch (Exception e) {
                                e.printStackTrace();
                                Toast.makeText(
                                                MainActivity.this,
                                                "无法打开文件夹：" + e.getMessage(),
                                                Toast.LENGTH_SHORT)
                                        .show();
                            }
                        } else {
                            if (fileName.toLowerCase().endsWith(".adofai")) {
                                ConvertToOld(selectedFile);
                            } else {
                                Toast.makeText(
                                                MainActivity.this,
                                                "该文件不是.adofai文件",
                                                Toast.LENGTH_SHORT)
                                        .show();
                            }
                        }
                    }
                });

        explain.setOnClickListener(
                new View.OnClickListener() {

                    @Override
                    public void onClick(View view) {
                        String message =
                                "需知：特殊修改：修复移动摄像头错误和背景错误\n只修改版本号是为11\n该工具免费提供！禁止倒卖\n更新内容如下\n修复错误\n(如果出错请在编辑器里重新保存关卡再转换),修复暂停节拍错误,新增版本选择，用的什么版本就选择哪个版本,新增修复自由轨道/n只显示文件夹/.adofai文件，SDK版本调新，所有文件访问权限需要开启";
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
                                Toast.makeText(MainActivity.this, "路径无效或不存在！", Toast.LENGTH_SHORT)
                                        .show();
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
                                    (InputMethodManager)
                                            getSystemService(Context.INPUT_METHOD_SERVICE);
                            if (imm != null) {
                                imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
                            }
                        },
                        100); // 增加延迟时间
    }

    private void displayFiles(File directory) {
        try {
            currentDirectory = directory;
            currentDirectoryTextView.setText("当前目录：" + directory.getAbsolutePath());
            itemList.clear();

            File[] files =
                    directory.listFiles(
                            new FileFilter() {
                                @Override
                                public boolean accept(File file) {
                                    if (file.isDirectory()) {
                                        return true;
                                    }
                                    // 修改这里：添加对.zip文件的判断
                                    String name = file.getName().toLowerCase();
                                    return name.endsWith(".adofai") || name.endsWith(".zip");
                                }
                            });

            if (files != null) {
                // 分别存储文件夹和文件
                List<File> folders = new ArrayList<>();
                List<File> validFiles = new ArrayList<>(); // 修改变量名

                for (File file : files) {
                    if (file.isDirectory()) {
                        folders.add(file);
                    } else {
                        validFiles.add(file); // 包含.adofai和.zip文件
                    }
                }

                // 对文件夹和文件分别排序
                folders.sort((f1, f2) -> f1.getName().compareTo(f2.getName()));
                validFiles.sort((f1, f2) -> f1.getName().compareTo(f2.getName())); // 排序所有有效文件

                // 先添加文件夹，再添加文件
                for (File folder : folders) {
                    itemList.add(folder.getName());
                }
                for (File validFile : validFiles) {
                    itemList.add(validFile.getName());
                }
            }

            adapter.notifyDataSetChanged();
            swipeRefreshLayout.setRefreshing(false);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(MainActivity.this, "无法加载目录：" + e.getMessage(), Toast.LENGTH_SHORT)
                    .show();
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
                if (file.getName().toLowerCase().contains(filter.toLowerCase())) {
                    itemList.add(file.getName());
                }
            }
        }
        adapter.notifyDataSetChanged();
    }

    private void showMessageDialog(String message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(message)
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
    public void onCreateContextMenu(
            ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        if (v.getId() == R.id.listView) {
            AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
            String selectedItem = itemList.get(info.position);
            menu.setHeaderTitle(selectedItem);
            menu.add(0, MENU_COPY, 0, "复制");
            menu.add(0, MENU_MOVE, 0, "移动");
            if (copiedFile != null) menu.add(0, MENU_PASTE, 0, "粘贴");
            menu.add(0, MENU_RENAME, 0, "重命名");
            menu.add(0, MENU_DELETE, 0, "删除");
            File selectedFile = new File(currentDirectory, selectedItem);

            // 检查文件是否为ZIP格式
            if (selectedFile.isFile() && selectedItem.toLowerCase().endsWith(".zip")) {
                menu.setHeaderTitle(selectedItem);
                // 如果是ZIP文件，添加“解压”选项到菜单
                menu.add(0, MENU_UNZIP, 0, "解压");
            }
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterView.AdapterContextMenuInfo info =
                (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        String selectedItem = itemList.get(info.position);
        final File selectedFile = new File(currentDirectory, selectedItem);
        switch (item.getItemId()) {
            case MENU_COPY:
                copiedFile = selectedFile;
                lastActionWasCopy = true;
                Toast.makeText(this, "已选择：" + selectedItem, Toast.LENGTH_SHORT).show();
                return true;
            case MENU_RENAME:
                final EditText renameEditText = new EditText(this);
                renameEditText.setInputType(InputType.TYPE_CLASS_TEXT);

                // 提取文件名和后缀
                String fileName = selectedItem;
                final String fileExtension; // 声明为 final
                int dotIndex = fileName.lastIndexOf('.');
                if (dotIndex > 0) {
                    fileExtension = fileName.substring(dotIndex); // 提取后缀
                    fileName = fileName.substring(0, dotIndex); // 提取文件名部分
                } else {
                    fileExtension = ""; // 如果没有后缀，设置为空字符串
                }

                renameEditText.setText(fileName); // 设置文件名部分作为默认值
                renameEditText.selectAll(); // 选中文件名部分，方便用户修改

                new AlertDialog.Builder(this)
                        .setTitle("重命名")
                        .setMessage("输入新的文件名称")
                        .setView(renameEditText)
                        .setPositiveButton(
                                "确定",
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        String newName = renameEditText.getText().toString();
                                        if (!newName.isEmpty()) {
                                            newName += fileExtension; // 添加后缀
                                            File newFile = new File(currentDirectory, newName);
                                            if (selectedFile.renameTo(newFile)) {
                                                displayFiles(currentDirectory);
                                                showMessageDialog("重命名成功！");
                                            } else {
                                                showMessageDialog("重命名失败！");
                                            }
                                        } else {
                                            showMessageDialog("文件名不能为空！");
                                        }
                                    }
                                })
                        .setNegativeButton("取消", null)
                        .show();
                return true;
            case MENU_DELETE:
                new AlertDialog.Builder(this)
                        .setTitle("确认删除")
                        .setMessage("确定要删除 " + selectedItem + " 吗？")
                        .setPositiveButton(
                                "确定",
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        if (deleteFile(selectedFile)) {
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
            case MENU_MOVE:
                copiedFile = selectedFile;
                lastActionWasCopy = false;
                showMessageDialog("已选择：" + selectedItem + " ！");
                return true;
            case MENU_PASTE:
                if (lastActionWasCopy && copiedFile != null) { // 如果上一次操作是复制
                    File newFile = new File(currentDirectory, copiedFile.getName());
                    try {
                        // 复制文件
                        copyFile(copiedFile, newFile);
                        displayFiles(currentDirectory);
                        showMessageDialog("已复制到：" + currentDirectory.getAbsolutePath() + " ！");
                    } catch (IOException e) {
                        e.printStackTrace();
                        showMessageDialog("无法复制文件！");
                    }
                } else if (!lastActionWasCopy && copiedFile != null) { // 如果上一次操作是移动
                    File newFile = new File(currentDirectory, copiedFile.getName());
                    try {
                        // 移动文件
                        if (copiedFile.renameTo(newFile)) {
                            displayFiles(currentDirectory);
                            showMessageDialog("已移动到：" + currentDirectory.getAbsolutePath() + " ！");
                        } else {
                            showMessageDialog("移动文件失败！");
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        showMessageDialog("移动文件失败！");
                    }
                } else {
                    showMessageDialog("请先选择要移动或复制的文件！");
                }
                return true;
            case MENU_UNZIP:
                // 用户选择“解压”选项
                unZipFile(selectedFile);
                displayFiles(currentDirectory);

                showMessageDialog("已解压到：" + currentDirectory.getAbsolutePath() + " ！");

                return true;
            default:
                return super.onContextItemSelected(item);
        }
    }

    @Override
    public void onBackPressed() {
        if (!directoryStack.isEmpty()) {
            String previousPath = directoryStack.pop();
            displayFiles(new File(previousPath));
        } else {
            super.onBackPressed();
        }
    }

    private void copyFile(File sourceFile, File destFile) throws IOException {
        if (!destFile.exists()) {
            destFile.createNewFile();
        }
        FileChannel source = null;
        FileChannel destination = null;
        try {
            source = new FileInputStream(sourceFile).getChannel();
            destination = new FileOutputStream(destFile).getChannel();
            destination.transferFrom(source, 0, source.size());
        } finally {
            if (source != null) {
                source.close();
            }
            if (destination != null) {
                destination.close();
            }
        }
    }

    private boolean deleteFile(File file) {
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File child : files) {
                    deleteFile(child);
                }
            }
        }
        return file.delete();
    }

    public void ConvertToOld(File file) {
        this.file = file;
        convertFile();
    }

    // 执行文件转换
    private void convertFile() {
        StringBuilder result = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!actionsFound && line.contains("\"actions\":")) {
                    actionsFound = true;
                }
                // 检查是否需要删除当前行
                if (enterDeleteMode(line)) {
                    continue; // 跳过这一行，不添加到结果
                }

                // 应用特殊修改，如果需要
                line = modifyLine(line);

                // 添加到结果
                result.append(line).append("\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        // 保存转换后的内容到新文件
        String fileContent = result.toString();
        saveFile(file, fileContent);
    }

    // 检查行是否包含特殊方法或关键词
    private boolean enterDeleteMode(String line) {
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
    }

    // 修改行内容，如果行包含关键词则返回null
    private String modifyLine(String line) {
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
                                "		\"bgVideo\":",
                                "\"eventTag\":");
        */
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
        /*
        // 处理 RepeatEvents 类型的事件

        if (line.contains("\"eventType\": \"RepeatEvents\"")) {
            line = modifyRepeatEvents(line);
        }

        // 处理 ScalePlanets 类型的事件
        else if (line.contains("\"eventType\": \"ScalePlanets\"")) {
            line = modifyScalePlanets(line);
        }
        // 处理其他事件类型
        else {
            */
        line = modifyGeneralEvents(line);
        // }
        return line;
    }

    private String modifyRepeatEvents(String line) {
        StringBuilder sb1 = new StringBuilder(line);
        try {
            // 查找 repetitions 字段的位置
            int repetitionsStart = line.indexOf("\"repetitions\":");
            if (repetitionsStart != -1) {
                int start = repetitionsStart + "\"repetitions\":".length();
                int end = start;

                // 跳过字段值前的空格
                while (end < line.length() && Character.isWhitespace(line.charAt(end))) {
                    end++;
                }

                // 找到 repetitions 字段的值
                while (end < line.length()
                        && (Character.isDigit(line.charAt(end))
                                || line.charAt(end) == '.'
                                || line.charAt(end) == 'E'
                                || line.charAt(end) == 'e'
                                || line.charAt(end) == '+'
                                || line.charAt(end) == '-')) {
                    end++;
                }

                // 提取 repetitions 字段的值
                String repetitionsValue = line.substring(start, end);

                // 使用 BigDecimal 解析科学计数法
                BigDecimal repetitions = new BigDecimal(repetitionsValue);

                // 限制 repetitions 的值在 int32 范围内
                if (repetitions.compareTo(BigDecimal.valueOf(Integer.MAX_VALUE)) > 0) {
                    repetitions = BigDecimal.valueOf(Integer.MAX_VALUE);
                } else if (repetitions.compareTo(BigDecimal.valueOf(Integer.MIN_VALUE)) < 0) {
                    repetitions = BigDecimal.valueOf(Integer.MIN_VALUE);
                }

                // 替换 repetitions 字段
                sb1.replace(start, end, String.valueOf(repetitions.intValue()));
            }
        } catch (Exception e) {
            System.err.println("Error modifying RepeatEvents: " + line);
            e.printStackTrace();
        }
        return sb1.toString();
    }

    private String modifyScalePlanets(String line) {
        try {
            // 查找 scale 字段的位置
            int scaleStart = line.indexOf("\"scale\":");
            if (scaleStart != -1) {
                int start = scaleStart + "\"scale\":".length(); // 字段值的起始位置
                int end = start;

                // 找到 scale 字段的值的结束位置
                while (end < line.length()
                        && (Character.isDigit(line.charAt(end))
                                || line.charAt(end) == '.'
                                || line.charAt(end) == 'E'
                                || line.charAt(end) == 'e'
                                || line.charAt(end) == '+'
                                || line.charAt(end) == '-')) {
                    end++;
                }

                // 提取 scale 字段的值
                String scaleValue = line.substring(start, end);

                // 尝试解析 scaleValue 为数字
                BigDecimal scale;
                try {
                    scale = new BigDecimal(scaleValue);
                } catch (NumberFormatException e) {
                    System.err.println("Invalid number format for scale: " + scaleValue);
                    return line; // 返回原始字符串，避免崩溃
                }

                // 限制 scale 的值在 int32 范围内
                if (scale.compareTo(BigDecimal.valueOf(Integer.MAX_VALUE)) > 0) {
                    scale = BigDecimal.valueOf(Integer.MAX_VALUE);
                } else if (scale.compareTo(BigDecimal.valueOf(Integer.MIN_VALUE)) < 0) {
                    scale = BigDecimal.valueOf(Integer.MIN_VALUE);
                }

                // 替换 scale 字段的值
                line = line.substring(0, start) + scale.toPlainString() + line.substring(end);
            }
        } catch (Exception e) {
            System.err.println("Error modifying ScalePlanets: " + line);
            e.printStackTrace();
        }
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
                        defaultDir,
                        originalFile.getName().replace(".adofai", versionSuffix + "_old.adofai"));
        try {
            FileWriter writer = new FileWriter(saveFile);
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

    private static void unZipFile(File zipFile) {
        String zipFileName = zipFile.getName();
        String zipFileNameWithoutExtension = zipFileName.substring(0, zipFileName.lastIndexOf('.'));
        String destinationDir = zipFile.getParent() + File.separator + zipFileNameWithoutExtension;

        // 创建与ZIP文件同名的目录
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
                    extractFile(zipIn, filePath); // 提取文件到指定路径
                }

                zipIn.closeEntry();
            }

            String message = "解压完成！";
        } catch (IOException e) {
            e.printStackTrace();
            String message = "解压失败！";
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

    private static void extractFile(ZipInputStream zipIn, String filePath) throws IOException {
        try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(filePath))) {
            byte[] bytesIn = new byte[4096];
            int read;
            while ((read = zipIn.read(bytesIn)) != -1) {
                bos.write(bytesIn, 0, read);
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
                            Intent intent =
                                    new Intent(
                                            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                            intent.setData(Uri.fromParts("package", getPackageName(), null));
                            startActivity(intent);
                        })
                .setNegativeButton(
                        "取消",
                        (dialog, which) -> {
                            dialog.dismiss();
                            Toast.makeText(MainActivity.this, "未开启权限，应用将退出！", Toast.LENGTH_SHORT)
                                    .show();
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
                        getApplication(),
                        "更新日期：2025.3.28\n作者：HitMargin | QQ：2228293026",
                        Toast.LENGTH_SHORT)
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
