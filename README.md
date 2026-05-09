# java-tutorial

A growing companion project for the [DevOpsMonk Java Tutorial series](https://devops-monk.com/tutorials/).
Currently covers **Java 8** in depth — Java 11, 17, and 21 chapters coming soon.

Every chapter in the tutorial has a matching runnable class here.  
All examples use a consistent **Employee / Order / Product** domain so you can
see the features composing together in a realistic codebase rather than
isolated toy snippets.

---

## Project Structure

```
src/main/java/com/devopsmonk/java8/
├── model/                          # Shared domain objects used across all chapters
│   ├── Employee.java
│   ├── Department.java
│   ├── Order.java
│   ├── OrderItem.java
│   └── Product.java
├── ch03_lambdas/                   # Ch 3 — Lambda Expressions
│   └── LambdaExamples.java
├── ch04_functional/                # Ch 4 — Functional Interfaces
│   └── FunctionalInterfaceExamples.java
├── ch05_methodrefs/                # Ch 5 — Method References
│   └── MethodReferenceExamples.java
├── ch06_streams/                   # Ch 6 — Streams Introduction
│   └── StreamBasicsExamples.java
├── ch07_streams_advanced/          # Ch 7 — Advanced Streams
│   └── StreamAdvancedExamples.java
├── ch08_parallel/                  # Ch 8 — Parallel Streams
│   └── ParallelStreamExamples.java
├── ch09_optional/                  # Ch 9 — Optional
│   └── OptionalExamples.java
├── ch10_datetime/                  # Ch 10 — Date and Time API
│   └── DateTimeExamples.java
├── ch11_interfaces/                # Ch 11 — Default and Static Interface Methods
│   └── DefaultStaticMethodExamples.java
├── ch12_collections/               # Ch 12 — Collections and Map Enhancements
│   └── CollectionMapExamples.java
├── ch13_async/                     # Ch 13 — CompletableFuture
│   └── CompletableFutureExamples.java
├── ch14_newapis/                   # Ch 14 — Base64, StampedLock, LongAdder
│   └── NewApisExamples.java
└── ch16_bestpractices/             # Ch 16 — Best Practices
    └── BestPracticesExamples.java
```

---

## Prerequisites

- Java 8 or higher (`java -version`)
- Gradle 7+ (`./gradlew --version` — wrapper included)

---

## Running the Examples

Compile everything:

```bash
./gradlew compileJava
```

Run a specific chapter using the convenience tasks:

```bash
./gradlew runLambdaExamples
./gradlew runFunctionalInterfaceExamples
./gradlew runMethodReferenceExamples
./gradlew runStreamBasicsExamples
./gradlew runStreamAdvancedExamples
./gradlew runParallelStreamExamples
./gradlew runOptionalExamples
./gradlew runDateTimeExamples
./gradlew runDefaultStaticMethodExamples
./gradlew runCollectionMapExamples
./gradlew runCompletableFutureExamples
./gradlew runNewApisExamples
./gradlew runBestPracticesExamples
```

Or run any class directly:

```bash
./gradlew run -PmainClass=com.devopsmonk.java8.ch07_streams_advanced.StreamAdvancedExamples
```

---

## Tutorial Articles

| Chapter | Topic | Code |
|---------|-------|------|
| 3 | [Lambda Expressions](https://devops-monk.com/tutorials/java8/lambdas/) | `ch03_lambdas/` |
| 4 | [Functional Interfaces](https://devops-monk.com/tutorials/java8/functional-interfaces/) | `ch04_functional/` |
| 5 | [Method References](https://devops-monk.com/tutorials/java8/method-references/) | `ch05_methodrefs/` |
| 6 | [Streams Introduction](https://devops-monk.com/tutorials/java8/streams-introduction/) | `ch06_streams/` |
| 7 | [Advanced Streams](https://devops-monk.com/tutorials/java8/streams-advanced/) | `ch07_streams_advanced/` |
| 8 | [Parallel Streams](https://devops-monk.com/tutorials/java8/parallel-streams/) | `ch08_parallel/` |
| 9 | [Optional](https://devops-monk.com/tutorials/java8/optional/) | `ch09_optional/` |
| 10 | [Date and Time API](https://devops-monk.com/tutorials/java8/date-time-api/) | `ch10_datetime/` |
| 11 | [Default & Static Methods](https://devops-monk.com/tutorials/java8/default-static-methods/) | `ch11_interfaces/` |
| 12 | [Collections & Maps](https://devops-monk.com/tutorials/java8/collections-maps/) | `ch12_collections/` |
| 13 | [CompletableFuture](https://devops-monk.com/tutorials/java8/completablefuture/) | `ch13_async/` |
| 14 | [New APIs](https://devops-monk.com/tutorials/java8/new-apis/) | `ch14_newapis/` |
| 16 | [Best Practices](https://devops-monk.com/tutorials/java8/best-practices/) | `ch16_bestpractices/` |
