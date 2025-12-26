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
    software-properties-common \
    openjdk-8-jdk \
    && \
    # Добавляем ключ и репозиторий для старой библиотеки
    apt-key adv --keyserver keyserver.ubuntu.com --recv-keys 3B4FE6ACC0B21F32 && \
    add-apt-repository "deb http://archive.ubuntu.com/ubuntu/ bionic main universe" && \
    apt-get update && \
    # Устанавливаем libncurses5
    apt-get install -y libncurses5 libtinfo5 && \
    # Чистим кеши apt, чтобы уменьшить размер образа
    rm -rf /var/lib/apt/lists/*

# --- Установка инструментов ---
ENV JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64
ENV GRADLE_VERSION=5.6.4
ENV ANDROID_SDK_TOOLS_VERSION=4333796
ENV ANDROID_NDK_VERSION=r17c

ENV ANDROID_SDK_ROOT=/opt/android-sdk
ENV GRADLE_HOME=/opt/gradle/gradle-${GRADLE_VERSION}
ENV ANDROID_NDK_HOME=/opt/android-ndk-${ANDROID_NDK_VERSION}

ENV PATH=$PATH:${GRADLE_HOME}/bin:${ANDROID_SDK_ROOT}/tools/bin:${ANDROID_SDK_ROOT}/platform-tools

# Установка Gradle
RUN wget -q https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip -O gradle.zip && \
    unzip -q gradle.zip -d /opt/gradle && \
    rm gradle.zip

# Установка Android SDK
RUN wget -q https://dl.google.com/android/repository/sdk-tools-linux-${ANDROID_SDK_TOOLS_VERSION}.zip -O sdk-tools.zip && \
    mkdir -p ${ANDROID_SDK_ROOT} && \
    unzip -q sdk-tools.zip -d ${ANDROID_SDK_ROOT} && \
    rm sdk-tools.zip && \
    yes | ${ANDROID_SDK_ROOT}/tools/bin/sdkmanager --licenses > /dev/null && \
    ${ANDROID_SDK_ROOT}/tools/bin/sdkmanager "platforms;android-28" "build-tools;28.0.3" "platform-tools"

# Установка NDK
RUN wget -q https://dl.google.com/android/repository/android-ndk-${ANDROID_NDK_VERSION}-linux-x86_64.zip -O ndk.zip && \
    unzip -q ndk.zip -d /opt/ && \
    rm ndk.zip

# Создаем директорию, куда будет монтироваться проект
WORKDIR /app

# Просто запускаем баш, чтобы можно было зайти в контейнер и осмотреться
CMD ["/bin/bash"]
