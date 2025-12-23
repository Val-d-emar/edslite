#!/bin/bash

# --- Конфигурация ---
# Локальное имя образа для удобства
IMAGE_NAME="edslite-builder"
# ПОЛНОЕ имя образа на Docker Hub.
DOCKERHUB_IMAGE="valdemarsu/edslite-builder:latest"
WAYDROID_IP="192.168.240.112"
APK_FILE_PATH="app/build/outputs/apk/liteLicCheckNoneNoinetNofsml/debug/"
# APK_FILE_PATH="app/build/outputs/apk/liteLicCheckNoneNoinetNofsml/release/"

# --- Проверка флага --install ---
INSTALL_FLAG=false
if [ "$1" == "--install" -o "$1" == "-i" ]; then
  INSTALL_FLAG=true
fi

# --- Проверка, сборка или скачивание Docker-образа ---
echo "--- Проверка Docker-образа '$IMAGE_NAME' ---"
if [[ "$(docker images -q $IMAGE_NAME 2> /dev/null)" == "" ]]; then
  echo "Локальный образ не найден. Попытка скачивания с Docker Hub..."
  # Пытаемся скачать готовый образ
  docker pull $DOCKERHUB_IMAGE
  
  if [ $? -eq 0 ]; then
    echo "✅ Образ успешно скачан с Docker Hub."
    # Добавляем локальный тег
    docker tag $DOCKERHUB_IMAGE $IMAGE_NAME
  else
    echo "⚠️ Не удалось скачать образ. Начинаю локальную сборку (это займет несколько минут)..."
    docker build -t $IMAGE_NAME .
    if [ $? -ne 0 ]; then
      echo "❌ Ошибка сборки Docker-образа. Выход."
      exit 1
    fi
    echo "✅ Сборка образа завершена."
  fi
else
  echo "Локальный образ найден."
fi

echo ""
echo "--- Запуск сборки APK в контейнере ---"

# Запускаем контейнер для сборки.
# --rm : удалить контейнер после завершения.
# -v "$(pwd)":/app : монтируем текущую папку (хост) в папку /app (контейнер).
#                    Все созданные файлы в /app появятся на хосте.
docker run --rm \
  -v "$(pwd)":/app \
  $IMAGE_NAME \
  /bin/bash -c '
    echo "--- Создание local.properties ---" && \
    echo "sdk.dir=${ANDROID_SDK_ROOT}" > local.properties && \
    echo "ndk.dir=${ANDROID_NDK_HOME}" >> local.properties && \
    \
    echo "--- Запуск сборки Gradle ---" && \
    gradle :app:assembleLiteLicCheckNoneNoinetNofsmlDebug --parallel && \
    \
    echo "--- Смена владельца скомпилированных файлов ---" && \
    chown -R $(id -u):$(id -g) app/build
  '

# Или
# gradle :app:assembleLiteLicCheckNoneNoinetNofsmlRelease && \

# Проверяем, что сборка прошла успешно
if [ $? -eq 0 ]; then
  echo ""
  echo "✅ Сборка APK прошла успешно!"
  
  # --- Опциональная установка в Waydroid ---
  if [ "$INSTALL_FLAG" = true ]; then
    echo ""
    echo "--- Попытка установки в Waydroid ---"
    
    # Проверяем, есть ли adb
    if ! command -v adb &> /dev/null; then
      echo "⚠️  Команда 'adb' не найдена. Установка невозможна."
      exit 0
    fi
    
    # Пытаемся подключиться
    if adb connect $WAYDROID_IP:5555 | grep -q "connected"; then
      echo "✅ ADB Подключено к Waydroid ($WAYDROID_IP)."
      
      # Ищем APK-файл динамически
      APK_PATH=$(find "$(pwd)/$APK_FILE_PATH" -type f -name "*.apk" | head -n 1)
      
      if [ -n "$APK_PATH" ]; then
        echo "Нашел APK: $APK_PATH"
        echo "Устанавливаю (флаг -r для переустановки)..."
        adb install -r "$APK_PATH"
      else
        echo "⚠️  Не удалось найти APK-файл для установки."
      fi
    else
      echo "⚠️  Не удалось подключиться ADB к Waydroid. Убедитесь, что он запущен и IP-адрес ($WAYDROID_IP) верен."
    fi
  fi
  
else
  echo ""
  echo "❌ Сборка APK провалилась."
fi
