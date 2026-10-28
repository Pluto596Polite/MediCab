# Medicine Tracker App Plan

## Overview
An Android application built with **Kotlin** and **Jetpack Compose** to help users manage their medicine inventory, track expiry dates, and receive proactive notifications before medications expire.

## Core Features
### 1. Inventory Management
- **Dashboard:** View current medications in the cupboard.
- **Add Medication:**
    - **Scan Feature:** Use the camera to scan barcodes/QR codes for quick identification and data entry.
    ies.
    - **Manual Entry:** A fallback form to manually type in medication name, category, and expiry date.
- **Status Tracking:** Identify which medicines are safe, expiring soon, or expired.

### 2. Expiry Alerts
- **Push Notifications:** Automated alerts when a medication is approaching its expiration date.
- **Background Processing:** System to check the database periodically for upcoming dates.

### 3. Local Storage
- **Database:** SQLite via the **Room Persistence Library**.
- **Data:** Offline-first storage for privacy and reliability.

## Technical Stack
- **Language:** Kotlin
- **UI:** Jetpack Compose
- **Architecture:** MVVM (Model-View-ViewModel)
- **Database:** Room
- **Dependency Injection:** Hilt
- **Image Loading:** Coil
- **Scanning:** Google ML Kit (Barcode Scanning)
- **Background Tasks:** WorkManager (for expiry checks)

## Database Schema (Room)
| Field | Type | Description |
| :--- | :--- | :--- |
| `id` | Integer (PK) | Unique identifier |
| `name` | String | Name of the medication |
| `description` | String | Notes or dosage instructions |
| `category` | String | Category (e.g., Allergy, Pain) |
| `expiryDate` | Long | Timestamp of the expiration date |
| `barcode` | String | The raw data from the barcode scan |
| `last_used` | Long | Timestamp of last entry |
| `is_active` | Boolean | To filter out used/removed items |

## Implementation Roadmap

### Phase 1: Foundation & Architecture
- Project setup with Clean Architecture and Hilt.
- Define Room Entities and DAOs.
- Establish a basic UI Theme (Material 3).

### Phase 2: Manual Entry & Local DB
- Create the "Add Medication" screen (Manual Form).
- Implement the "Inventory List" screen.
- Connect the UI to the Room Database via ViewModels.

### Phase 3: Camera & Scanning
- Integrate **CameraX** and **ML Kit**.
- Implement the scanner overlay.
- Connect the scan result to the "Add Medication" flow.

### Phase 4: Notifications & Polish
- Implement **WorkManager** to run a daily check.
- Configure **Notification Channels** and push alerts.
- UI/UX refinements and testing.

## Success Metrics
- Successfully scan a barcode and auto-fill the medication name.
- Receive a notification 30 days before a scheduled expiry date.
- View a filtered list of "Expiring Soon" items.
