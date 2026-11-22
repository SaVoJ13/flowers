# TASK-051 — мінімальний GitHub Actions CI

## Мета

Додати перевірку pull request перед злиттям: JDK 21, усі локальні unit-тести, release bundle і базові release-запобіжники без публікації та без використання реальних секретів.

## Реалізація

- Workflow: `.github/workflows/android-ci.yml`; події: pull request, push у `main`, ручний запуск.
- Мінімальні права: лише `contents: read`; паралельні застарілі запуски скасовуються.
- Офіційні actions pinned на повні SHA: `actions/checkout` v7.0.1, `actions/setup-java` v6.2.0, `gradle/actions` v6.4.1 (перевірено за офіційними репозиторіями 2026-11-22).
- Справжній `app/google-services.json` не читається й не передається в GitHub. Workflow створює тимчасовий фіктивний файл лише для компіляції; CI AAB непридатний для публікації.
- Команда: `./gradlew --no-daemon --no-parallel --stacktrace testDebugUnitTest bundleRelease`.
- Після збірки CI перевіряє: AAB існує; у release dex немає debug App Check provider і є Play Integrity provider; tracked-файли не містять шаблону реального Google API-ключа.

## Критерії

1. Workflow синтаксично валідний і запускається на PR.
2. Job `Tests and release bundle` зелений.
3. Реальні Firebase-конфігурація, upload-ключ і паролі не потрапляють у workflow, логи, artifacts або Git.
4. Незалежна QA іншою моделлю — `QA Verified`.
5. Після злиття CI-гілки PR #29 оновлюється від `main`, отримує зелений CI й лише тоді зливається.

## Журнал 2026-11-22

- Створено окрему гілку `codex/task-051-github-actions-ci` від `origin/main`.
- Локальна smoke-перевірка виконана в чистому `git archive` без реального `google-services.json`, лише з тимчасовою CI-only конфігурацією: `BUILD SUCCESSFUL`; 154/154 тести, 0 failures/errors/skipped; AAB створено.
- Перевірки AAB: `DebugAppCheckProviderFactory` відсутній, `PlayIntegrityAppCheckProviderFactory` присутній; шаблон реального Google API-ключа у tracked-файлах не знайдено.
- Наступний крок: PR, реальний GitHub Actions run і незалежна QA.

