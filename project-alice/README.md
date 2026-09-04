# Project Alice

## Overview

Project Alice is a conversational AI application designed to facilitate seamless interactions between users and the AI assistant. The project focuses on creating a robust domain model that encapsulates the core functionalities of conversations, messages, and their associated behaviors.

## Features

- **Immutable Domain Models**: The application uses plain Dart models to represent core entities such as `Conversation` and `Message`, ensuring that their states remain consistent throughout their lifecycle.
- **Value Equality**: All domain models implement value equality, allowing for reliable comparisons and hash code generation.
- **Error Handling**: The project includes a structured approach to handle domain-specific errors, ensuring that invalid states are managed gracefully.

## Directory Structure

- **lib/conversation/domain/entities**: Contains the core domain models including `Conversation`, `Message`, and error handling classes.
- **lib/conversation/domain/value_objects**: Houses value objects such as `MessageRole` that define specific roles within a conversation.
- **test/conversation/domain/entities**: Contains unit tests for the domain models to ensure correctness and reliability.

## Getting Started

To get started with Project Alice, clone the repository and run the following commands:

```bash
flutter pub get
```

This will install the necessary dependencies defined in the `pubspec.yaml` file.

## Running Tests

To run the unit tests for the domain models, use the following command:

```bash
flutter test
```

This will execute all tests located in the `test/conversation/domain/entities` directory.

## Contribution

Contributions are welcome! Please feel free to submit a pull request or open an issue for any enhancements or bug fixes.

## License

This project is licensed under the MIT License. See the LICENSE file for more details.