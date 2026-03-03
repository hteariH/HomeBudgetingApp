# Home Budgeting App

A personal home budgeting web application built with Spring Boot (Kotlin) and React (TypeScript).

## Overview

This application helps users track their personal finances, including accounts, transactions, transfers, and categories. It provides a modern web interface for managing budgets and visualizing financial data.

### Tech Stack

- **Backend:**
  - Language: Kotlin 2.1.20
  - Framework: Spring Boot 3.4.3
  - Build Tool: Maven
  - Persistence: Spring Data JPA (Hibernate)
  - Database: H2 (Development/Local), MariaDB (Production)
  - Database Migrations: Flyway
  - Java Version: 25
- **Frontend:**
  - Language: TypeScript (React)
  - Framework: React 19
  - UI Library: Tailwind CSS (implied), Lucide React, Recharts
  - Package Manager: npm
  - Version: Node.js v20.11.1, npm 10.2.4

---

## Requirements

To build and run this project, you will need:

- **JDK 25** or higher.
- **Node.js v20.11.1** (automatically managed by Maven during full build).
- **npm 10.2.4** (automatically managed by Maven during full build).
- **Maven 3.x** (or use the provided Maven wrapper if available).
- **MariaDB** (for production profile).

---

## Setup & Run

### Development Mode (Local)

The local profile uses an H2 in-memory database and is pre-configured for rapid development.

1.  **Run Backend:**
    ```bash
    mvn spring-boot:run -Dspring-boot.run.profiles=local
    ```
    The backend will be available at [http://localhost:8080](http://localhost:8080).
    The H2 console is available at [http://localhost:8080/h2-console](http://localhost:8080/h2-console).

2.  **Run Frontend (Hot Reload):**
    ```bash
    cd src/main/frontend
    npm install
    npm start
    ```
    The frontend will be available at [http://localhost:3000](http://localhost:3000). Requests are proxied to the backend at `localhost:8080`.

### Production Mode

In production mode, the frontend is built and bundled into the backend JAR.

1.  **Full Build (Backend + Frontend):**
    ```bash
    mvn clean install
    ```
    This will install Node.js/npm locally, install frontend dependencies, build the frontend, and package everything into a single JAR file in the `target` directory.

2.  **Run JAR:**
    ```bash
    java -jar target/homebudgeting-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod
    ```
    *Ensure you have a MariaDB instance running and configured (see [Environment Variables](#environment-variables)).*

---

## Scripts

### Maven Goals (Root)
- `mvn clean install`: Builds both frontend and backend and packages them together.
- `mvn spring-boot:run`: Runs the Spring Boot application.
- `mvn test`: Runs backend tests.

### npm Scripts (`src/main/frontend`)
- `npm start`: Starts the React development server.
- `npm run build`: Builds the React production bundle.
- `npm test`: Runs frontend tests using Jest.
- `npm run eject`: Ejects from Create React App (not recommended).

---

## Environment Variables

When running in `prod` profile, the following environment variables can be used to configure the database:

| Variable | Description | Default Value |
| :--- | :--- | :--- |
| `DB_USERNAME` | MariaDB username | `homebudget` |
| `DB_PASSWORD` | MariaDB password | `homebudget` |

---

## Tests

### Backend Tests
Run backend tests using Maven:
```bash
mvn test
```

### Frontend Tests
Run frontend tests using npm:
```bash
cd src/main/frontend
npm test
```

---

## Project Structure

```text
HomeBudgetingApp/
├── pom.xml                 # Maven project configuration
├── src/
│   ├── main/
│   │   ├── kotlin/         # Kotlin backend source code
│   │   │   └── com.homebudget.app
│   │   │       ├── config/
│   │   │       ├── controller/
│   │   │       ├── domain/     # JPA Entities (Account, Category, Transaction, etc.)
│   │   │       ├── dto/        # Data Transfer Objects
│   │   │       ├── repository/ # Spring Data JPA Repositories
│   │   │       └── service/    # Business Logic
│   │   ├── resources/      # Backend resources
│   │   │   ├── application.yml
│   │   │   ├── application-local.yml
│   │   │   ├── application-prod.yml
│   │   │   └── db/migration/   # Flyway migrations
│   │   └── frontend/       # React frontend source code
│   │       ├── public/
│   │       ├── src/
│   │       │   ├── App.tsx
│   │       │   └── index.css
│   │       ├── package.json
│   │       └── tsconfig.json
│   └── test/
│       └── kotlin/         # Backend tests
└── target/                 # Build artifacts
```

---

## License

TODO: Specify the license for this project (e.g., MIT, Apache 2.0).

---

## TODOs
- [ ] Add specific deployment instructions for cloud platforms.
- [ ] Define project license.
- [ ] Expand documentation on API endpoints.
