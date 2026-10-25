# Week 0: Project Bootstrap & Architectural Foundation

## Objective
Establish the foundational structure of the Post-Trade Clearing Platform. This includes defining the multi-module Maven project layout, standardizing dependencies, and creating the core shared libraries required for event-driven microservices communication.

## 1. Multi-Module Project Structure
A root Maven POM (`pom.xml`) was created to govern dependency management across the entire platform. This ensures all microservices use identical versions of Spring Boot, Spring Cloud, Kafka, and PostgreSQL libraries.

**Modules initialized:**
- `shared-libraries`: Core DTOs, Kafka Events, and Exceptions.
- `api-gateway`: Spring Cloud Gateway with WebFlux.
- `market-integration-gateway`: Ingests external trade requests.
- `trade-service`: Validates and persists trades.
- `position-service`: Manages participant asset positions.
- `clearing-service`: Executes settlement and netting logic.
- `corporate-action-service`: Handles splits and dividends.
- `reference-data-service`: Manages instrument and participant metadata.
- `notification-service`: Triggers external alerts (email/webhooks).

## 2. Technology Stack
- **Languages:** Java 21
- **Framework:** Spring Boot 3.3.5
- **Build Tool:** Maven

## 3. Shared Libraries (`shared-libraries`)
To enforce strict contracts between microservices, a dedicated `shared-libraries` module was built.

### Key Components:
- **`TradeRequest.java`**: A tightly validated DTO object. Ensures incoming API requests contain mandatory fields (e.g., `accountId`, `instrumentId`, `quantity`, `price`).
- **`TradeSubmittedEvent.java`**: A Java `record` representing the immutable Kafka event fired when a trade enters the platform. It strictly models the data contract that downstream consumers (like the Position Service) rely on.

## 4. Architectural Principles Enforced
- **Hexagonal Architecture:** Business domain logic is isolated from infrastructure mapping.
- **Idempotency Standards:** Events are structured with unique identifiers (`tradeId`) to prevent duplicate processing failures later in the pipeline.
