#!/bin/bash

# --- Конфигурация ---
# Ветка, с которой будем сравнивать все остальные.
BASE_BRANCH="feat/integration"

# Папка, куда будут складываться все сгенерированные дифы.
OUTPUT_DIR="fork_diffs"

# --- Паттерны веток для исключения из сравнения ---
# Добавьте сюда любые паттерны, которые нужно игнорировать.
# `orig/.*` будет игнорировать все ветки, начинающиеся с 'orig/'.
EXCLUDE_PATTERNS=("master" "$BASE_BRANCH" "HEAD" "orig/.*" "dev" "main" "feat/.*" "chore/.*" "fix/.*" "chores/.*")

# --- Логика скрипта ---
echo "--- Шаг 1: Загрузка всех веток с GitHub (fetch) ---"
git fetch origin --prune

if ! git show-ref --verify --quiet "refs/remotes/origin/$BASE_BRANCH"; then
    echo "❌ Ошибка: Базовая ветка '$BASE_BRANCH' не найдена на origin."
    exit 1
fi
git checkout $BASE_BRANCH > /dev/null 2>&1

echo ""
echo "--- Шаг 2: Начинаю генерацию дифов относительно ветки '$BASE_BRANCH' ---"
rm -rf $OUTPUT_DIR
mkdir -p $OUTPUT_DIR

EXCLUDE_REGEX=$(IFS='|'; echo "^(${EXCLUDE_PATTERNS[*]})")
BRANCHES_TO_COMPARE=$(git for-each-ref --format='%(refname:short)' refs/remotes/origin/ | sed 's/origin\///' | grep -v -E "$EXCLUDE_REGEX")

if [ -z "$BRANCHES_TO_COMPARE" ]; then
    echo "⚠️  Не найдено веток для сравнения на GitHub."
    exit 0
fi

for branch in $BRANCHES_TO_COMPARE
do
    echo "Обрабатываю ветку: $branch"
    
    SANITIZED_BRANCH_NAME=$(echo "$branch" | tr '/' '-')
    
    # Создаем краткий .stat.txt файл
    git diff "origin/${BASE_BRANCH}"..."origin/${branch}" --stat > "${OUTPUT_DIR}/${SANITIZED_BRANCH_NAME}.stat.txt"
    
    # Создаем полный .full.diff файл (для Gist)
    git diff "origin/${BASE_BRANCH}"..."origin/${branch}" > "${OUTPUT_DIR}/${SANITIZED_BRANCH_NAME}.full.diff"

    # Создаем "чистый" .clean.diff файл, игнорируя пробелы и концы строк
    git diff --ignore-all-space --ignore-cr-at-eol "origin/${BASE_BRANCH}"..."origin/${branch}" > "${OUTPUT_DIR}/${SANITIZED_BRANCH_NAME}.clean.diff"
done

echo ""
echo "✅ Готово! Все дифы (stat, full, clean) сохранены в папку '$OUTPUT_DIR'."
