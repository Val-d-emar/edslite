# Используем самую современную версию Ubuntu
FROM ubuntu:latest

# Отключаем интерактивные запросы от apt, чтобы установка шла автоматически
ENV DEBIAN_FRONTEND=noninteractive

# --- Установка всех зависимостей ---
# Выполняем все в одном RUN, чтобы уменьшить количество слоев в образе
RUN apt-get update && \
    apt-get install -y \
    wget \
    unzip \
    openjdk-21-jdk \
    && \
    # Чистим кеши apt, чтобы уменьшить размер образа
    rm -rf /var/lib/apt/lists/*

# --- Установка инструментов ---
# ЗАМЕНА: Указываем путь к новой Java
ENV JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64

ENV ANDROID_SDK_TOOLS_VERSION=11076708

ENV ANDROID_SDK_ROOT=/opt/android-sdk

# Обновляем PATH для новых инструментов
ENV PATH=$PATH:${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin:${ANDROID_SDK_ROOT}/platform-tools

# Установка Android SDK
RUN wget -q https://dl.google.com/android/repository/commandlinetools-linux-${ANDROID_SDK_TOOLS_VERSION}_latest.zip -O sdk-tools.zip && \
    mkdir -p ${ANDROID_SDK_ROOT}/cmdline-tools && \
    unzip -q sdk-tools.zip -d ${ANDROID_SDK_ROOT}/cmdline-tools && \
    mv ${ANDROID_SDK_ROOT}/cmdline-tools/cmdline-tools ${ANDROID_SDK_ROOT}/cmdline-tools/latest && \
    rm sdk-tools.zip && \
    yes | sdkmanager --licenses > /dev/null && \
    sdkmanager "platforms;android-35" "build-tools;34.0.0" "platform-tools"

# Создаем директорию, куда будет монтироваться проект
WORKDIR /app

# Просто запускаем баш, чтобы можно было зайти в контейнер и осмотреться
CMD ["/bin/bash"]
