# AutoBook Milestone 4: Enterprise AI Walkthrough and Demo Script

Target length: about 8–10 minutes. As in Milestones 2 and 3, most of the time is spent in VS Code on the real
implementation. Three live demonstrations (A, B, C) show the agents working with a real Gemini API key.

No video has been recorded yet; this is the script to record from. In VS Code, **Cmd+P** opens a file by name and
**Cmd+F** searches inside it (Ctrl on Windows). Each section lists the search term to type.

## Before recording

1. Get a Gemini API key from Google AI Studio (<https://aistudio.google.com/apikey>). Put it **only** in `.env`:
   ```bash
   cp .env.example .env     # only if .env does not exist yet
   # edit .env: GEMINI_API_KEY=<your key>   (never commit .env)
   git status               # .env must NOT appear
   ```
2. Start the stack, keeping the existing database volume:
   ```bash
   docker compose up -d --build
   docker compose ps                                   # both services "healthy"
   docker compose logs app | grep "AI chat model"      # "AI chat model enabled (gemini-3.5-flash)"
   ```
3. Do one practice question on the Assistant page before recording. Gemini's first answer can take a few seconds.
4. For Demo B you need a booked appointment that starts within the next 24 hours. If there is none: sign in as
   `service@downtownautocare.example.com`, add an Oil Change slot for tomorrow under **Availability**, then sign in as
   `jada.nguyen@example.com` and book it on the website.
5. Open these files in tabs, in this order:
   `pom.xml`, `application.properties`, `AiModels.java`, `ConversationalAgentService.java`, `BookingAgentTools.java`,
   `PendingActionStore.java`, `BookingConfirmationService.java`, `AgentBookingConcurrencyIntegrationTests.java`,
   `ReminderScheduler.java`, `ReminderAgentService.java`, `V4__create_appointment_reminders.sql`,
   `autobook-faq.md`, `KnowledgeBaseService.java`, `RagQuestionAnswerService.java`.
6. Open the GitHub Actions page and the latest run for `milestone-4` (see section 6 below).
7. Close Slack, email, and anything else that could show a notification. Never show the `.env` file on screen.

---

## 1. Introduction (0:00 – 0:40)

**Show:** `docs/diagrams/milestone4-ai-architecture.png`.

> "This is AutoBook Milestone 4. I added three AI agents using Spring AI and Google's Gemini Developer API. Agent 1
> is a conversational booking assistant with tool calling. Agent 2 is a scheduled agent that writes appointment
> reminders without anyone asking. Agent 3 answers policy questions with retrieval-augmented generation.
> None of them replace the existing architecture: they call the same services, transactions, security rules, and
> notification outbox as the rest of AutoBook."

## 2. Spring AI and Gemini configuration (0:40 – 1:30)

**Show:** `pom.xml`, search `spring-ai`. Then `application.properties`, search `spring.ai`.

> "Spring AI 2.0.2 comes in through its BOM, with the Google GenAI chat and embedding starters. I use the Gemini
> Developer API with an API key, not Vertex AI, so there's no project or service account. The key comes only from
> the `GEMINI_API_KEY` environment variable. The model names are configurable: `gemini-3.5-flash` for chat and
> `gemini-embedding-001` for embeddings.
>
> Spring AI fails at startup if Gemini is enabled without a key, so the provider defaults to `none`. Docker Compose
> turns it on only when a key is present."

**Show:** `AiModels.java`, search `chatClient()`.

> "Everything AI goes through `AiModels`. Without a key, the AI endpoints answer 503 'unavailable', and the rest of
> AutoBook keeps working. That's also how the automated tests run without a key."

## 3. Agent 1 code: tools and identity (1:30 – 3:00)

**Show:** `ConversationalAgentService.java`, search `.tools(tools)`.

> "Each chat message goes to Gemini through Spring AI's `ChatClient`, with a system prompt, the last ten turns of
> this conversation, and a tools object. The tools object is created for this customer only, from the Spring Security
> principal."

**Show:** `BookingAgentTools.java`, search `@Tool`.

> "These `@Tool` methods are what Gemini can call: search slots, list my appointments, look up policies, and request
> a booking or cancellation. None of them takes a user id, so the model can't act for someone else. They call the
> existing `SlotService` and `AppointmentService`, and the search returns clarification notes and nearest
> alternatives when nothing matches."

## 4. Confirmation before write (3:00 – 4:15)

**Show:** `BookingAgentTools.java`, search `requestBooking`.

> "`requestBooking` does not book. It only creates a pending action and tells the model 'confirmation required, not
> booked yet'. The model has no tool that writes."

**Show:** `PendingActionStore.java`, search `claim(`.

> "A pending action has a random single-use token tied to one customer and one conversation, and it expires after
> five minutes. Another customer's token gets 403."

**Show:** `BookingConfirmationService.java`, search `bookingService.book`.

> "Only when the customer presses Confirm, or types yes, does this call the existing `BookingService.book`
> transaction, with the same row lock, conditional update, and unique constraint as every other booking. If the slot
> was taken in the meantime, the customer gets a 409 with alternatives."

## 5. Demo A: conversational booking (4:15 – 5:30)

**Browser:** sign in as `jada.nguyen@example.com` / `password123`, click **Assistant**.

1. Type "Find the earliest oil change this week." Read out the slots it lists.
2. Type "Book the first one." Point at the **Confirm** card.
   > "Nothing is booked yet." Open **My appointments** in a new tab to show it is not there.
3. Press **Confirm**. Read the confirmation number. Refresh **My appointments**: it's there.
4. In the terminal, show it in PostgreSQL:
   ```bash
   docker compose exec db psql -U autobook -d autobook -c "SELECT appointment_id, slot_id, status FROM appointments ORDER BY appointment_id DESC LIMIT 3;"
   ```
5. Sign out, sign in as `alex.rivera@example.com`, and try to book the same slot: it's rejected as already booked.

## 6. Required concurrency test (5:30 – 6:10)

**Show:** `AgentBookingConcurrencyIntegrationTests.java`, then the GitHub Actions run.

> "This is the required test. Two customers book the same slot at the same moment: one through the agent's real
> book tool and confirmation, the other directly through `BookingService`, and in a second test both through the
> agent. Nothing is mocked except the Gemini model itself. Five rounds each, and every time exactly one booking
> succeeds and PostgreSQL holds exactly one active appointment for the slot."

Show the GitHub Actions result for this test only if a run of `b431a55` or later has passed. If it has not, say the
test is written but not yet verified in CI, and show Demo A's step 5 (Alex is rejected) as the live evidence instead.

## 7. Agent 2: reminders (6:10 – 7:30)

**Show:** `ReminderScheduler.java`, then `ReminderAgentService.java`, search `transactionTemplate.execute`, then
`V4__create_appointment_reminders.sql`.

> "Every 15 minutes, with no user involved, the reminder agent finds booked appointments in the next 24 hours, asks
> Gemini for a short reminder, and puts it in the Milestone 3 notification outbox. The outbox row and a row in the
> new `appointment_reminders` table are written in one transaction, and that table is unique per appointment, so
> nothing is ever sent twice. If Gemini is down, it waits for a later run, or close to the appointment it sends a
> fixed template that's recorded as TEMPLATE, not as AI."

**Demo B, terminal:**
```bash
B=http://localhost:8080
T=$(curl -s -c a.txt -b a.txt $B/api/csrf | python3 -c "import sys,json;print(json.load(sys.stdin)['token'])")
curl -s -c a.txt -b a.txt -d "username=morgan.lee@example.com&password=password123&_csrf=$T" $B/login -o /dev/null
T=$(curl -s -c a.txt -b a.txt $B/api/csrf | python3 -c "import sys,json;print(json.load(sys.stdin)['token'])")
curl -s -b a.txt -X POST -H "X-CSRF-TOKEN: $T" $B/api/admin/ai/reminders/run
curl -s -b a.txt $B/api/admin/ai/reminders
curl -s -b a.txt $B/mock-notifications/api/notifications
curl -s -b a.txt -X POST -H "X-CSRF-TOKEN: $T" $B/api/admin/ai/reminders/run
```
> Point out: the first run queues the reminder written by Gemini (`GEMINI`), the notification service delivered it,
> and the second run sends nothing again.

## 8. Agent 3: RAG (7:30 – 8:45)

**Show:** `autobook-faq.md` (scroll the section headings; point at a "Demonstration policy" label),
`KnowledgeBaseService.java`, search `split`, and `RagQuestionAnswerService.java`, search `retrieve`.

> "The knowledge base is one file. AutoBook splits it into one chunk per section, embeds the chunks with Gemini, and
> stores them in Spring AI's `SimpleVectorStore`. For a question, only the most similar sections are sent to Gemini,
> with rules to answer only from them and cite them. If nothing is relevant enough, AutoBook says it isn't covered and
> doesn't call the model at all. Sections like business hours are labelled as demonstration policies because AutoBook
> doesn't define them."

**Demo C, browser** (Assistant page, Policy questions panel):
1. "What is the cancellation policy?" Point at the `Cancellation policy` source.
2. "Can I reschedule an appointment?" Point at the `Rescheduling` source.
3. "What is the capital of France?" It says the knowledge base doesn't cover it.

## 9. Wrap-up (8:45 – 9:15)

> "All three agents use Gemini only for language and act only through AutoBook's existing services, security, and
> transactions. The automated tests use test doubles in place of Gemini, so they verify AutoBook's behavior, not
> Gemini's answers; the live answers are what you just saw in the demo. Thanks for watching."
