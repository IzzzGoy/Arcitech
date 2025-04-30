## Введение

`ndmatrix-parameter` — это архитектурный фреймворк для управления состоянием и обработки событий в проектах компании. Фреймворк основан на разделении на:

- **Параметры (Parameters)** — атомарные состояния, доступные по запросу.
- **Проекции (Projections)** — агрегированные состояния, вычисляемые по набору параметров и/или других проекций.
- **События (Events) и Интенты (Intents)** — абстракции вычислений и правил изменения параметров.
- **Цепочки событий (EventChain)** — маршрутизация и организация последовательного и параллельного исполнения событий.

Фреймворк позволяет строго отделять логику изменения состояния от вычислений, обеспечивая предсказуемость, тестируемость и расширяемость кода.

---

## Основные компоненты

### Параметры (Parameters)

- Представляют собой состояние типа `S : Any`.
- Реализованы через `MutableStateFlow<S>`.
- **Не** имеют публичных методов модификации.
- Модификация состояния происходит **только** через соответствующие **Intent**-события.
- API:
    - `interface Parameter<S>`: содержит `val value: S` и `val flow: StateFlow<S>`.
    - `abstract class ParameterHolder<E: Message.Intent, S: Any>(initialValue: S)` — базовая реализация.

### Проекции (Projections)

- Вычисляются как агрегирующие функции над одним или несколькими параметрами и/или другими проекциями.
- **Не** имеют методов модификации.
- Автоматически пересчитываются при изменении источников.
- Представлены в библиотеке маркерным классом:
  ```kotlin
  abstract class Projection<S: Any> : Parameter<S>
  ```

### События (Events) и Интенты (Intents)

- **Message.Event** — маркер общих событий, начинающих цепочку вычислений.
- **Message.Intent** — маркер событий, изменяющих состояние параметров.
- Любой `Message` может порождать дочерние события.
- Интенты **не** могут быть стартом цепочки.

### Обработчики событий

- `abstract class EventHandler<E: Message>` — базовый класс для всех обработчиков.
    - `suspend fun handle(e: E)` — основной метод обработки конкретного типа.
    - `suspend fun process(e: Message)` — обобщённый метод для маршрутизации.
- `IntentHandler<E: Message.Intent>` — базовый класс для параметров-процессоров.
- `AbstractEventHandler<E : Message.Event>` — инфраструктура для событий:
    - Собирает и шёрит внешний поток `events: SharedFlow<Message>`.
    - Эмитит события с учётом **CallMetadata** (parentId, currentId).

### Цепочка событий (EventChain)

- `abstract class EventChain<E : Message.Event>`
    - Получает на вход список `intentsHandlers: List<ParameterHolder<*, *>>` и `eventsSender: List<AbstractEventHandler<*>>`.
    - При инициализации настраивает подписки:
        - Если `isDebug = true`, собирает `postMetadata` для отладки.
        - Подписывается на `rawEvents` всех `eventsSender`, маршрутизируя дочерние события в обработчики.
    - Метод `general(e: E)` запускает цепочку из всех обработчиков в корневом контексте.

---

## Установка

Добавьте в зависимости Gradle:

```groovy
implementation "io.github.izzzgoy:ndmatrix:1.0.2"
implementation "io.github.izzzgoy:ndmatrix-plugin:1.0.10"
```

Подключите плагин в `build.gradle.kts` модуля (например, `feature.auth`):
```kotlin
architect {
    packageName = "com.multiplatform.feature.auth.api.ui"
}
commonMain {
    kotlin.srcDirs("build/generated/architect")
}
```

Структура файлов:
```
commonMain/
  └─ config/{filename}.json    // Файлы конфигурации
build/generated/architect/    // Сгенерированные классы
```

---

## Конфигурация генератора

Конфиг (`config/{name}.json`) содержит следующие разделы:

1. **parameters** — объявление параметров:
   ```json
   "SomeParam": {
     "type": "integer",         // или полный путь к типу
     "nullable": false,
     "initial": "0",
     "intents": {
       "SetValue": {
         "args": { "value": { "type": "integer", "nullable": false } }
       }
     }
   }
   ```
2. **projections** — список проекций:
   ```json
   [{
     "name": "Total",
     "type": "integer",
     "nullable": false,
     "initial": "0",
     "sources": [
       { "type": "Param", "name": "SomeParam" }
     ]
   }]
   ```
3. **events** — описание обработчиков событий:
   ```json
   "UserLoggedIn": {
     "args": { "userId": { "type": "string" } },
     "returns": [
       { "name": "SomeParam.SetValue", "type": "Param" }
     ]
   }
   ```
4. **general** — корневые события:
   ```json
   ["UserLoggedIn"]
   ```

На основе конфига плагин сгенерирует:
- ParameterHolder для каждого параметра.
- Intent-классы.
- EventHandler-классы для событий и цепочку `EventChain`.

---

## Миграция существующего кода

1. **Параметры**:
    - Ищите `StateFlow`/`MutableStateFlow`, которые **не** являются результатом `map`, `combine` и т.д.
    - Из таких `StateFlow` выносите параметры.
2. **Интенты**:
    - В методе `dispatch` определяйте изменения состояния — они превращаются в `Intent`.
    - Логическую часть без модификации выносите в отдельные `Event`.
3. **События**:
    - Каждое событие в `dispatch` оформляйте как отдельный `EventHandler`.
    - Если в обработчике есть изменение состояния — выносите его как `Intent`.
4. **Проекции**:
    - Заменяйте `flow.map`, `combine` и т.д. на проекции в конфиге.

---

## Правила нейминга

- **Parameters**: `{ParamName}ParameterHolder`
- **Intents**: `{ParamName}Intents`
- **Events и их классы**: `Event{EventName}` и `Event{EventName}Handler`
- **Chains**: `{GeneralEventName}Chain`

---
