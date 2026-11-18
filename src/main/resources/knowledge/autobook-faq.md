# AutoBook Knowledge Base

AutoBook is the CMPE 172 course project "AutoBook — Automotive Service Appointment System". This file is
the knowledge source for AutoBook's question-answering assistant. Each section below is retrieved on its own.

Sections marked "Demonstration policy" are fictional policies written for the course demonstration. They are
not the policies of any real business. Everything else describes how the AutoBook application actually works.

## About AutoBook

AutoBook is a web application for booking automotive service appointments. Customers browse open appointment
slots published by repair shops (providers), book a slot, and view or cancel their appointments. Providers
manage their own availability. Administrators manage provider information and review activity.
Customers, providers, and administrators each sign in with their own account. AutoBook does not offer
self-service registration or password reset in this version.

## Services offered

AutoBook currently lists four service types:
- Oil Change: engine oil and filter replacement with a basic fluid check. Typical duration 45 minutes.
- Vehicle Inspection: safety and maintenance inspection of common vehicle systems. Typical duration 60 minutes.
- Vehicle Diagnostics: diagnostic scan and technician review for warning lights or performance problems.
  Typical duration 90 minutes.
- Tire Service: tire rotation, pressure check, and tread inspection. Typical duration 45 minutes.
Each appointment slot is for one service at one provider. The service is chosen by choosing the slot.

## Repair shops (providers)

The demonstration data includes two providers: Downtown Auto Care (phone 408-555-0101) and Westside Tire and
Inspection (phone 408-555-0112). Each provider publishes its own appointment slots. Different providers may
offer different services at different times. The slot page shows the provider's name and phone number.

## Business hours

Demonstration policy: participating repair shops accept appointments Monday through Friday from 8:00 AM to
6:00 PM and Saturday from 9:00 AM to 2:00 PM, Pacific Time. Shops are closed on Sundays and on major public
holidays. The real available times are always the open slots shown in AutoBook, because each provider decides
which slots to publish.

## How to book an appointment

To book, sign in as a customer, open Browse slots (or search by service, provider, and date), choose an open
slot, and confirm the booking. AutoBook books the slot immediately inside a database transaction and shows a
confirmation number in the format AB-00012. A confirmation notification is sent after the booking is saved.
Only customer accounts can book. A slot that has already started cannot be booked.
The AutoBook assistant can also find slots and prepare a booking, but it always asks you to confirm before it
books anything.

## Double booking and slot availability

Each appointment slot can have only one active booking. If two customers try to book the same slot at the same
moment, AutoBook locks the slot in the database: exactly one booking succeeds and the other customer is told
that the slot has already been booked and can choose another time. Booked slots no longer appear as open slots.

## Cancellation policy

Customers can cancel their own booked appointments from My appointments at any time before the appointment
starts. Appointments that have already started cannot be cancelled. A cancelled appointment stays in your
appointment history with the status Cancelled, and the time slot becomes open again for other customers.
A cancellation notification is sent after the cancellation is saved.
Demonstration policy: as a courtesy to the repair shop, please cancel at least 24 hours in advance.
AutoBook does not charge cancellation fees.

## Rescheduling

AutoBook does not have a separate reschedule button. To move an appointment, cancel the current appointment and
book a new open slot. Cancelling first reopens your old slot; if you want to keep your old time until you are sure
about the new one, book the new slot first and then cancel the old appointment.

## Appointment reminders and notifications

AutoBook sends a confirmation when you book, a notice when you cancel, and a reminder before an upcoming
appointment. Reminders are written for you by the AutoBook assistant and sent through AutoBook's notification
service. In this demonstration the notification service is a mock that records messages instead of sending real
email. If the notification service is temporarily down, your booking is still saved and the message is retried
later.

## Arrival, payment, and vehicle requirements

Demonstration policy: please arrive 10 minutes before your appointment and bring your vehicle registration.
If you arrive more than 15 minutes late, the shop may need to rebook you. Payment is made directly at the repair
shop after the service; AutoBook does not take payments or store card details.

## Provider policies

Providers can add appointment slots of up to 8 hours that do not overlap their other slots, and can remove open
slots. A provider cannot remove a slot that is already booked or closed; the customer has to cancel first.
Providers can see who booked with them but cannot book or cancel on behalf of customers.

## Privacy and accounts

Customers can only see and change their own appointments. The AutoBook assistant always acts as the signed-in
customer and cannot view or change other customers' appointments. AutoBook does not store payment information.

## Using the AutoBook assistant

The assistant can search open slots, list your appointments, prepare a booking or a cancellation, and answer
questions from this knowledge base. It never books or cancels until you confirm. Confirmations expire after
5 minutes. If a slot is taken before you confirm, the assistant tells you and suggests other open times.
If the knowledge base does not cover your question, the assistant says so instead of guessing; please contact
the repair shop directly in that case.
