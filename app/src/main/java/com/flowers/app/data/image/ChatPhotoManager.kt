package com.flowers.app.data.image

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Результат успішного атомарного збереження фотографії чату на диск (Фаза 1 двофазного протоколу).
 */
data class SavedChatPhoto(
    val file: File,
    val canonicalPath: String,
    val plantId: Long,
    val width: Int,
    val height: Int,
    val fileSizeBytes: Long,
    val mimeType: String = "image/jpeg",
    val createdAt: Long
)

/**
 * Стан доступності раніше збереженої фотографії чату.
 */
sealed class ChatPhotoAvailability {
    /** Файл фізично існує в межах дозволеного каталогу, непорожній та доступний для читання. */
    data class Available(
        val file: File,
        val canonicalPath: String,
        val byteSize: Long
    ) : ChatPhotoAvailability()

    /**
     * Файл відсутній на пристрої (наприклад, після відновлення текстової бази даних із Google Auto Backup,
     * оскільки `filesDir/chat_photos` виключено з `backup_rules.xml`, або через зовнішнє видалення).
     */
    data class Missing(
        val requestedPath: String,
        val reason: String
    ) : ChatPhotoAvailability()

    /** Шлях порушує правила безпеки файлової системи (Path Traversal, symlink, чужа рослина або вихід за межі chat_photos). */
    data class InvalidPath(
        val requestedPath: String,
        val error: ChatPhotoException.SecurityViolation
    ) : ChatPhotoAvailability()
}

/**
 * Підсумковий звіт роботи служби очищення осиротілих файлів (Orphan Cleanup).
 */
data class OrphanCleanupReport(
    val deletedFilesCount: Int = 0,
    val skippedYoungFilesCount: Int = 0,
    val skippedActiveFilesCount: Int = 0,
    val failedDeletionsCount: Int = 0,
    val deletedEmptyDirsCount: Int = 0
)

/**
 * Типізовані помилки менеджера фотографій персонального чату (TASK-056 / PR №44).
 */
sealed class ChatPhotoException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class SecurityViolation(message: String, cause: Throwable? = null) : ChatPhotoException(message, cause)
    class InvalidImage(message: String, cause: Throwable? = null) : ChatPhotoException(message, cause)
    class OutOfMemory(message: String, cause: Throwable? = null) : ChatPhotoException(message, cause)
    class IoFailure(message: String, cause: Throwable? = null) : ChatPhotoException(message, cause)
    class AtomicMoveFailed(message: String, cause: Throwable? = null) : ChatPhotoException(message, cause)
    class FileAlreadyExists(message: String, cause: Throwable? = null) : ChatPhotoException(message, cause)
    class PhotoMissing(message: String, cause: Throwable? = null) : ChatPhotoException(message, cause)
    class PlantNotFound(message: String, cause: Throwable? = null) : ChatPhotoException(message, cause)
    class DatabaseFailure(message: String, cause: Throwable? = null) : ChatPhotoException(message, cause)
}

/**
 * Сервіс безпечного локального зберігання, обробки, перевірки та очищення фотографій
 * персонального ШІ-чату рослини (TASK-056 / PR №44, PHOTO_ARCHITECTURE_PROPOSAL.md v1.2).
 *
 * Гарантії безпеки:
 * 1. Ізольований постійний каталог: `filesDir/chat_photos/plant_<plantId>/`.
 * 2. Жодні операції не виходять за межі `filesDir/chat_photos` і ніколи не зачіпають
 *    фотографії карток рослин (`filesDir/plants`), діагностики чи кореневий `filesDir`.
 * 3. Перевірка Canonical Path (`Path.startsWith`), блокування `..` та символічних посилань (symlinks)
 *    перед будь-яким відкриттям або видаленням файла.
 * 4. Атомарний запис через тимчасовий файл у тій самій директорії з переміщенням `StandardCopyOption.ATOMIC_MOVE`
 *    без перезапису існуючих файлів.
 * 5. Захист активних операцій (`withPhotoInUse`, `inFlightSavingFiles`) та повторне очищення при збоях видалення.
 * 6. Очищення сиріт (Orphan Cleanup) лише для файлів, старших за 24 години, із повторною перевіркою Room перед видаленням.
 */
class ChatPhotoManager(
    private val filesDir: File,
    private val context: Context? = null,
    private val engine: ImageDecoderEngine = AndroidImageDecoderEngine(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val fileDeleter: (File) -> Boolean = { it.delete() },
    private val atomicMover: (Path, Path) -> Unit = Companion::defaultAtomicMove
) {

    constructor(
        context: Context,
        engine: ImageDecoderEngine = AndroidImageDecoderEngine(),
        dispatcher: CoroutineDispatcher = Dispatchers.IO
    ) : this(
        filesDir = context.applicationContext.filesDir,
        context = context.applicationContext,
        engine = engine,
        dispatcher = dispatcher
    )

    companion object {
        const val CHAT_PHOTOS_DIR_NAME = "chat_photos"
        const val PLANT_DIR_PREFIX = "plant_"
        const val ORPHAN_MIN_AGE_MS = 24L * 60L * 60L * 1000L // 24 години

        private val PLANT_DIR_REGEX = Regex("^plant_([1-9][0-9]*)$")

        fun defaultAtomicMove(source: Path, target: Path) {
            if (target.toFile().exists()) {
                throw FileAlreadyExistsException(target.toString())
            }
            try {
                Files.move(source, target, StandardCopyOption.ATOMIC_MOVE)
            } catch (e: UnsupportedOperationException) {
                throw AtomicMoveNotSupportedException(
                    source.toString(),
                    target.toString(),
                    e.message ?: "ATOMIC_MOVE не підтримується файловою системою"
                )
            }
        }
    }

    private val imageProcessor = ImageProcessor(
        context = context,
        outputDirectory = File(filesDir, CHAT_PHOTOS_DIR_NAME),
        engine = engine,
        dispatcher = dispatcher,
        clock = clock,
        fileMover = atomicMover
    )

    /** Лічильники файлів, які зараз активно читаються або передаються в запиті (ключ — canonicalPath). */
    private val activeFileLocks = ConcurrentHashMap<String, AtomicInteger>()

    /** Набір canonicalPath файлів, що перебувають у процесі двофазного збереження (до завершення транзакції Room). */
    private val inFlightSavingFiles = ConcurrentHashMap.newKeySet<String>()

    /** Набір canonicalPath файлів, фізичне видалення яких було відкладено або завершилось помилкою I/O і потребує повтору. */
    private val pendingDeletionPaths = ConcurrentHashMap.newKeySet<String>()

    /** Повертає канонічний шлях кореневого `filesDir`. */
    private fun getCanonicalFilesDir(): Path = filesDir.canonicalFile.toPath()

    /**
     * Повертає та перевіряє канонічний шлях каталогу `filesDir/chat_photos`.
     * Забороняє символічні посилання та вихід за межі `filesDir`.
     */
    fun getValidatedChatPhotosRootDir(createIfMissing: Boolean = false): File {
        val rawRoot = File(filesDir, CHAT_PHOTOS_DIR_NAME)
        if (rawRoot.exists() && Files.isSymbolicLink(rawRoot.toPath())) {
            throw ChatPhotoException.SecurityViolation(
                "Безпекова помилка: каталог chat_photos є символічним посиланням"
            )
        }
        val canonicalFiles = getCanonicalFilesDir()
        val canonicalRoot = rawRoot.canonicalFile.toPath()
        if (!canonicalRoot.startsWith(canonicalFiles) || canonicalRoot == canonicalFiles) {
            throw ChatPhotoException.SecurityViolation(
                "Безпекова помилка: каталог chat_photos не лежить всередині filesDir"
            )
        }
        val rootFile = canonicalRoot.toFile()
        if (createIfMissing && !rootFile.exists() && !rootFile.mkdirs() && !rootFile.exists()) {
            throw ChatPhotoException.IoFailure(
                "Не вдалося створити кореневий каталог фотографій чату: ${rootFile.absolutePath}"
            )
        }
        return rootFile
    }

    /**
     * Повертає та перевіряє канонічний каталог конкретної рослини: `filesDir/chat_photos/plant_<plantId>/`.
     */
    fun getValidatedPlantDirectory(plantId: Long, createIfMissing: Boolean = false): File {
        if (plantId <= 0L) {
            throw ChatPhotoException.SecurityViolation(
                "Некоректний ідентифікатор рослини для файлової операції: $plantId"
            )
        }
        val rootFile = getValidatedChatPhotosRootDir(createIfMissing = createIfMissing)
        val canonicalRoot = rootFile.canonicalFile.toPath()
        val rawPlantDir = File(rootFile, "$PLANT_DIR_PREFIX$plantId")

        if (rawPlantDir.exists() && Files.isSymbolicLink(rawPlantDir.toPath())) {
            throw ChatPhotoException.SecurityViolation(
                "Безпекова помилка: каталог рослини plant_$plantId є символічним посиланням"
            )
        }

        val canonicalPlantPath = rawPlantDir.canonicalFile.toPath()
        if (!canonicalPlantPath.startsWith(canonicalRoot) ||
            canonicalPlantPath == canonicalRoot ||
            canonicalPlantPath.parent != canonicalRoot
        ) {
            throw ChatPhotoException.SecurityViolation(
                "Безпекова помилка: шлях каталогу рослини виходить за межі chat_photos: $canonicalPlantPath"
            )
        }

        val plantDirFile = canonicalPlantPath.toFile()
        if (createIfMissing && !plantDirFile.exists() && !plantDirFile.mkdirs() && !plantDirFile.exists()) {
            throw ChatPhotoException.IoFailure(
                "Не вдалося створити каталог фотографій для рослини $plantId: ${plantDirFile.absolutePath}"
            )
        }
        return plantDirFile
    }

    /**
     * Сувора перевірка шляху до файла фотографії перед будь-яким відкриттям або видаленням.
     *
     * Перевіряє:
     * - відсутність порожнього рядка, нульових байтів (`\u0000`) та сегментів `..`;
     * - відсутність символічних посилань (`Files.isSymbolicLink`) на рівні файла та батьківських папок;
     * - належність канонічного шляху до `filesDir/chat_photos/plant_<id>/<file>` через `Path.startsWith()`;
     * - для операцій конкретної рослини (`expectedPlantId != null`) — сувору належність папці `plant_<expectedPlantId>`;
     * - захист від видалення чи відкриття самого `filesDir`, `chat_photos` або папки рослини замість файла.
     */
    fun validateAndResolveFilePath(rawPath: String, expectedPlantId: Long? = null): File {
        val trimmed = rawPath.trim()
        if (trimmed.isEmpty()) {
            throw ChatPhotoException.SecurityViolation("Шлях до файла фотографії не може бути порожнім")
        }
        if (trimmed.contains('\u0000')) {
            throw ChatPhotoException.SecurityViolation("Шлях до файла містить заборонений нульовий байт")
        }

        val segments = trimmed.split('/', '\\')
        if (segments.any { it == ".." || it == "." }) {
            throw ChatPhotoException.SecurityViolation(
                "Виявлено спробу Path Traversal у шляху файла: $rawPath"
            )
        }

        if (expectedPlantId != null && expectedPlantId <= 0L) {
            throw ChatPhotoException.SecurityViolation(
                "Некоректний ідентифікатор рослини при перевірці файла: $expectedPlantId"
            )
        }

        val canonicalFiles = getCanonicalFilesDir()
        val rootFile = getValidatedChatPhotosRootDir(createIfMissing = false)
        val canonicalRoot = rootFile.canonicalFile.toPath()

        val rawFile = File(trimmed)
        if (!rawFile.isAbsolute) {
            throw ChatPhotoException.SecurityViolation(
                "Шлях до файла з бази даних повинен бути повним шляхом у межах chat_photos: $rawPath"
            )
        }

        // Перевірка на символічні посилання для самого файла та його батьківської директорії
        if (Files.isSymbolicLink(rawFile.toPath())) {
            throw ChatPhotoException.SecurityViolation(
                "Безпекова помилка: цільовий файл є символічним посиланням: $rawPath"
            )
        }
        val rawParent = rawFile.parentFile
        if (rawParent != null && Files.isSymbolicLink(rawParent.toPath())) {
            throw ChatPhotoException.SecurityViolation(
                "Безпекова помилка: батьківський каталог файла є символічним посиланням: $rawPath"
            )
        }

        val canonicalTarget = try {
            rawFile.canonicalFile.toPath()
        } catch (e: Exception) {
            throw ChatPhotoException.SecurityViolation(
                "Неможливо визначити канонічний шлях для: $rawPath",
                e
            )
        }

        // Суворий захист кореневих каталогів
        if (canonicalTarget == canonicalFiles || canonicalTarget == canonicalRoot) {
            throw ChatPhotoException.SecurityViolation(
                "Заборонено виконувати файлові операції над кореневим каталогом: $rawPath"
            )
        }

        if (!canonicalTarget.startsWith(canonicalRoot)) {
            throw ChatPhotoException.SecurityViolation(
                "Безпекова помилка: файл знаходиться за межами дозволеного каталогу chat_photos: $rawPath"
            )
        }

        // Перевірка структури: файл повинен лежати безпосередньо в `chat_photos/plant_<id>/<filename>`
        val parentPath = canonicalTarget.parent
        if (parentPath == null || parentPath == canonicalRoot || parentPath.parent != canonicalRoot) {
            throw ChatPhotoException.SecurityViolation(
                "Безпекова помилка: файл має знаходитися безпосередньо в підкаталозі рослини plant_<id>: $rawPath"
            )
        }

        val parentDirName = parentPath.fileName?.toString() ?: ""
        val match = PLANT_DIR_REGEX.matchEntire(parentDirName)
            ?: throw ChatPhotoException.SecurityViolation(
                "Безпекова помилка: некоректна назва каталогу рослини '$parentDirName' для шляху: $rawPath"
            )

        val actualPlantId = match.groupValues[1].toLongOrNull()
            ?: throw ChatPhotoException.SecurityViolation(
                "Безпекова помилка: неможливо визначити ID рослини з каталогу '$parentDirName'"
            )

        if (expectedPlantId != null) {
            val expectedPlantDir = getValidatedPlantDirectory(expectedPlantId, createIfMissing = false)
                .canonicalFile.toPath()
            if (actualPlantId != expectedPlantId ||
                !canonicalTarget.startsWith(expectedPlantDir) ||
                canonicalTarget == expectedPlantDir
            ) {
                throw ChatPhotoException.SecurityViolation(
                    "Безпекова помилка: файл '$rawPath' належить plant_$actualPlantId, а очікувався plant_$expectedPlantId"
                )
            }
        }

        return canonicalTarget.toFile()
    }

    /**
     * Обробляє та атомарно зберігає нове фото для конкретної рослини у `filesDir/chat_photos/plant_<plantId>/`.
     *
     * Використовує профіль [ImageProfile.ChatConsultation] (макс. 1536 px, JPEG 85%, очищення EXIF/GPS,
     * нормалізація орієнтації, обмеження пам'яті через inSampleSize).
     *
     * @param registerInFlight Якщо `true`, атомарно реєструє створений файл у [inFlightSavingFiles]
     *        ще до повернення результату, щоб жодна паралельна операція очищення не видалила його до завершення транзакції Room.
     */
    suspend fun saveChatPhoto(
        plantId: Long,
        source: ImageSource,
        customFileName: String? = null,
        registerInFlight: Boolean = false
    ): Result<SavedChatPhoto> = withContext(dispatcher) {
        val plantDir = try {
            getValidatedPlantDirectory(plantId, createIfMissing = true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: ChatPhotoException) {
            return@withContext Result.failure(e)
        } catch (t: Throwable) {
            return@withContext Result.failure(
                ChatPhotoException.IoFailure("Помилка підготовки каталогу рослини $plantId: ${t.message}", t)
            )
        }

        val processResult = imageProcessor.process(
            source = source,
            profile = ImageProfile.ChatConsultation,
            customFileName = customFileName,
            targetDirectory = plantDir
        )

        processResult.fold(
            onSuccess = { res ->
                try {
                    val validatedFile = validateAndResolveFilePath(res.file.absolutePath, expectedPlantId = plantId)
                    if (!validatedFile.exists() || !validatedFile.isFile || validatedFile.length() <= 0L) {
                        return@withContext Result.failure(
                            ChatPhotoException.IoFailure("Збережений файл фотографії відсутній або порожній")
                        )
                    }
                    val canonicalPath = validatedFile.canonicalPath
                    if (registerInFlight) {
                        inFlightSavingFiles.add(canonicalPath)
                    }
                    Result.success(
                        SavedChatPhoto(
                            file = validatedFile,
                            canonicalPath = canonicalPath,
                            plantId = plantId,
                            width = res.processedDimensions.width,
                            height = res.processedDimensions.height,
                            fileSizeBytes = validatedFile.length(),
                            mimeType = res.mimeType,
                            createdAt = clock()
                        )
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: ChatPhotoException) {
                    Result.failure(e)
                } catch (t: Throwable) {
                    Result.failure(
                        ChatPhotoException.IoFailure("Помилка перевірки збереженого фото: ${t.message}", t)
                    )
                }
            },
            onFailure = { err ->
                Result.failure(mapImageException(err))
            }
        )
    }

    /**
     * Обробляє та атомарно зберігає нове фото з локального файла.
     */
    suspend fun saveChatPhoto(
        plantId: Long,
        sourceFile: File,
        customFileName: String? = null
    ): Result<SavedChatPhoto> {
        return saveChatPhoto(plantId, ImageSource.FromFile(sourceFile), customFileName)
    }

    /**
     * Обробляє та атомарно зберігає нове фото з Android Content URI (галерея або камера).
     */
    suspend fun saveChatPhoto(
        plantId: Long,
        uri: Uri,
        customFileName: String? = null
    ): Result<SavedChatPhoto> {
        val appContext = context
            ?: return Result.failure(
                ChatPhotoException.IoFailure("Context необхідний для читання Android Content URI")
            )
        val source = ImageSource.ContentUri(uri) { targetUri ->
            appContext.contentResolver.openInputStream(targetUri)
        }
        return saveChatPhoto(plantId, source, customFileName)
    }

    /**
     * Перевіряє існування, безпеку шляху та фізичну доступність раніше збереженого фото.
     * Безпечно обробляє відсутні файли після відновлення бази даних із Google Auto Backup.
     */
    fun checkPhotoAvailability(
        filePath: String,
        expectedPlantId: Long? = null
    ): ChatPhotoAvailability {
        val validatedFile = try {
            validateAndResolveFilePath(filePath, expectedPlantId)
        } catch (e: ChatPhotoException.SecurityViolation) {
            return ChatPhotoAvailability.InvalidPath(filePath, e)
        } catch (e: Exception) {
            return ChatPhotoAvailability.InvalidPath(
                filePath,
                ChatPhotoException.SecurityViolation("Некоректний шлях файла: ${e.message}", e)
            )
        }

        if (!validatedFile.exists() || !validatedFile.isFile || !validatedFile.canRead() || validatedFile.length() <= 0L) {
            return ChatPhotoAvailability.Missing(
                requestedPath = filePath,
                reason = "Фото недоступне (файл відсутній на пристрої або не входить до резервної копії)"
            )
        }

        return ChatPhotoAvailability.Available(
            file = validatedFile,
            canonicalPath = validatedFile.canonicalPath,
            byteSize = validatedFile.length()
        )
    }

    /**
     * Повертає раніше збережений файл фотографії після перевірки canonical path та фізичної наявності.
     */
    fun getSavedPhotoFile(
        filePath: String,
        expectedPlantId: Long? = null
    ): Result<File> {
        return when (val availability = checkPhotoAvailability(filePath, expectedPlantId)) {
            is ChatPhotoAvailability.Available -> Result.success(availability.file)
            is ChatPhotoAvailability.Missing -> Result.failure(ChatPhotoException.PhotoMissing(availability.reason))
            is ChatPhotoAvailability.InvalidPath -> Result.failure(availability.error)
        }
    }

    /**
     * Швидка перевірка доступності збереженого фото для читання.
     */
    fun isPhotoAvailable(filePath: String, expectedPlantId: Long? = null): Boolean {
        return checkPhotoAvailability(filePath, expectedPlantId) is ChatPhotoAvailability.Available
    }

    /**
     * Реєструє файл як такий, що перебуває в процесі двофазного збереження (Фаза 1 -> Фаза 2 Room),
     * щоб служба Orphan Cleanup не видалила його до завершення транзакції БД.
     */
    fun markInFlightSaveStart(canonicalPath: String) {
        inFlightSavingFiles.add(canonicalPath)
    }

    /**
     * Знімає позначку двофазного збереження після завершення або відкату транзакції Room.
     */
    fun markInFlightSaveEnd(canonicalPath: String) {
        inFlightSavingFiles.remove(canonicalPath)
    }

    /**
     * Перевіряє, чи використовується фотографія активною операцією прямо зараз.
     */
    fun isPhotoInUse(canonicalPath: String): Boolean {
        return (activeFileLocks[canonicalPath]?.get() ?: 0) > 0 || inFlightSavingFiles.contains(canonicalPath)
    }

    /**
     * Виконує блок коду, гарантуючи, що файл фотографії не буде видалений посеред читання або активного запиту.
     * Якщо під час виконання блоку надійшов запит на видалення цього фото (очищення чату або видалення рослини),
     * файл буде автоматично й безпечно видалений одразу після виходу з [block].
     */
    suspend fun <T> withPhotoInUse(
        filePath: String,
        expectedPlantId: Long? = null,
        block: suspend (File) -> T
    ): T {
        val validatedFile = validateAndResolveFilePath(filePath, expectedPlantId)
        val canonicalPath = validatedFile.canonicalPath

        activeFileLocks.compute(canonicalPath) { _, existing ->
            (existing ?: AtomicInteger(0)).apply { incrementAndGet() }
        }

        try {
            if (!validatedFile.exists() || !validatedFile.isFile || !validatedFile.canRead() || validatedFile.length() <= 0L) {
                throw ChatPhotoException.PhotoMissing(
                    "Фото недоступне (файл відсутній на пристрої або не входить до резервної копії)"
                )
            }
            return block(validatedFile)
        } finally {
            withContext(NonCancellable) {
                var shouldDeleteNow = false
                activeFileLocks.computeIfPresent(canonicalPath) { _, counter ->
                    if (counter.decrementAndGet() <= 0) {
                        shouldDeleteNow = pendingDeletionPaths.contains(canonicalPath)
                        null
                    } else {
                        counter
                    }
                }
                if (shouldDeleteNow) {
                    withContext(dispatcher) {
                        tryDeleteDeferredFile(validatedFile, canonicalPath)
                    }
                }
            }
        }
    }

    /**
     * Безпечно видаляє конкретну фотографію рослини за її шляхом.
     *
     * - Якщо шлях не належить `plant_<plantId>` або порушує правила безпеки — повертає `Result.failure(SecurityViolation)`.
     * - Якщо файл зараз активно використовується в `withPhotoInUse` — відкладає фізичне видалення до завершення читання.
     * - Якщо `fileDeleter` повернув `false` (тимчасове блокування дескриптора ОС або збій I/O) — реєструє файл
     *   у черзі повторного очищення (`pendingDeletionPaths`).
     */
    suspend fun deletePhotoForPlant(
        plantId: Long,
        filePath: String
    ): Result<Boolean> = withContext(dispatcher) {
        val targetFile = try {
            validateAndResolveFilePath(filePath, expectedPlantId = plantId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: ChatPhotoException) {
            return@withContext Result.failure(e)
        } catch (t: Throwable) {
            return@withContext Result.failure(
                ChatPhotoException.SecurityViolation("Невалідний шлях при видаленні фото: ${t.message}", t)
            )
        }

        val canonicalPath = targetFile.canonicalPath
        if (isPhotoInUse(canonicalPath)) {
            pendingDeletionPaths.add(canonicalPath)
            return@withContext Result.success(false)
        }

        if (!targetFile.exists()) {
            pendingDeletionPaths.remove(canonicalPath)
            cleanupEmptyPlantDirectoryIfSafe(targetFile.parentFile)
            return@withContext Result.success(true)
        }

        val deleted = runCatching { fileDeleter(targetFile) }.getOrDefault(false)
        if (deleted || !targetFile.exists()) {
            pendingDeletionPaths.remove(canonicalPath)
            cleanupEmptyPlantDirectoryIfSafe(targetFile.parentFile)
            Result.success(true)
        } else {
            pendingDeletionPaths.add(canonicalPath)
            Result.success(false)
        }
    }

    /**
     * Безпечно очищує всі фотографії чату конкретної рослини (`filesDir/chat_photos/plant_<plantId>/`)
     * та видаляє порожню папку цієї рослини, не зачіпаючи інші рослини чи інші каталоги застосунку.
     */
    suspend fun deleteAllPhotosForPlant(plantId: Long): Result<Int> = withContext(dispatcher) {
        val plantDir = try {
            getValidatedPlantDirectory(plantId, createIfMissing = false)
        } catch (e: CancellationException) {
            throw e
        } catch (e: ChatPhotoException) {
            return@withContext Result.failure(e)
        } catch (t: Throwable) {
            return@withContext Result.failure(
                ChatPhotoException.SecurityViolation("Некоректний каталог рослини $plantId: ${t.message}", t)
            )
        }

        if (!plantDir.exists()) {
            return@withContext Result.success(0)
        }

        var deletedCount = 0
        val files = plantDir.listFiles() ?: emptyArray()
        for (child in files) {
            if (Files.isSymbolicLink(child.toPath())) {
                continue
            }
            if (child.isFile) {
                val validatedChild = runCatching {
                    validateAndResolveFilePath(child.absolutePath, expectedPlantId = plantId)
                }.getOrNull() ?: continue

                val canonicalPath = validatedChild.canonicalPath
                if (isPhotoInUse(canonicalPath)) {
                    pendingDeletionPaths.add(canonicalPath)
                    continue
                }

                val deleted = runCatching { fileDeleter(validatedChild) }.getOrDefault(false)
                if (deleted || !validatedChild.exists()) {
                    pendingDeletionPaths.remove(canonicalPath)
                    deletedCount++
                } else {
                    pendingDeletionPaths.add(canonicalPath)
                }
            }
        }

        cleanupEmptyPlantDirectoryIfSafe(plantDir)
        Result.success(deletedCount)
    }

    /**
     * Повертає кількість шляхів, які очікують повторної спроби фізичного видалення.
     */
    fun getPendingDeletionsCount(): Int = pendingDeletionPaths.size

    /**
     * Виконує повторну спробу видалення файлів, які не вдалося видалити раніше через блокування I/O.
     * Перед видаленням перевіряє, що файл не прив'язаний до актуального запису в Room і не використовується.
     */
    suspend fun retryPendingDeletions(
        fetchStoredPaths: suspend () -> Set<String> = { emptySet() }
    ): Int = withContext(dispatcher) {
        if (pendingDeletionPaths.isEmpty()) return@withContext 0

        val storedCanonical = canonicalizePathSet(fetchStoredPaths())
        var deletedCount = 0

        for (canonicalPath in pendingDeletionPaths.toList()) {
            if (storedCanonical.contains(canonicalPath)) {
                pendingDeletionPaths.remove(canonicalPath)
                continue
            }
            if (isPhotoInUse(canonicalPath)) {
                continue
            }
            val validatedFile = runCatching {
                validateAndResolveFilePath(canonicalPath, expectedPlantId = null)
            }.getOrNull()

            if (validatedFile == null) {
                pendingDeletionPaths.remove(canonicalPath)
                continue
            }

            if (!validatedFile.exists()) {
                pendingDeletionPaths.remove(canonicalPath)
                cleanupEmptyPlantDirectoryIfSafe(validatedFile.parentFile)
                continue
            }

            // Повторна перевірка перед фізичним видаленням
            val recheckedStored = canonicalizePathSet(fetchStoredPaths())
            if (recheckedStored.contains(canonicalPath)) {
                pendingDeletionPaths.remove(canonicalPath)
                continue
            }

            val deleted = runCatching { fileDeleter(validatedFile) }.getOrDefault(false)
            if (deleted || !validatedFile.exists()) {
                pendingDeletionPaths.remove(canonicalPath)
                cleanupEmptyPlantDirectoryIfSafe(validatedFile.parentFile)
                deletedCount++
            }
        }
        deletedCount
    }

    /**
     * Служба очищення осиротілих файлів (Orphan Cleanup):
     * - Сканує ВИКЛЮЧНО `filesDir/chat_photos`.
     * - Знаходить файли без відповідного запису в Room.
     * - Видаляє непідтверджені файли, старші за 24 години ([ORPHAN_MIN_AGE_MS]), або файли з черги [pendingDeletionPaths].
     * - Зберігає файли, молодші за 24 години.
     * - Не видаляє файли активних операцій ([isPhotoInUse] або `isPlantActive(plantId)`).
     * - Перед кожним фізичним видаленням повторно опитує [fetchStoredPaths] для перевірки актуального стану Room.
     * - Виконується на фоновому [dispatcher] (`Dispatchers.IO`), ніколи не блокує головний UI-потік.
     */
    suspend fun cleanupOrphanFiles(
        fetchStoredPaths: suspend () -> Set<String>,
        isPlantActive: (Long) -> Boolean = { false }
    ): OrphanCleanupReport = withContext(dispatcher) {
        val rootDir = try {
            getValidatedChatPhotosRootDir(createIfMissing = false)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return@withContext OrphanCleanupReport()
        }

        if (!rootDir.exists() || !rootDir.isDirectory) {
            return@withContext OrphanCleanupReport()
        }

        val initialStoredCanonical = canonicalizePathSet(fetchStoredPaths())

        var deletedFiles = 0
        var skippedYoung = 0
        var skippedActive = 0
        var failedDeletions = 0
        var deletedEmptyDirs = 0

        val subDirs = rootDir.listFiles() ?: emptyArray()
        for (plantDir in subDirs) {
            if (Files.isSymbolicLink(plantDir.toPath())) {
                continue
            }
            if (!plantDir.isDirectory) {
                continue
            }

            val match = PLANT_DIR_REGEX.matchEntire(plantDir.name) ?: continue
            val plantId = match.groupValues[1].toLongOrNull() ?: continue

            val validatedPlantDir = runCatching {
                getValidatedPlantDirectory(plantId, createIfMissing = false)
            }.getOrNull() ?: continue

            val files = validatedPlantDir.listFiles() ?: emptyArray()
            for (file in files) {
                if (Files.isSymbolicLink(file.toPath()) || !file.isFile) {
                    continue
                }

                val validatedFile = runCatching {
                    validateAndResolveFilePath(file.absolutePath, expectedPlantId = plantId)
                }.getOrNull() ?: continue

                val canonicalPath = validatedFile.canonicalPath

                // 1. Якщо файл присутній у Room — він не є сиротою
                if (initialStoredCanonical.contains(canonicalPath)) {
                    pendingDeletionPaths.remove(canonicalPath)
                    continue
                }

                // 2. Захист активних операцій
                if (isPhotoInUse(canonicalPath) || isPlantActive(plantId)) {
                    skippedActive++
                    continue
                }

                // 3. Перевірка віку файла (файли з pendingDeletionPaths були явно видалені користувачем раніше)
                val isExplicitlyPendingDelete = pendingDeletionPaths.contains(canonicalPath)
                if (!isExplicitlyPendingDelete) {
                    val fileAgeMs = clock() - validatedFile.lastModified()
                    if (fileAgeMs < ORPHAN_MIN_AGE_MS) {
                        skippedYoung++
                        continue
                    }
                }

                // 4. Обов'язкова повторна перевірка актуального стану Room безпосередньо перед видаленням
                val latestStoredCanonical = canonicalizePathSet(fetchStoredPaths())
                if (latestStoredCanonical.contains(canonicalPath)) {
                    pendingDeletionPaths.remove(canonicalPath)
                    continue
                }

                // Повторна перевірка активності перед видаленням
                if (isPhotoInUse(canonicalPath) || isPlantActive(plantId)) {
                    skippedActive++
                    continue
                }

                val deleted = runCatching { fileDeleter(validatedFile) }.getOrDefault(false)
                if (deleted || !validatedFile.exists()) {
                    pendingDeletionPaths.remove(canonicalPath)
                    deletedFiles++
                } else {
                    pendingDeletionPaths.add(canonicalPath)
                    failedDeletions++
                }
            }

            // Видаляємо підкаталог рослини, якщо він порожній і рослина не в активній відправці
            if (!isPlantActive(plantId) && cleanupEmptyPlantDirectoryIfSafe(validatedPlantDir)) {
                deletedEmptyDirs++
            }
        }

        OrphanCleanupReport(
            deletedFilesCount = deletedFiles,
            skippedYoungFilesCount = skippedYoung,
            skippedActiveFilesCount = skippedActive,
            failedDeletionsCount = failedDeletions,
            deletedEmptyDirsCount = deletedEmptyDirs
        )
    }

    private fun tryDeleteDeferredFile(file: File, canonicalPath: String) {
        if (isPhotoInUse(canonicalPath)) return
        if (!file.exists()) {
            pendingDeletionPaths.remove(canonicalPath)
            cleanupEmptyPlantDirectoryIfSafe(file.parentFile)
            return
        }
        val deleted = runCatching { fileDeleter(file) }.getOrDefault(false)
        if (deleted || !file.exists()) {
            pendingDeletionPaths.remove(canonicalPath)
            cleanupEmptyPlantDirectoryIfSafe(file.parentFile)
        }
    }

    /**
     * Видаляє порожню папку `plant_<id>` всередині `chat_photos`, ніколи не чіпаючи `chat_photos` чи `filesDir`.
     */
    private fun cleanupEmptyPlantDirectoryIfSafe(dir: File?): Boolean {
        if (dir == null || !dir.exists() || !dir.isDirectory) return false
        if (Files.isSymbolicLink(dir.toPath())) return false
        val rootDir = runCatching { getValidatedChatPhotosRootDir(createIfMissing = false) }.getOrNull() ?: return false
        val canonicalRoot = rootDir.canonicalFile.toPath()
        val canonicalDir = runCatching { dir.canonicalFile.toPath() }.getOrNull() ?: return false

        if (canonicalDir == canonicalRoot || canonicalDir == getCanonicalFilesDir()) return false
        if (canonicalDir.parent != canonicalRoot) return false
        if (!PLANT_DIR_REGEX.matches(canonicalDir.fileName?.toString() ?: "")) return false

        val hasInFlightInDir = inFlightSavingFiles.any { pathStr ->
            runCatching { File(pathStr).canonicalFile.toPath().startsWith(canonicalDir) }.getOrDefault(false)
        }
        val hasActiveInDir = activeFileLocks.keys.any { pathStr ->
            runCatching { File(pathStr).canonicalFile.toPath().startsWith(canonicalDir) }.getOrDefault(false)
        }
        if (hasInFlightInDir || hasActiveInDir) return false

        val remaining = dir.listFiles()
        if (remaining != null && remaining.isEmpty()) {
            return runCatching { fileDeleter(dir) }.getOrDefault(false)
        }
        return false
    }

    private fun canonicalizePathSet(rawPaths: Set<String>): Set<String> {
        val result = HashSet<String>(rawPaths.size)
        for (raw in rawPaths) {
            val trimmed = raw.trim()
            if (trimmed.isNotEmpty()) {
                result.add(trimmed)
                runCatching { File(trimmed).canonicalPath }.getOrNull()?.let { result.add(it) }
            }
        }
        return result
    }

    private fun mapImageException(error: Throwable): ChatPhotoException {
        return when (error) {
            is ChatPhotoException -> error
            is ImageProcessingException.InvalidFileName -> ChatPhotoException.SecurityViolation(
                error.message ?: "Неприпустиме ім'я файла",
                error
            )
            is ImageProcessingException.InvalidImage -> ChatPhotoException.InvalidImage(
                error.message ?: "Неможливо обробити пошкоджене зображення",
                error
            )
            is ImageProcessingException.UnsupportedFormat -> ChatPhotoException.InvalidImage(
                error.message ?: "Формат зображення не підтримується",
                error
            )
            is ImageProcessingException.OutOfMemory -> ChatPhotoException.OutOfMemory(
                error.message ?: "Недостатньо оперативної пам'яті для обробки фото",
                error
            )
            is ImageProcessingException.AtomicMoveNotSupported -> ChatPhotoException.AtomicMoveFailed(
                error.message ?: "Атомарне переміщення файла недоступне",
                error
            )
            is ImageProcessingException.FileAlreadyExists -> ChatPhotoException.FileAlreadyExists(
                error.message ?: "Цільовий файл фотографії вже існує",
                error
            )
            is ImageProcessingException.IoFailure,
            is ImageProcessingException.SaveFailed -> ChatPhotoException.IoFailure(
                error.message ?: "Помилка запису файла фотографії на диск",
                error
            )
            else -> ChatPhotoException.IoFailure(
                error.message ?: "Невідома помилка під час збереження фотографії",
                error
            )
        }
    }
}
