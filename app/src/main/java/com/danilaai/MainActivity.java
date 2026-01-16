package com.danilaai;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    
    private EditText inputText;
    private TextView outputText;
    private ImageView resultImage;
    private Button sendButton, generateImageButton, clearButton;
    private ProgressBar progressBar, imageProgressBar;
    private RadioGroup modeGroup;
    private RadioButton textModeBtn, codeModeBtn, imageModeBtn, hackModeBtn;
    
    private ExecutorService executor;
    private Handler mainHandler;
    private StringBuilder chatHistory;
    
    private enum Mode { TEXT, CODE, IMAGE, HACK }
    private Mode currentMode = Mode.TEXT;
    
    static {
        System.loadLibrary("danilka");
    }
    
    public native String generateText(String prompt, int mode);
    public native byte[] generateImage(String prompt);
    public native void initModel(String modelPath);
    public native void freeModel();
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        initViews();
        setupUI();
        copyModelToStorage();
    }
    
    private void initViews() {
        inputText = findViewById(R.id.input_text);
        outputText = findViewById(R.id.output_text);
        resultImage = findViewById(R.id.result_image);
        sendButton = findViewById(R.id.send_button);
        generateImageButton = findViewById(R.id.generate_image_button);
        clearButton = findViewById(R.id.clear_button);
        progressBar = findViewById(R.id.progress_bar);
        imageProgressBar = findViewById(R.id.image_progress_bar);
        modeGroup = findViewById(R.id.mode_group);
        textModeBtn = findViewById(R.id.mode_text);
        codeModeBtn = findViewById(R.id.mode_code);
        imageModeBtn = findViewById(R.id.mode_image);
        hackModeBtn = findViewById(R.id.mode_hack);
    }
    
    private void setupUI() {
        chatHistory = new StringBuilder();
        executor = Executors.newFixedThreadPool(2);
        mainHandler = new Handler(Looper.getMainLooper());
        
        sendButton.setOnClickListener(v -> processRequest());
        generateImageButton.setOnClickListener(v -> generateImageRequest());
        clearButton.setOnClickListener(v -> clearChat());
        
        modeGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.mode_text) currentMode = Mode.TEXT;
            else if (checkedId == R.id.mode_code) currentMode = Mode.CODE;
            else if (checkedId == R.id.mode_image) currentMode = Mode.IMAGE;
            else if (checkedId == R.id.mode_hack) currentMode = Mode.HACK;
            updateModeUI();
        });
    }
    
    private void updateModeUI() {
        String modeName = "";
        switch (currentMode) {
            case TEXT: modeName = "ТЕКСТ"; break;
            case CODE: modeName = "КОД"; break;
            case IMAGE: modeName = "ИЗОБРАЖЕНИЯ"; break;
            case HACK: modeName = "ХАКИНГ"; break;
        }
        addToChat("[SYSTEM]", "Режим: " + modeName);
        
        if (currentMode == Mode.IMAGE) {
            generateImageButton.setVisibility(View.VISIBLE);
            sendButton.setVisibility(View.GONE);
        } else {
            generateImageButton.setVisibility(View.GONE);
            sendButton.setVisibility(View.VISIBLE);
        }
    }
    
    private void copyModelToStorage() {
        executor.execute(() -> {
            try {
                File modelDir = new File(getFilesDir(), "models");
                if (!modelDir.exists()) modelDir.mkdirs();
                
                String modelPath = new File(modelDir, "danilkaai.gguf").getAbsolutePath();
                File modelFile = new File(modelPath);
                
                if (!modelFile.exists()) {
                    showProgress("Копирование модели...");
                    InputStream is = getAssets().open("models/danilkaai.gguf");
                    OutputStream os = new FileOutputStream(modelPath);
                    
                    byte[] buffer = new byte[8192];
                    int length;
                    while ((length = is.read(buffer)) > 0) {
                        os.write(buffer, 0, length);
                    }
                    os.close();
                    is.close();
                    
                    mainHandler.post(() -> {
                        hideProgress();
                        initNativeModel(modelPath);
                        addToChat("[SYSTEM]", "═══ DANILKA AI ═══\nГотов к работе!");
                    });
                } else {
                    initNativeModel(modelPath);
                }
            } catch (Exception e) {
                mainHandler.post(() -> {
                    hideProgress();
                    outputText.setText("[ERROR] " + e.getMessage());
                });
            }
        });
    }
    
    private void initNativeModel(String modelPath) {
        executor.execute(() -> {
            initModel(modelPath);
            mainHandler.post(() -> {
                sendButton.setEnabled(true);
                generateImageButton.setEnabled(true);
                addToChat("[SYSTEM]", "Модель загружена");
            });
        });
    }
    
    private void processRequest() {
        String prompt = inputText.getText().toString().trim();
        if (prompt.isEmpty()) {
            Toast.makeText(this, "Введите запрос", Toast.LENGTH_SHORT).show();
            return;
        }
        
        addToChat("[USER]", prompt);
        inputText.setText("");
        
        showProgress("Генерация...");
        sendButton.setEnabled(false);
        
        executor.execute(() -> {
            String response = generateText(prompt, currentMode.ordinal());
            mainHandler.post(() -> {
                hideProgress();
                addToChat("[DANILKA]", response);
                sendButton.setEnabled(true);
                scrollToBottom();
            });
        });
    }
    
    private void generateImageRequest() {
        String prompt = inputText.getText().toString().trim();
        if (prompt.isEmpty()) {
            Toast.makeText(this, "Введите описание", Toast.LENGTH_SHORT).show();
            return;
        }
        
        addToChat("[USER]", "Изображение: " + prompt);
        inputText.setText("");
        
        imageProgressBar.setVisibility(View.VISIBLE);
        generateImageButton.setEnabled(false);
        
        executor.execute(() -> {
            byte[] imageData = generateImage(prompt);
            mainHandler.post(() -> {
                imageProgressBar.setVisibility(View.GONE);
                generateImageButton.setEnabled(true);
                
                if (imageData != null && imageData.length > 0) {
                    Bitmap bitmap = BitmapFactory.decodeByteArray(imageData, 0, imageData.length);
                    resultImage.setImageBitmap(bitmap);
                    addToChat("[DANILKA]", "Изображение готово");
                } else {
                    addToChat("[ERROR]", "Ошибка генерации");
                }
            });
        });
    }
    
    private void clearChat() {
        chatHistory.setLength(0);
        outputText.setText("");
        resultImage.setImageBitmap(null);
        addToChat("[SYSTEM]", "Чат очищен");
    }
    
    private void addToChat(String sender, String message) {
        String time = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        chatHistory.append("[").append(time).append("] ").append(sender).append("\n")
                  .append(message).append("\n\n");
        outputText.setText(chatHistory.toString());
    }
    
    private void scrollToBottom() {
        outputText.post(() -> {
            int scrollAmount = outputText.getLayout().getLineTop(outputText.getLineCount()) - outputText.getHeight();
            if (scrollAmount > 0) outputText.scrollTo(0, scrollAmount);
        });
    }
    
    private void showProgress(String message) {
        progressBar.setVisibility(View.VISIBLE);
        updateProgress(message);
    }
    
    private void hideProgress() {
        progressBar.setVisibility(View.GONE);
    }
    
    private void updateProgress(String message) {
        mainHandler.post(() -> {
            String time = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
            outputText.setText(chatHistory.toString() + "\n[" + time + "] " + message);
        });
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executor != null) {
            executor.execute(this::freeModel);
            executor.shutdown();
        }
    }
                     }
