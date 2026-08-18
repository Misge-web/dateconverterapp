# Ethiopian ↔ Gregorian Date Converter

A distributed date-conversion application demonstrating **Java RMI** (Remote Method Invocation) with a **JavaFX** graphical interface.

---

## Architecture

```
┌─────────────────────────┐        RMI / TCP        ┌──────────────────────────────┐
│   JavaFX Client (UI)    │ ──────────────────────► │   RMI Server                 │
│                         │                          │                              │
│  DateConverterUI        │   ethiopianToGregorian() │  DateConverterServiceImpl    │
│  DateConverterClient    │   gregorianToEthiopian() │  (conversion logic lives     │
│                         │ ◄────────────────────── │   entirely on the server)    │
└─────────────────────────┘    result string         └──────────────────────────────┘
```

| Layer | Class | Role |
|-------|-------|------|
| RMI Interface | `rmi.DateConverterService` | Remote contract (extends `Remote`) |
| Server impl | `rmi.DateConverterServiceImpl` | Conversion algorithms + validation |
| Server startup | `rmi.DateConverterServer` | Creates RMI registry, binds service |
| RMI client | `client.DateConverterClient` | Looks up stub, delegates calls |
| JavaFX UI | `client.DateConverterUI` | Input fields, buttons, result display |

---

## Prerequisites

| Requirement | Version |
|-------------|---------|
| JDK | 11 or later |
| JavaFX SDK | 17 or later (download from [gluonhq.com](https://gluonhq.com/products/javafx/)) |

---

## Quick Start

### 1. Set the JavaFX path

```bat
set JAVAFX_LIB=C:\path\to\javafx-sdk-21\lib
```

### 2. Compile

```bat
compile.bat
```

### 3. Start the server (keep this window open)

```bat
run-server.bat
```

Expected output:
```
==============================================
  Date Converter RMI Server started
  Listening on port : 1099
  Service name      : DateConverterService
==============================================
```

### 4. Start the client (in a new terminal)

```bat
run-client.bat
```

---

## Using the Application

1. **Ethiopian → Gregorian**: Fill in the Ethiopian Day / Month / Year fields, click **Eth → Greg**.
2. **Gregorian → Ethiopian**: Fill in the Gregorian Day / Month / Year fields, click **Greg → Eth**.
3. **Clear**: Resets all fields and the result area.

After a successful conversion the result fields on the opposite side are automatically populated.

---

## Calendar Notes

- The Ethiopian calendar has **12 months of 30 days** plus a 13th month (*Pagume*) of 5 days (6 in a leap year).
- Ethiopian leap years occur every 4 years when `year % 4 == 3`.
- The Ethiopian year is approximately **7–8 years behind** the Gregorian year.
- Conversion uses **Julian Day Numbers (JDN)** as an intermediate representation for accuracy.

### Example conversions

| Ethiopian | Gregorian |
|-----------|-----------|
| 01/01/2016 | 11/09/2023 |
| 29/04/2016 | 07/01/2024 |
| 01/01/2017 | 11/09/2024 |

---

## Concepts Demonstrated

- **Java RMI** – remote interface, stub generation, registry binding
- **Distributed computation** – conversion logic runs on the server, not the client
- **Client-server model** – clean separation between UI and business logic
- **JavaFX** – modern Java desktop UI with event-driven programming
- **Input validation** – both client-side (empty check) and server-side (range check)
- **Error handling** – network errors, invalid dates, and RMI exceptions all surface gracefully in the UI
