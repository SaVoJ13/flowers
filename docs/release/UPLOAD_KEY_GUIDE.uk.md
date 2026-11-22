# Upload-ключ і підписаний AAB Flowers (TASK-021-C)

Цю інструкцію виконує Власник. Паролі, файл ключа й `keystore.properties` нікому не надсилати. Google Play App Signing зберігатиме окремий ключ підпису застосунку; локальний upload-ключ потрібен лише для підтвердження завантажень у Play Console.

Офіційні джерела: [Android — Sign your app](https://developer.android.com/studio/publish/app-signing), [Google Play — Use Play App Signing](https://support.google.com/googleplay/android-developer/answer/9842756).

## 1. Створити upload-ключ в Android Studio

1. Відкрийте проєкт Flowers в Android Studio.
2. Виберіть **Build → Generate Signed Bundle / APK**.
3. Виберіть **Android App Bundle**, натисніть **Next**.
4. Біля **Key store path** натисніть **Create new**.
5. Збережіть ключ у приватній папці поза репозиторієм, наприклад `C:\Users\<ваше ім'я>\Documents\Flowers keys\flowers-upload-key.jks`.
6. Самостійно оберіть один сильний пароль і збережіть його в менеджері паролів. У полях пароля сховища та ключа введіть **той самий пароль**: цього прямо вимагає поточна офіційна інструкція Android через відому проблему інструментів підпису. Alias: `flowers-upload`. Алгоритм: RSA, довжина щонайменше 2048 біт. Термін дії: щонайменше 25 років. Поля власника сертифіката заповніть своїми даними.
7. Створіть ключ. Зробіть резервну копію `.jks` на окремому захищеному носії.

## 2. Створити локальний `keystore.properties`

1. У корені проєкту скопіюйте `keystore.properties.example` у новий файл `keystore.properties`.
2. Укажіть абсолютний шлях до `.jks`, свій спільний пароль і alias. Файл читається як UTF-8, тому кирилиця підтримується. У Windows у шляху використовуйте прямі риски `/`, наприклад `C:/Users/Володимир/Documents/Flowers keys/flowers-upload-key.jks`. Якщо пароль містить зворотну риску `\`, у цьому файлі запишіть її двічі: `\\`.
3. Не додавайте цей файл або `.jks` до Git. Вони вже виключені через `.gitignore`.

Формат:

```properties
storeFile=C:/повний/шлях/flowers-upload-key.jks
storePassword=ваш_спільний_пароль
keyAlias=flowers-upload
keyPassword=ваш_спільний_пароль
```

## 3. Зібрати й перевірити AAB

Відкрийте PowerShell у папці проєкту й виконайте:

```powershell
$env:JAVA_HOME = 'C:\Users\cocro\.jdks\jbr-21.0.11'
.\gradlew.bat clean bundleRelease
```

Готовий файл: `app\build\outputs\bundle\release\app-release.aab`.

Перевірка підпису:

```powershell
& "$env:JAVA_HOME\bin\jarsigner.exe" -verify -verbose -certs app\build\outputs\bundle\release\app-release.aab
```

У кінці має бути `jar verified`. Команда не повинна просити пароль.

## 4. Що передати агенту

Повідомте лише: «upload-ключ створено, `keystore.properties` заповнено». Не надсилайте паролі, файл `.jks` або вміст `keystore.properties`. Агент збере AAB, перевірить підпис і дасть наступний один крок для Play Console.

## Важливо

- `versionCode = 1`, `versionName = 1.0.0` уже налаштовані.
- Якщо `keystore.properties` відсутній, Gradle може зібрати непідписаний release AAB для технічної перевірки. Завантажувати його в Play Console не можна.
- Якщо upload-ключ загублено, Play Console дозволяє запросити його скидання, але резервна копія все одно обов'язкова. Скидання upload-ключа не змінює app signing key, яким Google підписує збірки для користувачів.
