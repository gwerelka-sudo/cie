# Renamix

Fabric-мод для Minecraft **1.21.11**: полное редактирование имени, лора и
подписанных книг предмета в руке, с полной поддержкой MiniMessage
(градиенты, hover, click, rainbow, transition, font, sprite, head, key,
lang, insert и т.д.).

## Команды

Корень `/rnm`, алиасы `/rmx` и `/rm` — работают идентично.

```
/rnm name get <json|mm|plain>
/rnm name set <MiniMessage>

/rnm lore get <json|mm|plain>
/rnm lore line list
/rnm lore line clear
/rnm lore line add <MiniMessage>
/rnm lore line <index> get <json|mm|plain>
/rnm lore line <index> set <MiniMessage>
/rnm lore line <index> remove
/rnm lore line <index> insertafter <MiniMessage>
/rnm lore line <index> insertbefore <MiniMessage>

/rnm book author get <json|mm|plain>
/rnm book author set <текст>
/rnm book type get
/rnm book type set <original|copy|copy_of_copy|tattered>
/rnm book title get <json|mm|plain>
/rnm book title set <MiniMessage>
/rnm book page list
/rnm book page add <MiniMessage>          # поддерживает <newline> и \n
/rnm book page <index> get <json|mm|plain>
/rnm book page <index> set <MiniMessage>  # поддерживает <newline> и \n
/rnm book page <index> remove
```

Индексы строк лора и страниц книги — **с 1**, не с 0.

Все команды действуют на предмет **в главной руке**.

## Права доступа

Команды доступны только игрокам **в творческом режиме** (`isCreative()`).
Это осознанный выбор из твоего ответа: не нужен LuckPerms или op —
достаточно переключиться в креатив, и это работает на любом сервере "из
коробки".

## Сборка

1. Установи JDK 21.
2. Открой https://fabricmc.net/develop/, выбери версию **1.21.11** и
   сверь/поправь в `gradle.properties`:
   - `yarn_mappings`
   - `loader_version`
   - `fabric_api_version`
   Эти значения обновляются часто, а я не могу их проверить вживую —
   в файле стоят последние известные на момент написания мода.
3. `./gradlew build` (сгенерируется `gradlew`, если его нет — запусти
   `gradle wrapper` один раз, имея локально установленный Gradle, либо
   открой проект в IntelliJ IDEA с плагином Fabric — он сам подтянет
   wrapper).
4. Готовый джар — в `build/libs/renamix-1.0.0.jar`.

## Технические решения и на что обратить внимание

- **MiniMessage** реализован через чистые библиотеки Kyori
  (`adventure-api`, `adventure-text-minimessage`,
  `adventure-text-serializer-gson`, `adventure-text-serializer-legacy`),
  зашитые в джар через `include(...)`. Специально **не** использован
  `adventure-platform-fabric` — он даёт Audience-API для отправки
  сообщений, что нам не нужно; для конвертации
  MiniMessage → Component → vanilla `Text` достаточно
  `MiniMessageBridge` (см. `src/main/java/com/renamix/text/MiniMessageBridge.java`),
  который проходит через JSON-мостик (`TextCodecs.CODEC`).
- **Заголовок и автор книги** в ванильном формате — это простые строки,
  а не `Text`-компоненты. Поэтому:
  - `book title set` конвертирует MiniMessage в легаси `§`-коды и
    сохраняет их в строку — часть цвета/жирности реально отобразится в
    GUI книги, но это предел самого vanilla-формата, а не мода.
  - `book title get MM` / `get JSON` берут эту строку "как есть" —
    полноценного `Component` там никогда не было.
- **Самое рискованное место для компиляции** —
  `src/main/java/com/renamix/util/BookComponentUtil.java`. Поля
  `WrittenBookContentComponent` (`title`, `author`, `generation`,
  `pages`, `resolved`) и обёртка `RawFilteredPair<T>` — я не смог
  свериться с реальным decompiled-кодом 1.21.11 (нет доступа к
  интернету/игровым jar-ам в этой среде). Если при сборке ошибка
  именно в этом файле — запусти `./gradlew genSources`, открой
  `WrittenBookContentComponent` в исходниках и поправь только этот
  файл; остальной мод его не касается.
- Всё остальное (`DataComponentTypes.CUSTOM_NAME`,
  `DataComponentTypes.LORE`, `LoreComponent`, Brigadier-дерево команд)
  — стабильный, давно устоявшийся API, должен собраться без правок.

## Пример из твоего сообщения

```
/rm name set <gradient:#ff00ff:#ff0000>151515</gradient><b><yellow>2625625</b><hover:show_text:'<red>Нажми, чтобы тепнутся на спавн'><click:run_command:'/spawn'>spawn</click></hover><key:key.jump><lang:block.minecraft:diamond_block><rainbow>|||||||||||||||</rainbow><insert:test>shift+click</insert><transition:#ff00ff:#ff0000:#00ff00>aagagohagla</transition><font:uniform>hamamamamama</font><sprite:blocks:block/stone><sprite:items:item/diamond><head:Notch>
```

Всё это — валидные MiniMessage-теги, `MINI_MESSAGE.deserialize(...)`
разберёт их без дополнительных настроек (стандартный `MiniMessage.miniMessage()`
включает встроенные теги: gradient, transition, rainbow, hover, click,
insert, key, lang, font, sprite/selector и т.д.). Единственное, что стоит
проверить — тег `<head:...>` не входит в стандартный набор MiniMessage
(в самом Kyori его нет, это встречается у некоторых серверных
реализаций/форков). Если нужен именно текстовый рендер головы игрока в
компоненте — такой тег придётся зарегистрировать самостоятельно как
кастомный MiniMessage-тег (могу добавить, скажи).
