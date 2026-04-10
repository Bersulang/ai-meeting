# 本地构建手册（PR-00）

## 1. 标准构建命令

在仓库根目录执行：

```powershell
.\mvnw.cmd -pl admin -am clean test
```

仅编译：

```powershell
.\mvnw.cmd -pl admin -am -DskipTests compile
```

## 2. JDK 对齐要求

- JDK：17（建议 Temurin/Corretto）
- Maven：使用项目自带 `mvnw` / `mvnw.cmd`

检查命令：

```powershell
java -version
.\mvnw.cmd -version
```

## 3. `Failed setting boot class path` 处理

若执行 `java -version` 或 Maven 构建时报：

```text
Error occurred during initialization of VM
Failed setting boot class path.
```

按以下顺序排查：

1. 确认 `JAVA_HOME` 指向有效 JDK17 根目录（不是 `bin`）。
2. 确认 `PATH` 中优先使用 `${JAVA_HOME}\bin`，并移除失效 JDK 路径。
3. 检查并清理以下环境变量中的异常参数：
   - `_JAVA_OPTIONS`
   - `JAVA_TOOL_OPTIONS`
   - `JDK_JAVA_OPTIONS`
   - `CLASSPATH`
4. 重新打开终端后再执行 `java -version` 验证。

## 4. CI 基线

- 已提供 GitHub Actions：`.github/workflows/backend-ci.yml`
- 触发时机：push / pull request
- 任务内容：`./mvnw -B -ntp -pl admin -am clean test`
