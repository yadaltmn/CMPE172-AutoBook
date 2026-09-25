# AutoBook Wireframes

These wireframes describe the planned user flow for the appointment workflow. Milestone 1 implements only the database-backed skeleton and read endpoints; the full booking flow is future milestone work.

## Flow

Home -> Available Slots -> Book Appointment -> Confirmation

## 1. Home

```text
+--------------------------------------------------+
| AutoBook                                         |
| Automotive Service Appointment System             |
+--------------------------------------------------+
| [View Available Slots]                            |
|                                                  |
| Summary                                           |
| Providers: 2                                      |
| Services: 4                                       |
| Available Slots: 5                                |
+--------------------------------------------------+
```

Purpose: introduce AutoBook and let the user begin browsing available appointment slots.

## 2. View Available Slots

```text
+--------------------------------------------------+
| AutoBook > Available Slots                        |
+--------------------------------------------------+
| Service: [All v]  Provider: [All v] Date: [____] |
| [Search]                                          |
+--------------------------------------------------+
| Time                 Provider        Service      |
| Oct 5, 9:00 AM       Downtown Auto   Oil Change   |
| Oct 5, 10:30 AM      Downtown Auto   Inspection   |
| Oct 7, 9:30 AM       Westside Tire   Tire Service |
|                                                  |
|                         [Book] [Book] [Book]      |
+--------------------------------------------------+
```

Purpose: show available appointment slots and future filters for provider, service, and date.

## 3. Book Appointment

```text
+--------------------------------------------------+
| AutoBook > Book Appointment                       |
+--------------------------------------------------+
| Selected Slot                                     |
| Oil Change                                        |
| Downtown Auto Care                                |
| Oct 5, 2026, 9:00 AM - 9:45 AM                   |
+--------------------------------------------------+
| Customer Information                              |
| Name:  [____________________]                     |
| Email: [____________________]                     |
| Phone: [____________________]                     |
|                                                  |
| [Cancel]                              [Confirm]   |
+--------------------------------------------------+
```

Purpose: confirm the selected service slot and collect or verify customer information before booking.

## 4. Confirmation

```text
+--------------------------------------------------+
| AutoBook > Confirmation                           |
+--------------------------------------------------+
| Appointment Confirmed                             |
|                                                  |
| Service:  Oil Change                              |
| Provider: Downtown Auto Care                      |
| Time:     Oct 5, 2026, 9:00 AM                    |
| Status:   Booked                                  |
|                                                  |
| [View My Appointments]     [Back to Home]         |
+--------------------------------------------------+
```

Purpose: show the final booking result and provide next actions. In future milestones, this screen will also reflect the mock confirmation notification behavior.
