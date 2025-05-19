package com.example.lab_5;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Toast;
import java.util.ArrayList;
import android.net.Uri;
import android.content.Intent;
import android.provider.DocumentsContract;
import android.database.Cursor;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private ActivityResultLauncher<String> requestPermissionLauncher;

    // Додаємо список для URI вибраних папок
    private ArrayList<Uri> selectedFolders = new ArrayList<>();
    // Лаунчер для вибору папки
    private ActivityResultLauncher<Intent> folderPickerLauncher;

    private ArrayList<Uri> imagesList = new ArrayList<>();

    private int currentImageIndex = 0;

    private String currentFilter = "ALL"; // Значення: ALL, JPEG, PNG
    private ArrayList<Uri> allImagesList = new ArrayList<>(); // Список усіх зображень


    private GestureDetector gestureDetector;

    private boolean isSlideshowActive = false;
    private android.os.Handler slideshowHandler = new android.os.Handler();
    private Runnable slideshowRunnable;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);



        requestPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        // Дозвіл надано, можна працювати
                        Toast.makeText(this, "Дозвіл надано", Toast.LENGTH_SHORT).show();
                    } else {
                        // Відмова у дозволі — повідомлення і закрити додаток
                        Toast.makeText(this, "Без дозволу програма не працюватиме", Toast.LENGTH_LONG).show();
                        finish();
                    }
                }
        );

        checkStoragePermission();

        // Ініціалізація лаунчера для вибору папки
        folderPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri treeUri = result.getData().getData();
                        if (treeUri != null) {
                            selectedFolders.add(treeUri);
                            Toast.makeText(this, "Папка додана!", Toast.LENGTH_SHORT).show();

                            imagesList.clear();
                            allImagesList.clear(); // Очищаємо оригінальний список

                            for (Uri folderUri : selectedFolders) {
                                try {
                                    Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                                            folderUri, DocumentsContract.getTreeDocumentId(folderUri)
                                    );
                                    Cursor cursor = getContentResolver().query(
                                            childrenUri,
                                            new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE},
                                            null, null, null
                                    );
                                    if (cursor != null) {
                                        while (cursor.moveToNext()) {
                                            String docId = cursor.getString(0);
                                            String name = cursor.getString(1);
                                            String mime = cursor.getString(2);
                                            if (mime != null && (mime.startsWith("image/") ||
                                                    name.toLowerCase().endsWith(".jpg") ||
                                                    name.toLowerCase().endsWith(".jpeg") ||
                                                    name.toLowerCase().endsWith(".png"))) {
                                                Uri fileUri = DocumentsContract.buildDocumentUriUsingTree(folderUri, docId);
                                                allImagesList.add(fileUri); // Додаємо в оригінальний список
                                                filterImages(); // Оновлюємо список згідно поточного фільтра

                                            }
                                        }
                                        cursor.close();
                                    }
                                } catch (Exception e) {
                                    Toast.makeText(this, "Помилка при читанні папки", Toast.LENGTH_SHORT).show();
                                }
                            }

                            Toast.makeText(this, "Знайдено " + imagesList.size() + " зображень", Toast.LENGTH_SHORT).show();
                            showImageAtIndex(0);
                        }
                    }
                }
        );
        findViewById(R.id.button_select_folder).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            folderPickerLauncher.launch(intent);
        });

        ImageButton buttonPrev = findViewById(R.id.button_prev);
        ImageButton buttonNext = findViewById(R.id.button_next);

        buttonPrev.setOnClickListener(v -> {
            if (isSlideshowActive) stopSlideshow();
            if (!imagesList.isEmpty()) {
                int prevIndex = (currentImageIndex - 1 + imagesList.size()) % imagesList.size();
                showImageAtIndex(prevIndex);
            }
        });

        buttonNext.setOnClickListener(v -> {
            if (isSlideshowActive) stopSlideshow();
            if (!imagesList.isEmpty()) {
                int nextIndex = (currentImageIndex + 1) % imagesList.size();
                showImageAtIndex(nextIndex);
            }
        });

        Button filterAll = findViewById(R.id.filter_all);
        Button filterJpeg = findViewById(R.id.filter_jpeg);
        Button filterPng = findViewById(R.id.filter_png);
        Button filterTiff = findViewById(R.id.filter_tiff);

        filterAll.setOnClickListener(v -> {
            if (isSlideshowActive) stopSlideshow();
            currentFilter = "ALL";
            filterImages();
        });

        filterJpeg.setOnClickListener(v -> {
            if (isSlideshowActive) stopSlideshow();
            currentFilter = "JPEG";
            filterImages();
        });

        filterPng.setOnClickListener(v -> {
            if (isSlideshowActive) stopSlideshow();
            currentFilter = "PNG";
            filterImages();
        });

        filterTiff.setOnClickListener(v -> {
            if (isSlideshowActive) stopSlideshow();
            boolean hasTiff = false;
            for (Uri uri : allImagesList) {
                String path = uri.toString().toLowerCase();
                if (path.endsWith(".tiff") || path.endsWith(".tif")) {
                    hasTiff = true;
                    break;
                }
            }
            if (hasTiff) {
                Toast.makeText(this, "Формат TIFF не підтримується", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Зображень TIFF не знайдено", Toast.LENGTH_SHORT).show();
            }
        });

        Button buttonAuthor = findViewById(R.id.button_author);
        buttonAuthor.setOnClickListener(v -> showAuthorDialog());

        ImageView imageView = findViewById(R.id.image_view);

        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            private static final int SWIPE_THRESHOLD = 100;
            private static final int SWIPE_VELOCITY_THRESHOLD = 100;

            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (isSlideshowActive) stopSlideshow();
                float diffX = e2.getX() - e1.getX();
                float diffY = e2.getY() - e1.getY();
                if (Math.abs(diffX) > Math.abs(diffY)) {
                    if (Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                        if (diffX > 0) {
                            // Свайп вправо — попереднє зображення
                            if (!imagesList.isEmpty()) {
                                int prevIndex = (currentImageIndex - 1 + imagesList.size()) % imagesList.size();
                                showImageAtIndex(prevIndex);
                            }
                        } else {
                            // Свайп вліво — наступне зображення
                            if (!imagesList.isEmpty()) {
                                int nextIndex = (currentImageIndex + 1) % imagesList.size();
                                showImageAtIndex(nextIndex);
                            }
                        }
                        return true;
                    }
                }
                return false;
            }
            @Override
            public void onLongPress(MotionEvent e) {
                showImageInfoDialog();
            }
        });

        imageView.setOnTouchListener((v, event) -> {
            if (isSlideshowActive) stopSlideshow();
            return gestureDetector.onTouchEvent(event);
        });

        Button buttonPlayStop = findViewById(R.id.button_play_stop);
        updatePlayStopUI(); // одразу синхронізує стан інтерфейсу

        buttonPlayStop.setOnClickListener(v -> {
            if (!isSlideshowActive && !imagesList.isEmpty()) {
                startSlideshow();
            } else {
                stopSlideshow();
            }
        });



    }

    private void showImageAtIndex(int index) {
        ImageView imageView = findViewById(R.id.image_view);
        TextView imageCounter = findViewById(R.id.image_counter);


        if (imagesList.isEmpty()) {
            imageView.setImageResource(R.drawable.ic_launcher_foreground);
            imageCounter.setText("0/0");
            Toast.makeText(this, "Зображення не знайдені", Toast.LENGTH_SHORT).show();
            currentImageIndex = 0;
            return;
        }

        if (index < 0 || index >= imagesList.size()) {
            index = 0;
        }
        currentImageIndex = index;

        try {
            imageView.setImageURI(imagesList.get(index));
        } catch (Exception e) {
            imageView.setImageResource(R.drawable.ic_launcher_foreground); // або закоментуй
            Toast.makeText(this, "Не вдалося відкрити зображення", Toast.LENGTH_SHORT).show();
        }
        imageCounter.setText((index + 1) + "/" + imagesList.size());
    }

    private void filterImages() {
        imagesList.clear();
        for (Uri uri : allImagesList) {
            String path = uri.toString().toLowerCase();
            if (currentFilter.equals("ALL")
                    || (currentFilter.equals("JPEG") && (path.endsWith(".jpg") || path.endsWith(".jpeg")))
                    || (currentFilter.equals("PNG") && path.endsWith(".png"))) {
                imagesList.add(uri);
            }
        }
        currentImageIndex = 0;
        showImageAtIndex(currentImageIndex);
        updatePlayStopUI();
    }



    private void checkStoragePermission() {
        String permission = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                ? Manifest.permission.READ_MEDIA_IMAGES
                : Manifest.permission.READ_EXTERNAL_STORAGE;

        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            // Дозвіл вже наданий, нічого не робимо
        } else {
            // Попросити дозвіл
            requestPermissionLauncher.launch(permission);
        }
    }

    private void startSlideshow() {
        if (imagesList.isEmpty()) return;
        isSlideshowActive = true;
        updatePlayStopUI();
        Toast.makeText(this, "Автоперегляд увімкнено", Toast.LENGTH_SHORT).show();

        slideshowRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isSlideshowActive) return;
                int nextIndex = (currentImageIndex + 1) % imagesList.size();
                showImageAtIndex(nextIndex);
                slideshowHandler.postDelayed(this, 2000); // 2 секунд
            }
        };
        slideshowHandler.postDelayed(slideshowRunnable, 2000);
    }

    private void stopSlideshow() {
        isSlideshowActive = false;
        updatePlayStopUI();
        slideshowHandler.removeCallbacks(slideshowRunnable);
        Toast.makeText(this, "Автоперегляд вимкнено", Toast.LENGTH_SHORT).show();
    }

    private void updatePlayStopUI() {
        Button buttonPlayStop = findViewById(R.id.button_play_stop);
        if (isSlideshowActive) {
            buttonPlayStop.setText("Stop"); // можна додати іконку ⏸️
        } else {
            buttonPlayStop.setText("Play"); // можна додати іконку ▶️
        }
        buttonPlayStop.setEnabled(!imagesList.isEmpty());
    }

    private void showAuthorDialog() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        android.view.LayoutInflater inflater = getLayoutInflater();
        android.view.View dialogView = inflater.inflate(R.layout.dialog_author, null);

        builder.setView(dialogView)
                .setTitle("Про автора")
                .setPositiveButton("OK", (dialog, which) -> dialog.dismiss());

        // Можна додати тут налаштування, якщо потрібно (наприклад, змінити текст динамічно)
        android.app.AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void showImageInfoDialog() {
        if (imagesList.isEmpty()) {
            Toast.makeText(this, "Немає інформації про зображення", Toast.LENGTH_SHORT).show();
            return;
        }
        Uri imageUri = imagesList.get(currentImageIndex);

        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        android.view.LayoutInflater inflater = getLayoutInflater();
        android.view.View dialogView = inflater.inflate(R.layout.dialog_image_info, null);

        // Пошук елементів
        TextView filenameView = dialogView.findViewById(R.id.info_filename);
        TextView sizeView = dialogView.findViewById(R.id.info_size);
        TextView dateView = dialogView.findViewById(R.id.info_date);
        TextView resolutionView = dialogView.findViewById(R.id.info_resolution);

        // Заповнення даних
        String filename = "—";
        String size = "—";
        String date = "—";
        String resolution = "—";

        try {
            // Отримуємо ім’я, розмір, дату, роздільну здатність через ContentResolver
            android.database.Cursor cursor = getContentResolver().query(
                    imageUri, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                int sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE);

                if (nameIndex >= 0)
                    filename = cursor.getString(nameIndex);
                if (sizeIndex >= 0) {
                    long bytes = cursor.getLong(sizeIndex);
                    size = String.format("%.2f МБ", bytes / (1024.0 * 1024.0));
                }

                // Опціонально: отримаємо дату
                int dateIndex = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.DATE_MODIFIED);
                if (dateIndex >= 0) {
                    long timestamp = cursor.getLong(dateIndex) * 1000L;
                    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault());
                    date = sdf.format(new java.util.Date(timestamp));
                }
                cursor.close();
            }

            // Розмір у пікселях через BitmapFactory
            android.graphics.BitmapFactory.Options options = new android.graphics.BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            android.content.res.AssetFileDescriptor fd = getContentResolver().openAssetFileDescriptor(imageUri, "r");
            if (fd != null) {
                android.graphics.BitmapFactory.decodeFileDescriptor(fd.getFileDescriptor(), null, options);
                resolution = options.outWidth + " x " + options.outHeight;
                fd.close();
            }
        } catch (Exception e) {
            // Якщо не вдалося — залишаємо “—”
        }

        filenameView.setText("Файл: " + filename);
        sizeView.setText("Розмір: " + size);
        dateView.setText("Дата: " + date);
        resolutionView.setText("Розмір (px): " + resolution);

        builder.setView(dialogView)
                .setTitle("Інформація про зображення")
                .setPositiveButton("OK", (dialog, which) -> dialog.dismiss());
        builder.create().show();
    }


}
