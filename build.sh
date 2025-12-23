#!/bin/bash

# --- Конфигурация ---
IMAGE_NAME="edslite-builder"
CONTAINER_NAME="edslite-container"
DOCKERHUB_IMAGE="valdemarsu/$IMAGE_NAME:latest"
DEVICE_IP="192.168.240.112"
APK_FILE_PATH="app/build/outputs/apk/liteLicCheckNoneNoinetNofsml/debug/"
# APK_FILE_PATH="app/build/outputs/apk/liteLicCheckNoneNoinetNofsml/release/"

# --- Справка ---
usage() {
    echo "Usage: $0 [команда] [-i|--install]"
    echo ""
    echo "Команды:"
    echo "  (no args)     Запускает одноразовую сборку и удаляет контейнер."
    echo "  -d            Запускает контейнер в фоновом режиме (detached) для последующих пересборок."
    echo "  --exec, -e    Выполняет быструю пересборку в уже запущенном контейнере."
    echo "  --help, -h    Показывает эту справку."
    echo "  --stop, -s    Останавливает и удаляет фоновый контейнер."
    echo ""
    echo "Опции:"
    echo "  --install, -i Устанавливает APK в Android устройство после успешной сборки."
    exit 1
}

# --- Функция установки APK ---
install_apk() {
    if [ "$INSTALL_FLAG" = true ]; then
      echo ""
      echo "--- Попытка установки в устроиство $DEVICE_IP:5555 ---"
      
      if ! command -v adb &> /dev/null; then
        echo "⚠️  Команда 'adb' не найдена."
        return
      fi

      if adb connect $DEVICE_IP:5555 | grep -q "connected"; then
        echo "✅  ADB Подключено к устроиству $DEVICE_IP:5555."
        APK_PATH=$(find "$(pwd)/$APK_FILE_PATH" -type f -name "*.apk" | head -n 1)
        if [ -n "$APK_PATH" ]; then
          echo "Устанавливаю: $APK_PATH"
          adb install -r "$APK_PATH"
        else
          echo "⚠️  Не удалось найти APK-файл по пути: $APK_FILE_PATH."
        fi
      else
        echo "⚠️  Не удалось подключиться к устроиству $DEVICE_IP:5555."
      fi
    fi
}

# --- Парсинг аргументов ---
COMMAND="once" # Команда по умолчанию
INSTALL_FLAG=false

# Простой парсинг, чтобы флаги могли идти в любом порядке
for arg in "$@"; do
  case $arg in
    -d) COMMAND="start" ;;
    -h|--help) usage ;;
    -i|--install) INSTALL_FLAG=true ;;
    -e|--exec) COMMAND="build" ;;
    -s|--stop) COMMAND="stop" ;;
  esac
done

# --- Команда для выполнения сборки внутри контейнера ---
BUILD_COMMAND='
  echo "--- Запуск сборки Gradle ---" && \
  gradle :app:assembleLiteLicCheckNoneNoinetNofsmlDebug --parallel && \
  echo "--- Смена владельца скомпилированных файлов ---" && \
  chown -R $(id -u):$(id -g) .gradle app/build
'

# --- Логика выполнения команд ---
case $COMMAND in
"stop")
    if [ ! "$(docker ps -a -q -f name=$CONTAINER_NAME)" ]; then
        echo "Контейнер '$CONTAINER_NAME' не существует."
    else
        echo "Останавливаю и удаляю контейнер '$CONTAINER_NAME'..."
        # docker stop вернет ошибку, если контейнер уже остановлен, поэтому > /dev/null
        docker stop $CONTAINER_NAME > /dev/null || true
        docker rm $CONTAINER_NAME > /dev/null || true
        echo "Контейнер остановлен и удален."
    fi
    exit 0
    ;;

"build")
    if [ ! "$(docker ps -q -f name=$CONTAINER_NAME)" ]; then
        echo "❌ Ошибка: Контейнер '$CONTAINER_NAME' не запущен. Сначала запустите его с флагом -d."
        exit 1
    fi
    echo "--- Запуск быстрой пересборки в контейнере '$CONTAINER_NAME' ---"
    docker exec $CONTAINER_NAME /bin/bash -c "$BUILD_COMMAND"
    BUILD_EXIT_CODE=$?
    ;;

"start")
    if [ "$(docker ps -a -q -f name=$CONTAINER_NAME)" ]; then
        echo "⚠️  Контейнер с именем '$CONTAINER_NAME' уже существует. Используйте '--exec' для пересборки или '--stop' для удаления."
        exit 1
    fi

    if [[ "$(docker images -q $IMAGE_NAME 2> /dev/null)" == "" ]]; then
      echo "--- Проверка Docker-образа '$IMAGE_NAME' ---"
      echo "Локальный образ не найден. Попытка скачивания с Docker Hub..."
      docker pull $DOCKERHUB_IMAGE && docker tag $DOCKERHUB_IMAGE $IMAGE_NAME || {
        echo "⚠️ Не удалось скачать образ. Начинаю локальную сборку..."
        docker build -t $IMAGE_NAME . || { echo "❌ Ошибка сборки Docker-образа."; exit 1; }
      }
      echo "✅ Образ готов."
    fi

    echo "--- Запуск фонового контейнера '$CONTAINER_NAME' ---"
    # Запускаем контейнер в фоновом режиме с "вечной" командой чтобы он не завершался
    docker run -d --name $CONTAINER_NAME -v "$(pwd)":/app $IMAGE_NAME tail -f /dev/null
    
    echo "Контейнер запущен. Выполняю первую сборку..."
    FIRST_BUILD_COMMAND='
      echo "--- Создание local.properties ---" && \
      echo "sdk.dir=${ANDROID_SDK_ROOT}" > local.properties && \
      echo "ndk.dir=${ANDROID_NDK_HOME}" >> local.properties && \
      '${BUILD_COMMAND}
    docker exec $CONTAINER_NAME /bin/bash -c "$FIRST_BUILD_COMMAND"
    BUILD_EXIT_CODE=$?
    ;;

"once")
    if [[ "$(docker images -q $IMAGE_NAME 2> /dev/null)" == "" ]]; then
      echo "--- Проверка Docker-образа '$IMAGE_NAME' ---"
      echo "Локальный образ не найден. Попытка скачивания с Docker Hub..."
      docker pull $DOCKERHUB_IMAGE && docker tag $DOCKERHUB_IMAGE $IMAGE_NAME || {
        echo "⚠️ Не удалось скачать образ. Начинаю локальную сборку..."
        docker build -t $IMAGE_NAME . || { echo "❌ Ошибка сборки Docker-образа."; exit 1; }
      }
      echo "✅ Образ готов."
    fi

    echo "--- Запуск одноразовой сборки ---"
    FIRST_BUILD_COMMAND='
      echo "--- Создание local.properties ---" && \
      echo "sdk.dir=${ANDROID_SDK_ROOT}" > local.properties && \
      echo "ndk.dir=${ANDROID_NDK_HOME}" >> local.properties && \
      '${BUILD_COMMAND}
    docker run --rm -v "$(pwd)":/app $IMAGE_NAME /bin/bash -c "$FIRST_BUILD_COMMAND"
    BUILD_EXIT_CODE=$?
    ;;
esac

# --- Проверка результата и установка ---
if [ $BUILD_EXIT_CODE -eq 0 ]; then
  echo ""
  echo "✅ Сборка APK прошла успешно!"
  install_apk
else
  echo ""
  echo "❌ Сборка APK провалилась."
fi
