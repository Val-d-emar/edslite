#!/bin/bash

# --- Конфигурация ---
IMAGE_NAME="android-apk-builder"
CONTAINER_NAME="android-apk-builder-container"
DOCKERHUB_IMAGE="valdemarsu/$IMAGE_NAME:jdk21-sdk35"
DEVICE_IP="192.168.240.112" # Waydroid default
# Упрощенный путь к APK. Тип сборки (debug/release) добавится позже.
APK_FILE_PATH="app/build/outputs/apk"

# --- Справка ---

usage() {
    echo "Usage: $0 [команда] [-i|--install] [debug|release] [-h|--help] [IP-адрес устройства]"
    echo ""
    echo "Команды:"
    echo "  (no args)     Запускает одноразовую сборку и удаляет контейнер."
    echo "  -d            Запускает контейнер в фоновом режиме (detached) для последующих пересборок."
    echo "  --build, -b   Выполняет быструю пересборку в уже запущенном контейнере."
    echo "  --help, -h    Показывает эту справку."
    echo "  --stop, -s    Останавливает фоновый контейнер."
    echo "  --remove, -rm Останавливает и Удаляет фоновый контейнер."
    echo ""
    echo "Опции:"
    echo "  --install, -i Устанавливает APK в Android устройство после успешной сборки."
    echo "    IP-адрес устройства: IP-адрес устройства, на которое будет устанавливаться APK."
    echo "    debug|release: Тип сборки. debug - отладочная сборка, release - релизная сборка."
    exit 1
}

# --- Парсинг аргументов ---

COMMAND="once" # Команда по умолчанию
INSTALL_FLAG=false
BUILD_TYPE="debug"

for arg in "$@"; do
  case $arg in
    -d) COMMAND="demon" ;;
    -h|--help) usage ;;
    -i|--install) INSTALL_FLAG=true ;;
    -b|--build) COMMAND="build" ;;
    -s|--stop) COMMAND="stop" ;;
    -r|--run) COMMAND="run" ;;
    -rm|--remove) COMMAND="rm" ;;
    debug) BUILD_TYPE="debug" ;;
    release) BUILD_TYPE="release" ;;
    # Проверяем, что аргумент не флаг, прежде чем считать его IP-адресом
    *) [[ ! $arg =~ ^- ]] && DEVICE_IP=$arg ;;
  esac
done

# Добавляем тип сборки к пути
APK_FILE_PATH="${APK_FILE_PATH}/${BUILD_TYPE}/"
# Формируем имя задачи Gradle на основе типа сборки (Debug или Release)
TASK_NAME=":app:assemble${BUILD_TYPE^}"

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
          adb -e install -r "$APK_PATH"
        else
          echo "⚠️  Не удалось найти APK-файл по пути: $APK_FILE_PATH."
        fi
      else
        echo "⚠️  Не удалось подключиться к устроиству $DEVICE_IP:5555."
      fi
    fi
}

# --- Команда для выполнения сборки внутри контейнера ---

BUILD_COMMAND="
  echo \"--- Запуск сборки Gradle ($BUILD_TYPE) ---\" && \
  ./gradlew $TASK_NAME --parallel && \
  echo \"--- Смена владельца скомпилированных файлов ---\" && \
  chown -R \$(id -u):\$(id -g) .gradle app/build
"

# --- Логика выполнения команд ---

case $COMMAND in
"stop")
    if [ ! "$(docker ps -a -q -f name=$CONTAINER_NAME)" ]; then
        echo "Контейнер '$CONTAINER_NAME' не существует."
    else
        echo "Останавливаю контейнер '$CONTAINER_NAME'..."
        docker stop $CONTAINER_NAME > /dev/null || true
        echo "Контейнер остановлен."
    fi
    exit 0
    ;;

"rm")
    if [ ! "$(docker ps -a -q -f name=$CONTAINER_NAME)" ]; then
        echo "Контейнер '$CONTAINER_NAME' не существует."
    else
        echo "Останавливаю и удаляю контейнер '$CONTAINER_NAME'..."
        docker stop $CONTAINER_NAME > /dev/null || true
        docker rm $CONTAINER_NAME > /dev/null || true
        echo "Контейнер остановлен и удален."
    fi
    exit 0
    ;;

"run")
    if [ ! "$(docker ps -a -q -f name=$CONTAINER_NAME)" ]; then
        echo "Контейнер '$CONTAINER_NAME' не существует."
    else
        echo "Запускаю контейнер '$CONTAINER_NAME'..."
        docker start $CONTAINER_NAME
        echo "Контейнер запущен."
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

"demon")
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
    docker run -d --name $CONTAINER_NAME -v "$(pwd)":/app $IMAGE_NAME tail -f /dev/null
    
    echo "Контейнер запущен. Выполняю первую сборку..."

    FIRST_BUILD_COMMAND="
      echo \"--- Делаем gradlew исполняемым ---\" && \
      chmod +x gradlew && \
      ${BUILD_COMMAND}
    "
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
    # ЗАМЕНА: Убрали создание local.properties, добавили chmod для gradlew
    FIRST_BUILD_COMMAND="
      echo \"--- Делаем gradlew исполняемым ---\" && \
      chmod +x gradlew && \
      ${BUILD_COMMAND}
    "
    docker run --rm -v "$(pwd)":/app $IMAGE_NAME /bin/bash -c "$FIRST_BUILD_COMMAND"
    BUILD_EXIT_CODE=$?
    ;;
esac

# --- Проверка результата и установка ---
# Логика не тронута
if [ $BUILD_EXIT_CODE -eq 0 ]; then
  echo ""
  echo "✅ Сборка APK прошла успешно!"
  install_apk
else
  echo ""
  echo "❌ Сборка APK провалилась."
fi
