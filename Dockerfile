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
    git \
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
    sdkmanager "platforms;android-35" "build-tools;34.0.0" "platform-tools" "cmake;3.22.1"

# Создаем директорию, куда будет монтироваться проект
WORKDIR /app

# --- УСКОРЕНИЕ: КЕШИРОВАНИЕ GRADLE-ЗАВИСИМОСТЕЙ ---
# 1. Копируем ТОЛЬКО файлы сборки
COPY build.gradle settings.gradle gradle.properties ./
COPY gradlew ./gradlew
COPY gradle/ gradle/
COPY app/build.gradle app/
COPY library/build.gradle library/
COPY photoview/build.gradle photoview/

# 2. Делаем gradlew исполняемым и запускаем задачу, которая ТОЛЬКО СКАЧИВАЕТ зависимости
# Этот слой будет кешироваться, пока не изменится build.gradle
RUN chmod +x gradlew && ./gradlew dependencies

# 3. Теперь копируем ВЕСЬ остальной код
COPY . .

# Просто запускаем баш, чтобы можно было зайти в контейнер и осмотреться
CMD ["/bin/bash"]
