#!/bin/bash

# --- Конфигурация ---
INPUT_DIR="fork_diffs"
OUTPUT_DIR="fork_summaries"
# Устанавливаем размер чанка в строках. 2000 строк - безопасно для лимита токенов.
CHUNK_SIZE=2000
CHUNK_DELAY=2

# --- Промпты для AI ---
MAP_PROMPT="
Ты — ИИ-ассистент для ревью кода. Тебе на вход подается ЧАСТЬ большого git diff файла.
Твоя задача — проанализировать только этот фрагмент и очень кратко, в виде списка, описать ключевые изменения в логике, которые ты видишь.
Игнорируй изменения форматирования. Выведи только список изменений. Не делай вступлений и заключений.
"
REDUCE_PROMPT="
Ты — старший Android и C/C++ разработчик.
Тебе на вход подается набор кратких описаний изменений из разных частей одного большого diff-файла.
Твоя задача — объединить их в одно связное итоговое ревью на русском языке в формате Markdown.

Структурируй свой ответ строго по следующим пунктам:

1.  **Краткое резюме:** Опиши общую суть изменений в 1-2 предложениях.
2.  **Ключевые изменения по файлам:** Для каждого измененного файла кратко опиши, что было сделано. Сгруппируй изменения, если они однотипны.
3.  **Потенциальные риски и проблемы:** Укажи, если видишь что-то подозрительное.
4.  **Рекомендация:** Сделай вывод одним из трех вариантов: 'Включать полностью', 'Включать частично (указать, что именно)', или 'Игнорировать'.
"

echo "--- Начинаю генерацию саммари в папку '$OUTPUT_DIR' ---"
rm -rf $OUTPUT_DIR
mkdir -p $OUTPUT_DIR

find "$INPUT_DIR" -type f -name "*.diff" | while read diff_file; do
    sanitized_name=${diff_file#"$INPUT_DIR/"}
    sanitized_name=${sanitized_name%".diff"}
    original_branch_name=$(echo "$sanitized_name" | tr '-' '/')
    summary_file="${OUTPUT_DIR}/${sanitized_name}.summary.md"

    echo "Анализирую ветку: $original_branch_name (это может занять некоторое время)..."

    # --- Логика Map-Reduce ---
    TEMP_DIR=$(mktemp -d)
    
    # Шаг 1: Разделяем большой дифф на маленькие чанки
    split -l $CHUNK_SIZE "$diff_file" "${TEMP_DIR}/chunk_"
    
    # Создаем файл для промежуточных саммари
    INTERMEDIATE_SUMMARIES="${TEMP_DIR}/intermediate_summaries.txt"
    touch "$INTERMEDIATE_SUMMARIES"

    # Шаг 2 (Map): Проходим по каждому чанку и получаем его саммари
    echo "  -> Анализирую чанки..."
    CHUNK_COUNT=$(ls -1 "${TEMP_DIR}/chunk_"* | wc -l)
    CURRENT_CHUNK=1
    for chunk_file in "${TEMP_DIR}/chunk_"*; do
        echo "     - Обработка чанка ${CURRENT_CHUNK} из ${CHUNK_COUNT}..."
        cat "$chunk_file" | gemini "$MAP_PROMPT" >> "$INTERMEDIATE_SUMMARIES"
        if [ $? -ne 0 ]; then
            echo "  -> ❌ Ошибка при анализе чанка '$chunk_file' для ветки '$original_branch_name'."
            echo "Не удалось сгенерировать саммари." > "$summary_file"
            # Переходим к следующему diff-файлу
            continue 2
        fi
        CURRENT_CHUNK=$((CURRENT_CHUNK + 1))
        # Добавляем задержку, чтобы не превышать лимит API
        sleep $CHUNK_DELAY
    done

    # Шаг 3 (Reduce): Собираем итоговое саммари из промежуточных
    echo "  -> Собираю итоговое саммари..."
    cat "$INTERMEDIATE_SUMMARIES" | gemini "$REDUCE_PROMPT" > "$summary_file"

    if [ $? -eq 0 ]; then
        echo "  -> ✅ Саммари сохранено в: $summary_file"
    else
        echo "  -> ❌ Ошибка при создании итогового саммари для ветки '$original_branch_name'."
        echo "Не удалось сгенерировать саммари." > "$summary_file"
    fi

    # Очищаем временные файлы
    rm -rf "$TEMP_DIR"
done

echo ""
echo "✅ Готово! Все саммари сгенерированы в папке '$OUTPUT_DIR'."
