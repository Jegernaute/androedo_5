package com.example.lab_5;

// Імпорти стандартних Android-класів для роботи з UI, файловою системою, жестами, дозволами тощо
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

// Імпорти для підтримки роботи з ActivityResult API (робота з дозволами, вибір файлів)
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

/**
 * Головна активність програми — переглядач зображень із вибраних користувачем папок.
 */
public class MainActivity extends AppCompatActivity {

    // Лаунчер для запиту дозволу на читання зовнішнього сховища (runtime permission)
    private ActivityResultLauncher<String> requestPermissionLauncher;

    // Список URI вибраних користувачем папок
    private ArrayList<Uri> selectedFolders = new ArrayList<>();

    // Лаунчер для вибору папки через файловий менеджер
    private ActivityResultLauncher<Intent> folderPickerLauncher;

    // Список відфільтрованих (поточно видимих) зображень
    private ArrayList<Uri> imagesList = new ArrayList<>();

    // Індекс поточного зображення у imagesList
    private int currentImageIndex = 0;

    // Активний фільтр (ALL, JPEG, PNG)
    private String currentFilter = "ALL";

    // Список усіх знайдених зображень у вибраних папках (без фільтрації)
    private ArrayList<Uri> allImagesList = new ArrayList<>();

    // Детектор жестів для обробки свайпів і довгих натискань
    private GestureDetector gestureDetector;

    // Чи активний режим автоперегляду (слайдшоу)
    private boolean isSlideshowActive = false;
    // Handler для запуску слайдшоу з затримкою
    private android.os.Handler slideshowHandler = new android.os.Handler();
    // Об’єкт, який виконує зміну зображень у слайдшоу
    private Runnable slideshowRunnable;

    /**
     * Точка входу при створенні активності. Тут відбувається вся ініціалізація UI і логіки.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Реєструємо лаунчер для запиту дозволу на читання файлів
        requestPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        // Дозвіл надано — можна працювати з файлами
                        Toast.makeText(this, "Дозвіл надано", Toast.LENGTH_SHORT).show();
                    } else {
                        // Дозвіл не надано — попереджаємо користувача і закриваємо додаток
                        Toast.makeText(this, "Без дозволу програма не працюватиме", Toast.LENGTH_LONG).show();
                        finish();
                    }
                }
        );

        // Перевіряємо наявність дозволу і, якщо треба — запитуємо його
        checkStoragePermission();

        // Реєструємо лаунчер для вибору папки
        folderPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    // Якщо вибір пройшов успішно та є дані
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri treeUri = result.getData().getData();
                        if (treeUri != null) {
                            // Додаємо нову папку до списку вибраних
                            selectedFolders.add(treeUri);
                            Toast.makeText(this, "Папка додана!", Toast.LENGTH_SHORT).show();

                            // Очищаємо попередні результати
                            imagesList.clear();
                            allImagesList.clear();

                            // Для кожної вибраної папки шукаємо всі зображення
                            for (Uri folderUri : selectedFolders) {
                                try {
                                    // Отримуємо дочірні документи (файли) у цій папці
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
                                            // Перевіряємо, що це зображення (по MIME або розширенню)
                                            if (mime != null && (mime.startsWith("image/") ||
                                                    name.toLowerCase().endsWith(".jpg") ||
                                                    name.toLowerCase().endsWith(".jpeg") ||
                                                    name.toLowerCase().endsWith(".png"))) {
                                                Uri fileUri = DocumentsContract.buildDocumentUriUsingTree(folderUri, docId);
                                                allImagesList.add(fileUri); // Додаємо до повного списку
                                                filterImages(); // Оновлюємо поточний список зображень згідно фільтра
                                            }
                                        }
                                        cursor.close();
                                    }
                                } catch (Exception e) {
                                    Toast.makeText(this, "Помилка при читанні папки", Toast.LENGTH_SHORT).show();
                                }
                            }

                            // Показуємо користувачу кількість знайдених зображень
                            Toast.makeText(this, "Знайдено " + imagesList.size() + " зображень", Toast.LENGTH_SHORT).show();
                            showImageAtIndex(0); // Відображаємо перше зображення
                        }
                    }
                }
        );

        // Кнопка для вибору нової папки через файловий менеджер
        findViewById(R.id.button_select_folder).setOnClickListener(v -> {
            // Створюємо намір для вибору папки (ACTION_OPEN_DOCUMENT_TREE)
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            // Додаємо права на читання та збереження доступу до папки
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            folderPickerLauncher.launch(intent); // Запускаємо діалог вибору папки
        });

// Кнопки для переходу до попереднього та наступного зображення
        ImageButton buttonPrev = findViewById(R.id.button_prev);
        ImageButton buttonNext = findViewById(R.id.button_next);

// Обробник натискання кнопки "Назад"
        buttonPrev.setOnClickListener(v -> {
            if (isSlideshowActive) stopSlideshow(); // Зупиняємо автоперегляд, якщо він активний
            if (!imagesList.isEmpty()) {
                // Переходимо до попереднього зображення з урахуванням циклічності
                int prevIndex = (currentImageIndex - 1 + imagesList.size()) % imagesList.size();
                showImageAtIndex(prevIndex);
            }
        });

// Обробник натискання кнопки "Вперед"
        buttonNext.setOnClickListener(v -> {
            if (isSlideshowActive) stopSlideshow();
            if (!imagesList.isEmpty()) {
                int nextIndex = (currentImageIndex + 1) % imagesList.size();
                showImageAtIndex(nextIndex);
            }
        });

// Кнопки для фільтрації зображень за форматом
        Button filterAll = findViewById(R.id.filter_all);
        Button filterJpeg = findViewById(R.id.filter_jpeg);
        Button filterPng = findViewById(R.id.filter_png);
        Button filterTiff = findViewById(R.id.filter_tiff);

// Фільтр: показати всі зображення (ALL)
        filterAll.setOnClickListener(v -> {
            if (isSlideshowActive) stopSlideshow();
            currentFilter = "ALL";
            filterImages(); // Оновлюємо список
        });

// Фільтр: показати тільки JPEG
        filterJpeg.setOnClickListener(v -> {
            if (isSlideshowActive) stopSlideshow();
            currentFilter = "JPEG";
            filterImages();
        });

// Фільтр: показати тільки PNG
        filterPng.setOnClickListener(v -> {
            if (isSlideshowActive) stopSlideshow();
            currentFilter = "PNG";
            filterImages();
        });

// Фільтр: TIFF (демонстраційна обробка — підтримка не реалізована)
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

// Кнопка "Про автора" — відкриває інформаційний діалог
        Button buttonAuthor = findViewById(R.id.button_author);
        buttonAuthor.setOnClickListener(v -> showAuthorDialog());

// Основне зображення для перегляду
        ImageView imageView = findViewById(R.id.image_view);

// Налаштування жестів: свайп вліво/вправо, довге натискання
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            private static final int SWIPE_THRESHOLD = 100;
            private static final int SWIPE_VELOCITY_THRESHOLD = 100;

            @Override
            public boolean onDown(MotionEvent e) {
                return true; // Необхідно для коректної роботи жестів
            }

            // Обробка швидкого свайпу (fling) для переходу між зображеннями
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (isSlideshowActive) stopSlideshow();
                float diffX = e2.getX() - e1.getX();
                float diffY = e2.getY() - e1.getY();
                if (Math.abs(diffX) > Math.abs(diffY)) { // Аналізуємо напрямок: горизонтальний свайп
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

            // Довге натискання — показати інформацію про зображення
            @Override
            public void onLongPress(MotionEvent e) {
                showImageInfoDialog();
            }
        });


        // Додаємо обробку дотиків до imageView: свайп або довге натискання
        imageView.setOnTouchListener((v, event) -> {
            if (isSlideshowActive) stopSlideshow(); // Зупиняємо автоперегляд при ручному жесті
            return gestureDetector.onTouchEvent(event); // Передаємо подію детектору жестів
        });

// Кнопка Play/Stop для автоперегляду (слайдшоу)
        Button buttonPlayStop = findViewById(R.id.button_play_stop);
        updatePlayStopUI(); // Одразу оновлюємо інтерфейс відповідно до стану (play/stop)

// Обробник натискання кнопки Play/Stop
        buttonPlayStop.setOnClickListener(v -> {
            if (!isSlideshowActive && !imagesList.isEmpty()) {
                startSlideshow(); // Якщо слайдшоу не активне і є зображення — стартуємо
            } else {
                stopSlideshow(); // Інакше — зупиняємо слайдшоу
            }
        });
}
// Кінець onCreate()

/**
 * Відобразити зображення за індексом у списку, оновити лічильник
 */
        private void showImageAtIndex(int index) {
            ImageView imageView = findViewById(R.id.image_view);
            TextView imageCounter = findViewById(R.id.image_counter);

            // Якщо список порожній — показуємо стандартну картинку та повідомлення
            if (imagesList.isEmpty()) {
                imageView.setImageResource(R.drawable.ic_launcher_foreground);
                imageCounter.setText("0/0");
                Toast.makeText(this, "Зображення не знайдені", Toast.LENGTH_SHORT).show();
                currentImageIndex = 0;
                return;
            }

            // Коректуємо індекс, якщо він виходить за межі
            if (index < 0 || index >= imagesList.size()) {
                index = 0;
            }
            currentImageIndex = index;

            // Пробуємо відобразити зображення по URI, якщо не виходить — дефолтне зображення
            try {
                imageView.setImageURI(imagesList.get(index));
            } catch (Exception e) {
                imageView.setImageResource(R.drawable.ic_launcher_foreground);
                Toast.makeText(this, "Не вдалося відкрити зображення", Toast.LENGTH_SHORT).show();
            }
            imageCounter.setText((index + 1) + "/" + imagesList.size()); // Оновлюємо лічильник
        }

/**
 * Фільтрація зображень згідно активного фільтра
 */
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
            updatePlayStopUI(); // Оновлюємо стан кнопки Play/Stop
        }

/**
 * Перевіряємо дозвіл на читання зовнішнього сховища (враховуємо версію Android)
 */
        private void checkStoragePermission() {
            String permission = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                    ? Manifest.permission.READ_MEDIA_IMAGES
                    : Manifest.permission.READ_EXTERNAL_STORAGE;

            if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
                // Дозвіл вже є — нічого не робимо
            } else {
                // Запитуємо дозвіл
                requestPermissionLauncher.launch(permission);
            }
        }

/**
 * Запуск автоперегляду (слайдшоу): через кожні 2 секунди змінюємо зображення
 */
        private void startSlideshow() {
            if (imagesList.isEmpty()) return; // Якщо зображень немає — нічого не робимо
            isSlideshowActive = true;
            updatePlayStopUI();
            Toast.makeText(this, "Автоперегляд увімкнено", Toast.LENGTH_SHORT).show();

            slideshowRunnable = new Runnable() {
                @Override
                public void run() {
                    if (!isSlideshowActive) return;
                    int nextIndex = (currentImageIndex + 1) % imagesList.size();
                    showImageAtIndex(nextIndex);
                    slideshowHandler.postDelayed(this, 2000); // Наступне зображення через 2 сек
                }
            };
            slideshowHandler.postDelayed(slideshowRunnable, 2000);
        }

/**
 * Зупинка автоперегляду (слайдшоу)
 */
        private void stopSlideshow() {
            isSlideshowActive = false;
            updatePlayStopUI();
            slideshowHandler.removeCallbacks(slideshowRunnable);
            Toast.makeText(this, "Автоперегляд вимкнено", Toast.LENGTH_SHORT).show();
        }


    /**
     * Оновлює вигляд і доступність кнопки Play/Stop відповідно до стану слайдшоу та наявності зображень
     */
    private void updatePlayStopUI() {
        Button buttonPlayStop = findViewById(R.id.button_play_stop);
        if (isSlideshowActive) {
            buttonPlayStop.setText("Stop"); // Можна додати іконку «Пауза»
        } else {
            buttonPlayStop.setText("Play"); // Можна додати іконку «Старт»
        }
        // Доступна тільки якщо є що показувати
        buttonPlayStop.setEnabled(!imagesList.isEmpty());
    }

    /**
     * Відображає діалогове вікно "Про автора"
     */
    private void showAuthorDialog() {
        // Створюємо вікно через AlertDialog
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        android.view.LayoutInflater inflater = getLayoutInflater();
        android.view.View dialogView = inflater.inflate(R.layout.dialog_author, null);

        builder.setView(dialogView)
                .setTitle("Про автора")
                .setPositiveButton("OK", (dialog, which) -> dialog.dismiss());

        // Якщо потрібно, тут можна налаштувати текст у діалозі динамічно
        android.app.AlertDialog dialog = builder.create();
        dialog.show();
    }

    /**
     * Відображає інформацію про поточне зображення у діалоговому вікні:
     * - Ім'я файлу
     * - Розмір файлу
     * - Дата модифікації
     * - Роздільна здатність у пікселях
     */
    private void showImageInfoDialog() {
        // Якщо нема зображень — попереджаємо користувача
        if (imagesList.isEmpty()) {
            Toast.makeText(this, "Немає інформації про зображення", Toast.LENGTH_SHORT).show();
            return;
        }
        // Отримуємо поточний URI зображення
        Uri imageUri = imagesList.get(currentImageIndex);

        // Створюємо діалог через AlertDialog
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        android.view.LayoutInflater inflater = getLayoutInflater();
        android.view.View dialogView = inflater.inflate(R.layout.dialog_image_info, null);

        // Пошук елементів у діалозі
        TextView filenameView = dialogView.findViewById(R.id.info_filename);
        TextView sizeView = dialogView.findViewById(R.id.info_size);
        TextView dateView = dialogView.findViewById(R.id.info_date);
        TextView resolutionView = dialogView.findViewById(R.id.info_resolution);

        // Стартові значення по замовчуванню
        String filename = "—";
        String size = "—";
        String date = "—";
        String resolution = "—";

        try {
            // Отримуємо ім’я, розмір, дату через ContentResolver
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

                // Опціонально: дата зміни файлу
                int dateIndex = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.DATE_MODIFIED);
                if (dateIndex >= 0) {
                    long timestamp = cursor.getLong(dateIndex) * 1000L;
                    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault());
                    date = sdf.format(new java.util.Date(timestamp));
                }
                cursor.close();
            }

            // Витягуємо роздільну здатність зображення без завантаження у пам'ять
            android.graphics.BitmapFactory.Options options = new android.graphics.BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            android.content.res.AssetFileDescriptor fd = getContentResolver().openAssetFileDescriptor(imageUri, "r");
            if (fd != null) {
                android.graphics.BitmapFactory.decodeFileDescriptor(fd.getFileDescriptor(), null, options);
                resolution = options.outWidth + " x " + options.outHeight;
                fd.close();
            }
        } catch (Exception e) {
            // Якщо щось пішло не так — залишаємо дефолтні значення
        }

        // Заповнюємо елементи у діалозі отриманими даними
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
